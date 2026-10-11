# PHASE 2C-1 AUDIT REPORT — Repository Workspace & Safe AI Editing Engine

**Audit Date:** October 9, 2026  
**Audited Subsystems:**
- `scripts/github_service.py`
- `scripts/laptop_ai_server.py`
- `scripts/project_manager.py`
- `scripts/agent_orchestrator.py`
- `scripts/preview_server.py`
- Android GitHub Remote Models & Repository Selection (`GitHubModels.kt`, `LaptopAiRepository.kt`)
- Existing Test Suites & Git Configuration

---

## 1. Current State Assessment

### 1.1 GitHub Service Layer (`scripts/github_service.py`)
- **Implemented:**
  - Token discovery hierarchy: environment variable `GITHUB_TOKEN` -> `.env` file -> `.github_token` file.
  - RFC 8628 OAuth Device Authorization Flow (`initiate_device_flow`, `poll_device_flow`).
  - Strict credential scrubbing (`scrub_sensitive_text`) removing tokens across all exceptions and representations.
  - Strong identifier validation (`REPO_IDENTIFIER_PATTERN = re.compile(r"^[a-zA-Z0-9_\-\.]+$")`) preventing URL injection and path manipulation.
  - Endpoints for status, repository listing, and single repository metadata retrieval.
- **Gaps for Phase 2C:**
  - No cloning or local Git repository workspace handling.
  - Needs secure cloning without embedding tokens in `.git/config` remote URLs on disk.

### 1.2 Project Manager (`scripts/project_manager.py`)
- **Implemented:**
  - Thread-safe registry stored in `projects/projects.json`.
  - Path traversal defense: `get_safe_workspace_path` ensuring workspace paths strictly reside under `projects/`.
  - Name sanitization with `sanitize_project_id`.
- **Gaps for Phase 2C:**
  - Only handled locally scaffolded projects created from voice prompts (Phase 1).
  - Needs structured management for cloned GitHub repositories under `projects/workspaces/`.
  - Needs tracking of clone URLs, owner, repo name, default branch, baseline commit hash, and current commit.

### 1.3 Agent Orchestrator (`scripts/agent_orchestrator.py`)
- **Implemented:**
  - Scaffolding of standard HTML/CSS/JS starter projects via offline templates with optional Ollama enrichment.
- **Gaps for Phase 2C:**
  - Unsuited for editing existing repositories.
  - As mandated by Phase 2C requirements, deterministic templates must **not** be used as a substitute for real AI editing.
  - Requires a dedicated service: `scripts/repository_agent.py` capable of:
    - Inspecting existing repository files and directory structure.
    - Applying contextual multi-file edits and creating new files based on instructions.
    - Returning clear, non-silent failure errors if the AI provider (Ollama / LLM) is unavailable, while keeping the repository workspace intact.

### 1.4 Preview Server Manager (`scripts/preview_server.py`)
- **Implemented:**
  - Non-blocking threaded HTTP server bound to `0.0.0.0` with dynamic port allocation (`8081`+) and private LAN IP detection.
  - Serves static assets with CORS and development no-cache headers.
- **Gaps for Phase 2C:**
  - Needs logic to inspect cloned repositories and determine if they contain supported static web content (e.g., `index.html` at root or standard static web layouts).
  - Must decline and explain unsupported frameworks (Node backend, Java, Python, Android) without attempting arbitrary execution or shell commands.

### 1.5 Android Data & Selection (`GitHubModels.kt`, `LaptopAiRepository.kt`)
- **Implemented:**
  - Persistent `SelectedRepository` model storing: `owner`, `name`, `fullName`, `htmlUrl`, `defaultBranch`, `isPrivate`.
  - Selection preserved across screens and restarts via Android `SharedPreferences`.
  - Complete isolation from GitHub credentials.

---

## 2. Intended Design for Phase 2C-1

### 2.1 Workspace Isolation Architecture
- Repository workspaces will reside in `projects/workspaces/<owner>--<repo>/` (under project root).
- Path traversal validation ensures no path can escape `projects/workspaces/`.
- Workspace metadata tracked in `projects/workspaces/workspaces.json`:
  - `workspace_id`: `<owner>--<repo>`
  - `owner`: validated GitHub owner
  - `repo`: validated GitHub repository name
  - `clone_url`: sanitized `https://github.com/<owner>/<repo>.git`
  - `default_branch`: branch name from GitHub metadata
  - `workspace_path`: absolute path on local disk
  - `baseline_commit`: initial git commit hash when workspace was initialized/opened
  - `current_branch`: active git branch
  - `status`: `ready`, `editing`, `error`
  - `created_at`, `updated_at`

### 2.2 Secure Git Clone Mechanism
- Use `git clone` via Python `subprocess.run` with strict argument lists (never `shell=True`).
- **Zero Token in Config:**
  - Remote origin URL stored in `.git/config` is **always** the clean public URL: `https://github.com/<owner>/<repo>.git`.
  - When authenticated, clone requests pass authorization via dynamic git config header:
    `-c http.extraHeader="AUTHORIZATION: bearer <token>"`
    This guarantees that the token is used during the wire transfer but **never stored in the repo config or on disk**.
  - All subprocess output is sanitized through `scrub_sensitive_text` before logging or error raising.
- **Workspace Reuse:**
  - If `<owner>--<repo>` already exists on disk and is a valid git repository, reuse it instead of recloning.
  - Before any AI editing session, record the baseline commit (`git rev-parse HEAD`).
  - Preserve any uncommitted user changes.

### 2.3 Dedicated Repository Agent (`scripts/repository_agent.py`)
- **Inspection Stage:**
  - Scans workspace file tree (excluding `.git`, `node_modules`, `dist`, binaries, large files).
  - Identifies existing code conventions and relevant files.
- **AI Modification Stage:**
  - Connects to Ollama (`http://localhost:11434` or configured LLM).
  - If Ollama is offline or fails, returns an explicit error: `"AI provider is unavailable. Local workspace preserved without modifications."`
  - Never uses canned fallback templates to pretend edits were made.
  - Prompts model with repository context and instruction, parsing file modifications in structured multi-file format.
- **Validation & Application:**
  - Applies edits strictly within the workspace directory.
  - Verifies that file modifications actually changed code; never silently reports success if no change was made.
  - Tracks files created, files modified, and files deleted.

### 2.4 Git Change Tracking & Diff Engine
- After editing, executes:
  - `git status --porcelain` to identify file states (Added, Modified, Deleted).
  - `git diff <baseline_commit>` to generate a unified, reviewable diff.
- Never executes:
  - `git commit`
  - `git push`
  - `git reset --hard`
  - `git clean -fd`
  - `git merge` or pull requests.
- The user retains 100% control over all GitHub writes.

### 2.5 Preview Support & Static Detection
- Inspects workspace for `index.html` or static site directory.
- If present, launches dynamic LAN preview via `PreviewServerManager`.
- If absent, returns: `"Static web preview not supported for this repository structure. No arbitrary build commands executed."`

---

## 3. Security Risks and Mitigations

| Risk | Mitigation |
|---|---|
| **Token leak in `.git/config` or remote URL** | Use `-c http.extraHeader="AUTHORIZATION: bearer <token>"` during clone/fetch. Remote URL remains `https://github.com/owner/repo.git`. |
| **Path traversal in repo name / files** | Strict regex validation (`^[a-zA-Z0-9_\-\.]+$`) and containment assertions (`abspath` starts with `projects/workspaces/`). |
| **Arbitrary command execution** | Zero shell command execution from AI responses. Use Python file writing and strict argument-vector subprocesses only. |
| **Data loss of user uncommitted changes** | Never run `git reset` or `git clean`. Record baseline and preserve workspace files. |
| **WAN exposure of repository files** | Enforce existing RFC 1918 private subnet checks on all API endpoints. |
| **Silent fake editing** | Require actual file changes and real AI provider responses; fail fast with clear errors when AI is offline. |

---

## 4. Files to be Created or Modified

1. **New:** [scripts/repository_agent.py](file:///c:/Users/SafetyProtocol/Desktop/IQOO/scripts/repository_agent.py) — Core workspace isolation, git operations, AI file editing, diff engine, and static preview detection.
2. **Modified:** [scripts/laptop_ai_server.py](file:///c:/Users/SafetyProtocol/Desktop/IQOO/scripts/laptop_ai_server.py) — Add API endpoints:
   - `POST /repo/workspace/init`
   - `GET /repo/workspace/{workspace_id}/status`
   - `POST /repo/workspace/{workspace_id}/instruct`
   - `GET /repo/workspace/{workspace_id}/diff`
   - `POST /repo/workspace/{workspace_id}/preview`
   - `DELETE /repo/workspace/{workspace_id}/preview`
3. **New:** [tests/test_repository_agent.py](file:///c:/Users/SafetyProtocol/Desktop/IQOO/tests/test_repository_agent.py) — Comprehensive mocked test suite testing all 15 scenarios specified in Phase 2C-1 instructions.
