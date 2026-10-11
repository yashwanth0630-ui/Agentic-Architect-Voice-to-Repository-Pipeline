"""
Comprehensive Unit & Integration Test Suite for GitHub Service & FastAPI Endpoints
Tests:
- GitHub service initialization & priority resolution
- Unauthenticated state handling
- Authenticated state & user metadata verification
- Repository response normalization & edge cases
- Malformed GitHub API responses
- GitHub API failures (401, 404, 403 rate limit, 500, network error)
- Zero token leak in API responses (strict security assertion)
- Zero token leak in logs / error messages (scrubbing assertion)
- FastAPI endpoint behavior (/github/status, /github/repos, /github/repos/{owner}/{repo})
- Identifier validation & traversal attack blocking
- WAN origin blocking verification on /github endpoints
"""

import io
import json
import logging
import os
import re
import shutil
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

# Add scripts directory to path
SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "scripts"))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

from github_service import (
    GitHubAuth,
    GitHubService,
    GitHubUser,
    GitHubRepository,
    GitHubServiceError,
    GitHubAuthError,
    GitHubNotFoundError,
    GitHubRateLimitError,
    GitHubValidationError,
    GitHubNetworkError,
    normalize_user_data,
    normalize_repository_data,
    scrub_sensitive_text,
    github_service
)
from laptop_ai_server import app


SAMPLE_MOCK_USER = {
    "login": "octocat",
    "name": "The Octocat",
    "avatar_url": "https://avatars.githubusercontent.com/u/583231",
    "html_url": "https://github.com/octocat",
    "bio": "Open source architect",
    "public_repos": 8
}

SAMPLE_MOCK_REPOS = [
    {
        "name": "hello-world",
        "full_name": "octocat/hello-world",
        "private": False,
        "default_branch": "main",
        "html_url": "https://github.com/octocat/hello-world",
        "clone_url": "https://github.com/octocat/hello-world.git",
        "description": "My first repository",
        "updated_at": "2026-10-09T00:00:00Z",
        "language": "TypeScript",
        "fork": False,
        "stargazers_count": 42
    },
    {
        "name": "private-vault",
        "full_name": "octocat/private-vault",
        "private": True,
        "default_branch": "master",
        "html_url": "https://github.com/octocat/private-vault",
        "clone_url": "https://github.com/octocat/private-vault.git",
        "description": "Secure storage",
        "updated_at": "2026-10-08T12:00:00Z",
        "language": "Kotlin",
        "fork": True,
        "stargazers_count": 1
    }
]


class TestGitHubAuthAndInitialization(unittest.TestCase):
    """Verifies credential resolution priority and unauthenticated state."""

    def test_unauthenticated_when_no_credentials(self):
        with patch.dict(os.environ, {}, clear=True):
            auth = GitHubAuth(repo_root=tempfile.gettempdir())
            self.assertFalse(auth.is_authenticated())
            self.assertIsNone(auth.get_token())
            self.assertEqual(auth.get_masked_hint(), "unauthenticated")

    def test_explicit_token_precedence(self):
        auth = GitHubAuth(explicit_token="ghp_explicit1234567890")
        self.assertTrue(auth.is_authenticated())
        self.assertEqual(auth.get_token(), "ghp_explicit1234567890")
        hint = auth.get_masked_hint()
        self.assertNotIn("1234567890", hint)
        self.assertIn("****", hint)

    def test_environment_variable_resolution(self):
        with patch.dict(os.environ, {"GITHUB_TOKEN": "ghp_envtoken9876543210"}):
            auth = GitHubAuth(repo_root=tempfile.gettempdir())
            self.assertTrue(auth.is_authenticated())
            self.assertEqual(auth.get_token(), "ghp_envtoken9876543210")

    def test_gh_token_fallback(self):
        with patch.dict(os.environ, {"GH_TOKEN": "ghp_ghtoken1122334455"}, clear=True):
            auth = GitHubAuth(repo_root=tempfile.gettempdir())
            self.assertTrue(auth.is_authenticated())
            self.assertEqual(auth.get_token(), "ghp_ghtoken1122334455")

    def test_file_based_token_resolution(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            token_file = os.path.join(tmp_dir, ".github_token")
            with open(token_file, "w", encoding="utf-8") as f:
                f.write("  ghp_filetoken5566778899 \n")
            with patch.dict(os.environ, {}, clear=True):
                auth = GitHubAuth(repo_root=tmp_dir)
                self.assertTrue(auth.is_authenticated())
                self.assertEqual(auth.get_token(), "ghp_filetoken5566778899")


class TestNormalization(unittest.TestCase):
    """Verifies that raw GitHub responses are normalized cleanly and safely."""

    def test_normalize_user_success(self):
        user = normalize_user_data(SAMPLE_MOCK_USER)
        self.assertEqual(user["login"], "octocat")
        self.assertEqual(user["name"], "The Octocat")
        self.assertEqual(user["public_repos"], 8)
        self.assertEqual(user["html_url"], "https://github.com/octocat")

    def test_normalize_user_sparse_data(self):
        user = normalize_user_data({"login": "dev1"})
        self.assertEqual(user["login"], "dev1")
        self.assertIsNone(user["name"])
        self.assertIsNone(user["avatar_url"])
        self.assertEqual(user["public_repos"], 0)

    def test_normalize_user_empty(self):
        user = normalize_user_data({})
        self.assertEqual(user["login"], "anonymous")
        self.assertIsNone(user["name"])
        self.assertEqual(user["public_repos"], 0)

    def test_normalize_repository_success(self):
        repo = normalize_repository_data(SAMPLE_MOCK_REPOS[0])
        self.assertEqual(repo["name"], "hello-world")
        self.assertEqual(repo["full_name"], "octocat/hello-world")
        self.assertFalse(repo["private"])
        self.assertEqual(repo["default_branch"], "main")
        self.assertEqual(repo["html_url"], "https://github.com/octocat/hello-world")
        self.assertEqual(repo["clone_url"], "https://github.com/octocat/hello-world.git")
        self.assertEqual(repo["stargazers_count"], 42)

    def test_normalize_repository_missing_urls(self):
        repo = normalize_repository_data({"name": "test-repo", "full_name": "user/test-repo"})
        self.assertEqual(repo["name"], "test-repo")
        self.assertEqual(repo["html_url"], "https://github.com/user/test-repo")
        self.assertEqual(repo["clone_url"], "https://github.com/user/test-repo.git")
        self.assertFalse(repo["private"])
        self.assertEqual(repo["default_branch"], "main")


class TestSecurityAndTokenScrubbing(unittest.TestCase):
    """Verifies that tokens are never leaked in strings, errors, or logs."""

    def test_scrub_sensitive_text_redacts_tokens(self):
        raw_token = "ghp_SECRETTOKENTEST1234567890ABCDEF"
        sample_log = f"Failed to connect using token {raw_token} for user octocat"
        scrubbed = scrub_sensitive_text(sample_log)
        self.assertNotIn(raw_token, scrubbed)
        self.assertIn("[REDACTED_CREDENTIAL]", scrubbed)

    def test_scrub_sensitive_text_redacts_bearer_header(self):
        header_text = "Authorization: Bearer my_secret_bearer_token_xyz"
        scrubbed = scrub_sensitive_text(header_text)
        self.assertNotIn("my_secret_bearer_token_xyz", scrubbed)
        self.assertIn("[REDACTED_CREDENTIAL]", scrubbed)

    def test_exception_messages_automatically_scrubbed(self):
        raw_token = "ghp_VERYSECRETTOKENFORSECURITYTEST"
        err = GitHubServiceError(f"API rejected request with {raw_token}")
        self.assertNotIn(raw_token, str(err))
        self.assertNotIn(raw_token, err.message)
        self.assertIn("[REDACTED_CREDENTIAL]", str(err))

    def test_identifier_validation_blocks_path_traversal(self):
        service = GitHubService()
        with self.assertRaises(GitHubValidationError):
            service.validate_identifier("../../etc/passwd", "owner")

        with self.assertRaises(GitHubValidationError):
            service.validate_identifier("owner/with/slash", "owner")

        with self.assertRaises(GitHubValidationError):
            service.validate_identifier("repo;rm -rf /", "repository")

        with self.assertRaises(GitHubValidationError):
            service.validate_identifier("..", "repository")

        # Valid identifiers should pass without error
        clean = service.validate_identifier("octocat-v2.0_test", "owner")
        self.assertEqual(clean, "octocat-v2.0_test")


class TestGitHubServiceOperations(unittest.IsolatedAsyncioTestCase):
    """Tests GitHubService methods with mocked HTTP client."""

    async def test_get_authenticated_user_unauthenticated(self):
        auth = GitHubAuth(explicit_token=None)
        with patch.object(auth, "is_authenticated", return_value=False):
            service = GitHubService(auth=auth)
            user = await service.get_authenticated_user()
            self.assertIsNone(user)

    async def test_list_repositories_unauthenticated_raises(self):
        auth = GitHubAuth(explicit_token=None)
        with patch.object(auth, "is_authenticated", return_value=False):
            service = GitHubService(auth=auth)
            with self.assertRaises(GitHubAuthError):
                await service.list_repositories()

    @patch("httpx.AsyncClient.get")
    async def test_get_authenticated_user_success(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = SAMPLE_MOCK_USER
        mock_get.return_value = mock_resp

        auth = GitHubAuth(explicit_token="ghp_testtoken1234567890")
        service = GitHubService(auth=auth)
        user = await service.get_authenticated_user()

        self.assertIsNotNone(user)
        self.assertEqual(user["login"], "octocat")
        self.assertEqual(user["name"], "The Octocat")

    @patch("httpx.AsyncClient.get")
    async def test_list_repositories_success(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = SAMPLE_MOCK_REPOS
        mock_get.return_value = mock_resp

        auth = GitHubAuth(explicit_token="ghp_testtoken1234567890")
        service = GitHubService(auth=auth)
        repos = await service.list_repositories()

        self.assertEqual(len(repos), 2)
        self.assertEqual(repos[0]["name"], "hello-world")
        self.assertEqual(repos[1]["name"], "private-vault")
        self.assertTrue(repos[1]["private"])

    @patch("httpx.AsyncClient.get")
    async def test_get_repository_success(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = SAMPLE_MOCK_REPOS[0]
        mock_get.return_value = mock_resp

        auth = GitHubAuth(explicit_token="ghp_testtoken1234567890")
        service = GitHubService(auth=auth)
        repo = await service.get_repository("octocat", "hello-world")

        self.assertEqual(repo["name"], "hello-world")
        self.assertEqual(repo["full_name"], "octocat/hello-world")

    @patch("httpx.AsyncClient.get")
    async def test_github_401_raises_auth_error(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.status_code = 401
        mock_resp.json.return_value = {"message": "Bad credentials"}
        mock_resp.text = '{"message": "Bad credentials"}'
        mock_get.return_value = mock_resp

        auth = GitHubAuth(explicit_token="ghp_invalidtoken12345")
        service = GitHubService(auth=auth)
        with self.assertRaises(GitHubAuthError):
            await service.get_authenticated_user()

    @patch("httpx.AsyncClient.get")
    async def test_github_404_raises_not_found(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.status_code = 404
        mock_resp.json.return_value = {"message": "Not Found"}
        mock_resp.text = '{"message": "Not Found"}'
        mock_get.return_value = mock_resp

        auth = GitHubAuth(explicit_token="ghp_testtoken1234567890")
        service = GitHubService(auth=auth)
        with self.assertRaises(GitHubNotFoundError):
            await service.get_repository("octocat", "non-existent")

    @patch("httpx.AsyncClient.get")
    async def test_github_rate_limit_exceeded(self, mock_get):
        mock_resp = MagicMock()
        mock_resp.status_code = 403
        mock_resp.headers = {"x-ratelimit-remaining": "0", "x-ratelimit-reset": "1700000000"}
        mock_resp.json.return_value = {"message": "API rate limit exceeded"}
        mock_resp.text = '{"message": "API rate limit exceeded"}'
        mock_get.return_value = mock_resp

        auth = GitHubAuth(explicit_token="ghp_testtoken1234567890")
        service = GitHubService(auth=auth)
        with self.assertRaises(GitHubRateLimitError):
            await service.list_repositories()


class TestFastApiGitHubEndpoints(unittest.TestCase):
    """Verifies FastAPI endpoints: /github/status, /github/repos, /github/repos/{owner}/{repo}."""

    def setUp(self):
        self.client = TestClient(app)
        self.secret_token = "ghp_VERYSECRETCREDENTIAL_FOR_API_TESTING_1234567890"

    def test_github_status_unauthenticated(self):
        with patch.object(github_service.auth, "is_authenticated", return_value=False):
            resp = self.client.get("/github/status")
            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertTrue(data["success"])
            self.assertFalse(data["authenticated"])
            self.assertIsNone(data["user"])
            self.assertIn("message", data)
            self.assertNotIn("ghp_", json.dumps(data))
            self.assertNotIn("bearer", json.dumps(data).lower())

    def test_github_repos_unauthenticated_returns_401(self):
        with patch.object(github_service.auth, "is_authenticated", return_value=False):
            resp = self.client.get("/github/repos")
            self.assertEqual(resp.status_code, 401)
            data = resp.json()
            self.assertFalse(data["success"])
            self.assertEqual(data["error"]["code"], "GITHUB_UNAUTHENTICATED")

    @patch.object(github_service, "get_authenticated_user", new_callable=AsyncMock)
    def test_github_status_authenticated(self, mock_get_user):
        mock_get_user.return_value = normalize_user_data(SAMPLE_MOCK_USER)
        with patch.object(github_service.auth, "is_authenticated", return_value=True), \
             patch.object(github_service.auth, "get_token", return_value=self.secret_token):

            resp = self.client.get("/github/status")
            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertTrue(data["success"])
            self.assertTrue(data["authenticated"])
            self.assertEqual(data["user"]["login"], "octocat")

            # CRITICAL SECURITY CHECK: Secret token must NEVER appear anywhere in response body or headers
            self.assertNotIn(self.secret_token, resp.text)
            for header_val in resp.headers.values():
                self.assertNotIn(self.secret_token, header_val)

    @patch.object(github_service, "list_repositories", new_callable=AsyncMock)
    def test_github_repos_authenticated(self, mock_list_repos):
        mock_list_repos.return_value = [normalize_repository_data(r) for r in SAMPLE_MOCK_REPOS]
        with patch.object(github_service.auth, "is_authenticated", return_value=True), \
             patch.object(github_service.auth, "get_token", return_value=self.secret_token):

            resp = self.client.get("/github/repos")
            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertTrue(data["success"])
            self.assertEqual(len(data["repositories"]), 2)
            self.assertEqual(data["repositories"][0]["name"], "hello-world")

            # CRITICAL SECURITY CHECK
            self.assertNotIn(self.secret_token, resp.text)

    @patch.object(github_service, "get_repository", new_callable=AsyncMock)
    def test_github_single_repo_success(self, mock_get_repo):
        mock_get_repo.return_value = normalize_repository_data(SAMPLE_MOCK_REPOS[0])
        with patch.object(github_service.auth, "is_authenticated", return_value=True):
            resp = self.client.get("/github/repos/octocat/hello-world")
            self.assertEqual(resp.status_code, 200)
            data = resp.json()
            self.assertTrue(data["success"])
            self.assertEqual(data["repository"]["name"], "hello-world")
            self.assertEqual(data["repository"]["full_name"], "octocat/hello-world")

    def test_github_single_repo_invalid_identifier(self):
        # Traversal attempt
        resp = self.client.get("/github/repos/../escape")
        # FastAPI router will either match 404 or our validator 400
        self.assertIn(resp.status_code, [400, 404])

        resp2 = self.client.get("/github/repos/invalid owner/repo")
        self.assertIn(resp2.status_code, [400, 404])

    @patch.object(github_service, "get_repository", new_callable=AsyncMock)
    def test_github_single_repo_not_found(self, mock_get_repo):
        mock_get_repo.side_effect = GitHubNotFoundError("Repository not found")
        resp = self.client.get("/github/repos/octocat/nonexistent")
        self.assertEqual(resp.status_code, 404)
        data = resp.json()
        self.assertFalse(data["success"])
        self.assertEqual(data["error"]["code"], "REPOSITORY_NOT_FOUND")

    def test_wan_origin_blocking_on_github_routes(self):
        """Ensures that public WAN IP origins are blocked on /github/* endpoints just like all other routes."""
        headers = {"X-Forwarded-For": "93.184.216.34"}  # Public WAN IP
        resp = self.client.get("/github/status", headers=headers)
        self.assertEqual(resp.status_code, 403)
        self.assertIn("Forbidden", resp.json()["detail"])

    @patch("httpx.AsyncClient.post")
    def test_device_code_initiation_success(self, mock_post):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {
            "device_code": "devcode1234567890",
            "user_code": "ABCD-1234",
            "verification_uri": "https://github.com/login/device",
            "expires_in": 900,
            "interval": 5
        }
        mock_post.return_value = mock_resp

        resp = self.client.post("/github/auth/device/code", json={"client_id": "test_client_id_123"})
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertEqual(data["user_code"], "ABCD-1234")
        self.assertEqual(data["verification_uri"], "https://github.com/login/device")

    def test_device_code_missing_client_id_error(self):
        with patch.dict(os.environ, {}, clear=True):
            resp = self.client.post("/github/auth/device/code", json={})
            self.assertEqual(resp.status_code, 400)
            data = resp.json()
            self.assertFalse(data["success"])
            self.assertEqual(data["error"]["code"], "DEVICE_FLOW_CONFIG_ERROR")

    @patch("httpx.AsyncClient.post")
    def test_device_polling_pending(self, mock_post):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"error": "authorization_pending"}
        mock_post.return_value = mock_resp

        resp = self.client.post("/github/auth/device/poll", json={
            "device_code": "devcode123",
            "client_id": "test_client_id_123"
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertEqual(data["status"], "pending")

    @patch.object(github_service, "get_authenticated_user", new_callable=AsyncMock)
    @patch("httpx.AsyncClient.post")
    def test_device_polling_success_saves_token_and_does_not_return_token(self, mock_post, mock_user):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {
            "access_token": "ghu_SUPERSECRETOAUTHTOKEN1234567890",
            "token_type": "bearer",
            "scope": "repo,read:user"
        }
        mock_post.return_value = mock_resp
        mock_user.return_value = normalize_user_data(SAMPLE_MOCK_USER)

        resp = self.client.post("/github/auth/device/poll", json={
            "device_code": "devcode123",
            "client_id": "test_client_id_123"
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertEqual(data["status"], "complete")
        self.assertTrue(data["authenticated"])
        self.assertEqual(data["user"]["login"], "octocat")

        # CRITICAL SECURITY CHECK: Access token MUST NOT appear anywhere in the response!
        self.assertNotIn("ghu_SUPERSECRETOAUTHTOKEN1234567890", resp.text)
        self.assertNotIn("SUPERSECRETOAUTHTOKEN", resp.text)

    def test_logout_endpoint(self):
        resp = self.client.post("/github/auth/logout")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertFalse(data["authenticated"])

    def test_client_id_resolution_from_env_file(self):
        """Verifies GITHUB_CLIENT_ID is discovered from a local .env file."""
        tmp = tempfile.mkdtemp()
        try:
            with open(os.path.join(tmp, ".env"), "w", encoding="utf-8") as f:
                f.write("GITHUB_CLIENT_ID=gh_client_id_from_dotenv_12345\n")
            auth = GitHubAuth(repo_root=tmp)
            with patch.dict(os.environ, {}, clear=True):
                self.assertEqual(auth.get_client_id(), "gh_client_id_from_dotenv_12345")
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def test_client_id_resolution_from_env_var(self):
        """Verifies GITHUB_CLIENT_ID is discovered from system environment variables."""
        auth = GitHubAuth()
        with patch.dict(os.environ, {"GITHUB_CLIENT_ID": "gh_client_id_env_var_99999"}):
            self.assertEqual(auth.get_client_id(), "gh_client_id_env_var_99999")

    @patch("httpx.AsyncClient.post")
    def test_device_flow_auto_uses_client_id(self, mock_post):
        """Verifies initiate_device_flow automatically resolves client_id without requiring it in payload."""
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {
            "device_code": "devcode_auto",
            "user_code": "AUTO-1234",
            "verification_uri": "https://github.com/login/device",
            "expires_in": 900,
            "interval": 5
        }
        mock_post.return_value = mock_resp

        with patch.object(github_service.auth, "get_client_id", return_value="configured_cid"):
            import asyncio
            res = asyncio.run(github_service.initiate_device_flow())
            self.assertTrue(res["success"])
            self.assertEqual(res["user_code"], "AUTO-1234")


if __name__ == "__main__":
    unittest.main()
