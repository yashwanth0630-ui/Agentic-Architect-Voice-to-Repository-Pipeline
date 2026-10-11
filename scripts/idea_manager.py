"""
Idea Manager for Idea Beacon / Agentic Architect
Handles permanent storage, brainstorming, refinement, and blueprint transformation of user ideas.
Stores idea records in projects/ideas.json with thread safety and atomic writes.
"""

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
import json
import os
import re
import tempfile
import threading
from typing import Any, Dict, List, Optional, Tuple

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, ".."))
PROJECTS_DIR = os.path.join(REPO_ROOT, "projects")
DEFAULT_IDEAS_FILE = os.path.join(PROJECTS_DIR, "ideas.json")


def sanitize_idea_id(candidate: str, fallback: str = "idea") -> str:
    """Creates safe slug for idea IDs."""
    if not candidate:
        return fallback
    cleaned = re.sub(r"[^a-zA-Z0-9_\-]+", "-", candidate.strip().lower())
    cleaned = re.sub(r"^[-_.]+|[-_.]+$", "", cleaned)
    cleaned = re.sub(r"-{2,}", "-", cleaned)
    return cleaned[:40] if cleaned else fallback


@dataclass
class IdeaBlueprint:
    summary: str
    target_audience: str
    core_features: List[str]
    suggested_architecture: str
    recommended_tech_stack: str  # "python", "fastapi", "react", "fullstack", "static_web"
    key_dependencies: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Optional[Dict[str, Any]]) -> Optional["IdeaBlueprint"]:
        if not data:
            return None
        return cls(
            summary=data.get("summary", ""),
            target_audience=data.get("target_audience", ""),
            core_features=data.get("core_features", []),
            suggested_architecture=data.get("suggested_architecture", ""),
            recommended_tech_stack=data.get("recommended_tech_stack", "python"),
            key_dependencies=data.get("key_dependencies", [])
        )


@dataclass
class IdeaRecord:
    idea_id: str
    title: str
    raw_prompt: str
    created_at: str
    updated_at: str
    status: str = "captured"  # "captured", "refined", "planned", "ready_for_setup", "project_created"
    tech_stack: str = "auto"
    blueprint: Optional[IdeaBlueprint] = None
    linked_project_id: Optional[str] = None
    tags: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        if self.blueprint:
            d["blueprint"] = self.blueprint.to_dict()
        return d

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "IdeaRecord":
        bp_data = data.get("blueprint")
        blueprint = IdeaBlueprint.from_dict(bp_data) if bp_data else None
        return cls(
            idea_id=data["idea_id"],
            title=data.get("title", "Untitled Idea"),
            raw_prompt=data.get("raw_prompt", ""),
            created_at=data.get("created_at", ""),
            updated_at=data.get("updated_at", ""),
            status=data.get("status", "captured"),
            tech_stack=data.get("tech_stack", "auto"),
            blueprint=blueprint,
            linked_project_id=data.get("linked_project_id"),
            tags=data.get("tags", [])
        )


class IdeaManager:
    """Thread-safe persistent storage and refinement engine for ideas."""

    def __init__(self, ideas_file: str = DEFAULT_IDEAS_FILE):
        self.ideas_file = ideas_file
        self._lock = threading.Lock()
        os.makedirs(os.path.dirname(self.ideas_file), exist_ok=True)
        if not os.path.exists(self.ideas_file):
            self._write_registry({})

    def _read_registry(self) -> Dict[str, Dict[str, Any]]:
        if not os.path.exists(self.ideas_file):
            return {}
        try:
            with open(self.ideas_file, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}

    def _write_registry(self, data: Dict[str, Dict[str, Any]]):
        dir_name = os.path.dirname(self.ideas_file)
        os.makedirs(dir_name, exist_ok=True)
        fd, tmp_path = tempfile.mkstemp(dir=dir_name, prefix="ideas_", suffix=".tmp")
        try:
            with open(fd, "w", encoding="utf-8") as f:
                json.dump(data, f, indent=2)
            if os.path.exists(self.ideas_file):
                os.replace(tmp_path, self.ideas_file)
            else:
                os.rename(tmp_path, self.ideas_file)
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise

    def capture_idea(
        self,
        prompt: str,
        title: Optional[str] = None,
        tech_stack: Optional[str] = None,
        idea_id: Optional[str] = None
    ) -> IdeaRecord:
        """Captures a new user idea permanently. If idea_id is provided, uses it idempotently."""
        clean_prompt = prompt.strip()
        if not clean_prompt:
            raise ValueError("Idea prompt cannot be empty.")

        with self._lock:
            reg = self._read_registry()

            # If idea_id provided and already exists, update prompt/title idempotently
            if idea_id and idea_id in reg:
                record = IdeaRecord.from_dict(reg[idea_id])
                record.raw_prompt = clean_prompt
                if title and title.strip():
                    record.title = title.strip()
                if tech_stack and tech_stack != "auto":
                    record.tech_stack = tech_stack
                record.updated_at = datetime.now(timezone.utc).isoformat()
                reg[idea_id] = record.to_dict()
                self._write_registry(reg)
                return record

            # Derive title and id
            derived_title = title.strip() if title and title.strip() else ""
            if not derived_title:
                words = re.findall(r"[a-zA-Z0-9]+", clean_prompt)
                derived_title = " ".join(words[:5]).title() if words else "New Idea"

            if idea_id:
                final_id = sanitize_idea_id(idea_id)
            else:
                base_id = sanitize_idea_id(derived_title)
                final_id = base_id
                counter = 2
                while final_id in reg:
                    final_id = f"{base_id}-{counter}"
                    counter += 1

            now = datetime.now(timezone.utc).isoformat()
            detected_stack = tech_stack or self.detect_recommended_stack(clean_prompt)

            record = IdeaRecord(
                idea_id=final_id,
                title=derived_title,
                raw_prompt=clean_prompt,
                created_at=now,
                updated_at=now,
                status="captured",
                tech_stack=detected_stack
            )

            reg[final_id] = record.to_dict()
            self._write_registry(reg)
            return record

    def update_idea(
        self,
        idea_id: str,
        prompt: Optional[str] = None,
        title: Optional[str] = None,
        tech_stack: Optional[str] = None
    ) -> IdeaRecord:
        """Updates an existing idea's prompt, title, or tech_stack."""
        with self._lock:
            reg = self._read_registry()
            if idea_id not in reg:
                raise KeyError(f"Idea '{idea_id}' not found.")
            record = IdeaRecord.from_dict(reg[idea_id])
            if prompt is not None and prompt.strip():
                record.raw_prompt = prompt.strip()
            if title is not None and title.strip():
                record.title = title.strip()
            if tech_stack is not None and tech_stack.strip():
                record.tech_stack = tech_stack.strip()
            record.updated_at = datetime.now(timezone.utc).isoformat()
            reg[idea_id] = record.to_dict()
            self._write_registry(reg)
            return record

    def delete_idea(self, idea_id: str) -> bool:
        """Deletes an idea from persistent storage."""
        with self._lock:
            reg = self._read_registry()
            if idea_id in reg:
                del reg[idea_id]
                self._write_registry(reg)
                return True
            return False

    def get_idea(self, idea_id: str) -> Optional[IdeaRecord]:
        with self._lock:
            reg = self._read_registry()
            data = reg.get(idea_id)
            return IdeaRecord.from_dict(data) if data else None

    def list_ideas(self) -> List[IdeaRecord]:
        with self._lock:
            reg = self._read_registry()
            ideas = [IdeaRecord.from_dict(d) for d in reg.values()]
            ideas.sort(key=lambda x: x.updated_at, reverse=True)
            return ideas

    def refine_idea(
        self,
        idea_id: str,
        blueprint_override: Optional[IdeaBlueprint] = None
    ) -> IdeaRecord:
        """Refines idea into an actionable blueprint with architecture and dependencies."""
        with self._lock:
            reg = self._read_registry()
            if idea_id not in reg:
                raise KeyError(f"Idea '{idea_id}' not found.")

            record = IdeaRecord.from_dict(reg[idea_id])
            if blueprint_override:
                blueprint = blueprint_override
            else:
                blueprint = self._generate_blueprint_from_prompt(record.raw_prompt, record.tech_stack)

            record.blueprint = blueprint
            record.status = "refined"
            record.updated_at = datetime.now(timezone.utc).isoformat()
            if blueprint.recommended_tech_stack and record.tech_stack == "auto":
                record.tech_stack = blueprint.recommended_tech_stack

            reg[idea_id] = record.to_dict()
            self._write_registry(reg)
            return record

    def link_project(self, idea_id: str, project_id: str) -> Optional[IdeaRecord]:
        with self._lock:
            reg = self._read_registry()
            if idea_id not in reg:
                return None
            record = IdeaRecord.from_dict(reg[idea_id])
            record.linked_project_id = project_id
            record.status = "project_created"
            record.updated_at = datetime.now(timezone.utc).isoformat()
            reg[idea_id] = record.to_dict()
            self._write_registry(reg)
            return record

    @staticmethod
    def detect_recommended_stack(prompt: str) -> str:
        p = prompt.lower()
        has_api = bool(re.search(r"\b(api|fastapi|backend|endpoint|rest|microservice|service)\b", p))
        has_ui = bool(re.search(r"\b(ui|frontend|react|dashboard|component|portal|spa|web app)\b", p))
        has_python = bool(re.search(r"\b(python|script|bot|crawler|pandas|data|cli|ai)\b", p))

        if (has_api or has_python) and has_ui:
            return "fullstack"
        if has_api:
            return "fastapi"
        if has_ui:
            return "react"
        if has_python:
            return "python"
        if any(k in p for k in ["website", "landing page", "homepage"]):
            return "static_web"
        return "python"

    @classmethod
    def _generate_blueprint_from_prompt(cls, prompt: str, tech_stack: str = "auto") -> IdeaBlueprint:
        stack = tech_stack if tech_stack != "auto" else cls.detect_recommended_stack(prompt)
        p_clean = prompt.strip()

        if stack == "fastapi":
            return IdeaBlueprint(
                summary=f"High-performance REST API service: {p_clean}",
                target_audience="Developers and API consumers",
                core_features=[
                    "FastAPI REST endpoints with automatic OpenAPI/Swagger docs",
                    "Pydantic schema validation for all requests and responses",
                    "Health check and service status monitoring endpoint",
                    "CORS middleware for client integration"
                ],
                suggested_architecture="Modular FastAPI application with clean routers and domain models",
                recommended_tech_stack="fastapi",
                key_dependencies=["fastapi>=0.115.0", "uvicorn[standard]>=0.32.0", "pydantic>=2.9.0"]
            )
        elif stack == "react":
            return IdeaBlueprint(
                summary=f"Modern, responsive React web application: {p_clean}",
                target_audience="End users and interactive web clients",
                core_features=[
                    "Component-based UI with interactive state management",
                    "Fast Vite-powered development server with HMR",
                    "Responsive mobile and desktop layout",
                    "Client-side navigation and modular styling"
                ],
                suggested_architecture="Single Page Application (SPA) powered by Vite and React 18+",
                recommended_tech_stack="react",
                key_dependencies=["react", "react-dom", "vite"]
            )
        elif stack == "fullstack":
            return IdeaBlueprint(
                summary=f"Full-stack web application with FastAPI backend and React frontend: {p_clean}",
                target_audience="End users requiring rich interactive UI backed by robust API services",
                core_features=[
                    "FastAPI asynchronous backend with JSON REST endpoints",
                    "React Vite frontend communicating with backend via configured proxy/CORS",
                    "Dual-runtime local dev server coordination",
                    "Unified health check and live preview"
                ],
                suggested_architecture="Decoupled Full-Stack Architecture (FastAPI API + React Vite SPA)",
                recommended_tech_stack="fullstack",
                key_dependencies=["fastapi", "uvicorn", "react", "vite"]
            )
        elif stack == "static_web":
            return IdeaBlueprint(
                summary=f"Lightweight, high-performance static website: {p_clean}",
                target_audience="Visitors seeking fast information display and landing presentation",
                core_features=[
                    "Responsive HTML5 semantic layout",
                    "Modern glassmorphism CSS design system",
                    "Interactive vanilla JavaScript behaviors",
                    "Zero-build local preview server"
                ],
                suggested_architecture="Static Multi-File Web Workspace",
                recommended_tech_stack="static_web",
                key_dependencies=[]
            )
        else:  # python
            return IdeaBlueprint(
                summary=f"Python utility and application: {p_clean}",
                target_audience="Developers, power users, and automation engineers",
                core_features=[
                    "Structured Python module with entry-point execution",
                    "Virtual environment (.venv) isolation",
                    "Input parsing and automated validation",
                    "Modular domain logic and automated test scaffolding"
                ],
                suggested_architecture="Isolated Python Package with venv environment",
                recommended_tech_stack="python",
                key_dependencies=["pytest"]
            )


idea_manager = IdeaManager()
