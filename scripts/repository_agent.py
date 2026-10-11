"""
Repository Agent & Workspace Isolation Engine for Idea Beacon / Agentic Architect
Handles safe cloning, workspace isolation, baseline Git change tracking,
AI multi-file editing, reviewable diff calculation, and static preview detection.

Safety Guarantees:
- Zero automatic commits, pushes, merges, or pull requests.
- Zero credential exposure: tokens never written to .git/config remote URLs or logs.
- Strict path traversal defense: all file operations sandboxed to projects/workspaces/.
- Zero arbitrary command execution: AI cannot execute shell scripts or commands.
- Offline failure honesty: never uses fake templates if AI provider is offline.
"""

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
import json
import logging
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import time
from typing import Any, Dict, List, Optional, Set, Tuple, Union
import difflib
import uuid

import httpx

def safe_rmtree(target_path: Union[str, os.PathLike]) -> bool:
    """
    Safely removes a directory tree on Windows and POSIX systems.
    Clears read-only attributes (e.g. Git pack files, index, objects) before deletion.
    """
    p = os.path.abspath(str(target_path))
    if not os.path.exists(p):
        return True

    def _remove_readonly(func, path, exc_info):
        try:
            os.chmod(path, stat.S_IWRITE)
            func(path)
        except Exception:
            pass

    try:
        shutil.rmtree(p, onexc=_remove_readonly)
    except TypeError:
        shutil.rmtree(p, onerror=_remove_readonly)
    except Exception:
        pass

    return not os.path.exists(p)

DEFAULT_OLLAMA_TIMEOUT_SECONDS = float(os.getenv("OLLAMA_TIMEOUT", "75.0"))

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
DEFAULT_WORKSPACES_DIR = os.path.join(REPO_ROOT, "projects", "workspaces")

# Add scripts dir to path if needed
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from github_service import (
    github_service,
    GitHubService,
    GitHubAuth,
    GitHubAuthError,
    GitHubNotFoundError,
    GitHubValidationError,
    GitHubServiceError,
    scrub_sensitive_text,
    REPO_IDENTIFIER_PATTERN
)
from preview_server import preview_manager, PreviewServerManager, get_detected_lan_ip

logger = logging.getLogger("repository_agent")
logger.setLevel(logging.INFO)

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "qwen2.5-coder:1.5b")

# Maximum size per file to include in AI context (60 KB)
MAX_FILE_SIZE_BYTES = 60 * 1024
# Maximum number of files to inspect for context
MAX_INSPECT_FILES = 80

# Ignored directories and binary extensions
IGNORED_DIRS = {
    ".git", "node_modules", "dist", "build", ".next", ".nuxt",
    ".cache", "__pycache__", ".venv", "venv", ".idea", ".gradle",
    ".pytest_cache", "target", "out", "bin", "obj"
}

IGNORED_EXTENSIONS = {
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg", ".webp",
    ".mp4", ".mp3", ".wav", ".pdf", ".zip", ".tar", ".gz",
    ".exe", ".dll", ".so", ".dylib", ".bin", ".jar", ".war",
    ".class", ".pyc", ".lock"
}


# ==============================================================================
# EXCEPTIONS
# ==============================================================================
class RepositoryAgentError(Exception):
    """Base error for repository agent operations."""
    def __init__(self, message: str, code: str = "REPOSITORY_AGENT_ERROR", details: Optional[Dict[str, Any]] = None):
        safe_msg = scrub_sensitive_text(message)
        super().__init__(safe_msg)
        self.message = safe_msg
        self.code = code
        self.error_code = code
        self.details = details or {}


class WorkspaceSecurityError(RepositoryAgentError):
    """Raised on path traversal, boundary escape attempts, or workspace preservation conflicts."""
    def __init__(self, message: str, code: str = "SECURITY_VIOLATION"):
        super().__init__(message, code=code)


class GitOperationError(RepositoryAgentError):
    """Raised when a Git command fails."""
    def __init__(self, message: str):
        super().__init__(message, code="GIT_OPERATION_FAILED")


class AIProviderUnavailableError(RepositoryAgentError):
    """Raised when the configured AI provider cannot be reached."""
    def __init__(self, message: str):
        super().__init__(message, code="AI_PROVIDER_UNAVAILABLE")


class AIEditingError(RepositoryAgentError):
    """Raised when AI fails to generate valid changes."""
    def __init__(self, message: str):
        super().__init__(message, code="AI_EDITING_FAILED")


class PublishError(RepositoryAgentError):
    """Raised when a publishing operation is blocked or fails safety criteria."""
    def __init__(self, message: str, code: str = "PUBLISH_ERROR", details: Optional[Dict[str, Any]] = None):
        super().__init__(message, code=code, details=details)


# ==============================================================================
# DATA MODELS
# ==============================================================================
@dataclass
class WorkspaceRecord:
    workspace_id: str
    owner: str
    repo: str
    clone_url: str
    default_branch: str
    workspace_path: str
    baseline_commit: str
    current_branch: str
    created_at: str
    updated_at: str
    status: str = "ready"  # "ready", "editing", "error"
    preview_port: Optional[int] = None
    preview_url: Optional[str] = None
    last_instruction: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "WorkspaceRecord":
        return cls(**data)


@dataclass
class PreExistingChanges:
    staged: List[str] = field(default_factory=list)
    unstaged: List[str] = field(default_factory=list)
    untracked: List[str] = field(default_factory=list)
    clean: bool = True

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class OperationChangeRecord:
    operation_id: str
    instruction: str
    timestamp: str
    files_created: List[str] = field(default_factory=list)
    files_modified: List[str] = field(default_factory=list)
    files_deleted: List[str] = field(default_factory=list)
    total_changes: int = 0
    diff: str = ""
    status: str = "completed"
    error: Optional[str] = None
    pre_existing_changes: Optional[PreExistingChanges] = None
    pre_edit_snapshots: Dict[str, str] = field(default_factory=dict)
    dirty_file_collision: bool = False
    colliding_files: List[str] = field(default_factory=list)
    publish_blocked: bool = False
    publish_block_reason: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        res = asdict(self)
        if self.pre_existing_changes:
            res["pre_existing_changes"] = self.pre_existing_changes.to_dict()
        return res


def is_binary_file(filepath: str) -> bool:
    """Checks whether a file contains null bytes indicating binary content."""
    try:
        with open(filepath, "rb") as f:
            chunk = f.read(1024)
            return b"\x00" in chunk
    except Exception:
        return False


def generate_file_diff(
    rel_path: str,
    pre_content: Optional[str],
    post_content: Optional[str],
    is_binary: bool = False
) -> str:
    """Generates a standard unified diff for a single file edit with binary safety."""
    if is_binary:
        return f"Binary files a/{rel_path} and b/{rel_path} differ\n"
    from_file = f"a/{rel_path}" if pre_content is not None else "/dev/null"
    to_file = f"b/{rel_path}" if post_content is not None else "/dev/null"
    from_lines = pre_content.splitlines(keepends=True) if pre_content is not None else []
    to_lines = post_content.splitlines(keepends=True) if post_content is not None else []

    diff_lines = list(difflib.unified_diff(
        from_lines,
        to_lines,
        fromfile=from_file,
        tofile=to_file
    ))
    if diff_lines:
        return "".join(diff_lines)
    return ""


@dataclass
class ChangeSummary:
    files_created: List[str] = field(default_factory=list)
    files_modified: List[str] = field(default_factory=list)
    files_deleted: List[str] = field(default_factory=list)
    total_changes: int = 0
    diff: str = ""
    baseline_commit: str = ""

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class InstructionExecutionResult:
    success: bool
    workspace_id: str
    stage: str
    message: str
    changes: ChangeSummary
    preview_url: Optional[str] = None
    error: Optional[str] = None
    operation_id: Optional[str] = None
    pre_existing_changes: Optional[PreExistingChanges] = None
    workspace_changes: Optional[ChangeSummary] = None
    dirty_file_collision: bool = False
    colliding_files: List[str] = field(default_factory=list)
    publish_blocked: bool = False
    publish_block_reason: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        res = {
            "success": self.success,
            "workspace_id": self.workspace_id,
            "stage": self.stage,
            "message": self.message,
            "changes": self.changes.to_dict(),
            "preview_url": self.preview_url,
            "error": self.error,
            "dirty_file_collision": self.dirty_file_collision,
            "colliding_files": self.colliding_files,
            "publish_blocked": self.publish_blocked,
            "publish_block_reason": self.publish_block_reason
        }
        if self.operation_id:
            res["operation_id"] = self.operation_id
        if self.pre_existing_changes:
            res["pre_existing_changes"] = self.pre_existing_changes.to_dict()
        if self.workspace_changes:
            res["workspace_changes"] = self.workspace_changes.to_dict()
        return res


@dataclass
class FileReviewItem:
    path: str
    action: str  # "created", "modified", "deleted"
    source: str  # "ai_operation", "user_pre_existing", "untracked"
    eligible_for_commit: bool
    ineligibility_reason: Optional[str] = None
    staged_in_index: bool = False
    has_collision: bool = False

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class PublishReviewSummary:
    workspace_id: str
    owner: str
    repo: str
    current_branch: str
    remote_name: str
    remote_url: str  # Credentials scrubbed!
    head_commit: str
    baseline_commit: str
    files: List[FileReviewItem]
    diff: str
    publish_blocked: bool
    publish_block_reason: Optional[str] = None
    operations_count: int = 0
    latest_instruction: Optional[str] = None

    @property
    def target_repo(self) -> str:
        return f"{self.owner}/{self.repo}"

    @property
    def total_changed_files(self) -> int:
        return len(self.files)

    @property
    def has_ai_changes(self) -> bool:
        return any(f.source.lower() == "ai_operation" for f in self.files)

    @property
    def has_user_changes(self) -> bool:
        return any(f.source.lower() in ("user_pre_existing", "untracked", "preexisting_user") for f in self.files)

    @property
    def has_preexisting_collisions(self) -> bool:
        return any(f.has_collision or f.source.lower() == "collision" for f in self.files)

    @property
    def block_reasons(self) -> List[str]:
        if not self.publish_block_reason:
            return []
        return [r.strip() for r in self.publish_block_reason.split(" | ")]

    @property
    def unpushed_commits(self) -> int:
        return 0

    def to_dict(self) -> Dict[str, Any]:
        res = asdict(self)
        res["files"] = [f.to_dict() for f in self.files]
        res["target_repo"] = self.target_repo
        res["total_changed_files"] = self.total_changed_files
        res["has_ai_changes"] = self.has_ai_changes
        res["has_user_changes"] = self.has_user_changes
        res["has_preexisting_collisions"] = self.has_preexisting_collisions
        res["block_reasons"] = self.block_reasons
        res["unpushed_commits"] = self.unpushed_commits
        return res


@dataclass
class CommitResult:
    success: bool
    workspace_id: str
    commit_hash: str
    commit_message: str
    committed_files: List[str]
    branch: str
    timestamp: str

    @property
    def files_committed(self) -> List[str]:
        return self.committed_files

    def to_dict(self) -> Dict[str, Any]:
        res = asdict(self)
        res["files_committed"] = self.committed_files
        return res


@dataclass
class PushResult:
    success: bool
    workspace_id: str
    pushed_commit: str
    branch: str
    remote: str
    remote_url: str  # Credentials scrubbed!
    timestamp: str
    diverged: bool = False
    commits_ahead: int = 0
    commits_behind: int = 0
    message: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


# ==============================================================================
# WORKSPACE MANAGER
# ==============================================================================
class RepositoryWorkspaceManager:
    """
    Manages isolated repository workspaces under projects/workspaces/.
    Guarantees strict path sandboxing, Git credential safety, and workspace reuse.
    """
    def __init__(self, workspaces_dir: str = DEFAULT_WORKSPACES_DIR):
        self.workspaces_dir = os.path.abspath(workspaces_dir)
        self.registry_file = os.path.join(self.workspaces_dir, "workspaces.json")
        self._lock = threading.Lock()
        self._ensure_storage()

    def _ensure_storage(self):
        os.makedirs(self.workspaces_dir, exist_ok=True)
        if not os.path.exists(self.registry_file):
            self._write_registry({})

    def _read_registry(self) -> Dict[str, Dict[str, Any]]:
        if not os.path.exists(self.registry_file):
            return {}
        try:
            with open(self.registry_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
                if not content:
                    return {}
                return json.loads(content)
        except Exception as e:
            logger.warning(f"Failed to parse workspaces.json ({e}), resetting registry.")
            return {}

    def _write_registry(self, data: Dict[str, Dict[str, Any]]):
        os.makedirs(self.workspaces_dir, exist_ok=True)
        encoded = json.dumps(data, indent=2, ensure_ascii=False).encode("utf-8")
        temp_fd, temp_path = tempfile.mkstemp(dir=self.workspaces_dir, prefix=".ws_", suffix=".tmp")
        try:
            with os.fdopen(temp_fd, "wb") as f:
                f.write(encoded)
                f.flush()
                os.fsync(f.fileno())
            os.replace(temp_path, self.registry_file)
        except Exception:
            if os.path.exists(temp_path):
                try:
                    os.remove(temp_path)
                except OSError:
                    pass
            raise

    def get_workspace_id(self, owner: str, repo: str) -> str:
        """Constructs standardized workspace slug: owner--repo."""
        github_service.validate_identifier(owner, "owner")
        github_service.validate_identifier(repo, "repository")
        return f"{owner.lower()}--{repo.lower()}"

    def get_safe_workspace_path(self, workspace_id: str) -> str:
        """
        Validates workspace_id against directory traversal attacks.
        Returns the absolute path inside self.workspaces_dir.
        """
        if not workspace_id or ".." in workspace_id or "/" in workspace_id or "\\" in workspace_id:
            raise WorkspaceSecurityError(f"Security violation: path traversal detected for workspace '{workspace_id}'")

        # Must conform to safe slug pattern
        if not re.match(r"^[a-zA-Z0-9_\-\.]+$", workspace_id):
            raise WorkspaceSecurityError(f"Security violation: invalid characters in workspace ID '{workspace_id}'")

        candidate = os.path.abspath(os.path.join(self.workspaces_dir, workspace_id))
        if not (candidate.startswith(self.workspaces_dir + os.sep) or candidate == self.workspaces_dir):
            raise WorkspaceSecurityError(f"Security violation: boundary escape for workspace '{workspace_id}'")
        return candidate

    def get_workspace(self, workspace_id: str) -> Optional[WorkspaceRecord]:
        with self._lock:
            reg = self._read_registry()
            data = reg.get(workspace_id)
            return WorkspaceRecord.from_dict(data) if data else None

    def list_workspaces(self) -> List[WorkspaceRecord]:
        with self._lock:
            reg = self._read_registry()
            records = [WorkspaceRecord.from_dict(d) for d in reg.values()]
            records.sort(key=lambda r: r.updated_at, reverse=True)
            return records

    def update_workspace(self, workspace_id: str, **kwargs) -> Optional[WorkspaceRecord]:
        with self._lock:
            reg = self._read_registry()
            if workspace_id not in reg:
                return None
            data = reg[workspace_id]
            for k, v in kwargs.items():
                if hasattr(WorkspaceRecord, k) and k != "workspace_id":
                    data[k] = v
            data["updated_at"] = datetime.now(timezone.utc).isoformat()
            reg[workspace_id] = data
            self._write_registry(reg)
            return WorkspaceRecord.from_dict(data)

    def run_git_command(
        self,
        args: List[str],
        cwd: str,
        auth_token: Optional[str] = None,
        timeout: float = 60.0,
        extra_env: Optional[Dict[str, str]] = None
    ) -> Tuple[int, str, str]:
        """
        Executes a Git command with credential safety and argument-vector isolation.
        Tokens are NEVER passed on the process command-line in plaintext, arguments, or remote URLs.
        Uses subprocess-only in-memory GIT_CONFIG_COUNT configuration for HTTP authorization.
        """
        cmd = ["git"] + args

        env = dict(os.environ)
        # Suppress interactive git prompts
        env["GIT_TERMINAL_PROMPT"] = "0"

        if auth_token:
            # Safer subprocess-only credential mechanism: pass header via GIT_CONFIG_COUNT env vars.
            # Token does NOT appear in process command line arguments (ps aux, tasklist /v, wmic).
            env["GIT_CONFIG_COUNT"] = "1"
            env["GIT_CONFIG_KEY_0"] = "http.extraHeader"
            env["GIT_CONFIG_VALUE_0"] = f"AUTHORIZATION: bearer {auth_token}"

        if extra_env:
            env.update(extra_env)

        try:
            res = subprocess.run(
                cmd,
                cwd=cwd,
                capture_output=True,
                text=True,
                timeout=timeout,
                env=env
            )
            stdout = scrub_sensitive_text(res.stdout)
            stderr = scrub_sensitive_text(res.stderr)
            return res.returncode, stdout, stderr
        except subprocess.TimeoutExpired:
            raise GitOperationError(f"Git command timed out after {timeout} seconds: {' '.join(args[:2])}")
        except Exception as e:
            raise GitOperationError(f"Failed to execute git command: {scrub_sensitive_text(str(e))}")

    async def initialize_workspace(
        self,
        owner: str,
        repo: str,
        default_branch: Optional[str] = None
    ) -> WorkspaceRecord:
        """
        Initializes an isolated local workspace for the selected GitHub repository.
        - Validates owner and repository identifiers.
        - If workspace already exists and is a valid git repository, reuses it safely.
        - Clones repository using clean remote URL (no credentials stored on disk).
        - Records baseline commit and active branch.
        """
        github_service.validate_identifier(owner, "owner")
        github_service.validate_identifier(repo, "repository")

        workspace_id = self.get_workspace_id(owner, repo)
        workspace_path = self.get_safe_workspace_path(workspace_id)
        clean_clone_url = f"https://github.com/{owner}/{repo}.git"

        # Check existing record
        existing = self.get_workspace(workspace_id)

        # Check if local directory exists
        if os.path.isdir(workspace_path):
            git_dir = os.path.join(workspace_path, ".git")
            if os.path.isdir(git_dir):
                # Check if Git metadata is intact
                rc_git, _, _ = self.run_git_command(["rev-parse", "--git-dir"], cwd=workspace_path)
                if rc_git == 0:
                    # REUSE existing workspace safely
                    logger.info(f"Reusing existing repository workspace: {workspace_path}")
                    rc, branch_out, _ = self.run_git_command(["rev-parse", "--abbrev-ref", "HEAD"], cwd=workspace_path)
                    current_branch = branch_out.strip() if rc == 0 else (default_branch or "main")

                    rc, commit_out, _ = self.run_git_command(["rev-parse", "HEAD"], cwd=workspace_path)
                    baseline_commit = commit_out.strip() if rc == 0 else ""

                    now_iso = datetime.now(timezone.utc).isoformat()
                    record = WorkspaceRecord(
                        workspace_id=workspace_id,
                        owner=owner,
                        repo=repo,
                        clone_url=clean_clone_url,
                        default_branch=default_branch or (existing.default_branch if existing else current_branch),
                        workspace_path=workspace_path,
                        baseline_commit=baseline_commit,
                        current_branch=current_branch,
                        created_at=existing.created_at if existing else now_iso,
                        updated_at=now_iso,
                        status="ready"
                    )
                    with self._lock:
                        reg = self._read_registry()
                        reg[workspace_id] = record.to_dict()
                        self._write_registry(reg)
                    return record
                else:
                    # Git metadata is damaged, but contents exist!
                    # PER REQUIREMENT: Never automatically delete an existing workspace. Preserve contents.
                    raise WorkspaceSecurityError(
                        f"Workspace '{workspace_id}' exists at {workspace_path} with damaged Git metadata. "
                        "Directory contents were preserved without deletion. Please repair or backup before recloning.",
                        code="WORKSPACE_CORRUPT_PRESERVED"
                    )
            else:
                # Directory exists but has no .git folder.
                try:
                    entries = [e for e in os.listdir(workspace_path) if e not in (".", "..")]
                except Exception:
                    entries = []
                if len(entries) > 0:
                    # Contains files! NEVER automatically delete existing workspace without approval.
                    raise WorkspaceSecurityError(
                        f"Workspace directory '{workspace_id}' already exists at {workspace_path} with {len(entries)} file(s) "
                        "but is missing Git metadata. Automatic deletion is disabled to prevent data loss. "
                        "Please preserve or move existing files before proceeding.",
                        code="WORKSPACE_EXISTS_NO_GIT"
                    )
                else:
                    # Empty directory (e.g. leftover empty folder) - safe to clean up before cloning
                    safe_rmtree(workspace_path)

        # Fresh clone required
        logger.info(f"Cloning {clean_clone_url} into {workspace_path}")
        os.makedirs(self.workspaces_dir, exist_ok=True)

        token = github_service.auth.get_token()

        # Execute git clone
        # Note: clean_clone_url is used so remote.origin.url in .git/config has ZERO tokens!
        clone_args = ["clone", clean_clone_url, workspace_path]
        rc, out, err = self.run_git_command(
            clone_args,
            cwd=self.workspaces_dir,
            auth_token=token,
            timeout=180.0
        )

        if rc != 0:
            # Clean up failed partial directory if created
            if os.path.exists(workspace_path) and workspace_path.startswith(self.workspaces_dir + os.sep):
                safe_rmtree(workspace_path)
            error_hint = err.strip() or out.strip() or "Clone failed"
            raise GitOperationError(f"Failed to clone repository '{owner}/{repo}': {error_hint}")

        # Determine baseline commit & branch
        rc, commit_out, _ = self.run_git_command(["rev-parse", "HEAD"], cwd=workspace_path)
        baseline_commit = commit_out.strip() if rc == 0 else ""

        rc, branch_out, _ = self.run_git_command(["rev-parse", "--abbrev-ref", "HEAD"], cwd=workspace_path)
        current_branch = branch_out.strip() if rc == 0 else (default_branch or "main")

        now_iso = datetime.now(timezone.utc).isoformat()
        record = WorkspaceRecord(
            workspace_id=workspace_id,
            owner=owner,
            repo=repo,
            clone_url=clean_clone_url,
            default_branch=default_branch or current_branch,
            workspace_path=workspace_path,
            baseline_commit=baseline_commit,
            current_branch=current_branch,
            created_at=now_iso,
            updated_at=now_iso,
            status="ready"
        )

        with self._lock:
            reg = self._read_registry()
            reg[workspace_id] = record.to_dict()
            self._write_registry(reg)

        return record


# Global workspace manager singleton
workspace_manager = RepositoryWorkspaceManager()


# ==============================================================================
# GIT CHANGE TRACKING ENGINE
# ==============================================================================
class GitChangeTracker:
    """
    Computes reviewable diffs and file status changes against the recorded baseline.
    Never performs commits, pushes, merges, or destructive resets.
    """
    def __init__(self, wm: RepositoryWorkspaceManager = workspace_manager):
        self.wm = wm

    def get_status(self, workspace_path: str) -> Dict[str, Any]:
        """Runs git status --porcelain to detect modified, added, and deleted files."""
        rc, out, err = self.wm.run_git_command(["status", "--porcelain"], cwd=workspace_path)
        if rc != 0:
            raise GitOperationError(f"Could not retrieve git status: {err}")

        created: List[str] = []
        modified: List[str] = []
        deleted: List[str] = []

        for line in out.splitlines():
            line = line.strip()
            if not line:
                continue
            status_code = line[:2]
            filename = line[2:].strip().strip('"')

            if "?" in status_code or "A" in status_code:
                created.append(filename)
            elif "D" in status_code:
                deleted.append(filename)
            elif "M" in status_code or "R" in status_code:
                modified.append(filename)

        return {
            "created": sorted(created),
            "modified": sorted(modified),
            "deleted": sorted(deleted),
            "clean": len(created) + len(modified) + len(deleted) == 0
        }

    def get_detailed_status(self, workspace_path: str) -> PreExistingChanges:
        """
        Runs git status --porcelain=v1 to detect staged, unstaged, and untracked files accurately.
        Distinguishes pre-existing user work from new AI operations without altering working tree.
        """
        rc, out, err = self.wm.run_git_command(["status", "--porcelain=v1"], cwd=workspace_path)
        if rc != 0:
            raise GitOperationError(f"Could not retrieve git status: {err}")

        staged: List[str] = []
        unstaged: List[str] = []
        untracked: List[str] = []

        for line in out.splitlines():
            line = line.rstrip("\r\n")
            if not line.strip():
                continue
            if len(line) < 3:
                continue
            status_code = line[:2]
            filename = line[2:].strip().strip('"')
            if " -> " in filename:
                filename = filename.split(" -> ")[-1].strip('"')

            if status_code == "??":
                untracked.append(filename)
                continue

            index_status = status_code[0]
            worktree_status = status_code[1]

            if index_status in ("M", "A", "D", "R", "C"):
                staged.append(filename)
            if worktree_status in ("M", "D"):
                unstaged.append(filename)

        clean = (len(staged) == 0 and len(unstaged) == 0 and len(untracked) == 0)
        return PreExistingChanges(
            staged=sorted(list(set(staged))),
            unstaged=sorted(list(set(unstaged))),
            untracked=sorted(list(set(untracked))),
            clean=clean
        )

    def compute_diff(self, workspace_path: str, baseline_commit: Optional[str] = None) -> ChangeSummary:
        """
        Calculates diff against baseline_commit.
        Includes tracked changes and appends untracked files for comprehensive review.
        """
        status_info = self.get_status(workspace_path)
        created = status_info["created"]
        modified = status_info["modified"]
        deleted = status_info["deleted"]

        # Run git diff against baseline if provided, or against HEAD
        diff_target = baseline_commit if baseline_commit else "HEAD"
        rc, diff_out, _ = self.wm.run_git_command(["diff", diff_target], cwd=workspace_path)
        diff_text = diff_out if rc == 0 else ""

        # For untracked newly created files not in git diff, append pseudo-diff representation
        untracked_diffs = []
        for new_file in created:
            file_abs = os.path.join(workspace_path, new_file)
            if os.path.isfile(file_abs) and not diff_text.startswith(f"diff --git a/{new_file}"):
                try:
                    with open(file_abs, "r", encoding="utf-8", errors="replace") as f:
                        lines = f.readlines()
                    header = f"diff --git a/{new_file} b/{new_file}\nnew file mode 100644\n--- /dev/null\n+++ b/{new_file}\n@@ -0,0 +1,{len(lines)} @@\n"
                    body = "".join(f"+{line}" for line in lines)
                    untracked_diffs.append(header + body)
                except Exception:
                    pass

        if untracked_diffs:
            if diff_text and not diff_text.endswith("\n"):
                diff_text += "\n"
            diff_text += "\n".join(untracked_diffs)

        total_changes = len(created) + len(modified) + len(deleted)
        return ChangeSummary(
            files_created=created,
            files_modified=modified,
            files_deleted=deleted,
            total_changes=total_changes,
            diff=diff_text,
            baseline_commit=baseline_commit or ""
        )


# Global change tracker singleton
git_change_tracker = GitChangeTracker()


# ==============================================================================
# AI REPOSITORY EDITING ENGINE
# ==============================================================================
class RepositoryAgent:
    """
    Inspects repository workspaces, understands existing architecture and conventions,
    and applies AI-generated multi-file changes without arbitrary shell execution.
    """
    def __init__(
        self,
        wm: RepositoryWorkspaceManager = workspace_manager,
        tracker: GitChangeTracker = git_change_tracker,
        ollama_url: str = OLLAMA_BASE_URL,
        model: str = DEFAULT_MODEL,
        timeout: float = DEFAULT_OLLAMA_TIMEOUT_SECONDS
    ):
        self.wm = wm
        self.tracker = tracker
        self.ollama_url = ollama_url
        self.model = model
        self.timeout = timeout
        self._journal_lock = threading.RLock()
        self._last_partial_applied = ([], [], [], "", {})

    def scan_repository_structure(self, workspace_path: str) -> Dict[str, Any]:
        """
        Scans workspace directory tree, ignoring VCS, dependencies, and binary assets.
        Returns tree summary and map of inspected text files.
        """
        file_tree: List[str] = []
        file_samples: Dict[str, str] = {}

        for root, dirs, files in os.walk(workspace_path):
            dirs[:] = [d for d in dirs if d not in IGNORED_DIRS and not d.startswith(".")]

            rel_root = os.path.relpath(root, workspace_path)
            for f in sorted(files):
                if f.startswith(".") or any(f.endswith(ext) for ext in IGNORED_EXTENSIONS):
                    continue

                rel_path = f if rel_root == "." else os.path.join(rel_root, f).replace("\\", "/")
                file_tree.append(rel_path)

                if len(file_samples) < MAX_INSPECT_FILES:
                    abs_path = os.path.join(root, f)
                    try:
                        sz = os.path.getsize(abs_path)
                        if sz <= MAX_FILE_SIZE_BYTES:
                            with open(abs_path, "r", encoding="utf-8", errors="replace") as fh:
                                file_samples[rel_path] = fh.read()
                    except Exception:
                        pass

        return {
            "tree": file_tree,
            "samples": file_samples
        }

    async def verify_ai_provider(self) -> bool:
        """Checks if local Ollama provider is available."""
        try:
            async with httpx.AsyncClient(timeout=2.5) as client:
                res = await client.get(f"{self.ollama_url}/api/tags")
                return res.status_code == 200
        except Exception:
            return False

    def _build_ai_prompt(
        self,
        instruction: str,
        file_tree: List[str],
        file_samples: Dict[str, str]
    ) -> str:
        """Constructs high-fidelity prompt providing repository context and formatting rules."""
        # Select most relevant sample files (up to 10 files)
        sample_blocks = []
        for path, content in list(file_samples.items())[:10]:
            snippet = content[:2500] if len(content) > 2500 else content
            sample_blocks.append(f"--- File: {path} ---\n{snippet}")

        files_context = "\n\n".join(sample_blocks)
        tree_listing = "\n".join(file_tree[:50])

        return f"""You are an expert AI software engineer modifying an existing GitHub repository.

Repository File Tree:
{tree_listing}

Context of Existing Codebase:
{files_context}

User Instruction:
"{instruction}"

CRITICAL INSTRUCTIONS:
1. Understand the conventions, language, and styling of the existing files.
2. Produce only required, safe edits. You may create new files, modify existing files, or delete files.
3. NEVER replace unrelated code or truncate existing logic.
4. Output your response STRICTLY as a single valid JSON object with NO surrounding markdown backticks or commentary outside JSON.
Format:
{{
  "thought": "Brief explanation of architectural changes",
  "edits": [
    {{
      "path": "relative/path/to/file.ext",
      "action": "create" | "modify" | "delete",
      "content": "Full complete content of the file"
    }}
  ]
}}
"""

    def _parse_ai_edits(self, raw_response: str) -> List[Dict[str, Any]]:
        """Parses and validates JSON response from AI."""
        text = raw_response.strip()
        # Strip markdown code blocks if present
        if text.startswith("```json"):
            text = text[7:]
        elif text.startswith("```"):
            text = text[3:]
        if text.endswith("```"):
            text = text[:-3]
        text = text.strip()

        # Extract first JSON object if surrounded by preamble
        json_match = re.search(r"\{[\s\S]*\}", text)
        if json_match:
            text = json_match.group(0)

        data = json.loads(text)
        edits = data.get("edits")
        if not edits or not isinstance(edits, list):
            raise AIEditingError("AI response did not contain an 'edits' list.")
        return edits

    def apply_edits(
        self,
        workspace_path: str,
        edits: List[Dict[str, Any]]
    ) -> Tuple[List[str], List[str], List[str], str, Dict[str, str]]:
        """
        Applies validated edits safely inside workspace.
        Enforces path containment and records modified, created, and deleted files.
        Generates an isolated unified diff strictly comparing pre-edit vs post-edit state for each touched file.
        Captures pre-edit file snapshots to ensure zero user edits are lost.
        """
        applied_created: List[str] = []
        applied_modified: List[str] = []
        applied_deleted: List[str] = []
        diff_hunks: List[str] = []
        pre_edit_snapshots: Dict[str, str] = {}

        self._last_partial_applied = (applied_created, applied_modified, applied_deleted, "", pre_edit_snapshots)

        for edit in edits:
            rel_path = edit.get("path", "").strip().replace("\\", "/")
            action = edit.get("action", "modify").lower()
            content = edit.get("content", "")

            if not rel_path or ".." in rel_path or rel_path.startswith("/"):
                raise WorkspaceSecurityError(f"Security violation: unsafe target path in AI edit '{rel_path}'")

            target_abs = os.path.abspath(os.path.join(workspace_path, rel_path))
            if not target_abs.startswith(workspace_path + os.sep):
                raise WorkspaceSecurityError(f"Security violation: path escape in AI edit '{rel_path}'")

            pre_content: Optional[str] = None
            file_is_bin = False
            if os.path.isfile(target_abs):
                file_is_bin = is_binary_file(target_abs)
                try:
                    if not file_is_bin:
                        with open(target_abs, "r", encoding="utf-8", errors="replace") as f:
                            pre_content = f.read()
                    else:
                        pre_content = "[BINARY_DATA]"
                except Exception:
                    pass

            if pre_content is not None:
                pre_edit_snapshots[rel_path] = pre_content

            if action == "delete":
                if os.path.isfile(target_abs):
                    os.remove(target_abs)
                    applied_deleted.append(rel_path)
                    hunk = generate_file_diff(rel_path, pre_content, None, is_binary=file_is_bin)
                    if hunk:
                        diff_hunks.append(hunk)
            elif action in ("create", "modify"):
                file_existed = os.path.exists(target_abs)
                parent_dir = os.path.dirname(target_abs)
                os.makedirs(parent_dir, exist_ok=True)

                # Check if content actually changes
                if file_existed and pre_content == content:
                    # No effective change
                    continue

                with open(target_abs, "w", encoding="utf-8") as f:
                    f.write(content)

                if file_existed:
                    applied_modified.append(rel_path)
                else:
                    applied_created.append(rel_path)

                hunk = generate_file_diff(rel_path, pre_content, content, is_binary=file_is_bin)
                if hunk:
                    diff_hunks.append(hunk)

            self._last_partial_applied = (
                list(applied_created),
                list(applied_modified),
                list(applied_deleted),
                "\n".join(diff_hunks),
                dict(pre_edit_snapshots)
            )

        operation_diff = "\n".join(diff_hunks)
        return applied_created, applied_modified, applied_deleted, operation_diff, pre_edit_snapshots

    def get_journal_path(self, workspace_id: str) -> str:
        """Returns validated storage location for this workspace's operation journal outside the repo working tree."""
        if not workspace_id or ".." in workspace_id or "/" in workspace_id or "\\" in workspace_id:
            raise WorkspaceSecurityError(f"Security violation: path traversal detected for journal '{workspace_id}'")
        if not re.match(r"^[a-zA-Z0-9_\-\.]+$", workspace_id):
            raise WorkspaceSecurityError(f"Security violation: invalid characters in journal ID '{workspace_id}'")

        candidate = os.path.abspath(os.path.join(self.wm.workspaces_dir, f".{workspace_id}_journal.json"))
        expected_dir = os.path.abspath(self.wm.workspaces_dir)
        if not (candidate.startswith(expected_dir + os.sep) or candidate == expected_dir):
            raise WorkspaceSecurityError(f"Security violation: journal escape for workspace '{workspace_id}'")
        return candidate

    def get_operation_journal(self, workspace_id: str) -> List[OperationChangeRecord]:
        """Retrieves history of AI editing operations executed on this workspace."""
        jpath = self.get_journal_path(workspace_id)
        with self._journal_lock:
            if not os.path.isfile(jpath):
                return []
            try:
                with open(jpath, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                    if not content:
                        return []
                    data = json.loads(content)
                    records = []
                    for item in data:
                        pre_data = item.pop("pre_existing_changes", None)
                        pre_obj = PreExistingChanges(**pre_data) if pre_data else None
                        records.append(OperationChangeRecord(pre_existing_changes=pre_obj, **item))
                    return records
            except Exception as e:
                logger.warning(f"Could not read operation journal for {workspace_id} ({e}); preserving corrupted copy.")
                try:
                    corrupt_path = f"{jpath}.corrupt_{int(time.time())}"
                    shutil.copy2(jpath, corrupt_path)
                except Exception:
                    pass
                return []

    def append_operation_journal(self, workspace_id: str, record: OperationChangeRecord):
        """Appends an isolated operation change record to the workspace journal using atomic writes."""
        jpath = self.get_journal_path(workspace_id)
        with self._journal_lock:
            journal = self.get_operation_journal(workspace_id)
            journal.append(record)
            os.makedirs(self.wm.workspaces_dir, exist_ok=True)
            clean_records = []
            for r in journal:
                d = r.to_dict()
                for k, v in d.items():
                    if isinstance(v, str):
                        d[k] = scrub_sensitive_text(v)
                    elif isinstance(v, dict):
                        d[k] = {ik: scrub_sensitive_text(iv) if isinstance(iv, str) else iv for ik, iv in v.items()}
                clean_records.append(d)
            encoded = json.dumps(clean_records, indent=2, ensure_ascii=False).encode("utf-8")
            temp_fd, temp_path = tempfile.mkstemp(dir=self.wm.workspaces_dir, prefix=".jtmp_", suffix=".tmp")
            try:
                with os.fdopen(temp_fd, "wb") as f:
                    f.write(encoded)
                    f.flush()
                    os.fsync(f.fileno())
                os.replace(temp_path, jpath)
            except Exception as e:
                if os.path.exists(temp_path):
                    try:
                        os.remove(temp_path)
                    except OSError:
                        pass
                logger.warning(f"Could not atomically write operation journal: {e}")

    async def execute_instruction(
        self,
        workspace_id: str,
        instruction: str
    ) -> InstructionExecutionResult:
        """
        Main execution flow:
        1. Validate workspace, record baseline, and snapshot pre-existing working tree state.
        2. Verify AI provider availability (fails fast with meaningful error if offline).
        3. Scan repository structure.
        4. Query AI model for contextual edits.
        5. Apply edits within workspace boundaries, isolating operation changes.
        6. Compute isolated per-operation diff and cumulative workspace diff.
        7. If editing fails halfway, preserve partial changes and report them.
        """
        record = self.wm.get_workspace(workspace_id)
        if not record:
            raise RepositoryAgentError(f"Workspace '{workspace_id}' not found.", code="WORKSPACE_NOT_FOUND")

        workspace_path = record.workspace_path
        if not os.path.isdir(workspace_path):
            raise RepositoryAgentError(f"Workspace directory missing: {workspace_path}", code="WORKSPACE_DIR_MISSING")

        # 1. Record baseline commit and snapshot pre-existing working-tree state before editing session
        rc, commit_out, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=workspace_path)
        baseline_commit = commit_out.strip() if rc == 0 else record.baseline_commit
        pre_existing = self.tracker.get_detailed_status(workspace_path)

        # Update workspace status
        self.wm.update_workspace(workspace_id, status="editing", last_instruction=instruction)

        # 2. Check AI provider availability (NEVER pretend offline templates are general-purpose AI editing)
        ai_available = await self.verify_ai_provider()
        if not ai_available:
            self.wm.update_workspace(workspace_id, status="ready")
            raise AIProviderUnavailableError(
                f"AI provider is unavailable (Ollama is offline at {self.ollama_url}). "
                "Local workspace was preserved without modifications. Please ensure Ollama is running."
            )

        # 3. Scan workspace structure
        scan_data = self.scan_repository_structure(workspace_path)
        prompt = self._build_ai_prompt(instruction, scan_data["tree"], scan_data["samples"])

        # 4. Query Ollama
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                res = await client.post(
                    f"{self.ollama_url}/api/generate",
                    json={
                        "model": self.model,
                        "prompt": prompt,
                        "stream": False,
                        "format": "json",
                        "options": {"temperature": 0.2, "num_predict": 4096}
                    }
                )
                if res.status_code != 200:
                    raise AIProviderUnavailableError(f"Ollama returned HTTP {res.status_code}: {res.text[:200]}")
                raw_ai_text = res.json().get("response", "{}")
        except httpx.TimeoutException:
            self.wm.update_workspace(workspace_id, status="ready")
            raise AIEditingError("AI request timed out during code generation. Local workspace preserved.")
        except Exception as e:
            self.wm.update_workspace(workspace_id, status="ready")
            raise AIProviderUnavailableError(f"Error communicating with AI provider: {str(e)}")

        # 5. Parse edits
        try:
            edits = self._parse_ai_edits(raw_ai_text)
        except Exception as e:
            self.wm.update_workspace(workspace_id, status="ready")
            raise AIEditingError(f"Failed to parse AI modifications: {str(e)}")

        # 6. Apply edits safely
        partial_error: Optional[str] = None
        applied_created: List[str] = []
        applied_modified: List[str] = []
        applied_deleted: List[str] = []
        operation_diff: str = ""
        pre_edit_snapshots: Dict[str, str] = {}

        try:
            applied_created, applied_modified, applied_deleted, operation_diff, pre_edit_snapshots = self.apply_edits(workspace_path, edits)
        except Exception as e:
            # Partial failure occurred: capture what changed so far, do NOT discard
            partial_error = f"Partial modification failure: {str(e)}"
            logger.error(partial_error)
            applied_created, applied_modified, applied_deleted, operation_diff, pre_edit_snapshots = getattr(
                self, "_last_partial_applied", ([], [], [], "", {})
            )

        # 7. Check for collisions with pre-existing uncommitted user changes
        touched_files = set(applied_created + applied_modified + applied_deleted)
        dirty_files = set(pre_existing.staged + pre_existing.unstaged + pre_existing.untracked)
        colliding = sorted(list(touched_files.intersection(dirty_files)))

        if colliding:
            has_dirty_collision = True
            publish_blocked = True
            publish_block_reason = (
                f"Pre-existing uncommitted user changes collided in file(s): {colliding}. "
                "Exact Git attribution cannot be guaranteed for isolated publishing without "
                "either committing or discarding existing user work. Publishing is blocked."
            )
        else:
            has_dirty_collision = False
            publish_blocked = False
            publish_block_reason = None

        # 8. Construct attributable operation change summary
        op_total = len(applied_created) + len(applied_modified) + len(applied_deleted)
        operation_changes = ChangeSummary(
            files_created=applied_created,
            files_modified=applied_modified,
            files_deleted=applied_deleted,
            total_changes=op_total,
            diff=operation_diff,
            baseline_commit=baseline_commit
        )

        # Compute cumulative workspace diff against baseline commit (for reference)
        workspace_changes = self.tracker.compute_diff(workspace_path, baseline_commit=baseline_commit)

        # Ensure we never silently report success if no change was made
        if op_total == 0 and not partial_error:
            self.wm.update_workspace(workspace_id, status="ready")
            raise AIEditingError("AI did not produce any code modifications for the specified instruction.")

        # Record operation in isolated journal
        op_id = f"op_{int(time.time())}_{uuid.uuid4().hex[:6]}"
        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id=op_id,
            instruction=scrub_sensitive_text(instruction),
            timestamp=now_iso,
            files_created=applied_created,
            files_modified=applied_modified,
            files_deleted=applied_deleted,
            total_changes=op_total,
            diff=scrub_sensitive_text(operation_diff),
            status="completed" if not partial_error else "partial_error",
            error=scrub_sensitive_text(partial_error) if partial_error else None,
            pre_existing_changes=pre_existing,
            pre_edit_snapshots=pre_edit_snapshots,
            dirty_file_collision=has_dirty_collision,
            colliding_files=colliding,
            publish_blocked=publish_blocked,
            publish_block_reason=publish_block_reason
        )
        self.append_operation_journal(workspace_id, op_record)

        # Update workspace record status
        self.wm.update_workspace(workspace_id, status="ready")

        success = (partial_error is None) and (op_total > 0)
        message = (
            f"Successfully applied changes across {op_total} file(s)."
            if success else
            f"Partial edits applied: {partial_error}"
        )

        return InstructionExecutionResult(
            success=success,
            workspace_id=workspace_id,
            stage="COMPLETED" if success else "PARTIAL_ERROR",
            message=message,
            changes=operation_changes,
            workspace_changes=workspace_changes,
            operation_id=op_id,
            pre_existing_changes=pre_existing,
            preview_url=record.preview_url,
            error=partial_error,
            dirty_file_collision=has_dirty_collision,
            colliding_files=colliding,
            publish_blocked=publish_blocked,
            publish_block_reason=publish_block_reason
        )


# Global repository agent singleton
repository_agent = RepositoryAgent()


# ==============================================================================
# STATIC PREVIEW DETECTOR & LAUNCHER
# ==============================================================================
def detect_static_web_preview_root(workspace_path: str) -> Optional[str]:
    """
    Inspects workspace for static web signals (e.g. index.html).
    Returns the root directory to serve, or None if unsupported framework.
    """
    # 1. Direct index.html in root
    if os.path.isfile(os.path.join(workspace_path, "index.html")):
        return workspace_path

    # 2. Standard static subdirectories
    subdirs = ["public", "dist", "build", "www", "web", "html", "site"]
    for sub in subdirs:
        candidate = os.path.join(workspace_path, sub)
        if os.path.isdir(candidate) and os.path.isfile(os.path.join(candidate, "index.html")):
            return candidate

    return None


# ==============================================================================
# CONTROLLED GIT PUBLISHING ENGINE (PHASE 2D)
# ==============================================================================
class GitPublisher:
    """
    Controlled, review-first Git publishing engine.
    Guarantees:
    - Never uses broad 'git add .' or 'git add -A'; stages only exact approved files.
    - Revalidates working tree and index immediately before commit.
    - Fails closed if index has unrelated staged changes or colliding dirty files.
    - Never force-pushes, never automatically merges, never creates PRs automatically.
    - Fetches and checks remote divergence before pushing.
    - Serializes publishing operations per workspace to prevent race conditions.
    """
    def __init__(
        self,
        wm: RepositoryWorkspaceManager = workspace_manager,
        agent: RepositoryAgent = repository_agent,
        tracker: GitChangeTracker = git_change_tracker,
        auth: GitHubAuth = github_service.auth
    ):
        self.wm = wm
        self.agent = agent
        self.tracker = tracker
        self.auth = auth
        self._workspace_locks: Dict[str, threading.Lock] = {}
        self._meta_lock = threading.Lock()

    def _get_lock(self, workspace_id: str) -> threading.Lock:
        with self._meta_lock:
            if workspace_id not in self._workspace_locks:
                self._workspace_locks[workspace_id] = threading.Lock()
            return self._workspace_locks[workspace_id]

    def get_publish_review(self, workspace_id: str) -> PublishReviewSummary:
        """
        Inspects workspace and latest AI operations to build a review-first publishing summary.
        Identifies eligible files, pre-existing user changes, and dirty collisions.
        """
        record = self.wm.get_workspace(workspace_id)
        if not record:
            raise RepositoryAgentError(f"Workspace '{workspace_id}' not found.", code="WORKSPACE_NOT_FOUND")
        workspace_path = record.workspace_path

        # Current branch
        rc, branch_out, _ = self.wm.run_git_command(["rev-parse", "--abbrev-ref", "HEAD"], cwd=workspace_path)
        current_branch = branch_out.strip() if rc == 0 else record.current_branch

        # Current HEAD commit
        rc, head_out, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=workspace_path)
        head_commit = head_out.strip() if rc == 0 else record.baseline_commit

        # Remote info (credentials redacted)
        remote_name = "origin"
        rc, remote_out, _ = self.wm.run_git_command(["config", "--get", f"remote.{remote_name}.url"], cwd=workspace_path)
        raw_remote_url = remote_out.strip() if rc == 0 else record.clone_url
        clean_remote_url = scrub_sensitive_text(raw_remote_url)

        # Working tree status
        detailed_status = self.tracker.get_detailed_status(workspace_path)

        # Journal history
        journal = self.agent.get_operation_journal(workspace_id)
        latest_op = journal[-1] if journal else None

        file_items: List[FileReviewItem] = []
        seen_paths: Set[str] = set()

        publish_blocked = False
        block_reasons: List[str] = []

        if latest_op and latest_op.publish_blocked:
            publish_blocked = True
            if latest_op.publish_block_reason:
                block_reasons.append(latest_op.publish_block_reason)

        ai_created = set(latest_op.files_created if latest_op else [])
        ai_modified = set(latest_op.files_modified if latest_op else [])
        ai_deleted = set(latest_op.files_deleted if latest_op else [])
        ai_touched = ai_created.union(ai_modified).union(ai_deleted)
        colliding_set = set(latest_op.colliding_files if latest_op else [])

        # 1. AI-modified or created files
        for f in sorted(ai_touched):
            seen_paths.add(f)
            action = "created" if f in ai_created else ("deleted" if f in ai_deleted else "modified")
            has_coll = f in colliding_set
            staged = f in detailed_status.staged

            eligible = not has_coll
            inel_reason = None
            if has_coll:
                eligible = False
                inel_reason = "File has colliding uncommitted pre-existing user edits; attribution cannot be isolated."
                publish_blocked = True
                src = "collision"
                block_reasons.append(f"Collision detected on {f}: file has uncommitted pre-existing user edits.")
            else:
                src = "ai_operation"

            file_items.append(FileReviewItem(
                path=f,
                action=action,
                source=src,
                eligible_for_commit=eligible,
                ineligibility_reason=inel_reason,
                staged_in_index=staged,
                has_collision=has_coll
            ))

        # 2. Files changed in workspace but NOT by AI (pre-existing user edits)
        unrelated_staged = [f for f in detailed_status.staged if f not in seen_paths]
        unrelated_unstaged = [f for f in detailed_status.unstaged if f not in seen_paths]
        unrelated_untracked = [f for f in detailed_status.untracked if f not in seen_paths]

        for f in sorted(unrelated_staged):
            seen_paths.add(f)
            file_items.append(FileReviewItem(
                path=f,
                action="modified",
                source="user_pre_existing",
                eligible_for_commit=False,
                ineligibility_reason="Pre-existing staged change by user; cannot commit under AI operation.",
                staged_in_index=True,
                has_collision=False
            ))

        for f in sorted(unrelated_unstaged):
            seen_paths.add(f)
            file_items.append(FileReviewItem(
                path=f,
                action="modified",
                source="user_pre_existing",
                eligible_for_commit=False,
                ineligibility_reason="Pre-existing unstaged change by user; not part of AI operation.",
                staged_in_index=False,
                has_collision=False
            ))

        for f in sorted(unrelated_untracked):
            seen_paths.add(f)
            file_items.append(FileReviewItem(
                path=f,
                action="created",
                source="untracked",
                eligible_for_commit=False,
                ineligibility_reason="Untracked user file.",
                staged_in_index=False,
                has_collision=False
            ))

        # Diff calculation
        diff_text = latest_op.diff if latest_op and latest_op.diff else ""
        if not diff_text:
            ws_changes = self.tracker.compute_diff(workspace_path, baseline_commit=record.baseline_commit)
            diff_text = ws_changes.diff

        block_reason_str = " | ".join(block_reasons) if block_reasons else None

        return PublishReviewSummary(
            workspace_id=workspace_id,
            owner=record.owner,
            repo=record.repo,
            current_branch=current_branch,
            remote_name=remote_name,
            remote_url=clean_remote_url,
            head_commit=head_commit,
            baseline_commit=record.baseline_commit,
            files=file_items,
            diff=diff_text,
            publish_blocked=publish_blocked,
            publish_block_reason=block_reason_str,
            operations_count=len(journal),
            latest_instruction=latest_op.instruction if latest_op else None
        )

    def create_commit(
        self,
        workspace_id: str,
        commit_message: str,
        selected_files: List[str],
        author_name: Optional[str] = None,
        author_email: Optional[str] = None
    ) -> CommitResult:
        """
        Executes a controlled, explicit git commit.
        Stages strictly the approved files. Revalidates index and fails closed on collisions or unrelated staged files.
        """
        record = self.wm.get_workspace(workspace_id)
        if not record:
            raise RepositoryAgentError(f"Workspace '{workspace_id}' not found.", code="WORKSPACE_NOT_FOUND")
        workspace_path = record.workspace_path

        lock = self._get_lock(workspace_id)
        if not lock.acquire(blocking=False):
            raise PublishError("A publishing operation is already in progress for this workspace.", code="CONCURRENT_PUBLISH_OPERATION")

        try:
            # 1. Validation
            msg = commit_message.strip() if commit_message else ""
            if not msg:
                raise PublishError("Commit message cannot be empty.", code="INVALID_COMMIT_MESSAGE")

            if not selected_files:
                raise PublishError("At least one file must be selected for commit.", code="NO_FILES_SELECTED")

            clean_selected: List[str] = []
            for p in selected_files:
                p = p.strip().replace("\\", "/")
                if not p or ".." in p or p.startswith("/"):
                    raise WorkspaceSecurityError(f"Security violation: unsafe path in selected files '{p}'")
                target_abs = os.path.abspath(os.path.join(workspace_path, p))
                if not target_abs.startswith(workspace_path + os.sep):
                    raise WorkspaceSecurityError(f"Security violation: path escape in selected files '{p}'")
                clean_selected.append(p)

            # 2. Pre-commit state re-validation
            detailed_status = self.tracker.get_detailed_status(workspace_path)

            # Check if index has unrelated staged files
            unrelated_staged = [f for f in detailed_status.staged if f not in clean_selected]
            if unrelated_staged:
                raise PublishError(
                    f"Cannot commit: Unrelated pre-existing staged files detected in Git index: {unrelated_staged}. "
                    "To prevent accidental inclusion of user work, unstage these files before committing AI changes.",
                    code="UNRELATED_STAGED_CHANGES"
                )

            # Check for collision with dirty files
            journal = self.agent.get_operation_journal(workspace_id)
            latest_op = journal[-1] if journal else None
            if latest_op and latest_op.dirty_file_collision:
                colliding = [f for f in clean_selected if f in latest_op.colliding_files]
                if colliding:
                    raise PublishError(
                        f"Cannot commit: Selected file(s) {colliding} have colliding pre-existing uncommitted user edits. "
                        "Exact Git attribution cannot be guaranteed without either committing or discarding existing user work.",
                        code="DIRTY_FILE_COLLISION"
                    )

            # Check that selected files are eligible for publishing (reject pre-existing user edits)
            review = self.get_publish_review(workspace_id)
            eligible_paths = {f.path for f in review.files if f.eligible_for_commit}
            ineligible_selected = [f for f in clean_selected if f not in eligible_paths]
            if ineligible_selected:
                raise PublishError(
                    f"Cannot commit: Selected file(s) {ineligible_selected} are not eligible for publishing. "
                    "Only AI-attributed changes without pre-existing user collisions can be committed.",
                    code="INELIGIBLE_FILES_SELECTED"
                )

            # 3. Exact Staging (NEVER 'git add .' or 'git add -A')
            for rel_path in clean_selected:
                abs_path = os.path.join(workspace_path, rel_path)
                if os.path.exists(abs_path):
                    rc, out, err = self.wm.run_git_command(["add", "--", rel_path], cwd=workspace_path)
                    if rc != 0:
                        raise GitOperationError(f"Failed to stage file '{rel_path}': {err or out}")
                else:
                    # File was deleted
                    rc, out, err = self.wm.run_git_command(["rm", "--", rel_path], cwd=workspace_path)
                    if rc != 0:
                        rc2, out2, err2 = self.wm.run_git_command(["add", "-u", "--", rel_path], cwd=workspace_path)
                        if rc2 != 0:
                            raise GitOperationError(f"Failed to stage deletion for '{rel_path}': {err or err2 or out2}")

            # 4. Post-staging verification: staged files must match clean_selected
            post_status = self.tracker.get_detailed_status(workspace_path)
            if set(post_status.staged) != set(clean_selected):
                # Rollback staging without modifying working tree
                for p in clean_selected:
                    self.wm.run_git_command(["reset", "HEAD", "--", p], cwd=workspace_path)
                raise PublishError(
                    f"Staging verification failed: staged files {post_status.staged} do not match approved files {clean_selected}.",
                    code="STAGING_VERIFICATION_FAILED"
                )

            # 5. Commit Execution
            author = author_name or "Idea Beacon Agent"
            email = author_email or "agent@ideabeacon.local"
            commit_env = {
                "GIT_AUTHOR_NAME": author,
                "GIT_AUTHOR_EMAIL": email,
                "GIT_COMMITTER_NAME": author,
                "GIT_COMMITTER_EMAIL": email,
            }
            rc, commit_out, commit_err = self.wm.run_git_command(
                ["commit", "-m", msg],
                cwd=workspace_path,
                extra_env=commit_env
            )
            if rc != 0:
                raise GitOperationError(f"Git commit failed: {commit_err or commit_out}")

            # 6. Get new commit hash & branch
            rc, head_out, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=workspace_path)
            commit_hash = head_out.strip()
            rc, branch_out, _ = self.wm.run_git_command(["rev-parse", "--abbrev-ref", "HEAD"], cwd=workspace_path)
            current_branch = branch_out.strip()

            now_iso = datetime.now(timezone.utc).isoformat()
            self.wm.update_workspace(
                workspace_id,
                baseline_commit=commit_hash,
                current_branch=current_branch
            )

            return CommitResult(
                success=True,
                workspace_id=workspace_id,
                commit_hash=commit_hash,
                commit_message=msg,
                committed_files=clean_selected,
                branch=current_branch,
                timestamp=now_iso
            )
        finally:
            lock.release()

    def push_to_remote(
        self,
        workspace_id: str,
        remote_name: str = "origin",
        branch: Optional[str] = None,
        confirmed: bool = False
    ) -> PushResult:
        """
        Executes a controlled, verified git push.
        Requires explicit confirmation. Checks remote divergence and never force-pushes.
        """
        if not confirmed:
            raise PublishError(
                "Push operation requires explicit confirmation. Please confirm destination branch and remote.",
                code="PUSH_CONFIRMATION_REQUIRED"
            )

        record = self.wm.get_workspace(workspace_id)
        if not record:
            raise RepositoryAgentError(f"Workspace '{workspace_id}' not found.", code="WORKSPACE_NOT_FOUND")
        workspace_path = record.workspace_path

        lock = self._get_lock(workspace_id)
        if not lock.acquire(blocking=False):
            raise PublishError("A publishing operation is already in progress for this workspace.", code="CONCURRENT_PUBLISH_OPERATION")

        try:
            # 1. Branch determination
            rc, branch_out, _ = self.wm.run_git_command(["rev-parse", "--abbrev-ref", "HEAD"], cwd=workspace_path)
            current_branch = branch.strip() if branch else (branch_out.strip() if rc == 0 else record.current_branch)

            # 2. Remote validation
            rc, rem_url_out, _ = self.wm.run_git_command(["config", "--get", f"remote.{remote_name}.url"], cwd=workspace_path)
            if rc != 0:
                raise PublishError(f"Remote '{remote_name}' not configured in workspace.", code="REMOTE_NOT_FOUND")
            raw_remote_url = rem_url_out.strip()
            clean_remote_url = scrub_sensitive_text(raw_remote_url)

            # 3. Authentication verification
            token = self.auth.get_token()
            # If remote is remote HTTPS GitHub URL, token is strictly required
            if "github.com" in raw_remote_url.lower() and not token:
                raise GitHubAuthError(
                    "GitHub authentication required to push. Please connect your account via Device Flow or configure GITHUB_TOKEN."
                )

            # 4. Fetch & Remote Divergence Check
            rc, fetch_out, fetch_err = self.wm.run_git_command(
                ["fetch", remote_name, current_branch],
                cwd=workspace_path,
                auth_token=token,
                timeout=60.0
            )

            # Check if remote has commits not present in local HEAD (divergence)
            rc_behind, behind_out, _ = self.wm.run_git_command(
                ["rev-list", "--count", "HEAD..FETCH_HEAD"],
                cwd=workspace_path
            )
            behind_count = int(behind_out.strip()) if rc_behind == 0 and behind_out.strip().isdigit() else 0
            if behind_count > 0:
                recovery_steps = "1. Fetch remote updates. 2. Inspect conflicts. 3. Rebase or merge manually."
                raise PublishError(
                    f"Remote branch '{current_branch}' has {behind_count} new commit(s) ahead of local HEAD. "
                    "Push was stopped to prevent silently overwriting remote work. "
                    f"Recovery steps: {recovery_steps}",
                    code="REMOTE_DIVERGED",
                    details={
                        "remote": remote_name,
                        "branch": current_branch,
                        "commits_behind": behind_count,
                        "recovery_steps": recovery_steps
                    }
                )

            # Compute commits ahead of remote
            rc_ahead, ahead_out, _ = self.wm.run_git_command(
                ["rev-list", "--count", "FETCH_HEAD..HEAD"],
                cwd=workspace_path
            )
            ahead_count = int(ahead_out.strip()) if rc_ahead == 0 and ahead_out.strip().isdigit() else 0

            # 5. Push Execution (Strictly NO --force)
            push_cmd = ["push", remote_name, f"HEAD:{current_branch}"]
            rc, push_out, push_err = self.wm.run_git_command(
                push_cmd,
                cwd=workspace_path,
                auth_token=token,
                timeout=120.0
            )
            if rc != 0:
                clean_err = scrub_sensitive_text(push_err or push_out)
                raise GitOperationError(f"Git push failed: {clean_err}")

            # 6. Retrieve pushed commit
            rc, head_out, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=workspace_path)
            pushed_commit = head_out.strip() if rc == 0 else ""
            now_iso = datetime.now(timezone.utc).isoformat()

            return PushResult(
                success=True,
                workspace_id=workspace_id,
                pushed_commit=pushed_commit,
                branch=current_branch,
                remote=remote_name,
                remote_url=clean_remote_url,
                timestamp=now_iso,
                diverged=False,
                commits_ahead=ahead_count,
                commits_behind=0,
                message=f"Pushed {ahead_count} commit(s) cleanly to {remote_name}/{current_branch}."
            )
        finally:
            lock.release()


# Global git publisher singleton
git_publisher = GitPublisher()
