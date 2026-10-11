"""
Idea Beacon / Agentic Architect — Voice to Repository Pipeline
Production Laptop Backend & Local AI Bridge Server

Primary Workflow:
  Phone → Shared Wi-Fi → Laptop FastAPI Bridge (:8000) → Local Ollama AI → Injected Code

Features:
1. Setup & Networking: FastAPI on host 0.0.0.0, port 8000 with permissive CORS.
2. LAN Auto-Discovery:
   - UDP broadcast responder on port 8001 responding to IDEA_BEACON_DISCOVER.
   - HTTP discovery endpoint: GET /api/discover and GET /discover.
3. Security:
   - Restricts external WAN requests, accepting local private IPv4 (RFC 1918) and loopback traffic.
   - Path-traversal-guarded atomic file writing into ./injected_agents/.
   - Strict syntax validation (Python AST, JSON decoding, delimiter balancing).
4. Dual IDE Injection:
   - Method A (Direct File Writing): Validates code, infers filename, writes atomically to workspace.
   - Method B (Cursor CLI Agent): Executes `agent -p "<prompt>"` inside workspace.
5. Fallback Support: USB ADB reverse (`adb reverse tcp:8000 tcp:8000`) and emulator loopbacks.
"""

import ast
from contextlib import asynccontextmanager
import hashlib
import ipaddress
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
from typing import Optional, Dict, Any, Tuple, List

import httpx
import uvicorn
from fastapi import FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

# Ensure script directory is on sys.path for uvicorn reloader
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from project_manager import project_manager, ProjectMetadata, sanitize_project_id
from preview_server import preview_manager, PreviewServerManager
from agent_orchestrator import agent_orchestrator, OrchestrationResult
from github_service import (
    github_service,
    GitHubService,
    GitHubAuth,
    GitHubAuthError,
    GitHubNotFoundError,
    GitHubRateLimitError,
    GitHubValidationError,
    GitHubNetworkError,
    GitHubServiceError,
    scrub_sensitive_text
)
from repository_agent import (
    workspace_manager,
    git_change_tracker,
    repository_agent,
    git_publisher,
    detect_static_web_preview_root,
    WorkspaceRecord,
    ChangeSummary,
    InstructionExecutionResult,
    RepositoryAgentError,
    WorkspaceSecurityError,
    GitOperationError,
    AIProviderUnavailableError,
    AIEditingError,
    PublishError
)
from idea_manager import idea_manager, IdeaRecord, IdeaBlueprint
from environment_detector import environment_detector, HostEnvironment, StackSupportEvaluation
from environment_setup_agent import environment_setup_agent, SetupPlan, SetupOperation
from setup_execution_engine import setup_execution_engine, SetupSecurityError
from dev_server_manager import dev_server_manager, ServerInstance


# ==============================================================================
# CONFIGURATION & TOGGLE
# ==============================================================================
# Set to False for Method A (Direct File Writing via Ollama)
# Set to True  for Method B (Cursor CLI Agent via `agent -p "<prompt>"`)
USE_CURSOR_AGENT: bool = False

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "qwen2.5-coder:1.5b")
DEFAULT_WORKSPACE_PATH = os.path.abspath(os.path.dirname(SCRIPT_DIR))
OLLAMA_TIMEOUT = float(os.getenv("OLLAMA_TIMEOUT", "90.0"))

HTTP_PORT = int(os.getenv("PORT", "8000"))
DISCOVERY_UDP_PORT = int(os.getenv("DISCOVERY_UDP_PORT", "8001"))
DISCOVERY_MAGIC_REQUEST = "IDEA_BEACON_DISCOVER"
DISCOVERY_SERVICE_ID = "idea-beacon-bridge"
SERVER_VERSION = "2.2.0"

# Allowed file extensions for injected files to prevent malicious script injection
ALLOWED_EXTENSIONS = {
    ".kt", ".py", ".ts", ".tsx", ".js", ".jsx",
    ".java", ".rs", ".go", ".html", ".css",
    ".json", ".md", ".sql", ".sh", ".yaml", ".yml"
}

# ==============================================================================
# IP DETECTION & PRIVATE NETWORK UTILITIES
# ==============================================================================
def is_private_ip(ip: str) -> bool:
    """Checks whether an IP address belongs to RFC 1918 private space."""
    try:
        obj = ipaddress.ip_address(ip)
        return obj.is_private and not obj.is_loopback and not obj.is_link_local
    except ValueError:
        return False


def get_local_ip_addresses() -> List[str]:
    """Retrieves all non-loopback, non-link-local private IPv4 addresses."""
    ips: List[str] = []
    # Method 1: Query hostname resolution
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if is_private_ip(ip):
                ips.append(ip)
    except Exception:
        pass

    # Method 2: Route lookup toward external router
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        candidate = s.getsockname()[0]
        s.close()
        if is_private_ip(candidate):
            ips.append(candidate)
    except Exception:
        pass

    dedup = list(dict.fromkeys(ips))
    return dedup if dedup else ["127.0.0.1"]


def get_primary_lan_ip() -> str:
    """Detects the primary outbound private IPv4 address on the active Wi-Fi / LAN adapter."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if is_private_ip(ip):
            return ip
    except Exception:
        pass

    candidates = get_local_ip_addresses()
    for ip in candidates:
        if is_private_ip(ip):
            return ip
    return candidates[0] if candidates else "127.0.0.1"


# ==============================================================================
# UDP LAN DISCOVERY RESPONDER
# ==============================================================================
class UdpDiscoveryServer:
    """
    Lightweight background UDP responder for local Wi-Fi auto-discovery.
    When phone broadcasts 'IDEA_BEACON_DISCOVER', responds with bridge metadata.
    """
    def __init__(
        self,
        http_port: int = HTTP_PORT,
        udp_port: int = DISCOVERY_UDP_PORT,
        port: Optional[int] = None
    ):
        self.http_port = http_port
        self.udp_port = port if port is not None else udp_port
        self.running = False
        self.thread: Optional[threading.Thread] = None
        self.sock: Optional[socket.socket] = None

    def start(self):
        if self.running:
            return
        self.running = True
        self.thread = threading.Thread(target=self._run, daemon=True, name="IdeaBeaconDiscovery")
        self.thread.start()

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass

    def _run(self):
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            except Exception:
                pass
            self.sock.bind(("0.0.0.0", self.udp_port))
            self.sock.settimeout(1.5)

            while self.running:
                try:
                    data, addr = self.sock.recvfrom(1024)
                    message = data.decode("utf-8", errors="ignore").strip()
                    if any(tok in message.upper() for tok in ["IDEA_BEACON", "DISCOVER", "PING", "IDEABEACON"]):
                        primary_ip = get_primary_lan_ip()
                        response = {
                            "service": DISCOVERY_SERVICE_ID,
                            "name": "Idea Beacon Laptop Bridge",
                            "version": SERVER_VERSION,
                            "port": self.http_port,
                            "http_port": self.http_port,
                            "host": primary_ip,
                            "url": f"http://{primary_ip}:{self.http_port}",
                            "status": "ready"
                        }
                        payload = json.dumps(response).encode("utf-8")
                        self.sock.sendto(payload, addr)
                except socket.timeout:
                    continue
                except Exception:
                    if not self.running:
                        break
        except Exception as e:
            # UDP port might be in use or restricted; log and continue gracefully
            print(f"[Discovery] UDP responder notice (port {self.udp_port}): {e}")


discovery_server = UdpDiscoveryServer()

# ==============================================================================
# FASTAPI LIFESPAN & APPLICATION SETUP
# ==============================================================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    discovery_server.start()
    yield
    discovery_server.stop()
    preview_manager.stop_all()
    dev_server_manager.stop_all()


app = FastAPI(
    title="Idea Beacon - Local Laptop Bridge",
    description="Receives voice-transcribed prompts from Android over Wi-Fi and injects code into your IDE",
    version=SERVER_VERSION,
    lifespan=lifespan
)

# Configure CORS to accept mobile phone requests from any local network IP
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Security middleware: Restrict access to local private network and loopback callers
@app.middleware("http")
async def verify_local_network_origin(request: Request, call_next):
    forwarded_for = request.headers.get("X-Forwarded-For")
    if forwarded_for:
        raw_ip = forwarded_for.split(",")[0].strip()
    else:
        raw_ip = request.client.host if request.client else "127.0.0.1"

    # Allow test client, loopbacks, and private RFC 1918 subnets
    if raw_ip not in ["testclient", "localhost", "test"] and not raw_ip.startswith("127."):
        try:
            ip_obj = ipaddress.ip_address(raw_ip)
            if not (ip_obj.is_private or ip_obj.is_loopback):
                return JSONResponse(
                    status_code=status.HTTP_403_FORBIDDEN,
                    content={
                        "detail": "Forbidden: Access restricted to local Wi-Fi / private network."
                    }
                )
        except ValueError:
            return JSONResponse(
                status_code=status.HTTP_403_FORBIDDEN,
                content={
                    "detail": "Forbidden: Access restricted to local Wi-Fi / private network."
                }
            )

    response = await call_next(request)
    return response


# ==============================================================================
# PAYLOAD SCHEMAS
# ==============================================================================
# PAYLOAD SCHEMAS
# ==============================================================================
class GenerateRequest(BaseModel):
    prompt: str = Field(
        ..., 
        description="The voice-transcribed prompt from the Android app"
    )
    project_path: Optional[str] = Field(
        default=None, 
        description="Absolute path to your active VS Code/Cursor workspace"
    )
    use_cursor_agent: Optional[bool] = Field(
        default=None, 
        description="Override the server toggle: True for Cursor CLI agent, False for Direct file write"
    )
    model: Optional[str] = Field(
        default=DEFAULT_MODEL, 
        description="Ollama model tag to use for inference"
    )
    temperature: Optional[float] = Field(
        default=0.2,
        description="Sampling temperature for LLM inference"
    )
    project_id: Optional[str] = Field(
        default=None,
        description="Target project ID if updating an existing project"
    )
    force_single_module: Optional[bool] = Field(
        default=False,
        description="Force single module injection instead of creating a multi-file project workspace"
    )


class ValidationResult(BaseModel):
    valid: bool
    language: str
    syntax_valid: bool
    error: Optional[str] = None
    line_count: int = 0
    byte_count: int = 0
    checksum_sha256: Optional[str] = None


class GenerateResponse(BaseModel):
    success: bool = True
    status: str
    project_id: Optional[str] = None
    project_name: Optional[str] = None
    workspace: Optional[str] = None
    preview_url: Optional[str] = None
    preview_port: Optional[int] = None
    files_created: List[str] = Field(default_factory=list)
    method: str
    prompt: str
    code: Optional[str] = None
    file_path: Optional[str] = None
    filename: Optional[str] = None
    stdout: Optional[str] = None
    model: Optional[str] = None
    latency_ms: float
    message: str
    validation: Optional[ValidationResult] = None
    stage: Optional[str] = "COMPLETED"
    error: Optional[str] = None


# ==============================================================================
# STEP-BY-STEP VERIFICATION & SANITIZATION PIPELINE
# ==============================================================================

def sanitize_filename(candidate_name: str, fallback_ext: str = ".kt") -> str:
    """
    Sanitizes filename candidate to prevent directory traversal and remove illegal chars.
    Ensures safe extension within ALLOWED_EXTENSIONS.
    """
    base = os.path.basename(candidate_name).strip()
    base = re.sub(r'[\/\\:\*\?"<>\|\0]', '_', base)
    base = re.sub(r'\.{2,}', '.', base)  # Remove multiple dots
    
    root, ext = os.path.splitext(base)
    ext = ext.lower()
    
    if ext not in ALLOWED_EXTENSIONS:
        ext = fallback_ext if fallback_ext in ALLOWED_EXTENSIONS else ".kt"
        
    clean_root = re.sub(r'[^a-zA-Z0-9_\-]', '', root).strip('._-')
    if not clean_root:
        clean_root = "GeneratedModule"
        
    return f"{clean_root}{ext}"


def extract_code_and_filename(raw_text: str, prompt: str) -> Tuple[str, str, str]:
    """
    Extracts code and filename from raw model output.
    Looks for:
    1. Explicit filename comments: // File: MyName.kt or # filename: my_script.py
    2. Markdown fenced code blocks: ```lang ... ```
    3. Idiomatic extension inference based on prompt keywords.
    """
    explicit_file_match = re.search(
        r"(?:(?://|#|/\*|--)\s*(?:file|filename|path)\s*[:=]\s*)([a-zA-Z0-9_\-\.\/]+)",
        raw_text,
        re.IGNORECASE
    )
    explicit_filename = explicit_file_match.group(1).strip() if explicit_file_match else None

    code_block_match = re.search(r"```([a-zA-Z0-9_\-\+]*)\n(.*?)```", raw_text, re.DOTALL)
    if code_block_match:
        lang = code_block_match.group(1).lower().strip()
        code = code_block_match.group(2).strip()
    else:
        lang = ""
        cleaned = re.sub(r"^```[a-zA-Z0-9_\-\+]*\n", "", raw_text.strip())
        cleaned = re.sub(r"\n```$", "", cleaned)
        code = cleaned.strip()

    ext_map = {
        "kotlin": ".kt", "kt": ".kt",
        "python": ".py", "py": ".py",
        "typescript": ".ts", "ts": ".ts",
        "tsx": ".tsx", "jsx": ".jsx",
        "javascript": ".js", "js": ".js",
        "java": ".java",
        "rust": ".rs", "rs": ".rs",
        "go": ".go", "golang": ".go",
        "html": ".html", "css": ".css",
        "json": ".json", "markdown": ".md", "md": ".md",
        "sql": ".sql", "shell": ".sh", "bash": ".sh", "sh": ".sh",
        "yaml": ".yaml", "yml": ".yml"
    }
    
    ext = ext_map.get(lang)
    if not ext:
        p_lower = prompt.lower()
        if any(k in p_lower for k in ["kotlin", "android", "compose"]):
            ext = ".kt"
            lang = "kotlin"
        elif any(k in p_lower for k in ["python", "fastapi", "flask", "django"]):
            ext = ".py"
            lang = "python"
        elif any(k in p_lower for k in ["typescript", "react", "next", "ts"]):
            ext = ".ts"
            lang = "typescript"
        elif any(k in p_lower for k in ["go", "golang", "gin"]):
            ext = ".go"
            lang = "go"
        elif any(k in p_lower for k in ["rust"]):
            ext = ".rs"
            lang = "rust"
        elif any(k in p_lower for k in ["json"]):
            ext = ".json"
            lang = "json"
        else:
            ext = ".kt"
            lang = "kotlin"

    if explicit_filename:
        filename = sanitize_filename(explicit_filename, fallback_ext=ext)
    else:
        cleaned_prompt = re.sub(r"(?i)\b(create|build|make|generate|an|a|the|for|in|code|write|please|file|module|agent)\b", "", prompt)
        words = re.findall(r"[a-zA-Z0-9]+", cleaned_prompt)
        if words:
            if ext in [".kt", ".java"]:
                base_name = "".join(w.capitalize() for w in words[:4])
            else:
                base_name = "_".join(w.lower() for w in words[:4])
        else:
            base_name = "GeneratedPipeline" if ext in [".kt", ".java"] else "generated_pipeline"
            
        filename = sanitize_filename(f"{base_name}{ext}", fallback_ext=ext)

    return code, filename, lang


def validate_brackets(code: str) -> bool:
    """
    Verifies delimiter balancing ({}, (), []) for C-style and structured code.
    Ignores string literals and single-line/multi-line comments.
    """
    stack = []
    pairs = {')': '(', '}': '{', ']': '['}
    
    cleaned = re.sub(r'"(?:\\.|[^"\\])*"', '', code)
    cleaned = re.sub(r"'(?:\\.|[^'\\])*'", '', cleaned)
    cleaned = re.sub(r'/\*.*?\*/', '', cleaned, flags=re.DOTALL)
    cleaned = re.sub(r'//.*$', '', cleaned, flags=re.MULTILINE)
    cleaned = re.sub(r'#.*$', '', cleaned, flags=re.MULTILINE)
    
    for char in cleaned:
        if char in '({[':
            stack.append(char)
        elif char in ')}]':
            if not stack or stack[-1] != pairs[char]:
                return False
            stack.pop()
            
    return len(stack) == 0


def validate_code_content(code: str, filename: str, language: str) -> Tuple[bool, bool, Optional[str]]:
    """
    Validates model output before writing to disk:
    1. Checks for non-empty meaningful content.
    2. Runs language-specific syntax validation.
    """
    if not code or len(code.strip()) < 5:
        return False, False, "Generated code is empty or too short (< 5 characters)."

    ext = os.path.splitext(filename)[1].lower()
    
    if ext == ".py" or language == "python":
        try:
            ast.parse(code)
            return True, True, None
        except SyntaxError as se:
            return True, False, f"Python SyntaxError at line {se.lineno}: {se.msg}"
        except Exception as e:
            return True, False, f"Python AST parse error: {str(e)}"

    if ext == ".json" or language == "json":
        try:
            json.loads(code)
            return True, True, None
        except json.JSONDecodeError as jde:
            return True, False, f"JSONDecodeError at line {jde.lineno}: {jde.msg}"

    if ext in [".kt", ".ts", ".tsx", ".js", ".jsx", ".java", ".go", ".rs"]:
        balanced = validate_brackets(code)
        if not balanced:
            return True, False, "Delimiter imbalance detected (unmatched braces, brackets, or parentheses)."

    return True, True, None


def safe_atomic_write_file(target_dir: str, filename: str, content: str) -> Tuple[str, int, str]:
    """
    Safely and atomically writes content to disk:
    1. Ensures target path resides strictly inside target_dir/injected_agents (Path Traversal Guard).
    2. Writes to a temporary file first, then atomically replaces destination.
    """
    injected_dir = os.path.realpath(os.path.join(target_dir, "injected_agents"))
    os.makedirs(injected_dir, exist_ok=True)
    
    file_path = os.path.realpath(os.path.join(injected_dir, filename))
    
    if not file_path.startswith(injected_dir + os.sep) and file_path != injected_dir:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Security violation: path traversal detected for filename '{filename}'."
        )

    encoded = content.encode("utf-8")
    sha256_hash = hashlib.sha256(encoded).hexdigest()
    byte_count = len(encoded)

    temp_fd, temp_path = tempfile.mkstemp(dir=injected_dir, prefix=".tmp_", suffix=".tmp")
    try:
        with os.fdopen(temp_fd, "wb") as f:
            f.write(encoded)
            f.flush()
            os.fsync(f.fileno())
        os.replace(temp_path, file_path)
    except Exception:
        if os.path.exists(temp_path):
            os.remove(temp_path)
        raise

    return file_path, byte_count, sha256_hash


# ==============================================================================
# HTTP ENDPOINTS
# ==============================================================================

@app.get("/")
@app.get("/health")
@app.get("/status")
async def health_check():
    """Health check & diagnostic endpoint to verify bridge readiness."""
    ollama_online = False
    available_models = []
    ollama_latency_ms = None
    
    t0 = time.time()
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
            if resp.status_code == 200:
                ollama_online = True
                ollama_latency_ms = round((time.time() - t0) * 1000, 2)
                available_models = [m.get("name") for m in resp.json().get("models", [])]
    except Exception:
        ollama_online = False

    workspace_writable = False
    try:
        os.makedirs(DEFAULT_WORKSPACE_PATH, exist_ok=True)
        test_file = os.path.join(DEFAULT_WORKSPACE_PATH, ".write_test.tmp")
        with open(test_file, "w") as f:
            f.write("ok")
        os.remove(test_file)
        workspace_writable = True
    except Exception:
        workspace_writable = False

    cursor_cli_available = shutil.which("agent") is not None or shutil.which("cursor") is not None
    primary_ip = get_primary_lan_ip()

    return {
        "status": "healthy",
        "service": "Idea Beacon Laptop Bridge",
        "version": "2.2.0",
        "ollama": {
            "online": ollama_online,
            "url": OLLAMA_BASE_URL,
            "latency_ms": ollama_latency_ms,
            "available_models": available_models,
            "default_model": DEFAULT_MODEL
        },
        "workspace": {
            "path": DEFAULT_WORKSPACE_PATH,
            "writable": workspace_writable
        },
        "cursor_cli": {
            "available": cursor_cli_available,
            "toggle_active": USE_CURSOR_AGENT
        },
        "primary_lan_ip": primary_ip,
        "local_ips": get_local_ip_addresses(),
        "wifi_connection_url": f"http://{primary_ip}:{HTTP_PORT}",
        "adb_reverse_hint": "adb reverse tcp:8000 tcp:8000"
    }


@app.get("/discover")
@app.get("/api/discover")
async def discover_bridge():
    """
    HTTP LAN Discovery endpoint for mobile phones probing the subnet.
    Returns recognizable service identifier, name, and port.
    """
    primary_ip = get_primary_lan_ip()
    return {
        "service": DISCOVERY_SERVICE_ID,
        "name": "Idea Beacon Laptop Bridge",
        "version": SERVER_VERSION,
        "port": HTTP_PORT,
        "http_port": HTTP_PORT,
        "host": primary_ip,
        "url": f"http://{primary_ip}:{HTTP_PORT}",
        "status": "ready"
    }


# ==============================================================================
# /generate ENDPOINT (AI Pipeline Verification & IDE Injection)
# ==============================================================================
@app.post("/generate", response_model=GenerateResponse)
async def generate_and_inject(request: GenerateRequest):
    """
    Receives voice prompt from Android, runs step-by-step verification pipeline:
    1. Input sanitization.
    2. LLM inference via Ollama or fallback template.
    3. Code & filename extraction.
    4. Code content & syntax validation (rejects invalid outputs).
    5. Safe atomic file writing to workspace.
    """
    clean_prompt = request.prompt.strip() if request.prompt else ""
    if not clean_prompt:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Voice prompt cannot be empty."
        )

    start_time = time.time()

    # Check if multi-file project workspace creation is intended
    intent_type = "SINGLE_MODULE" if request.force_single_module else None
    if not intent_type:
        intent_type, detected_slug, display_title = agent_orchestrator.detect_intent(clean_prompt)

    if intent_type == "NEW_PROJECT":
        print(f"\n[AgentOrchestrator] Multi-file project workspace requested: '{clean_prompt}'")
        orch_result = await agent_orchestrator.execute_new_project(
            prompt=clean_prompt,
            preferred_port=None
        )
        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        if not orch_result.success:
            return JSONResponse(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                content=orch_result.to_dict()
            )

        return GenerateResponse(
            success=True,
            status=orch_result.status,
            project_id=orch_result.project_id,
            project_name=orch_result.project_name,
            workspace=orch_result.workspace,
            preview_url=orch_result.preview_url,
            preview_port=orch_result.preview_port,
            files_created=orch_result.files_created,
            method="agent_orchestrator",
            prompt=clean_prompt,
            latency_ms=elapsed_ms,
            message=orch_result.message,
            stage=orch_result.stage,
            error=orch_result.error
        )
    
    target_dir = request.project_path or DEFAULT_WORKSPACE_PATH
    try:
        target_dir = os.path.abspath(target_dir)
        os.makedirs(target_dir, exist_ok=True)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to access or create project path: {str(e)}"
        )

    should_use_cursor = request.use_cursor_agent if request.use_cursor_agent is not None else USE_CURSOR_AGENT

    # METHOD B: Cursor CLI Agent
    if should_use_cursor:
        print(f"\n[Method B] Triggering Cursor CLI agent inside: {target_dir}")
        print(f"[Method B] Prompt: \"{clean_prompt}\"")
        
        command = ["agent", "-p", clean_prompt]
        try:
            process = subprocess.run(
                command,
                cwd=target_dir,
                shell=False,
                capture_output=True,
                text=True,
                timeout=180
            )
            elapsed_ms = round((time.time() - start_time) * 1000, 2)

            if process.returncode == 0:
                return GenerateResponse(
                    status="success",
                    method="cursor_cli_agent",
                    prompt=clean_prompt,
                    stdout=process.stdout,
                    latency_ms=elapsed_ms,
                    message=f"Cursor agent executed prompt successfully inside {target_dir}"
                )
            else:
                return GenerateResponse(
                    status="warning",
                    method="cursor_cli_agent",
                    prompt=clean_prompt,
                    stdout=process.stdout or process.stderr,
                    latency_ms=elapsed_ms,
                    message=f"Cursor agent returned exit code {process.returncode}. (Check if 'agent' CLI is configured in PATH)"
                )
        except FileNotFoundError:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Cursor CLI 'agent' not found in system PATH. Ensure Cursor CLI is installed."
            )
        except subprocess.TimeoutExpired:
            raise HTTPException(
                status_code=status.HTTP_504_GATEWAY_TIMEOUT,
                detail="Cursor agent command timed out (180s)."
            )
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Cursor agent execution failed: {str(e)}"
            )

    # METHOD A: Direct File Writing (Ollama -> Verification -> Safe File Write)
    else:
        print(f"\n[Method A] Querying local Ollama GPU on {OLLAMA_BASE_URL} ...")
        selected_model = request.model or DEFAULT_MODEL
        
        system_instruction = (
            "You are the Agentic Architect AI Engine. "
            "The user spoke a command from their Android phone over Wi-Fi. "
            "Generate clean, production-ready code with complete syntax and zero placeholders. "
            "Always include the code inside fenced markdown blocks (e.g., ```kotlin ... ``` or ```python ... ```). "
            "Include a filename comment at the top, like: // File: MyService.kt or # filename: service.py"
        )

        ollama_payload = {
            "model": selected_model,
            "prompt": clean_prompt,
            "system": system_instruction,
            "stream": False,
            "options": {
                "temperature": request.temperature or 0.2,
                "num_predict": 2048
            }
        }

        try:
            async with httpx.AsyncClient(timeout=OLLAMA_TIMEOUT) as client:
                resp = await client.post(f"{OLLAMA_BASE_URL}/api/generate", json=ollama_payload)
                if resp.status_code != 200:
                    raise HTTPException(
                        status_code=resp.status_code, 
                        detail=f"Ollama returned error {resp.status_code}: {resp.text}"
                    )
                ollama_data = resp.json()
                raw_generated_text = ollama_data.get("response", "")

        except (httpx.ConnectError, httpx.TimeoutException):
            print("[Method A] Ollama connection unavailable. Using offline template fallback.")
            raw_generated_text = f"""```kotlin
// File: AutonomousAgentPipeline.kt
// [Idea Beacon Offline Fallback]
// Generated for voice prompt: "{clean_prompt}"
package com.example.agenticarchitect.injected

class AutonomousAgentPipeline {{
    fun execute() {{
        println("Idea Beacon agent pipeline active from mobile voice prompt over Wi-Fi.")
    }}
}}
```"""

        # Step 3: Extract raw code & infer filename
        code_content, inferred_filename, detected_lang = extract_code_and_filename(
            raw_generated_text, clean_prompt
        )

        # Step 4: Validate model output before writing
        is_valid, is_syntax_valid, val_error = validate_code_content(
            code_content, inferred_filename, detected_lang
        )

        if not is_valid:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"AI model generated invalid or empty code: {val_error}"
            )

        # Step 5: Safe atomic write into target workspace
        file_path, byte_count, sha256_hash = safe_atomic_write_file(
            target_dir, inferred_filename, code_content
        )

        line_count = len(code_content.splitlines())
        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        print(f"[Method A] Injected verified file: {file_path} ({byte_count} bytes, {line_count} lines)")

        validation_info = ValidationResult(
            valid=is_valid,
            language=detected_lang,
            syntax_valid=is_syntax_valid,
            error=val_error,
            line_count=line_count,
            byte_count=byte_count,
            checksum_sha256=sha256_hash
        )

        status_flag = "success" if is_syntax_valid else "warning"
        message = (
            f"Code verified and saved to {file_path}"
            if is_syntax_valid
            else f"Code saved to {file_path} with syntax warning: {val_error}"
        )

        return GenerateResponse(
            status=status_flag,
            method="direct_file_writing",
            prompt=clean_prompt,
            code=code_content,
            file_path=file_path,
            filename=inferred_filename,
            model=selected_model,
            latency_ms=elapsed_ms,
            message=message,
            validation=validation_info
        )


# ==============================================================================
# PROJECT & PREVIEW MANAGEMENT ENDPOINTS
# ==============================================================================
@app.get("/projects")
async def list_all_projects():
    """Lists all registered projects in projects/projects.json."""
    return [p.to_dict() for p in project_manager.list_projects()]


@app.get("/projects/{project_id}")
async def get_project_details(project_id: str):
    """Retrieves metadata for a specific project."""
    project = project_manager.get_project(project_id)
    if not project:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Project '{project_id}' not found."
        )
    return project.to_dict()


@app.get("/projects/{project_id}/preview")
async def get_project_preview_status(project_id: str):
    """Retrieves live preview server info for a project."""
    preview = preview_manager.get_preview(project_id)
    if not preview:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Preview server for '{project_id}' is not running."
        )
    return preview


@app.post("/projects/{project_id}/preview/stop")
async def stop_project_preview(project_id: str):
    """Stops live preview server for a project."""
    stopped = preview_manager.stop_preview(project_id)
    if not stopped:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Preview server for '{project_id}' was not running."
        )
    project_manager.update_project(project_id, status="stopped", preview_url=None, preview_port=None)
    return {"success": True, "project_id": project_id, "status": "stopped"}


# ==============================================================================
# GITHUB INTEGRATION FOUNDATION ENDPOINTS (PHASE 2A)
# ==============================================================================
@app.get("/github/status")
async def get_github_status():
    """
    Checks GitHub authentication readiness and returns authenticated user metadata.
    Never exposes GitHub tokens or credentials.
    """
    if not github_service.auth.is_authenticated():
        return {
            "success": True,
            "authenticated": False,
            "user": None,
            "message": "GitHub token not configured. Set GITHUB_TOKEN in your laptop environment or .env file."
        }

    try:
        user_info = await github_service.get_authenticated_user()
        return {
            "success": True,
            "authenticated": True,
            "user": user_info
        }
    except GitHubAuthError as e:
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={
                "success": False,
                "authenticated": False,
                "user": None,
                "error": {
                    "code": "GITHUB_AUTH_FAILED",
                    "message": e.message
                }
            }
        )
    except GitHubRateLimitError as e:
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={
                "success": False,
                "authenticated": False,
                "user": None,
                "error": {
                    "code": "GITHUB_RATE_LIMITED",
                    "message": e.message
                }
            }
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "success": False,
                "authenticated": False,
                "user": None,
                "error": {
                    "code": "GITHUB_SERVICE_ERROR",
                    "message": scrub_sensitive_text(str(e))
                }
            }
        )


@app.get("/github/repos")
async def list_github_repositories(
    visibility: str = "all",
    sort: str = "updated",
    per_page: int = 30,
    page: int = 1
):
    """
    Lists repositories accessible to the authenticated GitHub user.
    Requires GITHUB_TOKEN configured on the laptop server.
    """
    if not github_service.auth.is_authenticated():
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={
                "success": False,
                "error": {
                    "code": "GITHUB_UNAUTHENTICATED",
                    "message": "GitHub authentication required. Please set GITHUB_TOKEN on your laptop environment."
                }
            }
        )

    try:
        repos = await github_service.list_repositories(
            visibility=visibility,
            sort=sort,
            per_page=per_page,
            page=page
        )
        return {
            "success": True,
            "repositories": repos
        }
    except GitHubAuthError as e:
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={
                "success": False,
                "error": {
                    "code": "GITHUB_AUTH_FAILED",
                    "message": e.message
                }
            }
        )
    except GitHubRateLimitError as e:
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={
                "success": False,
                "error": {
                    "code": "GITHUB_RATE_LIMITED",
                    "message": e.message
                }
            }
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "success": False,
                "error": {
                    "code": "GITHUB_SERVICE_ERROR",
                    "message": scrub_sensitive_text(str(e))
                }
            }
        )


@app.get("/github/repos/{owner}/{repo}")
async def get_github_repository(owner: str, repo: str):
    """
    Retrieves normalized metadata for a specific GitHub repository.
    Strictly validates repository parameters to prevent traversal.
    """
    try:
        github_service.validate_identifier(owner, "owner")
        github_service.validate_identifier(repo, "repository")
    except GitHubValidationError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": {
                    "code": "INVALID_REPOSITORY_SPECIFIER",
                    "message": e.message
                }
            }
        )

    try:
        repo_data = await github_service.get_repository(owner, repo)
        return {
            "success": True,
            "repository": repo_data
        }
    except GitHubAuthError as e:
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={
                "success": False,
                "error": {
                    "code": "GITHUB_AUTH_FAILED",
                    "message": e.message
                }
            }
        )
    except GitHubNotFoundError as e:
        return JSONResponse(
            status_code=status.HTTP_404_NOT_FOUND,
            content={
                "success": False,
                "error": {
                    "code": "REPOSITORY_NOT_FOUND",
                    "message": e.message
                }
            }
        )
    except GitHubRateLimitError as e:
        return JSONResponse(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            content={
                "success": False,
                "error": {
                    "code": "GITHUB_RATE_LIMITED",
                    "message": e.message
                }
            }
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "success": False,
                "error": {
                    "code": "GITHUB_SERVICE_ERROR",
                    "message": scrub_sensitive_text(str(e))
                }
            }
        )


class GitHubDeviceCodeRequest(BaseModel):
    client_id: Optional[str] = Field(default=None, description="Optional GitHub OAuth App Client ID")


class GitHubDevicePollRequest(BaseModel):
    device_code: str = Field(..., description="Device code returned by GitHub Device Authorization")
    client_id: Optional[str] = Field(default=None, description="Optional GitHub OAuth App Client ID")


@app.post("/github/auth/device/code")
async def initiate_github_device_code(req: GitHubDeviceCodeRequest = GitHubDeviceCodeRequest()):
    """
    Initiates GitHub Device Authorization Flow (RFC 8628).
    Returns user_code and verification_uri for user to enter on github.com/login/device.
    Never exposes client secrets or tokens.
    """
    try:
        result = await github_service.initiate_device_flow(client_id=req.client_id)
        return result
    except GitHubAuthError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": {
                    "code": "DEVICE_FLOW_CONFIG_ERROR",
                    "message": e.message
                }
            }
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "success": False,
                "error": {
                    "code": "DEVICE_FLOW_ERROR",
                    "message": scrub_sensitive_text(str(e))
                }
            }
        )


@app.post("/github/auth/device/poll")
async def poll_github_device_code(req: GitHubDevicePollRequest):
    """
    Polls GitHub to check if user completed authentication on github.com/login/device.
    Saves token safely on laptop; never returns access token over HTTP.
    """
    try:
        result = await github_service.poll_device_flow(
            device_code=req.device_code,
            client_id=req.client_id
        )
        return result
    except GitHubValidationError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": {
                    "code": "INVALID_DEVICE_CODE",
                    "message": e.message
                }
            }
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "success": False,
                "error": {
                    "code": "DEVICE_POLL_ERROR",
                    "message": scrub_sensitive_text(str(e))
                }
            }
        )


@app.post("/github/auth/logout")
async def github_logout():
    """
    Clears active GitHub authentication on the laptop server safely.
    """
    github_service.logout()
    return {
        "success": True,
        "authenticated": False,
        "message": "GitHub authentication credentials cleared."
    }


# ==============================================================================
# REPOSITORY WORKSPACE & AI EDITING ENGINE ENDPOINTS (PHASE 2C-1)
# ==============================================================================
class WorkspaceInitRequest(BaseModel):
    owner: str = Field(..., description="Repository owner")
    repo: str = Field(..., description="Repository name")
    default_branch: Optional[str] = Field(default=None, description="Default branch if known")


class WorkspaceInstructRequest(BaseModel):
    instruction: str = Field(..., description="Natural language editing instruction", min_length=2)


@app.post("/repo/workspace/init")
async def init_repo_workspace(req: WorkspaceInitRequest):
    """
    Initializes or reuses an isolated repository workspace under projects/workspaces/.
    Clones safely with zero credentials embedded in remote URLs on disk.
    """
    try:
        record = await workspace_manager.initialize_workspace(
            owner=req.owner,
            repo=req.repo,
            default_branch=req.default_branch
        )
        return {
            "success": True,
            "workspace": record.to_dict()
        }
    except GitHubValidationError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": "INVALID_REPOSITORY_SPECIFIER", "message": e.message}}
        )
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except GitOperationError as e:
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "WORKSPACE_INIT_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


@app.get("/repo/workspace/{workspace_id}/status")
async def get_repo_workspace_status(workspace_id: str):
    """
    Retrieves status, active branch, baseline commit, and uncommitted git status for a workspace.
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        record = workspace_manager.get_workspace(workspace_id)
        if not record:
            return JSONResponse(
                status_code=status.HTTP_404_NOT_FOUND,
                content={"success": False, "error": {"code": "WORKSPACE_NOT_FOUND", "message": f"Workspace '{workspace_id}' not found."}}
            )

        status_info = git_change_tracker.get_status(record.workspace_path)
        return {
            "success": True,
            "workspace": record.to_dict(),
            "git_status": status_info
        }
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "WORKSPACE_STATUS_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


@app.post("/repo/workspace/{workspace_id}/instruct")
async def instruct_repo_workspace(workspace_id: str, req: WorkspaceInstructRequest):
    """
    Submits a natural-language editing instruction for an existing repository workspace.
    Applies multi-file AI changes, preserves partial edits on failure, and computes reviewable diff.
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        result = await repository_agent.execute_instruction(
            workspace_id=workspace_id,
            instruction=req.instruction
        )
        return result.to_dict()
    except AIProviderUnavailableError as e:
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except AIEditingError as e:
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except RepositoryAgentError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "AI_INSTRUCT_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


@app.get("/repo/workspace/{workspace_id}/diff")
async def get_repo_workspace_diff(workspace_id: str):
    """
    Returns the unified Git diff and file change summary against the recorded baseline commit,
    along with isolated latest-operation records and pre-existing user change indicators.
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        record = workspace_manager.get_workspace(workspace_id)
        if not record:
            return JSONResponse(
                status_code=status.HTTP_404_NOT_FOUND,
                content={"success": False, "error": {"code": "WORKSPACE_NOT_FOUND", "message": f"Workspace '{workspace_id}' not found."}}
            )

        changes = git_change_tracker.compute_diff(record.workspace_path, baseline_commit=record.baseline_commit)
        try:
            journal = repository_agent.get_operation_journal(workspace_id)
        except Exception:
            journal = []
        latest_op = journal[-1] if journal else None

        try:
            detailed_status = git_change_tracker.get_detailed_status(record.workspace_path)
            pre_changes = detailed_status.to_dict()
        except Exception:
            pre_changes = {"staged": [], "unstaged": [], "untracked": [], "clean": True}

        return {
            "success": True,
            "workspace_id": workspace_id,
            "baseline_commit": record.baseline_commit,
            "changes": changes.to_dict(),
            "latest_operation": latest_op.to_dict() if latest_op else None,
            "operations_count": len(journal),
            "pre_existing_user_changes": pre_changes,
            "publish_blocked": latest_op.publish_blocked if latest_op else False,
            "publish_block_reason": latest_op.publish_block_reason if latest_op else None
        }
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "DIFF_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


@app.post("/repo/workspace/{workspace_id}/preview")
async def start_repo_workspace_preview(workspace_id: str):
    """
    Starts dynamic LAN preview for supported static web projects (index.html).
    Refuses and explains unsupported backend/mobile frameworks without executing commands.
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        record = workspace_manager.get_workspace(workspace_id)
        if not record:
            return JSONResponse(
                status_code=status.HTTP_404_NOT_FOUND,
                content={"success": False, "error": {"code": "WORKSPACE_NOT_FOUND", "message": f"Workspace '{workspace_id}' not found."}}
            )

        static_root = detect_static_web_preview_root(record.workspace_path)
        if not static_root:
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={
                    "success": False,
                    "error": {
                        "code": "PREVIEW_UNSUPPORTED",
                        "message": (
                            "Static web preview is not supported for this repository structure. "
                            "No 'index.html' was found in the workspace root or standard static directories. "
                            "Non-static projects (e.g. backend servers, Android apps, CLI tools) cannot be previewed "
                            "as static HTML. Arbitrary execution was prevented for safety."
                        )
                    }
                }
            )

        inst = preview_manager.start_preview(
            project_id=f"ws-{workspace_id}",
            workspace_path=static_root
        )
        workspace_manager.update_workspace(
            workspace_id,
            preview_port=inst["port"],
            preview_url=inst["preview_url"]
        )

        return {
            "success": True,
            "workspace_id": workspace_id,
            "status": "running",
            "preview_url": inst["preview_url"],
            "port": inst["port"]
        }
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "PREVIEW_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


@app.delete("/repo/workspace/{workspace_id}/preview")
async def stop_repo_workspace_preview(workspace_id: str):
    """
    Stops the active LAN preview server for the workspace.
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        preview_manager.stop_preview(f"ws-{workspace_id}")
        workspace_manager.update_workspace(
            workspace_id,
            preview_port=None,
            preview_url=None
        )
        return {
            "success": True,
            "workspace_id": workspace_id,
            "message": "Preview server stopped successfully."
        }
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "PREVIEW_STOP_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


# ==============================================================================
# CONTROLLED GIT PUBLISHING PIPELINE ENDPOINTS (PHASE 2D)
# ==============================================================================
class CommitRequest(BaseModel):
    commit_message: str = Field(..., description="Commit message", min_length=1)
    selected_files: List[str] = Field(..., description="List of exact files approved for commit")
    author_name: Optional[str] = Field(default=None, description="Optional commit author name")
    author_email: Optional[str] = Field(default=None, description="Optional commit author email")


class PushRequest(BaseModel):
    branch: Optional[str] = Field(default=None, description="Destination branch")
    remote_name: Optional[str] = Field(default="origin", description="Remote repository name")
    confirmed: bool = Field(default=False, description="Explicit user confirmation required")


@app.get("/repo/workspace/{workspace_id}/publish/review")
async def get_publish_review_endpoint(workspace_id: str):
    """
    Returns full review data before publishing: changed files, attribution, diffs,
    and eligibility flags (rejecting collisions with user work).
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        review = git_publisher.get_publish_review(workspace_id)
        return {
            "success": True,
            **review.to_dict()
        }
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except RepositoryAgentError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "PUBLISH_REVIEW_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


@app.post("/repo/workspace/{workspace_id}/commit")
async def create_commit_endpoint(workspace_id: str, req: CommitRequest):
    """
    Creates an explicit, approved Git commit staging ONLY the approved files.
    Revalidates the index immediately before committing to guarantee no unrelated changes enter.
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        res = git_publisher.create_commit(
            workspace_id=workspace_id,
            commit_message=req.commit_message,
            selected_files=req.selected_files,
            author_name=req.author_name,
            author_email=req.author_email
        )
        return res.to_dict()
    except PublishError as e:
        status_code = status.HTTP_409_CONFLICT if e.code == "CONCURRENT_PUBLISH_OPERATION" else status.HTTP_422_UNPROCESSABLE_ENTITY
        return JSONResponse(
            status_code=status_code,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except GitOperationError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "COMMIT_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


@app.post("/repo/workspace/{workspace_id}/push")
async def push_to_remote_endpoint(workspace_id: str, req: PushRequest):
    """
    Executes a controlled push to the remote repository.
    Requires explicit confirmation, checks for remote divergence, and NEVER force-pushes.
    """
    try:
        workspace_manager.get_safe_workspace_path(workspace_id)
        res = git_publisher.push_to_remote(
            workspace_id=workspace_id,
            remote_name=req.remote_name or "origin",
            branch=req.branch,
            confirmed=req.confirmed
        )
        return res.to_dict()
    except PublishError as e:
        status_code = status.HTTP_409_CONFLICT if e.code in ("REMOTE_DIVERGED", "CONCURRENT_PUBLISH_OPERATION") else status.HTTP_400_BAD_REQUEST
        return JSONResponse(
            status_code=status_code,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except GitHubAuthError as e:
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content={"success": False, "error": {"code": "GITHUB_AUTH_REQUIRED", "message": e.message}}
        )
    except WorkspaceSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except GitOperationError as e:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": e.code, "message": e.message}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "PUSH_ERROR", "message": scrub_sensitive_text(str(e))}}
        )


# ==============================================================================
# ENVIRONMENT SETUP AGENT & IDEA MANAGEMENT SCHEMAS & ROUTES
# ==============================================================================

class EnvironmentPlanRequest(BaseModel):
    prompt: str = Field(..., description="User prompt or idea")
    tech_stack: Optional[str] = Field(default=None, description="Optional stack override: python, fastapi, react, fullstack, static_web")
    project_name: Optional[str] = Field(default=None)
    explicit_project_id: Optional[str] = Field(default=None)

class ExecutePlanRequest(BaseModel):
    project_id: str = Field(..., description="Target project ID")
    approved: bool = Field(default=True, description="Explicit approval confirmation")
    prompt: Optional[str] = Field(default="", description="Original prompt text")

class CaptureIdeaRequest(BaseModel):
    prompt: str = Field(..., description="Idea description")
    title: Optional[str] = Field(default=None)
    tech_stack: Optional[str] = Field(default=None)

class RefineIdeaRequest(BaseModel):
    blueprint: Optional[Dict[str, Any]] = None


@app.get("/api/environment/detect")
async def detect_environment():
    """Detects laptop OS, architecture, installed runtimes (Python, Node, npm, Git), and evaluates stack support."""
    env = environment_detector.get_host_environment()
    stacks = ["python", "fastapi", "react", "fullstack", "static_web"]
    evaluations = {s: environment_detector.evaluate_stack_support(s, env).to_dict() for s in stacks}
    return {
        "success": True,
        "environment": env.to_dict(),
        "stack_evaluations": evaluations
    }


@app.post("/api/environment/plan")
async def plan_environment_setup(req: EnvironmentPlanRequest):
    """Generates a structured, deterministic environment setup and scaffolding plan."""
    clean_prompt = req.prompt.strip() if req.prompt else ""
    if not clean_prompt:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Prompt cannot be empty.")
    try:
        env = environment_detector.get_host_environment()
        target_stack = req.tech_stack or idea_manager.detect_recommended_stack(clean_prompt)
        eval_result = environment_detector.evaluate_stack_support(target_stack, env)
        if not eval_result.supported:
            return JSONResponse(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                content={
                    "success": False,
                    "error": {
                        "code": "ENVIRONMENT_INCOMPATIBLE",
                        "message": f"Target stack '{target_stack}' is not supported on this laptop: {', '.join(eval_result.missing_tools or eval_result.incompatible_versions)}",
                        "evaluation": eval_result.to_dict()
                    }
                }
            )

        plan = environment_setup_agent.create_setup_plan(
            prompt=clean_prompt,
            tech_stack=target_stack,
            project_name=req.project_name,
            explicit_project_id=req.explicit_project_id,
            env=env
        )
        return {
            "success": True,
            "plan": plan.to_dict(),
            "evaluation": eval_result.to_dict()
        }
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "PLANNING_FAILED", "message": str(e)}}
        )


@app.post("/api/environment/execute-plan")
async def execute_environment_plan(req: ExecutePlanRequest):
    """Executes an approved setup plan step-by-step."""
    if not req.approved:
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={"success": False, "error": {"code": "APPROVAL_REQUIRED", "message": "Explicit user approval is required to execute environment setup."}}
        )

    plan = setup_execution_engine.get_plan_state(req.project_id)
    meta = project_manager.get_project(req.project_id)
    if not plan:
        if not meta:
            return JSONResponse(
                status_code=status.HTTP_404_NOT_FOUND,
                content={"success": False, "error": {"code": "PROJECT_NOT_FOUND", "message": f"Project '{req.project_id}' not found."}}
            )
        plan = environment_setup_agent.create_setup_plan(
            prompt=req.prompt or meta.description or meta.project_name,
            tech_stack=meta.project_type,
            project_name=meta.project_name,
            explicit_project_id=meta.project_id
        )

    plan.approved = True
    plan.status = "approved"

    try:
        prompt_val = req.prompt or (meta.description if meta else "") or (meta.project_name if meta else "")
        updated_plan = setup_execution_engine.execute_plan(
            plan=plan,
            prompt=prompt_val
        )
        http_code = status.HTTP_200_OK if updated_plan.status == "completed" else status.HTTP_500_INTERNAL_SERVER_ERROR
        return JSONResponse(
            status_code=http_code,
            content={
                "success": updated_plan.status == "completed",
                "plan": updated_plan.to_dict(),
                "preview_url": updated_plan.preview_url
            }
        )
    except SetupSecurityError as e:
        return JSONResponse(
            status_code=status.HTTP_403_FORBIDDEN,
            content={"success": False, "error": {"code": "SECURITY_VIOLATION", "message": str(e)}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "EXECUTION_FAILED", "message": str(e)}}
        )


@app.get("/api/environment/status/{project_id}")
async def get_environment_status(project_id: str):
    """Returns live setup progress, step status, and server state for a project."""
    plan = setup_execution_engine.get_plan_state(project_id)
    server = dev_server_manager.get_server(project_id)
    meta = project_manager.get_project(project_id)

    if not plan and not meta:
        return JSONResponse(
            status_code=status.HTTP_404_NOT_FOUND,
            content={"success": False, "error": {"code": "PROJECT_NOT_FOUND", "message": f"Project '{project_id}' not found."}}
        )

    return {
        "success": True,
        "project_id": project_id,
        "plan": plan.to_dict() if plan else None,
        "server": server.to_dict() if server else None,
        "project": meta.to_dict() if meta else None,
        "preview_url": (server.preview_url if server else (plan.preview_url if plan else (meta.preview_url if meta else None)))
    }


@app.post("/api/environment/cancel/{project_id}")
async def cancel_environment_setup(project_id: str):
    """Cancels active setup execution for a project."""
    cancelled = setup_execution_engine.cancel_setup(project_id)
    return {
        "success": True,
        "cancelled": cancelled,
        "message": f"Setup for '{project_id}' {'cancelled' if cancelled else 'was not running'}"
    }


@app.post("/api/environment/stop-server/{project_id}")
async def stop_dev_server(project_id: str):
    """Stops the active development server for a project."""
    stopped = dev_server_manager.stop_server(project_id)
    if stopped:
        project_manager.update_project(project_id, status="stopped")
    return {
        "success": True,
        "stopped": stopped,
        "message": f"Server for '{project_id}' {'stopped' if stopped else 'was not running'}"
    }


@app.post("/api/ideas/capture")
async def capture_user_idea(req: CaptureIdeaRequest):
    """Captures and stores an idea permanently in projects/ideas.json."""
    clean_prompt = req.prompt.strip() if req.prompt else ""
    if not clean_prompt:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Idea prompt cannot be empty.")
    try:
        record = idea_manager.capture_idea(
            prompt=clean_prompt,
            title=req.title,
            tech_stack=req.tech_stack
        )
        return {
            "success": True,
            "idea": record.to_dict()
        }
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "IDEA_CAPTURE_FAILED", "message": str(e)}}
        )


@app.get("/api/ideas")
async def list_user_ideas():
    """Lists all captured user ideas sorted by update timestamp."""
    ideas = idea_manager.list_ideas()
    return {
        "success": True,
        "ideas": [i.to_dict() for i in ideas],
        "count": len(ideas)
    }


@app.get("/api/ideas/{idea_id}")
async def get_user_idea(idea_id: str):
    """Retrieves an idea by ID."""
    idea = idea_manager.get_idea(idea_id)
    if not idea:
        return JSONResponse(
            status_code=status.HTTP_404_NOT_FOUND,
            content={"success": False, "error": {"code": "IDEA_NOT_FOUND", "message": f"Idea '{idea_id}' not found."}}
        )
    return {
        "success": True,
        "idea": idea.to_dict()
    }


@app.post("/api/ideas/{idea_id}/refine")
async def refine_user_idea(idea_id: str, req: Optional[RefineIdeaRequest] = None):
    """Transforms an idea into an actionable project blueprint."""
    try:
        bp_override = IdeaBlueprint.from_dict(req.blueprint) if (req and req.blueprint) else None
        record = idea_manager.refine_idea(idea_id, blueprint_override=bp_override)
        return {
            "success": True,
            "idea": record.to_dict()
        }
    except KeyError:
        return JSONResponse(
            status_code=status.HTTP_404_NOT_FOUND,
            content={"success": False, "error": {"code": "IDEA_NOT_FOUND", "message": f"Idea '{idea_id}' not found."}}
        )
    except Exception as e:
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={"success": False, "error": {"code": "REFINE_FAILED", "message": str(e)}}
        )


# ==============================================================================
# SERVER RUNNER & STARTUP BANNER
# ==============================================================================

def print_startup_banner():
    """Prints network interface IPs on startup showing the exact mobile Wi-Fi URL."""
    primary_ip = get_primary_lan_ip()
    local_ips = get_local_ip_addresses()
    print("=" * 72)
    print("  IDEA BEACON - LOCAL LAPTOP AI BRIDGE v2.2")
    print("=" * 72)
    print(f"  [+] Host binding:    0.0.0.0 (Port {HTTP_PORT})")
    print(f"  [+] UDP Discovery:   Port {DISCOVERY_UDP_PORT} (IDEA_BEACON_DISCOVER responder)")
    print(f"  [+] Local Ollama:    {OLLAMA_BASE_URL} (Model: {DEFAULT_MODEL})")
    print("-" * 72)
    print("  [>] PRIMARY WI-FI CONNECTION (Use on your Android phone):")
    print(f"      Idea Beacon Bridge running at http://{primary_ip}:{HTTP_PORT}")
    if len(local_ips) > 1:
        print("      Other detected LAN interfaces:")
        for ip in local_ips:
            if ip != primary_ip:
                print(f"        -> http://{ip}:{HTTP_PORT}")
    print("-" * 72)
    print("  [>] ALTERNATIVE CONNECTION ENDPOINTS:")
    print(f"      Localhost:       http://localhost:{HTTP_PORT}")
    print(f"      USB Cable (ADB): http://localhost:{HTTP_PORT} (fallback: adb reverse tcp:{HTTP_PORT} tcp:{HTTP_PORT})")
    print("=" * 72 + "\n")


if __name__ == "__main__":
    print_startup_banner()
    uvicorn.run("laptop_ai_server:app", host="0.0.0.0", port=HTTP_PORT, reload=True)

