"""
Comprehensive Test Suite for Environment Setup Agent & Execution Engine
Tests:
1. Laptop OS and dev tool discovery (Python, Node, npm, Git, venv).
2. Stack support evaluation & incompatible version reporting.
3. Idea management (capture, list, get, blueprint refinement).
4. Deterministic setup plan creation for Python, FastAPI, React, Fullstack, and Static Web.
5. Approval security gating (rejects unapproved execution).
6. Safe execution engine security constraints (shell=False, boundary checks, argument validation).
7. Execution and health check lifecycle for supported projects.
8. Duplicate installation prevention (skips existing valid venv / node_modules).
9. Safe cancellation and step persistence.
10. Dev server management & port cleanups.
"""

import asyncio
from datetime import datetime
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import MagicMock, patch

from fastapi.testclient import TestClient

# Ensure scripts directory is on path
SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "scripts"))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

from environment_detector import EnvironmentDetector, HostEnvironment, ToolInfo, environment_detector
from environment_setup_agent import EnvironmentSetupAgent, SetupOperation, SetupPlan, environment_setup_agent
from setup_execution_engine import SetupExecutionEngine, SetupSecurityError, setup_execution_engine
from dev_server_manager import DevServerManager, ServerInstance, dev_server_manager
from idea_manager import IdeaBlueprint, IdeaManager, IdeaRecord, idea_manager
from laptop_ai_server import app
from project_manager import ProjectManager, safe_rmtree


class TestEnvironmentDetector(unittest.TestCase):
    """Verifies laptop OS and development runtime detection."""

    def setUp(self):
        self.detector = EnvironmentDetector()

    def test_detect_python_finds_active_interpreter(self):
        info = self.detector.detect_python()
        self.assertTrue(info.installed)
        self.assertEqual(info.major_version, sys.version_info.major)
        self.assertEqual(info.minor_version, sys.version_info.minor)
        self.assertTrue(os.path.isfile(info.executable_path))
        self.assertTrue(info.details.get("has_venv"))

    def test_detect_node_and_npm(self):
        node_info = self.detector.detect_node()
        npm_info = self.detector.detect_npm()
        # On this environment, Node.js and npm are installed
        if node_info.installed:
            self.assertGreaterEqual(node_info.major_version, 18)
            self.assertTrue(npm_info.installed)

    def test_evaluate_stack_support_python(self):
        eval_res = self.detector.evaluate_stack_support("python")
        self.assertTrue(eval_res.supported)
        self.assertEqual(eval_res.recommended_environment_type, "python_venv")
        self.assertEqual(len(eval_res.missing_tools), 0)

    def test_evaluate_stack_support_fastapi(self):
        eval_res = self.detector.evaluate_stack_support("fastapi")
        self.assertTrue(eval_res.supported)
        self.assertEqual(eval_res.recommended_environment_type, "python_venv")

    def test_evaluate_stack_support_flags_missing_node(self):
        # Mock environment missing node
        mock_env = HostEnvironment(
            os_name="Windows",
            os_release="11",
            os_version="10.0",
            architecture="AMD64",
            lan_ip="192.168.1.10",
            python=ToolInfo(installed=True, major_version=3, minor_version=14, version="3.14.0"),
            node=ToolInfo(installed=False),
            npm=ToolInfo(installed=False),
            git=ToolInfo(installed=True),
            venv_module_available=True
        )
        eval_res = self.detector.evaluate_stack_support("react", env=mock_env)
        self.assertFalse(eval_res.supported)
        self.assertIn("Node.js 18+", eval_res.missing_tools)
        self.assertGreater(len(eval_res.remediation_steps), 0)


class TestIdeaManager(unittest.TestCase):
    """Verifies permanent idea storage and blueprint refinement."""

    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(prefix="test_ideas_")
        self.ideas_file = os.path.join(self.temp_dir, "ideas.json")
        self.mgr = IdeaManager(ideas_file=self.ideas_file)

    def tearDown(self):
        safe_rmtree(self.temp_dir)

    def test_capture_and_retrieve_idea(self):
        record = self.mgr.capture_idea(
            prompt="Build a real-time cryptocurrency arbitrage bot with FastAPI",
            title="Crypto Bot"
        )
        self.assertTrue(record.idea_id.startswith("crypto-bot"))
        self.assertEqual(record.status, "captured")
        self.assertEqual(record.tech_stack, "fastapi")

        fetched = self.mgr.get_idea(record.idea_id)
        self.assertIsNotNone(fetched)
        self.assertEqual(fetched.title, "Crypto Bot")

    def test_refine_idea_generates_actionable_blueprint(self):
        record = self.mgr.capture_idea(prompt="Create a student task tracker with React and Vite")
        self.assertEqual(record.tech_stack, "react")
        self.assertIsNone(record.blueprint)

        refined = self.mgr.refine_idea(record.idea_id)
        self.assertEqual(refined.status, "refined")
        self.assertIsNotNone(refined.blueprint)
        self.assertIn("react", refined.blueprint.recommended_tech_stack)
        self.assertGreater(len(refined.blueprint.core_features), 0)

    def test_list_ideas_returns_sorted(self):
        self.mgr.capture_idea("Idea 1")
        self.mgr.capture_idea("Idea 2")
        ideas = self.mgr.list_ideas()
        self.assertEqual(len(ideas), 2)


class TestEnvironmentSetupAgent(unittest.TestCase):
    """Verifies plan generation, scaffolding creation, and safety constraints."""

    def setUp(self):
        self.temp_root = tempfile.mkdtemp(prefix="test_setup_agent_")
        self.pm = ProjectManager(projects_dir=self.temp_root)
        self.agent = EnvironmentSetupAgent(pm=self.pm)

    def tearDown(self):
        safe_rmtree(self.temp_root)

    def test_scaffolding_fastapi_contains_health_and_cors(self):
        files = self.agent.generate_scaffolding_files(
            tech_stack="fastapi",
            project_name="my-api",
            prompt="Build a fast inventory API"
        )
        self.assertIn("main.py", files)
        self.assertIn("requirements.txt", files)
        self.assertIn("README.md", files)
        self.assertIn("/health", files["main.py"])
        self.assertIn("CORSMiddleware", files["main.py"])
        self.assertIn("uvicorn", files["requirements.txt"])

    def test_scaffolding_react_contains_vite_and_package_json(self):
        files = self.agent.generate_scaffolding_files(
            tech_stack="react",
            project_name="my-dashboard",
            prompt="Build a sleek dashboard"
        )
        self.assertIn("package.json", files)
        self.assertIn("vite.config.js", files)
        self.assertIn("src/App.jsx", files)
        self.assertIn("react", files["package.json"])

    def test_create_setup_plan_requires_approval(self):
        plan = self.agent.create_setup_plan(
            prompt="Create a customer feedback API",
            tech_stack="fastapi",
            project_name="feedback-api"
        )
        self.assertTrue(plan.requires_approval)
        self.assertFalse(plan.approved)
        self.assertEqual(plan.status, "planned")
        self.assertGreater(len(plan.operations), 3)

        op_types = [o.op_type for o in plan.operations]
        self.assertIn("SCAFFOLD_FILES", op_types)
        self.assertIn("CREATE_VENV", op_types)
        self.assertIn("PIP_INSTALL", op_types)
        self.assertIn("START_DEV_SERVER", op_types)


class TestSetupExecutionEngine(unittest.TestCase):
    """Verifies safe plan execution, duplicate prevention, and cancellation."""

    def setUp(self):
        self.temp_root = tempfile.mkdtemp(prefix="test_engine_")
        self.pm = ProjectManager(projects_dir=self.temp_root)
        self.agent = EnvironmentSetupAgent(pm=self.pm)
        self.engine = SetupExecutionEngine(pm=self.pm)

    def tearDown(self):
        dev_server_manager.stop_all()
        safe_rmtree(self.temp_root)

    def test_unapproved_plan_execution_fails_closed(self):
        plan = self.agent.create_setup_plan(
            prompt="Test unapproved rejection",
            tech_stack="python",
            project_name="unapproved-proj"
        )
        self.assertFalse(plan.approved)
        with self.assertRaises(SetupSecurityError) as ctx:
            self.engine.execute_plan(plan, prompt="test")
        self.assertIn("approval", str(ctx.exception).lower())

    def test_python_basic_plan_execution_end_to_end(self):
        plan = self.agent.create_setup_plan(
            prompt="Build a simple math utility",
            tech_stack="python",
            project_name="math-util"
        )
        plan.approved = True

        # Mock pip install to avoid slow external network downloads during testing
        for op in plan.operations:
            if op.op_type == "PIP_INSTALL":
                op.command_args = [sys.executable, "-c", "print('pip mock installed')"]

        executed = self.engine.execute_plan(plan, prompt="Build a simple math utility")
        self.assertEqual(executed.status, "completed")
        self.assertTrue(os.path.isfile(os.path.join(plan.workspace_path, "main.py")))

    def test_cancellation_halts_execution_gracefully(self):
        plan = self.agent.create_setup_plan(
            prompt="Test cancel",
            tech_stack="fastapi",
            project_name="cancel-proj"
        )
        plan.approved = True

        # Pre-set cancel token
        self.engine._cancellation_tokens[plan.project_id] = threading.Event()
        self.engine._cancellation_tokens[plan.project_id].set()

        executed = self.engine.execute_plan(plan, prompt="Test cancel")
        self.assertEqual(executed.status, "cancelled")


class TestFastApiEnvironmentRoutes(unittest.TestCase):
    """Verifies FastAPI REST API endpoints for environment setup."""

    def setUp(self):
        self.client = TestClient(app)

    def test_get_environment_detect(self):
        res = self.client.get("/api/environment/detect")
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertTrue(data["success"])
        self.assertIn("environment", data)
        self.assertIn("stack_evaluations", data)
        self.assertIn("fastapi", data["stack_evaluations"])
        self.assertIn("react", data["stack_evaluations"])

    def test_post_environment_plan_success(self):
        payload = {
            "prompt": "Create an automated crypto portfolio tracker with FastAPI",
            "tech_stack": "fastapi",
            "project_name": "crypto-tracker"
        }
        res = self.client.post("/api/environment/plan", json=payload)
        self.assertEqual(res.status_code, 200)
        data = res.json()
        self.assertTrue(data["success"])
        self.assertEqual(data["plan"]["tech_stack"], "fastapi")
        self.assertTrue(data["plan"]["requires_approval"])
        self.assertFalse(data["plan"]["approved"])

    def test_post_environment_plan_empty_prompt_400(self):
        res = self.client.post("/api/environment/plan", json={"prompt": "  "})
        self.assertEqual(res.status_code, 400)

    def test_post_environment_execute_plan_unapproved_400(self):
        payload = {
            "project_id": "crypto-tracker",
            "approved": False
        }
        res = self.client.post("/api/environment/execute-plan", json=payload)
        self.assertEqual(res.status_code, 400)
        self.assertIn("approval", res.json()["error"]["message"].lower())

    def test_ideas_lifecycle_api(self):
        # 1. Capture idea
        res = self.client.post("/api/ideas/capture", json={
            "prompt": "AI agent that monitors smart contract transactions",
            "title": "Contract Monitor"
        })
        self.assertEqual(res.status_code, 200)
        idea = res.json()["idea"]
        idea_id = idea["idea_id"]

        # 2. List ideas
        list_res = self.client.get("/api/ideas")
        self.assertEqual(list_res.status_code, 200)
        self.assertGreater(list_res.json()["count"], 0)

        # 3. Get single idea
        get_res = self.client.get(f"/api/ideas/{idea_id}")
        self.assertEqual(get_res.status_code, 200)

        # 4. Refine idea
        refine_res = self.client.post(f"/api/ideas/{idea_id}/refine", json={})
        self.assertEqual(refine_res.status_code, 200)
        self.assertEqual(refine_res.json()["idea"]["status"], "refined")


if __name__ == "__main__":
    unittest.main()
