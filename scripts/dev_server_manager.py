"""
Development Server Manager for Idea Beacon / Agentic Architect
Handles multi-runtime background servers (FastAPI, Python, Vite/React, Node, Static)
Binds to 0.0.0.0 for LAN preview on mobile devices over Wi-Fi.
Features active HTTP health checking, clean process tracking, and graceful termination.
"""

from dataclasses import asdict, dataclass
import http.client
import os
import signal
import socket
import subprocess
import sys
import threading
import time
from typing import Any, Dict, List, Optional, Tuple

from preview_server import find_available_port, get_detected_lan_ip, preview_manager


@dataclass
class ServerInstance:
    project_id: str
    server_type: str  # "fastapi", "react_vite", "python_script", "fullstack", "static"
    workspace_path: str
    host: str
    port: int
    preview_url: str
    lan_ip: str
    status: str  # "starting", "running", "failed", "stopped"
    pid: Optional[int] = None
    secondary_port: Optional[int] = None
    secondary_url: Optional[str] = None
    created_at: float = 0.0
    error: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


class DevServerManager:
    """Manages active development server processes."""

    def __init__(self):
        self._servers: Dict[str, ServerInstance] = {}
        self._processes: Dict[str, List[subprocess.Popen]] = {}
        self._lock = threading.Lock()

    def get_server(self, project_id: str) -> Optional[ServerInstance]:
        with self._lock:
            return self._servers.get(project_id)

    def list_servers(self) -> List[ServerInstance]:
        with self._lock:
            return list(self._servers.values())

    @staticmethod
    def check_http_health(host: str, port: int, path: str = "/", timeout: float = 1.0) -> bool:
        """Performs a quick HTTP GET to verify server is responding."""
        try:
            conn = http.client.HTTPConnection(host, port, timeout=timeout)
            conn.request("GET", path)
            res = conn.getresponse()
            # 2xx, 3xx, and even 404 mean the HTTP server is alive and responding
            status_alive = res.status < 500
            conn.close()
            return status_alive
        except Exception:
            return False

    def poll_until_healthy(
        self,
        host: str,
        port: int,
        path: str = "/",
        max_wait_sec: float = 12.0,
        interval: float = 0.5
    ) -> bool:
        """Polls server until it responds or timeout expires."""
        deadline = time.time() + max_wait_sec
        while time.time() < deadline:
            if self.check_http_health(host, port, path):
                return True
            time.sleep(interval)
        return False

    def start_static_server(
        self,
        project_id: str,
        workspace_path: str,
        preferred_port: Optional[int] = None
    ) -> ServerInstance:
        """Starts a static HTTP file preview server."""
        self.stop_server(project_id)

        info = preview_manager.start_preview(project_id, workspace_path, preferred_port)
        port = info["port"]
        lan_ip = get_detected_lan_ip()
        preview_url = f"http://{lan_ip}:{port}"

        instance = ServerInstance(
            project_id=project_id,
            server_type="static",
            workspace_path=workspace_path,
            host="0.0.0.0",
            port=port,
            preview_url=preview_url,
            lan_ip=lan_ip,
            status="running",
            created_at=time.time()
        )

        with self._lock:
            self._servers[project_id] = instance
        return instance

    def start_fastapi_server(
        self,
        project_id: str,
        workspace_path: str,
        python_exe: str,
        app_module: str = "main:app",
        preferred_port: Optional[int] = None
    ) -> ServerInstance:
        """Launches a FastAPI uvicorn development server."""
        self.stop_server(project_id)

        port = preferred_port or find_available_port(start_port=8080)
        lan_ip = get_detected_lan_ip()
        preview_url = f"http://{lan_ip}:{port}"

        cmd = [
            python_exe,
            "-m",
            "uvicorn",
            app_module,
            "--host",
            "0.0.0.0",
            "--port",
            str(port)
        ]

        # Ensure cwd is workspace
        env = dict(os.environ)
        env["PYTHONUNBUFFERED"] = "1"

        try:
            proc = subprocess.Popen(
                cmd,
                cwd=workspace_path,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env,
                shell=False
            )
        except Exception as e:
            instance = ServerInstance(
                project_id=project_id,
                server_type="fastapi",
                workspace_path=workspace_path,
                host="0.0.0.0",
                port=port,
                preview_url=preview_url,
                lan_ip=lan_ip,
                status="failed",
                error=f"Failed to spawn uvicorn: {str(e)}",
                created_at=time.time()
            )
            with self._lock:
                self._servers[project_id] = instance
            return instance

        # Poll health
        healthy = self.poll_until_healthy("127.0.0.1", port, path="/health", max_wait_sec=8.0)
        if not healthy:
            # Also try root
            healthy = self.poll_until_healthy("127.0.0.1", port, path="/", max_wait_sec=2.0)

        if not healthy and proc.poll() is not None:
            # Process died
            _, stderr = proc.communicate(timeout=1.0)
            instance = ServerInstance(
                project_id=project_id,
                server_type="fastapi",
                workspace_path=workspace_path,
                host="0.0.0.0",
                port=port,
                preview_url=preview_url,
                lan_ip=lan_ip,
                status="failed",
                error=f"Uvicorn server crashed: {stderr.strip()}",
                created_at=time.time()
            )
            with self._lock:
                self._servers[project_id] = instance
            return instance

        instance = ServerInstance(
            project_id=project_id,
            server_type="fastapi",
            workspace_path=workspace_path,
            host="0.0.0.0",
            port=port,
            preview_url=preview_url,
            lan_ip=lan_ip,
            status="running",
            pid=proc.pid,
            created_at=time.time()
        )

        with self._lock:
            self._servers[project_id] = instance
            self._processes[project_id] = [proc]

        return instance

    def start_react_vite_server(
        self,
        project_id: str,
        workspace_path: str,
        npm_cmd: str,
        preferred_port: Optional[int] = None
    ) -> ServerInstance:
        """Launches a Vite React development server."""
        self.stop_server(project_id)

        port = preferred_port or find_available_port(start_port=5173)
        lan_ip = get_detected_lan_ip()
        preview_url = f"http://{lan_ip}:{port}"

        # Run npm run dev -- --host 0.0.0.0 --port <port>
        cmd = [npm_cmd, "run", "dev", "--", "--host", "0.0.0.0", "--port", str(port)]

        env = dict(os.environ)

        try:
            proc = subprocess.Popen(
                cmd,
                cwd=workspace_path,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env,
                shell=False
            )
        except Exception as e:
            instance = ServerInstance(
                project_id=project_id,
                server_type="react_vite",
                workspace_path=workspace_path,
                host="0.0.0.0",
                port=port,
                preview_url=preview_url,
                lan_ip=lan_ip,
                status="failed",
                error=f"Failed to spawn Vite: {str(e)}",
                created_at=time.time()
            )
            with self._lock:
                self._servers[project_id] = instance
            return instance

        # Poll health on root
        healthy = self.poll_until_healthy("127.0.0.1", port, path="/", max_wait_sec=10.0)

        if not healthy and proc.poll() is not None:
            _, stderr = proc.communicate(timeout=1.0)
            instance = ServerInstance(
                project_id=project_id,
                server_type="react_vite",
                workspace_path=workspace_path,
                host="0.0.0.0",
                port=port,
                preview_url=preview_url,
                lan_ip=lan_ip,
                status="failed",
                error=f"Vite dev server crashed: {stderr.strip()}",
                created_at=time.time()
            )
            with self._lock:
                self._servers[project_id] = instance
            return instance

        instance = ServerInstance(
            project_id=project_id,
            server_type="react_vite",
            workspace_path=workspace_path,
            host="0.0.0.0",
            port=port,
            preview_url=preview_url,
            lan_ip=lan_ip,
            status="running",
            pid=proc.pid,
            created_at=time.time()
        )

        with self._lock:
            self._servers[project_id] = instance
            self._processes[project_id] = [proc]

        return instance

    def start_fullstack_server(
        self,
        project_id: str,
        backend_dir: str,
        frontend_dir: str,
        python_exe: str,
        npm_cmd: str,
        backend_port: Optional[int] = None,
        frontend_port: Optional[int] = None
    ) -> ServerInstance:
        """Launches both FastAPI backend and React frontend coordinated servers."""
        self.stop_server(project_id)

        b_port = backend_port or find_available_port(start_port=8080)
        f_port = frontend_port or find_available_port(start_port=5173)
        lan_ip = get_detected_lan_ip()

        backend_url = f"http://{lan_ip}:{b_port}"
        frontend_url = f"http://{lan_ip}:{f_port}"

        # 1. Start backend
        backend_cmd = [
            python_exe,
            "-m",
            "uvicorn",
            "main:app",
            "--host",
            "0.0.0.0",
            "--port",
            str(b_port)
        ]
        b_proc = subprocess.Popen(
            backend_cmd,
            cwd=backend_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            shell=False
        )

        # 2. Start frontend
        frontend_cmd = [npm_cmd, "run", "dev", "--", "--host", "0.0.0.0", "--port", str(f_port)]
        f_proc = subprocess.Popen(
            frontend_cmd,
            cwd=frontend_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            shell=False
        )

        # Verify backend health
        self.poll_until_healthy("127.0.0.1", b_port, path="/health", max_wait_sec=8.0)
        self.poll_until_healthy("127.0.0.1", f_port, path="/", max_wait_sec=10.0)

        instance = ServerInstance(
            project_id=project_id,
            server_type="fullstack",
            workspace_path=os.path.dirname(backend_dir),
            host="0.0.0.0",
            port=f_port,  # Primary preview is frontend UI
            preview_url=frontend_url,
            lan_ip=lan_ip,
            status="running",
            pid=f_proc.pid,
            secondary_port=b_port,
            secondary_url=backend_url,
            created_at=time.time()
        )

        with self._lock:
            self._servers[project_id] = instance
            self._processes[project_id] = [b_proc, f_proc]

        return instance

    def stop_server(self, project_id: str) -> bool:
        """Terminates dev server process(es) associated with project."""
        with self._lock:
            # 1. Check if preview_manager was handling it (static)
            preview_manager.stop_preview(project_id)

            # 2. Terminate subprocesses
            procs = self._processes.pop(project_id, [])
            for proc in procs:
                try:
                    if proc.poll() is None:
                        proc.terminate()
                        try:
                            proc.wait(timeout=2.0)
                        except subprocess.TimeoutExpired:
                            proc.kill()
                except Exception:
                    pass

            # 3. Update server record
            if project_id in self._servers:
                self._servers[project_id].status = "stopped"
                return True
            return False

    def stop_all(self):
        """Stops all running dev servers across all projects."""
        with self._lock:
            pids = list(self._servers.keys())
        for pid in pids:
            self.stop_server(pid)


dev_server_manager = DevServerManager()
