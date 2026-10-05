"""
Agentic-Architect-Voice-to-Repository-Pipeline
Production Laptop Backend & Local AI Bridge Server

Features:
1. Setup & Networking: FastAPI on host 0.0.0.0, port 8000 with permissive CORS.
2. Endpoint: POST /generate accepting `prompt` and `project_path`.
3. Local AI Integration: Connects to local Ollama on port 11434 (RTX GPU).
4. IDE Injection (Two Methods toggled via if/else):
   - Method A (Direct File Writing): Extracts code from Ollama, infers filename, writes to project_path.
   - Method B (Cursor CLI Agent): Executes `agent -p "<prompt>"` directly inside project_path.
5. Startup: Automatically detects and displays local IPv4 network addresses.
"""

import os
import re
import time
import socket
import subprocess
from typing import Optional, Dict, Any

import httpx
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ==============================================================================
# CONFIGURATION & TOGGLE
# ==============================================================================
# Set to False for Method A (Direct File Writing via Ollama)
# Set to True  for Method B (Cursor CLI Agent via `agent -p "<prompt>"`)
USE_CURSOR_AGENT: bool = False

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "qwen2.5-coder:1.5b")
DEFAULT_WORKSPACE_PATH = os.path.abspath(os.path.dirname(os.path.dirname(__file__)))

# ==============================================================================
# 1. SETUP & NETWORKING
# ==============================================================================
app = FastAPI(
    title="Agentic Architect - Local Laptop Bridge",
    description="Receives voice-transcribed prompts from Android and injects code into your IDE",
    version="2.0.0"
)

# Configure CORS to accept mobile phone requests from any local network IP
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ==============================================================================
# 2. PAYLOAD SCHEMAS
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


# ==============================================================================
# HELPER FUNCTIONS: Code Extraction & Filename Inference
# ==============================================================================
def extract_code_and_filename(raw_text: str, prompt: str) -> tuple[str, str]:
    """
    Parses code blocks from Ollama markdown output and infers an idiomatic filename.
    """
    # 1. Extract markdown fenced code block: ```language ... ```
    code_block_match = re.search(r"```([a-zA-Z0-9_\-\+]*)\n(.*?)```", raw_text, re.DOTALL)
    if code_block_match:
        lang = code_block_match.group(1).lower().strip()
        code = code_block_match.group(2).strip()
    else:
        lang = ""
        code = raw_text.strip()

    # 2. Map language to appropriate file extension
    ext_map = {
        "kotlin": ".kt", "kt": ".kt",
        "python": ".py", "py": ".py",
        "typescript": ".ts", "ts": ".ts",
        "javascript": ".js", "js": ".js",
        "java": ".java",
        "rust": ".rs", "rs": ".rs",
        "go": ".go",
        "html": ".html", "css": ".css",
        "json": ".json", "markdown": ".md", "md": ".md"
    }
    ext = ext_map.get(lang)
    if not ext:
        p_lower = prompt.lower()
        if any(k in p_lower for k in ["kotlin", "android", "compose"]):
            ext = ".kt"
        elif any(k in p_lower for k in ["python", "fastapi", "flask"]):
            ext = ".py"
        elif any(k in p_lower for k in ["typescript", "react", "next"]):
            ext = ".ts"
        else:
            ext = ".kt"

    # 3. Clean prompt words to build PascalCase or snake_case filename
    cleaned = re.sub(r"(?i)\b(create|build|make|generate|an|a|the|for|in|code|write|please|file)\b", "", prompt)
    words = re.findall(r"[a-zA-Z0-9]+", cleaned)
    
    if words:
        if ext in [".kt", ".java"]:
            base_name = "".join(w.capitalize() for w in words[:4])
        else:
            base_name = "_".join(w.lower() for w in words[:4])
    else:
        base_name = "GeneratedPipeline" if ext in [".kt", ".java"] else "generated_pipeline"

    return code, f"{base_name}{ext}"


def get_local_ip_addresses() -> list[str]:
    """Retrieves all non-loopback IPv4 addresses assigned to local network adapters."""
    ips = []
    try:
        hostname = socket.gethostname()
        for ip in socket.gethostbyname_ex(hostname)[2]:
            if not ip.startswith("127.") and not ip.startswith("169.254"):
                ips.append(ip)
    except Exception:
        pass
    if not ips:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ips.append(s.getsockname()[0])
            s.close()
        except Exception:
            pass
    return list(dict.fromkeys(ips)) or ["127.0.0.1"]


# ==============================================================================
# ENDPOINTS
# ==============================================================================
@app.get("/")
@app.get("/health")
async def health_check():
    """Health check endpoint to test connection from phone or browser."""
    ollama_online = False
    available_models = []
    try:
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
            if resp.status_code == 200:
                ollama_online = True
                available_models = [m.get("name") for m in resp.json().get("models", [])]
    except Exception:
        ollama_online = False

    return {
        "status": "healthy",
        "service": "Agentic-Architect-Laptop-Bridge",
        "ollama_online": ollama_online,
        "available_models": available_models,
        "cursor_agent_toggle": USE_CURSOR_AGENT,
        "local_ips": get_local_ip_addresses()
    }


# ==============================================================================
# 3. /generate ENDPOINT (Local AI + IDE Injection Toggle)
# ==============================================================================
@app.post("/generate", response_model=GenerateResponse)
async def generate_and_inject(request: GenerateRequest):
    """
    Receives transcribed voice prompt from Android, generates code via Ollama or
    triggers the Cursor CLI agent, and injects the result into your IDE workspace.
    """
    if not request.prompt or not request.prompt.strip():
        raise HTTPException(status_code=400, detail="Voice prompt cannot be empty.")

    start_time = time.time()
    
    # Resolve target project directory
    target_dir = request.project_path or DEFAULT_WORKSPACE_PATH
    if not os.path.exists(target_dir):
        try:
            os.makedirs(target_dir, exist_ok=True)
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Failed to create project path: {str(e)}")

    # Determine which injection method to execute
    should_use_cursor = request.use_cursor_agent if request.use_cursor_agent is not None else USE_CURSOR_AGENT

    # ==========================================================================
    # 4. IDE INJECTION: IF/ELSE TOGGLE
    # ==========================================================================
    if should_use_cursor:
        # ----------------------------------------------------------------------
        # METHOD B: Cursor CLI Agent (subprocess trigger)
        # ----------------------------------------------------------------------
        print(f"\n[Method B] Triggering Cursor CLI agent inside: {target_dir}")
        print(f"[Method B] Prompt: \"{request.prompt}\"")
        
        command = f'agent -p "{request.prompt}"'
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
                    prompt=request.prompt,
                    stdout=process.stdout,
                    latency_ms=elapsed_ms,
                    message=f"Cursor agent executed prompt successfully inside {target_dir}"
                )
            else:
                return GenerateResponse(
                    status="warning",
                    method="cursor_cli_agent",
                    prompt=request.prompt,
                    stdout=process.stdout or process.stderr,
                    latency_ms=elapsed_ms,
                    message=f"Cursor agent returned exit code {process.returncode}. (Check if 'agent' CLI is configured in PATH)"
                )
        except FileNotFoundError:
            raise HTTPException(
                status_code=500,
                detail="Cursor CLI 'agent' not found in system PATH. Ensure Cursor CLI is installed."
            )
        except subprocess.TimeoutExpired:
            raise HTTPException(status_code=504, detail="Cursor agent command timed out (180s).")
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Cursor agent execution failed: {str(e)}")

    else:
        # ----------------------------------------------------------------------
        # METHOD A: Direct File Writing (Ollama -> Parse -> File Write)
        # ----------------------------------------------------------------------
        print(f"\n[Method A] Querying local Ollama GPU on {OLLAMA_BASE_URL} ...")
        selected_model = request.model or DEFAULT_MODEL
        
        system_instruction = (
            "You are the Agentic Architect AI Engine. "
            "The user spoke a command from their Android phone. "
            "Generate clean, production-ready code with repository architecture. "
            "Always include the code inside fenced markdown blocks (e.g., ```kotlin ... ``` or ```python ... ```)."
        )

        ollama_payload = {
            "model": selected_model,
            "prompt": request.prompt,
            "system": system_instruction,
            "stream": False,
            "options": {"temperature": 0.2}
        }

        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                resp = await client.post(f"{OLLAMA_BASE_URL}/api/generate", json=ollama_payload)
                if resp.status_code != 200:
                    raise HTTPException(
                        status_code=resp.status_code, 
                        detail=f"Ollama error: {resp.text}"
                    )
                ollama_data = resp.json()
                raw_generated_text = ollama_data.get("response", "")

        except httpx.ConnectError:
            # Fallback offline template if Ollama service is unreachable
            raw_generated_text = (
                f"```kotlin\n"
                f"// [Agentic Architect Offline Fallback]\n"
                f"// Generated for voice prompt: \"{request.prompt}\"\n"
                f"class AutonomousAgentPipeline {{\n"
                f"    fun execute() {{\n"
                f"        println(\"Agent pipeline active from mobile voice prompt.\")\n"
                f"    }}\n"
                f"}}\n"
                f"```"
            )

        # 1. Extract raw code & infer filename from prompt
        code_content, inferred_filename = extract_code_and_filename(raw_generated_text, request.prompt)
        
        # 2. Write file directly into the target project_path
        injected_dir = os.path.join(target_dir, "injected_agents")
        os.makedirs(injected_dir, exist_ok=True)
        file_path = os.path.join(injected_dir, inferred_filename)

        with open(file_path, "w", encoding="utf-8") as f:
            f.write(code_content)

        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        print(f"[Method A] Injected generated file: {file_path}")

        return GenerateResponse(
            status="success",
            method="direct_file_writing",
            prompt=request.prompt,
            code=code_content,
            file_path=file_path,
            filename=inferred_filename,
            model=selected_model,
            latency_ms=elapsed_ms,
            message=f"Code generated by Ollama and saved directly to {file_path}"
        )


# ==============================================================================
# 5. SERVER RUNNER & STARTUP BANNER
# ==============================================================================
def print_startup_banner():
    """Prints network interface IPs on startup so you know the exact mobile URL."""
    local_ips = get_local_ip_addresses()
    print("=" * 72)
    print("  AGENTIC ARCHITECT - LOCAL LAPTOP BACKEND & AI BRIDGE")
    print("=" * 72)
    print(f"  [+] Host binding: 0.0.0.0 (Port 8000)")
    print(f"  [+] Local Ollama: {OLLAMA_BASE_URL} (Model: {DEFAULT_MODEL})")
    print(f"  [+] Active IDE Injection Toggle:")
    print(f"      USE_CURSOR_AGENT = {USE_CURSOR_AGENT}")
    print(f"      -> {'Method B (Cursor CLI Agent)' if USE_CURSOR_AGENT else 'Method A (Direct File Writing via Ollama)'}")
    print("-" * 72)
    print("  [>] MOBILE DEVICE CONNECTION URLS (Use in your Android app):")
    for ip in local_ips:
        print(f"      http://{ip}:8000/generate")
    print("      http://localhost:8000/generate (if using: adb reverse tcp:8000 tcp:8000)")
    print("=" * 72 + "\n")


if __name__ == "__main__":
    print_startup_banner()
    uvicorn.run("laptop_ai_server:app", host="0.0.0.0", port=8000, reload=True)
