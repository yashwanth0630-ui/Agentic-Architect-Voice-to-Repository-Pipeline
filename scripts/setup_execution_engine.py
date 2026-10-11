"""
Safe Setup Execution Engine for Idea Beacon / Agentic Architect
Executes approved setup operations deterministically with strict boundary isolation,
explicit argument vectors, step-by-step persistence, and graceful cancellation.
"""

import asyncio
from datetime import datetime, timezone
import json
import os
import shutil
import subprocess
import sys
import threading
import time
from typing import Any, Callable, Dict, List, Optional, Tuple

from dev_server_manager import dev_server_manager
from environment_setup_agent import SetupOperation, SetupPlan, environment_setup_agent
from project_manager import ProjectManager, project_manager


class SetupSecurityError(Exception):
    """Raised when safety, boundary, or approval rules are violated."""
    pass


class SetupExecutionEngine:
    """Deterministic, secure engine executing validated environment setup plans."""

    def __init__(self, pm: ProjectManager = project_manager):
        self.pm = pm
        self._cancellation_tokens: Dict[str, threading.Event] = {}
        self._active_plans: Dict[str, SetupPlan] = {}
        self._lock = threading.Lock()

    def get_plan_state(self, project_id: str) -> Optional[SetupPlan]:
        with self._lock:
            if project_id in self._active_plans:
                return self._active_plans[project_id]

        # Check persistent file
        ws = self.pm.get_safe_workspace_path(project_id)
        state_file = os.path.join(ws, "setup_state.json")
        if os.path.isfile(state_file):
            try:
                with open(state_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    plan = SetupPlan.from_dict(data)
                    with self._lock:
                        self._active_plans[project_id] = plan
                    return plan
            except Exception:
                pass
        return None

    def cancel_setup(self, project_id: str) -> bool:
        """Signals active setup for project to halt at the next safe boundary."""
        with self._lock:
            if project_id not in self._cancellation_tokens:
                token = threading.Event()
                token.set()
                self._cancellation_tokens[project_id] = token
            else:
                token = self._cancellation_tokens[project_id]
                token.set()

            if project_id in self._active_plans:
                self._active_plans[project_id].status = "cancelled"
                self._save_state(self._active_plans[project_id])
            dev_server_manager.stop_server(project_id)
            return True

    def _save_state(self, plan: SetupPlan):
        """Persists setup state to projects/<project_id>/setup_state.json."""
        plan.updated_at = datetime.now(timezone.utc).isoformat()
        state_file = os.path.join(plan.workspace_path, "setup_state.json")
        try:
            with open(state_file, "w", encoding="utf-8") as f:
                json.dump(plan.to_dict(), f, indent=2)
        except Exception as e:
            print(f"[SetupEngine] Notice: Failed to save setup state: {e}")

    @staticmethod
    def _is_venv_hydrated(workspace: str) -> bool:
        """Checks if a valid Python virtual environment is already present."""
        py = environment_setup_agent.get_venv_python_path(workspace)
        if not os.path.isfile(py):
            return False
        try:
            rc = subprocess.run([py, "--version"], capture_output=True, timeout=2.0).returncode
            return rc == 0
        except Exception:
            return False

    @staticmethod
    def _is_node_modules_hydrated(workspace: str) -> bool:
        """Checks if node_modules exists and is populated."""
        nm = os.path.join(workspace, "node_modules")
        return os.path.isdir(nm) and len(os.listdir(nm)) > 3

    def execute_plan(
        self,
        plan: SetupPlan,
        prompt: str,
        on_progress: Optional[Callable[[int, int, str], None]] = None
    ) -> SetupPlan:
        """
        Executes an approved setup plan step-by-step.
        Guarantees:
        - Rejects unapproved execution.
        - Enforces workspace boundaries.
        - Uses argument vectors (shell=False).
        - Skips duplicate / already satisfied operations.
        - Halts safely on cancellation.
        """
        # 1. Approval Gate
        if not plan.approved:
            plan.status = "failed"
            plan.error = "Security violation: plan requires explicit user approval before execution."
            raise SetupSecurityError(plan.error)

        project_id = plan.project_id
        workspace = os.path.abspath(plan.workspace_path)

        # 2. Workspace boundary check
        safe_root = os.path.abspath(self.pm.projects_dir)
        if not (workspace.startswith(safe_root + os.sep) or workspace == safe_root):
            plan.status = "failed"
            plan.error = f"Security violation: workspace '{workspace}' escapes authorized directory."
            raise SetupSecurityError(plan.error)

        with self._lock:
            if project_id not in self._cancellation_tokens:
                self._cancellation_tokens[project_id] = threading.Event()
            cancel_token = self._cancellation_tokens[project_id]
            self._active_plans[project_id] = plan

        plan.status = "in_progress"
        self._save_state(plan)

        total_ops = len(plan.operations)

        try:
            for idx, op in enumerate(plan.operations):
                # Check cancellation boundary
                if cancel_token.is_set():
                    op.status = "cancelled"
                    plan.status = "cancelled"
                    plan.error = "Setup was cancelled by user."
                    self._save_state(plan)
                    return plan

                op.status = "in_progress"
                self._save_state(plan)
                if on_progress:
                    on_progress(idx + 1, total_ops, op.title)

                target_cwd = os.path.abspath(os.path.join(workspace, op.cwd_rel))
                os.makedirs(target_cwd, exist_ok=True)

                # ==========================================================
                # OPERATION: SCAFFOLD_FILES
                # ==========================================================
                if op.op_type == "SCAFFOLD_FILES":
                    try:
                        files = environment_setup_agent.generate_scaffolding_files(
                            tech_stack=plan.tech_stack,
                            project_name=plan.project_name,
                            prompt=prompt
                        )
                        created_paths = []
                        for rel_path, content in files.items():
                            abs_dest = os.path.join(workspace, rel_path)
                            os.makedirs(os.path.dirname(abs_dest), exist_ok=True)
                            with open(abs_dest, "w", encoding="utf-8") as f:
                                f.write(content)
                            created_paths.append(rel_path)

                        op.status = "completed"
                        op.output_log = f"Created {len(created_paths)} files: {', '.join(created_paths[:4])}"
                        self.pm.update_project(project_id, files=created_paths)
                    except Exception as e:
                        op.status = "failed"
                        op.error = f"Scaffolding error: {str(e)}"
                        plan.status = "failed"
                        plan.error = op.error
                        self._save_state(plan)
                        return plan

                # ==========================================================
                # OPERATION: CREATE_VENV
                # ==========================================================
                elif op.op_type == "CREATE_VENV":
                    if self._is_venv_hydrated(target_cwd):
                        op.status = "skipped"
                        op.output_log = "Existing valid virtual environment detected. Skipping creation."
                    else:
                        rc, out, err = self._run_command(op.command_args, target_cwd)
                        if rc != 0:
                            op.status = "failed"
                            op.error = f"Failed to create virtual environment: {err or out}"
                            plan.status = "failed"
                            plan.error = op.error
                            self._save_state(plan)
                            return plan
                        op.status = "completed"
                        op.output_log = "Virtual environment created successfully."

                # ==========================================================
                # OPERATION: PIP_INSTALL
                # ==========================================================
                elif op.op_type == "PIP_INSTALL":
                    # Run pip install with timeout
                    rc, out, err = self._run_command(op.command_args, target_cwd, timeout=120)
                    if rc != 0:
                        op.status = "failed"
                        op.error = f"Pip dependency installation failed: {err or out}"
                        plan.status = "failed"
                        plan.error = op.error
                        self._save_state(plan)
                        return plan
                    op.status = "completed"
                    op.output_log = "Python dependencies installed successfully."

                # ==========================================================
                # OPERATION: NPM_INSTALL
                # ==========================================================
                elif op.op_type == "NPM_INSTALL":
                    if self._is_node_modules_hydrated(target_cwd):
                        op.status = "skipped"
                        op.output_log = "Node modules already hydrated. Skipping npm install."
                    else:
                        rc, out, err = self._run_command(op.command_args, target_cwd, timeout=180)
                        if rc != 0:
                            op.status = "failed"
                            op.error = f"NPM package installation failed: {err or out}"
                            plan.status = "failed"
                            plan.error = op.error
                            self._save_state(plan)
                            return plan
                        op.status = "completed"
                        op.output_log = "NPM packages installed successfully."

                # ==========================================================
                # OPERATION: VALIDATE
                # ==========================================================
                elif op.op_type == "VALIDATE":
                    rc, out, err = self._run_command(op.command_args, target_cwd, timeout=10)
                    if rc != 0:
                        op.status = "failed"
                        op.error = f"Validation failed: {err or out}"
                        plan.status = "failed"
                        plan.error = op.error
                        self._save_state(plan)
                        return plan
                    op.status = "completed"
                    op.output_log = "Application syntax verified cleanly."

                # ==========================================================
                # OPERATION: START_DEV_SERVER
                # ==========================================================
                elif op.op_type == "START_DEV_SERVER":
                    server_instance = None
                    stack = plan.tech_stack

                    if stack == "fastapi":
                        venv_py = environment_setup_agent.get_venv_python_path(workspace)
                        rc = subprocess.run([venv_py, "-m", "uvicorn", "--version"], capture_output=True).returncode if os.path.isfile(venv_py) else -1
                        active_py = venv_py if rc == 0 else sys.executable
                        server_instance = dev_server_manager.start_fastapi_server(
                            project_id=project_id,
                            workspace_path=workspace,
                            python_exe=active_py
                        )
                    elif stack == "react":
                        from environment_detector import environment_detector
                        host_npm = environment_detector.detect_npm().executable_path or "npm"
                        server_instance = dev_server_manager.start_react_vite_server(
                            project_id=project_id,
                            workspace_path=workspace,
                            npm_cmd=host_npm
                        )
                    elif stack == "fullstack":
                        from environment_detector import environment_detector
                        host_npm = environment_detector.detect_npm().executable_path or "npm"
                        b_py = environment_setup_agent.get_venv_python_path(os.path.join(workspace, "backend"))
                        rc_b = subprocess.run([b_py, "-m", "uvicorn", "--version"], capture_output=True).returncode if os.path.isfile(b_py) else -1
                        active_b_py = b_py if rc_b == 0 else sys.executable
                        server_instance = dev_server_manager.start_fullstack_server(
                            project_id=project_id,
                            backend_dir=os.path.join(workspace, "backend"),
                            frontend_dir=os.path.join(workspace, "frontend"),
                            python_exe=active_b_py,
                            npm_cmd=host_npm
                        )
                    elif stack == "static_web":
                        server_instance = dev_server_manager.start_static_server(
                            project_id=project_id,
                            workspace_path=workspace
                        )
                    else:
                        # Python basic: no long-running dev server; execute run
                        venv_py = environment_setup_agent.get_venv_python_path(workspace)
                        rc, out, err = self._run_command([venv_py, "main.py"], workspace, timeout=10)
                        op.status = "completed" if rc == 0 else "failed"
                        op.output_log = out or err
                        plan.status = "completed" if rc == 0 else "failed"
                        self._save_state(plan)
                        return plan

                    if server_instance and server_instance.status == "running":
                        op.status = "completed"
                        op.output_log = f"Server active at {server_instance.preview_url}"
                        plan.preview_url = server_instance.preview_url
                        plan.preview_port = server_instance.port
                        self.pm.update_project(
                            project_id,
                            status="running",
                            preview_port=server_instance.port,
                            preview_url=server_instance.preview_url
                        )
                    else:
                        op.status = "failed"
                        op.error = server_instance.error if server_instance else "Dev server failed to start"
                        plan.status = "failed"
                        plan.error = op.error
                        self._save_state(plan)
                        return plan

                self._save_state(plan)

            plan.status = "completed"
            self._save_state(plan)
            return plan

        finally:
            with self._lock:
                self._cancellation_tokens.pop(project_id, None)

    @staticmethod
    def _run_command(cmd: List[str], cwd: str, timeout: float = 60.0) -> Tuple[int, str, str]:
        """Runs an explicit argument vector safely without shell=True."""
        try:
            res = subprocess.run(
                cmd,
                cwd=cwd,
                capture_output=True,
                text=True,
                timeout=timeout,
                shell=False
            )
            return res.returncode, res.stdout.strip(), res.stderr.strip()
        except subprocess.TimeoutExpired:
            return -1, "", f"Operation timed out after {timeout} seconds"
        except Exception as e:
            return -1, "", str(e)


setup_execution_engine = SetupExecutionEngine()
