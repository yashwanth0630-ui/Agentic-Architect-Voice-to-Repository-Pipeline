"""
Preview Server Manager for Idea Beacon / Agentic Architect
Provides dynamic, multi-project static & web application preview servers.
Binds to 0.0.0.0 for instant LAN access from Android phones and other devices on the same Wi-Fi.
"""

import functools
import http.server
import ipaddress
import os
import socket
import threading
import time
from typing import Any, Dict, Optional, Tuple


def _is_private_ip(ip: str) -> bool:
    try:
        obj = ipaddress.ip_address(ip)
        return obj.is_private and not obj.is_loopback and not obj.is_link_local
    except ValueError:
        return False


def get_detected_lan_ip() -> str:
    """Detects active private IPv4 on local Wi-Fi or returns 127.0.0.1 fallback."""
    # Method 1: route lookup
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        candidate = s.getsockname()[0]
        s.close()
        if _is_private_ip(candidate):
            return candidate
    except Exception:
        pass

    # Method 2: hostname resolution
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if _is_private_ip(ip):
                return ip
    except Exception:
        pass

    return "127.0.0.1"


def find_available_port(start_port: int = 8081, max_attempts: int = 100) -> int:
    """Finds an unused TCP port binding to 0.0.0.0 within safe preview range."""
    for port in range(start_port, start_port + max_attempts):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                s.bind(("0.0.0.0", port))
                return port
            except OSError:
                continue
    raise RuntimeError(f"Unable to find an open preview port in range {start_port}-{start_port + max_attempts}")


class IdeaBeaconHTTPHandler(http.server.SimpleHTTPRequestHandler):
    """
    Custom HTTP request handler with CORS support, no-cache headers,
    and fallback to index.html for Single Page Applications.
    """
    def __init__(self, *args, directory=None, **kwargs):
        super().__init__(*args, directory=directory, **kwargs)

    def end_headers(self):
        # Enable CORS for cross-origin access from mobile web views & dev tools
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")
        # Disable caching during development
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(204)
        self.end_headers()

    def do_GET(self):
        # If requesting root or directory, let SimpleHTTPRequestHandler handle index.html
        path_translated = self.translate_path(self.path)
        if not os.path.exists(path_translated) and not self.path.startswith("/api/"):
            # SPA fallback: if file does not exist, serve index.html if present
            index_path = os.path.join(self.directory, "index.html")
            if os.path.isfile(index_path):
                self.path = "/index.html"
        super().do_GET()

    def log_message(self, format: str, *args: Any):
        # Suppress noisy standard HTTP access logs
        pass


class PreviewInstance:
    """Represents a running preview server instance for a specific project."""
    def __init__(
        self,
        project_id: str,
        workspace_path: str,
        port: int,
        server: http.server.ThreadingHTTPServer,
        thread: threading.Thread
    ):
        self.project_id = project_id
        self.workspace_path = workspace_path
        self.port = port
        self.server = server
        self.thread = thread
        self.started_at = time.time()
        self.lan_ip = get_detected_lan_ip()

    @property
    def lan_url(self) -> str:
        return f"http://{self.lan_ip}:{self.port}"

    @property
    def local_url(self) -> str:
        return f"http://localhost:{self.port}"

    def to_dict(self) -> Dict[str, Any]:
        return {
            "project_id": self.project_id,
            "workspace_path": self.workspace_path,
            "port": self.port,
            "lan_ip": self.lan_ip,
            "preview_url": self.lan_url,
            "local_url": self.local_url,
            "started_at": self.started_at,
            "status": "running"
        }


class PreviewServerManager:
    """
    Manages the lifecycle of live development preview servers for projects.
    Allows concurrent previews for multiple projects on separate ports.
    """
    def __init__(self, port_range_start: int = 8081):
        self.port_range_start = port_range_start
        self._instances: Dict[str, PreviewInstance] = {}
        self._lock = threading.Lock()

    def start_preview(
        self,
        project_id: str,
        workspace_path: str,
        preferred_port: Optional[int] = None
    ) -> Dict[str, Any]:
        """
        Starts or returns an existing live preview server for the project workspace.
        """
        with self._lock:
            # If already running, return existing info (refresh LAN IP in case network shifted)
            if project_id in self._instances:
                inst = self._instances[project_id]
                inst.lan_ip = get_detected_lan_ip()
                return inst.to_dict()

            if not os.path.isdir(workspace_path):
                raise ValueError(f"Workspace path does not exist: {workspace_path}")

            # Pick port
            port = preferred_port or find_available_port(self.port_range_start)

            # Create handler factory bound to workspace path
            handler_factory = functools.partial(IdeaBeaconHTTPHandler, directory=workspace_path)
            
            # Create ThreadingHTTPServer bound to 0.0.0.0
            server = http.server.ThreadingHTTPServer(("0.0.0.0", port), handler_factory)
            server.daemon_threads = True

            # Run in daemon thread
            thread = threading.Thread(
                target=server.serve_forever,
                name=f"PreviewServer-{project_id}:{port}",
                daemon=True
            )
            thread.start()

            inst = PreviewInstance(
                project_id=project_id,
                workspace_path=workspace_path,
                port=port,
                server=server,
                thread=thread
            )
            self._instances[project_id] = inst
            print(f"[PreviewServer] Started live preview for '{project_id}' on {inst.lan_url} (port {port})")
            return inst.to_dict()

    def stop_preview(self, project_id: str) -> bool:
        """Stops the preview server for a given project ID."""
        with self._lock:
            inst = self._instances.pop(project_id, None)
            if not inst:
                return False

            try:
                inst.server.shutdown()
                inst.server.server_close()
            except Exception as e:
                print(f"[PreviewServer] Notice while shutting down {project_id}: {e}")
            print(f"[PreviewServer] Stopped preview for '{project_id}' on port {inst.port}")
            return True

    def get_preview(self, project_id: str) -> Optional[Dict[str, Any]]:
        """Returns preview status for a project."""
        with self._lock:
            inst = self._instances.get(project_id)
            if inst:
                inst.lan_ip = get_detected_lan_ip()
                return inst.to_dict()
            return None

    def list_previews(self) -> Dict[str, Dict[str, Any]]:
        """Lists all running preview servers."""
        with self._lock:
            return {pid: inst.to_dict() for pid, inst in self._instances.items()}

    def stop_all(self):
        """Stops all running preview servers cleanly."""
        with self._lock:
            for pid, inst in list(self._instances.items()):
                try:
                    inst.server.shutdown()
                    inst.server.server_close()
                except Exception:
                    pass
            self._instances.clear()


# Global singleton instance
preview_manager = PreviewServerManager()
