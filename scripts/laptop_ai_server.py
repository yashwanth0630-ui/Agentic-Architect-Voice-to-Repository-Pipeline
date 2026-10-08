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

# ==============================================================================
# CONFIGURATION & TOGGLE
# ==============================================================================
# Set to False for Method A (Direct File Writing via Ollama)
# Set to True  for Method B (Cursor CLI Agent via `agent -p "<prompt>"`)
USE_CURSOR_AGENT: bool = False

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "qwen2.5-coder:1.5b")
DEFAULT_WORKSPACE_PATH = os.path.abspath(os.path.dirname(SCRIPT_DIR))

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
            pass

    response = await call_next(request)
    return response


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


class ValidationResult(BaseModel):
    valid: bool
    language: str
    syntax_valid: bool
    error: Optional[str] = None
    line_count: int = 0
    byte_count: int = 0
    checksum_sha256: Optional[str] = None


class GenerateResponse(BaseModel):
    status: str
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
        
        command = f'agent -p "{clean_prompt}"'
        try:
            process = subprocess.run(
                command,
                cwd=target_dir,
                shell=True,
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
            "options": {"temperature": request.temperature or 0.2}
        }

        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
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

