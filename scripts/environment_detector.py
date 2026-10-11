"""
Environment Detector and Tool Checker for Idea Beacon / Agentic Architect
Detects laptop operating system, hardware architecture, development runtimes,
and verifies tool availability before attempting environment setup.
"""

from dataclasses import asdict, dataclass, field
import ipaddress
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
from typing import Any, Dict, List, Optional, Tuple


def is_private_ip(ip: str) -> bool:
    try:
        obj = ipaddress.ip_address(ip)
        return obj.is_private and not obj.is_loopback and not obj.is_link_local
    except ValueError:
        return False


def get_lan_ip() -> str:
    """Resolves active private LAN IPv4 address or fallback."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        candidate = s.getsockname()[0]
        s.close()
        if is_private_ip(candidate):
            return candidate
    except Exception:
        pass

    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if is_private_ip(ip):
                return ip
    except Exception:
        pass

    return "127.0.0.1"


@dataclass
class ToolInfo:
    installed: bool
    version: Optional[str] = None
    executable_path: Optional[str] = None
    major_version: Optional[int] = None
    minor_version: Optional[int] = None
    details: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class HostEnvironment:
    os_name: str  # "Windows", "macOS", "Linux"
    os_release: str
    os_version: str
    architecture: str
    lan_ip: str
    python: ToolInfo
    node: ToolInfo
    npm: ToolInfo
    git: ToolInfo
    venv_module_available: bool

    def to_dict(self) -> Dict[str, Any]:
        return {
            "os_name": self.os_name,
            "os_release": self.os_release,
            "os_version": self.os_version,
            "architecture": self.architecture,
            "lan_ip": self.lan_ip,
            "python": self.python.to_dict(),
            "node": self.node.to_dict(),
            "npm": self.npm.to_dict(),
            "git": self.git.to_dict(),
            "venv_module_available": self.venv_module_available
        }


@dataclass
class StackSupportEvaluation:
    tech_stack: str
    supported: bool
    missing_tools: List[str]
    incompatible_versions: List[str]
    remediation_steps: List[str]
    recommended_environment_type: str  # e.g. "python_venv", "node_modules", "dual_runtime", "static"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


class EnvironmentDetector:
    """Inspects host machine development environment."""

    @staticmethod
    def _run_cmd(args: List[str], timeout: float = 3.0) -> Tuple[int, str, str]:
        try:
            res = subprocess.run(
                args,
                capture_output=True,
                text=True,
                timeout=timeout,
                shell=False
            )
            return res.returncode, res.stdout.strip(), res.stderr.strip()
        except Exception as e:
            return -1, "", str(e)

    def detect_python(self) -> ToolInfo:
        exe = sys.executable
        ver_str = f"{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}"
        
        # Check pip
        rc, out, _ = self._run_cmd([exe, "-m", "pip", "--version"])
        has_pip = rc == 0

        # Check venv
        rc_venv, _, _ = self._run_cmd([exe, "-m", "venv", "-h"])
        has_venv = rc_venv == 0

        return ToolInfo(
            installed=True,
            version=ver_str,
            executable_path=exe,
            major_version=sys.version_info.major,
            minor_version=sys.version_info.minor,
            details={"has_pip": has_pip, "has_venv": has_venv}
        )

    def detect_node(self) -> ToolInfo:
        # Look for node in PATH or common Windows locations
        node_path = shutil.which("node")
        if not node_path and os.name == "nt":
            for candidate in [
                r"C:\Program Files\nodejs\node.exe",
                r"C:\Program Files (x86)\nodejs\node.exe",
                os.path.expandvars(r"%LOCALAPPDATA%\Programs\node\node.exe")
            ]:
                if os.path.isfile(candidate):
                    node_path = candidate
                    break

        if not node_path:
            return ToolInfo(installed=False)

        rc, out, _ = self._run_cmd([node_path, "--version"])
        if rc != 0 or not out:
            return ToolInfo(installed=False)

        ver = out.lstrip("v")
        major, minor = None, None
        m = re.match(r"(\d+)\.(\d+)", ver)
        if m:
            major = int(m.group(1))
            minor = int(m.group(2))

        return ToolInfo(
            installed=True,
            version=ver,
            executable_path=node_path,
            major_version=major,
            minor_version=minor
        )

    def detect_npm(self) -> ToolInfo:
        npm_cmd = "npm.cmd" if os.name == "nt" else "npm"
        npm_path = shutil.which(npm_cmd) or shutil.which("npm")

        if not npm_path and os.name == "nt":
            for candidate in [
                r"C:\Program Files\nodejs\npm.cmd",
                r"C:\Program Files (x86)\nodejs\npm.cmd"
            ]:
                if os.path.isfile(candidate):
                    npm_path = candidate
                    break

        if not npm_path:
            return ToolInfo(installed=False)

        rc, out, _ = self._run_cmd([npm_path, "--version"])
        if rc != 0 or not out:
            return ToolInfo(installed=False)

        ver = out.strip()
        major = int(ver.split(".")[0]) if ver and ver[0].isdigit() else None

        return ToolInfo(
            installed=True,
            version=ver,
            executable_path=npm_path,
            major_version=major
        )

    def detect_git(self) -> ToolInfo:
        git_path = shutil.which("git")
        if not git_path and os.name == "nt":
            for candidate in [
                r"C:\Program Files\Git\cmd\git.exe",
                r"C:\Program Files\Git\bin\git.exe",
                os.path.expandvars(r"%LOCALAPPDATA%\Programs\Git\cmd\git.exe")
            ]:
                if os.path.isfile(candidate):
                    git_path = candidate
                    break

        if not git_path:
            return ToolInfo(installed=False)

        rc, out, _ = self._run_cmd([git_path, "--version"])
        if rc != 0 or not out:
            return ToolInfo(installed=False)

        # "git version 2.45.2.windows.1"
        m = re.search(r"(\d+\.\d+\.\d+)", out)
        ver = m.group(1) if m else out

        return ToolInfo(
            installed=True,
            version=ver,
            executable_path=git_path
        )

    def get_host_environment(self) -> HostEnvironment:
        os_sys = platform.system()
        os_name = "macOS" if os_sys == "Darwin" else os_sys
        
        py_info = self.detect_python()
        node_info = self.detect_node()
        npm_info = self.detect_npm()
        git_info = self.detect_git()
        lan_ip = get_lan_ip()

        return HostEnvironment(
            os_name=os_name,
            os_release=platform.release(),
            os_version=platform.version(),
            architecture=platform.machine(),
            lan_ip=lan_ip,
            python=py_info,
            node=node_info,
            npm=npm_info,
            git=git_info,
            venv_module_available=bool(py_info.details.get("has_venv", False))
        )

    def evaluate_stack_support(
        self,
        tech_stack: str,
        env: Optional[HostEnvironment] = None
    ) -> StackSupportEvaluation:
        """
        Validates if laptop environment meets requirements for the given tech stack.
        Supported stacks: 'python', 'fastapi', 'react', 'fullstack', 'static_web'
        """
        if env is None:
            env = self.get_host_environment()

        stack = tech_stack.lower().strip()
        missing: List[str] = []
        incompatible: List[str] = []
        remediation: List[str] = []
        env_type = "none"

        if stack in ("python", "python_basic"):
            env_type = "python_venv"
            if not env.python.installed:
                missing.append("Python 3.10+")
                remediation.append("Install Python 3.10 or newer from https://www.python.org/downloads/.")
            elif (env.python.major_version or 0) < 3 or ((env.python.major_version == 3) and (env.python.minor_version or 0) < 10):
                incompatible.append(f"Python version {env.python.version} is older than required 3.10+.")
                remediation.append("Upgrade Python to 3.10+ to ensure modern library compatibility.")
            
            if not env.venv_module_available:
                missing.append("python-venv module")
                remediation.append("Install python3-venv (e.g. 'sudo apt install python3-venv' on Linux).")

        elif stack == "fastapi":
            env_type = "python_venv"
            if not env.python.installed:
                missing.append("Python 3.10+")
                remediation.append("Install Python 3.10 or newer.")
            elif (env.python.major_version or 0) < 3 or ((env.python.major_version == 3) and (env.python.minor_version or 0) < 10):
                incompatible.append(f"Python version {env.python.version} is older than required 3.10+ for FastAPI.")
                remediation.append("Upgrade to Python 3.10+.")
            
            if not env.venv_module_available:
                missing.append("python-venv module")
                remediation.append("Ensure python venv module is installed.")

        elif stack == "react":
            env_type = "node_modules"
            if not env.node.installed:
                missing.append("Node.js 18+")
                remediation.append("Install Node.js (LTS version 20+ recommended) from https://nodejs.org/.")
            elif (env.node.major_version or 0) < 18:
                incompatible.append(f"Node.js version {env.node.version} is older than required 18+.")
                remediation.append("Upgrade Node.js to version 18 or 20+.")

            if not env.npm.installed:
                missing.append("npm package manager")
                remediation.append("Ensure npm is installed and added to system PATH.")

        elif stack == "fullstack":
            env_type = "dual_runtime"
            # Requires both Python and Node
            if not env.python.installed:
                missing.append("Python 3.10+")
                remediation.append("Install Python 3.10+ for the backend.")
            if not env.node.installed:
                missing.append("Node.js 18+")
                remediation.append("Install Node.js 18+ for the frontend.")
            if not env.npm.installed:
                missing.append("npm package manager")
                remediation.append("Install npm for React dependency management.")

        elif stack == "static_web":
            env_type = "static"
            # Requires only Python to run local preview server
            if not env.python.installed:
                missing.append("Python 3.x")
                remediation.append("Install Python 3 to serve static files.")

        else:
            # Fallback to python
            env_type = "python_venv"
            if not env.python.installed:
                missing.append("Python")
                remediation.append("Install Python.")

        supported = len(missing) == 0 and len(incompatible) == 0

        return StackSupportEvaluation(
            tech_stack=stack,
            supported=supported,
            missing_tools=missing,
            incompatible_versions=incompatible,
            remediation_steps=remediation,
            recommended_environment_type=env_type
        )


environment_detector = EnvironmentDetector()
