# Idea Beacon / Agentic Architect — Full Repository Audit Report

**Date:** 2026-10-09  
**Repository:** `yashwanth0630-ui/Agentic-Architect-Voice-to-Repository-Pipeline`  
**Auditor:** Antigravity (Lead Engineer)  
**Status:** Audit Completed — Pending User Review & Approval  

---

## 1. Executive Summary

This audit assesses the current state of **Idea Beacon (Agentic Architect)** to determine why spoken prompts such as:
> *"Create me a folder where I can build a website for my company"*

currently generate a single placeholder file:
> `injected_agents/AutonomousAgentPipeline.kt`

instead of creating an actual, functional project workspace with dependencies and a live browser preview.

### Core Audit Verdict
The existing system has a **rock-solid local network transport layer** (UDP auto-discovery, HTTP fallback, local Wi-Fi LAN routing between Android phone and laptop, and robust input validation), but it **completely lacks a Project/Workspace Management layer and a multi-tool Agent Orchestrator**. 

Currently, the server on port 8000 treats every request—regardless of intent—as a request to inject a single Kotlin/Python source file into a flat `./injected_agents/` directory. When local Ollama inference fails or is offline, it defaults to a hardcoded Kotlin snippet called `AutonomousAgentPipeline.kt`.

---

## 2. Current Architecture & Request Flow

```
┌────────────────────────────────────────────────────────┐
│                   PHYSICAL ANDROID PHONE               │
│                  (Poco X6 5G / iQOO Device)            │
│                                                        │
│  1. Voice Input (SpeechRecognizer)                     │
│  2. Qualcomm GenieX NPU (Intent/Keywords Parsing)      │
│  3. Auto-Discovery (UDP 8001 / HTTP /discover)         │
│  4. User clicks "Sync to Laptop" / Send                │
└───────────────────────────┬────────────────────────────┘
                            │
                            │ HTTP POST /generate (Port 8000)
                            │ Wi-Fi LAN (e.g., 192.168.29.47:8000)
                            ▼
┌────────────────────────────────────────────────────────┐
│             LAPTOP FASTAPI BRIDGE (Port 8000)          │
│              (scripts/laptop_ai_server.py)             │
│                                                        │
│  1. Origin verification (RFC 1918 private subnets)     │
│  2. Target directory check (default: ./injected_agents)│
│  3. Ollama query (http://localhost:11434/api/generate) │
│     ├── IF OFFLINE: Catch ConnectError/Timeout         │
│     │   └── Fallback: Hardcoded Kotlin template        │
│     │       "AutonomousAgentPipeline.kt"               │
│     └── IF ONLINE: Single-file prompt instruction      │
│  4. Validate code syntax (Python AST / Brackets)       │
│  5. Safe atomic file write:                            │
│     Writes ONLY 1 file into ./injected_agents/<file>   │
└────────────────────────────────────────────────────────┘
```

### Sidecar Server (Port 3000)
There is also a Node.js/Express server in `src/api/server.ts` listening on port 3000:
- Provides `/api/npu/infer` (Snapdragon emulator), `/api/prompt/parse`, and `/api/manifests/generate`.
- Provides `/api/export` (streams a memory-buffered `bundle.zip` with `AGENTS.md`, `.cursorrules`, `CLAUDE.md`, `GEMINI.md`, and `skills/`).
- Operates independently from `scripts/laptop_ai_server.py`. The Android app currently talks directly to port 8000 (`laptop_ai_server.py`).

---

## 3. Existing Files & Their Responsibilities

| File / Component | Language / Type | Current Responsibility | Status |
| :--- | :--- | :--- | :--- |
| `scripts/laptop_ai_server.py` | Python (FastAPI) | Primary laptop bridge on `0.0.0.0:8000`. Handles UDP 8001 discovery, `/discover`, `/health`, and `/generate`. Injects single files into `injected_agents/`. | **Working**, but limited to single-file injection & placeholder fallback |
| `src/api/server.ts` | TypeScript (Express) | Manifest generation API on port 3000. Serves `/api/health`, `/api/npu/infer`, `/api/prompt/parse`. | **Working** (all 17 Vitest tests passing) |
| `src/api/export.ts` | TypeScript | Streams memory-buffered `bundle.zip` containing AI manifest pyramids (`AGENTS.md`, `.cursorrules`, etc.). | **Working** |
| `src/generator/generator.ts` | TypeScript | Zod-validated template generator for agent guidelines (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, `.cursorrules`, and `skills/`). | **Working** |
| `src/core/npu.ts` | TypeScript | Keyword/heuristic parser emulating Qualcomm GenieX SDK on Snapdragon Hexagon NPU. | **Working** |
| `src/mcp/server.ts` | TypeScript (Stdio) | Model Context Protocol server exposing `infer_architecture`, `generate_agent_manifests`, `package_bundle_zip`. | **Working** |
| `android/.../MainActivity.kt` | Kotlin (Compose) | Android entry point. Coordinates `VoicePromptManager`, `QualcommGenieXNpuEngine`, and `LaptopAiRepository`. | **Working** |
| `android/.../LaptopAiRepository.kt` | Kotlin | Discovers laptop IP over Wi-Fi, dispatches `/generate` POST requests, manages connection state badges. | **Working** |
| `android/.../AgentManifestGenerator.kt` | Kotlin | Generates AI manifests and ZIP bundles on-device on Android. | **Working** |
| `tests/test_laptop_ai_server.py` | Python (unittest) | 32 comprehensive tests for path traversal, syntax checks, discovery, and endpoint security. | **Working** (32/32 tests pass) |
| `tests/*.test.ts` | TypeScript (Vitest) | 17 unit/integration tests for NPU parsing, manifest generation, and ZIP streaming. | **Working** (17/17 tests pass) |
| `injected_agents/` | Directory | Destination folder where `laptop_ai_server.py` drops generated files. | **Working**, but wrong place for actual project workspaces |
| `OpenMontage/` | Subproject | Independent sub-repository for automated video composition (`DemoVidForIQOO.mp4`). | **Independent** (Do not touch) |

---

## 4. Why `"create me a folder where I can build a website for my company"` Generated `AutonomousAgentPipeline.kt`

A detailed code trace through `scripts/laptop_ai_server.py` reveals the exact root cause:

1. **Prompt Received:** The Android phone captured the voice prompt and sent:
   ```json
   {
     "prompt": "create me a folder where I can build a website for my company",
     "model": "qwen2.5-coder:1.5b",
     "temperature": 0.2
   }
   ```
2. **Ollama Offline Catch:** On line 709 of `scripts/laptop_ai_server.py`, the server attempted:
   ```python
   async with httpx.AsyncClient(timeout=120.0) as client:
       resp = await client.post(f"{OLLAMA_BASE_URL}/api/generate", json=ollama_payload)
   ```
   Because Ollama was not actively running on `localhost:11434`, httpx threw `httpx.ConnectError`.
3. **Hardcoded Fallback Triggered:** Line 719 caught the exception:
   ```python
   except (httpx.ConnectError, httpx.TimeoutException):
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
   ```
4. **Filename Inferred:** Line 347 regex parsed `// File: AutonomousAgentPipeline.kt` -> `inferred_filename = "AutonomousAgentPipeline.kt"`.
5. **Single File Injected into Flat Directory:** Line 488 created `./injected_agents/` and wrote `AutonomousAgentPipeline.kt` there.
6. **Even If Ollama Was Running:**
   The server system prompt (line 692) asked the LLM for **a single code block with a single filename comment**, and always wrote that single file to `./injected_agents/`. It possessed **no concept of creating a folder, scaffolding multiple files, creating `package.json`, running `npm install`, or starting a web server**.

---

## 5. What Already Works

- **Phone-to-Laptop Wi-Fi Connectivity:** Verified live between Poco X6 5G (`192.168.29.145`) and laptop (`192.168.29.47:8000`). Auto-discovery (UDP 8001 and HTTP `/discover`) works seamlessly.
- **Safety & Validation Layer:** Path traversal sanitization, AST syntax checks, bracket balancing, atomic file writes, and RFC 1918 private subnet firewall guards.
- **AI Manifest Generation:** Complete manifest pyramid generators (`AGENTS.md`, `.cursorrules`, `CLAUDE.md`, `GEMINI.md`, `skills/`) exist in TypeScript and Kotlin.
- **Test Suites:** 100% green test coverage across 49 existing test cases (17 Vitest + 32 Python unittest).

---

## 6. What Is Incomplete / Missing

- **No Project Manager Layer:** No tracking of project IDs, project types, workspace directory paths, creation dates, or server statuses.
- **No Workspace Directory Scaffolding:** No facility to create `projects/<project-name>/` with structured trees (`src/`, `public/`, `package.json`, `README.md`, `index.html`).
- **No Agent Orchestrator / Tool Action Engine:** The server cannot execute compound tool actions (e.g., `create_directory`, `write_file`, `install_dependencies`, `start_dev_server`, `run_command`).
- **No Live Browser / LAN Preview Server:** Projects cannot be previewed in a browser or from a mobile phone over LAN.
- **No GitHub Integration:** No GitHub token configuration, repository listing, repository cloning, diff inspection, or approval-gated commit/push workflow.
- **Single-File Trap:** `/generate` assumes 1 prompt = 1 injected file.

---

## 7. Recommended Target Architecture

```
                       ┌────────────────────────────┐
                       │        ANDROID PHONE       │
                       │     (Idea Beacon App)      │
                       └─────────────┬──────────────┘
                                     │
                                     │ Wi-Fi LAN / HTTP
                                     ▼
                       ┌────────────────────────────┐
                       │  IDEA BEACON LAPTOP SERVER │
                       │    (FastAPI / REST API)    │
                       └─────────────┬──────────────┘
                                     │
                                     ▼
                       ┌────────────────────────────┐
                       │      PROJECT MANAGER       │
                       │  • projects.json registry  │
                       │  • projects/<project-id>/  │
                       │  • Port & Dev Server Alloc │
                       └──────┬──────────────┬──────┘
                              │              │
           MODE A: NEW PROJECT│              │MODE B: GITHUB REPO
                              ▼              ▼
     ┌────────────────────────────┐     ┌────────────────────────────┐
     │   AI AGENT ORCHESTRATOR    │     │   GITHUB INTEGRATION LAYER │
     │  1. Understand intent      │     │  1. Secure token (env/cfg) │
     │  2. Decide project type    │     │  2. List user repos        │
     │  3. Plan files & structure │     │  3. Clone into workspace   │
     │  4. Execute tool actions   │     │  4. Stage AI modifications │
     │  5. Verify dependencies    │     │  5. Generate git diff      │
     │  6. Start dev server       │     │  6. User approval gate     │
     └─────────────┬──────────────┘     │  7. Commit & push          │
                   │                    └─────────────┬──────────────┘
                   │                                  │
                   ▼                                  ▼
     ┌───────────────────────────────────────────────────────────────┐
     │                     WORKSPACE DIRECTORY                       │
     │   projects/company-website/                                   │
     │   ├── package.json / index.html                               │
     │   ├── src/                                                    │
     │   └── README.md                                               │
     └───────────────────────────────┬───────────────────────────────┘
                                     │
                                     ▼
     ┌───────────────────────────────────────────────────────────────┐
     │                      LAN BROWSER PREVIEW                      │
     │  Local static/Vite/Node server bound to 0.0.0.0               │
     │  Returns: http://192.168.x.x:<DEV_PORT>                       │
     │  Accessible instantly on phone browser & laptop               │
     └───────────────────────────────────────────────────────────────┘
```

### Detailed Mode Descriptions

#### Mode A — New Project Workflow
1. User speaks: *"Create a modern website for my company"*
2. **Intent Interpreter:** Detects project creation request, identifies tech stack (e.g. Modern responsive HTML/CSS/JS or Vite/React), derives clean slug: `company-website`.
3. **Project Manager:** Allocates `projects/company-website/`, registers entry in `projects/projects.json`.
4. **Agent Orchestrator (Multi-File Scaffolder):** Generates full real website:
   - `index.html` (modern, responsive landing page with hero, features, contact section, polished CSS)
   - `styles.css` / `app.js`
   - `README.md`
   - `package.json` (if node-based)
5. **Dev Preview Server:** Starts a lightweight local HTTP server bound to `0.0.0.0:<port>` on an available port (e.g., 5173 or 8080).
6. **Structured Response:** Returns:
   ```json
   {
     "success": true,
     "project_id": "company-website",
     "project_name": "Company Website",
     "workspace": "C:/.../projects/company-website",
     "status": "created",
     "preview_url": "http://192.168.29.47:8080",
     "message": "Company website created and live at http://192.168.29.47:8080"
   }
   ```
7. Subsequent prompt: *"Add a contact page to this website"* -> Detects context, updates existing `projects/company-website/` without creating a duplicate project.

#### Mode B — Existing GitHub Repository Workflow
1. Securely configure GitHub token via environment variable (`GITHUB_TOKEN` in `.env` or system keyring). **Never expose in logs, UI, or commits.**
2. Endpoints:
   - `GET /api/github/repos` -> Lists authenticated user's repositories.
   - `POST /api/github/clone` -> Clones selected repo into `projects/<repo-name>`.
   - `POST /api/projects/:id/modify` -> AI modifies existing project within workspace boundary.
   - `GET /api/projects/:id/diff` -> Returns exact `git diff`.
   - `POST /api/projects/:id/commit-and-push` -> Requires explicit user confirmation; commits and pushes to origin.

---

## 8. Implementation File Plan

### A. Files That Should Be MODIFIED
1. **`scripts/laptop_ai_server.py`**:
   - Integrate `ProjectManager` and `AgentOrchestrator`.
   - Update `POST /generate` to support both project creation/modification and single-file fallbacks.
   - Return structured response (`project_id`, `preview_url`, `workspace`, `status`).
   - Add endpoints for project listing (`GET /projects`), project status (`GET /projects/{id}`), preview control, and GitHub operations.
   - Replace the simplistic offline fallback with an intelligent offline template scaffolder that builds real multi-file projects when Ollama is unavailable.
2. **`android/app/src/main/java/com/example/agenticarchitect/data/remote/LaptopAiRepository.kt`**:
   - Update `GenerateResponse` data class to capture `projectId`, `previewUrl`, `status`.
3. **`android/app/src/main/java/com/example/agenticarchitect/MainActivity.kt`**:
   - Display `previewUrl` in toast and chat UI so the user can tap to open the live preview in their phone's browser.
4. **`tests/test_laptop_ai_server.py`**:
   - Add test cases for project creation, directory scaffolding, dev server preview, and project management while preserving existing 32 tests.

### B. Files That Should Be CREATED
1. **`scripts/project_manager.py`**:
   - Core domain class `ProjectManager`.
   - Manages `projects/` folder and `projects/projects.json` metadata database.
   - Methods: `create_project()`, `get_project()`, `list_projects()`, `allocate_preview_port()`, `delete_project()`.
2. **`scripts/agent_orchestrator.py`**:
   - Intent recognition (New Project vs. Modify Existing Project vs. Inspect vs. Single Module).
   - Tool execution engine (`create_directory`, `write_files`, `install_deps`, `start_preview_server`, `get_diff`).
   - Template engine for rich web apps, APIs, and microservices (offline-capable).
3. **`scripts/preview_server.py`**:
   - Manages live background HTTP preview servers for generated projects.
   - Dynamic port selection, LAN binding (`0.0.0.0`), process lifecycle (start, status, stop).
4. **`scripts/github_service.py`**:
   - Secure GitHub API client using `GITHUB_TOKEN` from environment.
   - Methods: `list_repositories()`, `clone_repository()`, `git_status()`, `git_diff()`, `git_commit()`, `git_push()`.
5. **`ARCHITECTURE.md`**:
   - Comprehensive system documentation detailing architecture, API contracts, lifecycles, and security model.

### C. Files That Should NOT Be Touched
- **`OpenMontage/`**: Independent video generation subproject. Must not be altered.
- **`src/api/server.ts` & `src/api/export.ts`**: Existing TypeScript manifest API on port 3000; already passing all 17 Vitest tests.
- **`src/generator/generator.ts`**: Core manifest pyramid generator.
- **`src/mcp/server.ts`**: Stdio MCP server.
- **Existing pitch decks, media, and licensing files** (`Agentic_Architect_Pitch_Deck.*`, `DemoVidForIQOO.mp4`, `LICENSE`).

---

## 9. Next Steps (Awaiting User Approval)

The audit confirms:
1. The networking between the user's phone and laptop is fully functional.
2. The root cause of `AutonomousAgentPipeline.kt` is the lack of a Project Manager and Agent Scaffolder in `scripts/laptop_ai_server.py`.
3. The planned architecture satisfies all requirements of Phase 1 through Phase 15.

**Antigravity is currently STOPPED per instructions.**  
Awaiting your approval of this audit and architecture plan before proceeding to Step 4 (Project Manager implementation).
