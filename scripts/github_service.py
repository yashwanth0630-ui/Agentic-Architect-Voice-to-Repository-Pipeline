"""
GitHub Service Layer for Idea Beacon / Agentic Architect
Handles secure authentication abstraction, user verification, repository listing,
and metadata normalization with strict credential boundary enforcement.

Security Rules:
- NEVER hardcodes credentials or commits tokens.
- NEVER returns or logs GitHub tokens.
- Scans and scrubs tokens from all error messages and exception representations.
- Restricts input formats for owner and repository names to prevent URL manipulation.
"""

from dataclasses import asdict, dataclass, field
import json
import logging
import os
import re
from typing import Any, Dict, List, Optional, Tuple
import httpx

# Set up logger with credential safety
logger = logging.getLogger("github_service")

GITHUB_API_BASE_URL = os.getenv("GITHUB_API_BASE_URL", "https://api.github.com").rstrip("/")
GITHUB_API_VERSION = "2022-11-28"
DEFAULT_TIMEOUT_SECONDS = 10.0

# Safe pattern for GitHub repository owner and repository names
REPO_IDENTIFIER_PATTERN = re.compile(r"^[a-zA-Z0-9_\-\.]+$")

URL_CREDENTIAL_PATTERN = re.compile(r"(https?://)[^/\s@]+@", re.IGNORECASE)

# Regex patterns to detect and redact GitHub tokens from any string/log/error
TOKEN_SCRUB_PATTERNS = [
    re.compile(r"ghp_[a-zA-Z0-9]{8,}", re.IGNORECASE),
    re.compile(r"gho_[a-zA-Z0-9]{8,}", re.IGNORECASE),
    re.compile(r"github_pat_[a-zA-Z0-9_]{8,}", re.IGNORECASE),
    re.compile(r"ghu_[a-zA-Z0-9]{8,}", re.IGNORECASE),
    re.compile(r"ghs_[a-zA-Z0-9]{8,}", re.IGNORECASE),
    re.compile(r"Bearer\s+[a-zA-Z0-9_\-\.]+", re.IGNORECASE),
]


def scrub_sensitive_text(text: Optional[str]) -> str:
    """Removes all GitHub personal access tokens, auth headers, and URL credentials from text."""
    if not text:
        return ""
    scrubbed = str(text)
    scrubbed = URL_CREDENTIAL_PATTERN.sub(r"\1***@", scrubbed)
    for pattern in TOKEN_SCRUB_PATTERNS:
        scrubbed = pattern.sub("[REDACTED_CREDENTIAL]", scrubbed)
    return scrubbed


# ==============================================================================
# EXCEPTIONS
# ==============================================================================
class GitHubServiceError(Exception):
    """Base exception for all GitHub service operations with automatic credential scrubbing."""
    def __init__(self, message: str, status_code: Optional[int] = None, details: Optional[Any] = None):
        safe_message = scrub_sensitive_text(message)
        super().__init__(safe_message)
        self.message = safe_message
        self.status_code = status_code
        self.details = details


class GitHubAuthError(GitHubServiceError):
    """Raised when GitHub token is missing, invalid, or unauthorized (HTTP 401)."""
    pass


class GitHubNotFoundError(GitHubServiceError):
    """Raised when a repository or user resource is not found (HTTP 404)."""
    pass


class GitHubRateLimitError(GitHubServiceError):
    """Raised when GitHub API rate limits are exceeded (HTTP 403/429)."""
    pass


class GitHubValidationError(GitHubServiceError):
    """Raised when owner or repository format is invalid."""
    pass


class GitHubNetworkError(GitHubServiceError):
    """Raised when network connectivity or timeouts occur."""
    pass


# ==============================================================================
# DATA MODELS & NORMALIZATION
# ==============================================================================
@dataclass
class GitHubUser:
    login: str
    name: Optional[str] = None
    avatar_url: Optional[str] = None
    html_url: Optional[str] = None
    bio: Optional[str] = None
    public_repos: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class GitHubRepository:
    name: str
    full_name: str
    private: bool
    default_branch: str
    html_url: str
    clone_url: str
    description: Optional[str] = None
    updated_at: Optional[str] = None
    language: Optional[str] = None
    fork: bool = False
    stargazers_count: int = 0

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


def normalize_user_data(raw: Dict[str, Any]) -> Dict[str, Any]:
    """Normalizes raw GitHub user payload into a clean, safe dictionary."""
    if not isinstance(raw, dict):
        raw = {}
    return GitHubUser(
        login=str(raw.get("login") or "anonymous"),
        name=raw.get("name") if raw.get("name") else None,
        avatar_url=raw.get("avatar_url") if raw.get("avatar_url") else None,
        html_url=raw.get("html_url") if raw.get("html_url") else None,
        bio=raw.get("bio") if raw.get("bio") else None,
        public_repos=int(raw.get("public_repos") or 0)
    ).to_dict()


def normalize_repository_data(raw: Dict[str, Any]) -> Dict[str, Any]:
    """Normalizes raw GitHub repository payload into a clean, safe dictionary."""
    if not isinstance(raw, dict):
        raw = {}
    name = str(raw.get("name") or "unknown-repo")
    full_name = str(raw.get("full_name") or f"unknown/{name}")
    html_url = str(raw.get("html_url") or f"https://github.com/{full_name}")
    clone_url = str(raw.get("clone_url") or f"{html_url}.git")

    return GitHubRepository(
        name=name,
        full_name=full_name,
        private=bool(raw.get("private", False)),
        default_branch=str(raw.get("default_branch") or "main"),
        html_url=html_url,
        clone_url=clone_url,
        description=raw.get("description") if raw.get("description") else None,
        updated_at=raw.get("updated_at") if raw.get("updated_at") else None,
        language=raw.get("language") if raw.get("language") else None,
        fork=bool(raw.get("fork", False)),
        stargazers_count=int(raw.get("stargazers_count") or 0)
    ).to_dict()


# ==============================================================================
# AUTHENTICATION ABSTRACTION
# ==============================================================================
class GitHubAuth:
    """
    Manages GitHub credentials with priority resolution:
    1. Explicit constructor token parameter (e.g. from tests or secure injection)
    2. GITHUB_TOKEN environment variable
    3. GH_TOKEN environment variable (standard GitHub CLI)
    4. Local .github_token file in repository root (gitignored)
    5. Local .env file parsing (if not already exported into env)
    """
    def __init__(self, explicit_token: Optional[str] = None, repo_root: Optional[str] = None):
        self._repo_root = repo_root or os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        self._explicit_token = explicit_token.strip() if explicit_token else None

    def get_token(self) -> Optional[str]:
        """Resolves the raw token for internal HTTP Authorization header ONLY."""
        if self._explicit_token:
            return self._explicit_token

        # Check environment variables
        for env_var in ("GITHUB_TOKEN", "GH_TOKEN"):
            val = os.getenv(env_var)
            if val and val.strip():
                return val.strip()

        # Check .github_token file in repo root
        token_file = os.path.join(self._repo_root, ".github_token")
        if os.path.isfile(token_file):
            try:
                with open(token_file, "r", encoding="utf-8") as f:
                    content = f.read().strip()
                    if content:
                        return content
            except Exception:
                pass

        # Check .env file in repo root
        env_file = os.path.join(self._repo_root, ".env")
        if os.path.isfile(env_file):
            try:
                with open(env_file, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line.startswith("#") or not line:
                            continue
                        if "=" in line:
                            key, val = line.split("=", 1)
                            key = key.strip()
                            val = val.strip().strip("\"'")
                            if key in ("GITHUB_TOKEN", "GH_TOKEN") and val:
                                return val
            except Exception:
                pass

        return None

    def is_authenticated(self) -> bool:
        """Returns True if a valid non-empty token is present."""
        token = self.get_token()
        return bool(token and len(token) > 5)

    def get_masked_hint(self) -> str:
        """Returns a non-sensitive masked hint of the token status (safe for logs)."""
        token = self.get_token()
        if not token:
            return "unauthenticated"
        if len(token) <= 8:
            return "configured (masked)"
        return f"{token[:4]}****{token[-4:]}"

    def get_client_id(self) -> Optional[str]:
        """Resolves GitHub OAuth App Client ID from environment or .env file."""
        cid = os.getenv("GITHUB_CLIENT_ID")
        if cid and cid.strip():
            return cid.strip()

        env_file = os.path.join(self._repo_root, ".env")
        if os.path.isfile(env_file):
            try:
                with open(env_file, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line.startswith("#") or not line:
                            continue
                        if "=" in line:
                            key, val = line.split("=", 1)
                            key = key.strip()
                            val = val.strip().strip("\"'")
                            if key == "GITHUB_CLIENT_ID" and val:
                                return val
            except Exception:
                pass

        return None


# ==============================================================================
# GITHUB SERVICE IMPLEMENTATION
# ==============================================================================
class GitHubService:
    """
    Universal GitHub Service Layer.
    Provides authenticated user verification, repository listings, and individual
    repository inspection.
    """
    def __init__(
        self,
        auth: Optional[GitHubAuth] = None,
        base_url: str = GITHUB_API_BASE_URL,
        timeout: float = DEFAULT_TIMEOUT_SECONDS
    ):
        self.auth = auth or GitHubAuth()
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    @staticmethod
    def validate_identifier(name: str, label: str = "identifier") -> str:
        """Sanitizes owner or repo identifier to prevent path traversal or URI injection."""
        if not name or not isinstance(name, str):
            raise GitHubValidationError(f"Invalid {label}: value cannot be empty.")
        clean = name.strip()
        if not REPO_IDENTIFIER_PATTERN.match(clean) or clean in (".", ".."):
            raise GitHubValidationError(
                f"Invalid {label} '{scrub_sensitive_text(clean)}': contains forbidden characters."
            )
        return clean

    def _build_headers(self) -> Dict[str, str]:
        """Constructs headers for GitHub API requests. Never includes token if unauthenticated."""
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": GITHUB_API_VERSION,
            "User-Agent": "Agentic-Architect-Bridge/2.2",
        }
        token = self.auth.get_token()
        if token:
            headers["Authorization"] = f"Bearer {token}"
        return headers

    def _handle_response_error(self, resp: httpx.Response, action_description: str) -> None:
        """Standardized error handler for GitHub HTTP status codes."""
        status = resp.status_code
        if status in (200, 201, 204):
            return

        try:
            error_json = resp.json()
            message = error_json.get("message", resp.text)
        except Exception:
            message = resp.text or f"HTTP {status}"

        # Redact any tokens that might have been mirrored
        clean_msg = scrub_sensitive_text(message)

        if status == 401:
            raise GitHubAuthError(
                f"GitHub authentication failed during {action_description}: {clean_msg}",
                status_code=401
            )
        elif status == 403:
            remaining = resp.headers.get("x-ratelimit-remaining")
            if remaining == "0":
                reset_time = resp.headers.get("x-ratelimit-reset", "soon")
                raise GitHubRateLimitError(
                    f"GitHub API rate limit exceeded during {action_description}. Resets at {reset_time}.",
                    status_code=403
                )
            raise GitHubAuthError(
                f"GitHub access forbidden during {action_description}: {clean_msg}",
                status_code=403
            )
        elif status == 404:
            raise GitHubNotFoundError(
                f"GitHub resource not found during {action_description}: {clean_msg}",
                status_code=404
            )
        else:
            raise GitHubServiceError(
                f"GitHub API error ({status}) during {action_description}: {clean_msg}",
                status_code=status
            )

    # --------------------------------------------------------------------------
    # ASYNC METHODS (For FastAPI Server)
    # --------------------------------------------------------------------------
    async def get_authenticated_user(self) -> Optional[Dict[str, Any]]:
        """
        Retrieves normalized information for the authenticated GitHub user.
        Returns None if not authenticated.
        Raises GitHubAuthError if token is invalid or rejected by GitHub.
        """
        if not self.auth.is_authenticated():
            return None

        url = f"{self.base_url}/user"
        headers = self._build_headers()

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(url, headers=headers)
                self._handle_response_error(resp, "user verification")
                data = resp.json()
                return normalize_user_data(data)
        except (GitHubAuthError, GitHubRateLimitError, GitHubNotFoundError):
            raise
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error verifying GitHub user: {scrub_sensitive_text(str(e))}")
        except Exception as e:
            raise GitHubServiceError(f"Unexpected error retrieving user: {scrub_sensitive_text(str(e))}")

    async def list_repositories(
        self,
        visibility: str = "all",
        sort: str = "updated",
        per_page: int = 30,
        page: int = 1
    ) -> List[Dict[str, Any]]:
        """
        Lists repositories accessible to the authenticated user.
        Raises GitHubAuthError if not authenticated.
        Returns a list of normalized repository metadata objects.
        """
        if not self.auth.is_authenticated():
            raise GitHubAuthError("Cannot list repositories: GitHub token not configured.")

        url = f"{self.base_url}/user/repos"
        headers = self._build_headers()
        params = {
            "visibility": visibility,
            "sort": sort,
            "per_page": max(1, min(per_page, 100)),
            "page": max(1, page)
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(url, headers=headers, params=params)
                self._handle_response_error(resp, "listing repositories")
                data = resp.json()
                if not isinstance(data, list):
                    raise GitHubServiceError("Unexpected response format: expected repository list.")
                return [normalize_repository_data(repo) for repo in data]
        except (GitHubAuthError, GitHubRateLimitError, GitHubNotFoundError):
            raise
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error listing GitHub repositories: {scrub_sensitive_text(str(e))}")
        except Exception as e:
            raise GitHubServiceError(f"Unexpected error listing repositories: {scrub_sensitive_text(str(e))}")

    async def get_repository(self, owner: str, repo: str) -> Dict[str, Any]:
        """
        Retrieves detailed normalized metadata for a specific repository: {owner}/{repo}.
        Validates owner and repo to prevent injection or path manipulation.
        """
        safe_owner = self.validate_identifier(owner, "owner")
        safe_repo = self.validate_identifier(repo, "repository")

        url = f"{self.base_url}/repos/{safe_owner}/{safe_repo}"
        headers = self._build_headers()

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.get(url, headers=headers)
                self._handle_response_error(resp, f"fetching repository {safe_owner}/{safe_repo}")
                data = resp.json()
                return normalize_repository_data(data)
        except (GitHubAuthError, GitHubRateLimitError, GitHubNotFoundError, GitHubValidationError):
            raise
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error fetching repository: {scrub_sensitive_text(str(e))}")
        except Exception as e:
            raise GitHubServiceError(f"Unexpected error fetching repository: {scrub_sensitive_text(str(e))}")

    # --------------------------------------------------------------------------
    # SYNCHRONOUS CONVENIENCE METHODS (For Unit Testing & CLI Utilities)
    # --------------------------------------------------------------------------
    def get_authenticated_user_sync(self) -> Optional[Dict[str, Any]]:
        if not self.auth.is_authenticated():
            return None
        url = f"{self.base_url}/user"
        headers = self._build_headers()
        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.get(url, headers=headers)
                self._handle_response_error(resp, "user verification")
                return normalize_user_data(resp.json())
        except (GitHubAuthError, GitHubRateLimitError, GitHubNotFoundError):
            raise
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error verifying GitHub user: {scrub_sensitive_text(str(e))}")
        except Exception as e:
            raise GitHubServiceError(f"Unexpected error retrieving user: {scrub_sensitive_text(str(e))}")

    def list_repositories_sync(
        self,
        visibility: str = "all",
        sort: str = "updated",
        per_page: int = 30,
        page: int = 1
    ) -> List[Dict[str, Any]]:
        if not self.auth.is_authenticated():
            raise GitHubAuthError("Cannot list repositories: GitHub token not configured.")
        url = f"{self.base_url}/user/repos"
        headers = self._build_headers()
        params = {
            "visibility": visibility,
            "sort": sort,
            "per_page": max(1, min(per_page, 100)),
            "page": max(1, page)
        }
        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.get(url, headers=headers, params=params)
                self._handle_response_error(resp, "listing repositories")
                data = resp.json()
                if not isinstance(data, list):
                    raise GitHubServiceError("Unexpected response format: expected repository list.")
                return [normalize_repository_data(repo) for repo in data]
        except (GitHubAuthError, GitHubRateLimitError, GitHubNotFoundError):
            raise
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error listing GitHub repositories: {scrub_sensitive_text(str(e))}")
        except Exception as e:
            raise GitHubServiceError(f"Unexpected error listing repositories: {scrub_sensitive_text(str(e))}")

    def get_repository_sync(self, owner: str, repo: str) -> Dict[str, Any]:
        safe_owner = self.validate_identifier(owner, "owner")
        safe_repo = self.validate_identifier(repo, "repository")
        url = f"{self.base_url}/repos/{safe_owner}/{safe_repo}"
        headers = self._build_headers()
        try:
            with httpx.Client(timeout=self.timeout) as client:
                resp = client.get(url, headers=headers)
                self._handle_response_error(resp, f"fetching repository {safe_owner}/{safe_repo}")
                return normalize_repository_data(resp.json())
        except (GitHubAuthError, GitHubRateLimitError, GitHubNotFoundError, GitHubValidationError):
            raise
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error fetching repository: {scrub_sensitive_text(str(e))}")
        except Exception as e:
            raise GitHubServiceError(f"Unexpected error fetching repository: {scrub_sensitive_text(str(e))}")

    # --------------------------------------------------------------------------
    # DEVICE AUTHORIZATION FLOW (RFC 8628)
    # --------------------------------------------------------------------------
    async def initiate_device_flow(self, client_id: Optional[str] = None) -> Dict[str, Any]:
        """
        Initiates GitHub OAuth Device Authorization Flow (RFC 8628).
        Requests device_code and user_code from GitHub.
        Returns user_code, verification_uri, expires_in, interval.
        Never exposes client secrets or access tokens.
        """
        cid = client_id or self.auth.get_client_id()
        if not cid or not cid.strip():
            raise GitHubAuthError(
                "GitHub Client ID is required for Device Flow. Set GITHUB_CLIENT_ID in your .env or laptop environment, or configure GITHUB_TOKEN directly."
            )

        url = "https://github.com/login/device/code"
        headers = {
            "Accept": "application/json",
            "User-Agent": "Agentic-Architect-Bridge/2.2"
        }
        data = {
            "client_id": cid.strip(),
            "scope": "repo,read:user"
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(url, headers=headers, data=data)
                if resp.status_code != 200:
                    raise GitHubServiceError(f"GitHub returned HTTP {resp.status_code} during device code request")
                res_data = resp.json()
                if "error" in res_data:
                    raise GitHubAuthError(f"Device code error: {res_data.get('error_description', res_data['error'])}")

                return {
                    "success": True,
                    "device_code": res_data.get("device_code"),
                    "user_code": res_data.get("user_code"),
                    "verification_uri": res_data.get("verification_uri", "https://github.com/login/device"),
                    "expires_in": res_data.get("expires_in", 900),
                    "interval": res_data.get("interval", 5)
                }
        except (GitHubAuthError, GitHubServiceError):
            raise
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error requesting device code: {scrub_sensitive_text(str(e))}")

    async def poll_device_flow(self, device_code: str, client_id: Optional[str] = None) -> Dict[str, Any]:
        """
        Polls GitHub to verify if user has completed authorization on github.com/login/device.
        If successful: saves token safely to local .github_token and marks auth active.
        CRITICAL: Never returns the access token to the caller.
        """
        cid = client_id or self.auth.get_client_id()
        if not cid or not cid.strip():
            raise GitHubAuthError("GitHub Client ID required for device flow polling.")

        if not device_code or not device_code.strip():
            raise GitHubValidationError("device_code cannot be empty.")

        url = "https://github.com/login/oauth/access_token"
        headers = {
            "Accept": "application/json",
            "User-Agent": "Agentic-Architect-Bridge/2.2"
        }
        data = {
            "client_id": cid.strip(),
            "device_code": device_code.strip(),
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code"
        }

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                resp = await client.post(url, headers=headers, data=data)
                res_data = resp.json()

                if "error" in res_data:
                    err = res_data["error"]
                    if err == "authorization_pending":
                        return {
                            "success": True,
                            "status": "pending",
                            "message": "Waiting for authorization on github.com/login/device..."
                        }
                    elif err == "slow_down":
                        return {
                            "success": True,
                            "status": "slow_down",
                            "interval": res_data.get("interval", 10),
                            "message": "Polling too quickly; slowing down."
                        }
                    elif err == "expired_token":
                        return {
                            "success": False,
                            "status": "expired",
                            "message": "Device authorization code expired. Please request a new code."
                        }
                    elif err == "access_denied":
                        return {
                            "success": False,
                            "status": "denied",
                            "message": "Authorization was cancelled or denied."
                        }
                    else:
                        return {
                            "success": False,
                            "status": err,
                            "message": res_data.get("error_description", err)
                        }

                access_token = res_data.get("access_token")
                if access_token:
                    token_path = os.path.join(self.auth._repo_root, ".github_token")
                    try:
                        flags = os.O_WRONLY | os.O_CREAT | os.O_TRUNC
                        mode = 0o600
                        fd = os.open(token_path, flags, mode)
                        with os.fdopen(fd, "w", encoding="utf-8") as f:
                            f.write(access_token.strip())
                        if hasattr(os, "chmod"):
                            try:
                                os.chmod(token_path, 0o600)
                            except OSError:
                                pass
                    except Exception as e:
                        logger.warning(f"Could not persist .github_token file: {e}")

                    self.auth._explicit_token = access_token.strip()
                    user = await self.get_authenticated_user()
                    return {
                        "success": True,
                        "status": "complete",
                        "authenticated": True,
                        "user": user,
                        "message": "Successfully authenticated with GitHub!"
                    }

                return {
                    "success": False,
                    "status": "unknown",
                    "message": "Unexpected response from GitHub."
                }
        except httpx.RequestError as e:
            raise GitHubNetworkError(f"Network error during device flow polling: {scrub_sensitive_text(str(e))}")

    def logout(self) -> bool:
        """
        Removes stored credentials and clears in-memory auth state.
        """
        self.auth._explicit_token = None
        token_path = os.path.join(self.auth._repo_root, ".github_token")
        if os.path.isfile(token_path):
            try:
                os.remove(token_path)
            except Exception:
                pass
        return True


# Default global instance
github_service = GitHubService()
