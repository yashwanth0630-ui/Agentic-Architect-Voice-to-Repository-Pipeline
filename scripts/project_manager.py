"""
Project Manager for Idea Beacon / Agentic Architect
Handles persistent project tracking, workspace directory isolation, and metadata registry.
Stores project records in projects/projects.json with path traversal safeguards.
"""

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
import json
import os
import re
import shutil
import tempfile
import threading
import stat
from typing import Any, Dict, List, Optional

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
DEFAULT_PROJECTS_DIR = os.path.join(REPO_ROOT, "projects")


def safe_rmtree(target_path: str) -> bool:
    """Safely removes directory tree on Windows handling read-only files."""
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


def sanitize_project_id(candidate: str, fallback: str = "project-workspace") -> str:
    """
    Sanitizes raw candidate name into a safe slug suitable for directory naming.
    Strips path separators, illegal symbols, multiple dots, and leading/trailing dashes.
    """
    if not candidate:
        return fallback

    # Strip any paths (handling both / and \)
    cleaned = str(candidate).replace("\\", "/").rstrip("/")
    base = cleaned.split("/")[-1].strip().lower()
    # Replace non-alphanumeric characters with hyphens
    slug = re.sub(r"[^a-z0-9_\-]+", "-", base)
    # Remove leading/trailing dots and hyphens
    slug = re.sub(r"^[-_.]+|[-_.]+$", "", slug)
    # Compress multiple hyphens
    slug = re.sub(r"-{2,}", "-", slug)

    if not slug or slug in [".", "..", "injected_agents"]:
        slug = fallback

    return slug[:48]  # safe length limit


@dataclass
class ProjectMetadata:
    project_id: str
    project_name: str
    project_type: str  # e.g., "website", "web_app", "api", "agent_module"
    workspace_path: str
    created_at: str
    updated_at: str
    status: str = "created"  # "created", "running", "stopped", "failed"
    preview_port: Optional[int] = None
    preview_url: Optional[str] = None
    description: str = ""
    files: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "ProjectMetadata":
        return cls(
            project_id=data["project_id"],
            project_name=data.get("project_name", data["project_id"]),
            project_type=data.get("project_type", "website"),
            workspace_path=data["workspace_path"],
            created_at=data.get("created_at", datetime.now(timezone.utc).isoformat()),
            updated_at=data.get("updated_at", datetime.now(timezone.utc).isoformat()),
            status=data.get("status", "created"),
            preview_port=data.get("preview_port"),
            preview_url=data.get("preview_url"),
            description=data.get("description", ""),
            files=data.get("files", [])
        )


class ProjectManager:
    """
    Manages project workspaces and persistent registry (projects/projects.json).
    Guarantees that all workspaces reside strictly within projects_root.
    """
    def __init__(self, projects_dir: str = DEFAULT_PROJECTS_DIR):
        self.projects_dir = os.path.abspath(projects_dir)
        self.registry_file = os.path.join(self.projects_dir, "projects.json")
        self._lock = threading.Lock()
        self._ensure_storage()

    def _ensure_storage(self):
        """Creates projects/ directory and projects.json if not present."""
        os.makedirs(self.projects_dir, exist_ok=True)
        if not os.path.exists(self.registry_file):
            self._write_registry({})

    def _read_registry(self) -> Dict[str, Dict[str, Any]]:
        """Reads projects.json safely with error fallback."""
        if not os.path.exists(self.registry_file):
            return {}
        try:
            with open(self.registry_file, "r", encoding="utf-8") as f:
                content = f.read().strip()
                if not content:
                    return {}
                return json.loads(content)
        except Exception as e:
            print(f"[ProjectManager] Warning: failed to parse projects.json ({e}), resetting.")
            return {}

    def _write_registry(self, data: Dict[str, Dict[str, Any]]):
        """Atomically writes registry dictionary to projects.json."""
        os.makedirs(self.projects_dir, exist_ok=True)
        encoded = json.dumps(data, indent=2, ensure_ascii=False).encode("utf-8")
        temp_fd, temp_path = tempfile.mkstemp(dir=self.projects_dir, prefix=".registry_", suffix=".tmp")
        try:
            with os.fdopen(temp_fd, "wb") as f:
                f.write(encoded)
                f.flush()
                os.fsync(f.fileno())
            os.replace(temp_path, self.registry_file)
        except Exception:
            if os.path.exists(temp_path):
                os.remove(temp_path)
            raise

    def get_safe_workspace_path(self, project_id: str) -> str:
        """
        Resolves workspace path and verifies path traversal security.
        Raises ValueError if project_id attempts escape or contains traversal sequences.
        """
        if not project_id or ".." in project_id or "/" in project_id or "\\" in project_id:
            raise ValueError(f"Security violation: path traversal detected for project '{project_id}'")

        clean_id = sanitize_project_id(project_id)
        candidate = os.path.abspath(os.path.join(self.projects_dir, clean_id))
        
        # Verify candidate path stays strictly inside self.projects_dir
        if not (candidate.startswith(self.projects_dir + os.sep) or candidate == self.projects_dir):
            raise ValueError(f"Security violation: path traversal detected for project '{project_id}'")
        return candidate

    def create_project(
        self,
        name_or_prompt: str,
        project_type: str = "website",
        description: str = "",
        explicit_id: Optional[str] = None
    ) -> ProjectMetadata:
        """
        Creates a new project workspace and registers it in projects.json.
        """
        if explicit_id and (".." in explicit_id or "/" in explicit_id or "\\" in explicit_id):
            raise ValueError(f"Security violation: invalid explicit_id or path traversal: '{explicit_id}'")

        with self._lock:
            registry = self._read_registry()

            # Determine project slug
            if explicit_id:
                base_id = sanitize_project_id(explicit_id)
            else:
                base_id = sanitize_project_id(name_or_prompt)

            # Ensure unique project_id if already in registry
            project_id = base_id
            counter = 2
            while project_id in registry:
                project_id = f"{base_id}-{counter}"
                counter += 1

            workspace_path = self.get_safe_workspace_path(project_id)
            os.makedirs(workspace_path, exist_ok=True)

            # Clean display name
            display_name = name_or_prompt.strip().title()
            if len(display_name) > 60:
                display_name = base_id.replace("-", " ").title()
            elif not display_name:
                display_name = project_id.replace("-", " ").title()

            now_iso = datetime.now(timezone.utc).isoformat()
            meta = ProjectMetadata(
                project_id=project_id,
                project_name=display_name,
                project_type=project_type,
                workspace_path=workspace_path,
                created_at=now_iso,
                updated_at=now_iso,
                status="created",
                description=description
            )

            registry[project_id] = meta.to_dict()
            self._write_registry(registry)
            print(f"[ProjectManager] Created project '{project_id}' in {workspace_path}")
            return meta

    def get_project(self, project_id: str) -> Optional[ProjectMetadata]:
        """Retrieves project metadata by ID."""
        with self._lock:
            registry = self._read_registry()
            data = registry.get(project_id)
            return ProjectMetadata.from_dict(data) if data else None

    def list_projects(self) -> List[ProjectMetadata]:
        """Lists all registered projects sorted by updated_at descending."""
        with self._lock:
            registry = self._read_registry()
            projects = [ProjectMetadata.from_dict(d) for d in registry.values()]
            projects.sort(key=lambda p: p.updated_at, reverse=True)
            return projects

    def update_project(self, project_id: str, **updates) -> Optional[ProjectMetadata]:
        """Updates project metadata fields."""
        with self._lock:
            registry = self._read_registry()
            if project_id not in registry:
                return None

            data = registry[project_id]
            for key, val in updates.items():
                if hasattr(ProjectMetadata, key) and key != "project_id":
                    data[key] = val

            data["updated_at"] = datetime.now(timezone.utc).isoformat()
            registry[project_id] = data
            self._write_registry(registry)
            return ProjectMetadata.from_dict(data)

    def delete_project(self, project_id: str, delete_files: bool = True) -> bool:
        """Deletes a project from the registry and optionally wipes its workspace."""
        with self._lock:
            registry = self._read_registry()
            if project_id not in registry:
                return False

            meta_data = registry.pop(project_id)
            self._write_registry(registry)

            if delete_files:
                workspace = meta_data.get("workspace_path")
                if workspace and os.path.isdir(workspace):
                    # Path security double-check
                    if workspace.startswith(self.projects_dir + os.sep):
                        safe_rmtree(workspace)
            return True


# Global singleton instance
project_manager = ProjectManager()
