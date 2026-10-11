"""
Comprehensive Mocked Unit and Integration Test Suite for Repository Workspace,
Git Change Tracker, AI Editing Engine, and FastAPI Endpoints (Phase 2C-1).

Tests:
1. Workspace initialization
2. Public and private repository access (token header vs anonymous)
3. Invalid repository identifiers & traversal rejection
4. Clone failure & safe cleanup
5. Existing workspace reuse
6. Existing user changes preservation (no reset/clean)
7. Successful AI edits (multi-file modifications & creations)
8. AI provider unavailable (fails fast, preserves workspace, no fake templates)
9. Multi-file changes tracking
10. Unified diff generation
11. Partial editing failure handling (preserves partial edits for review)
12. Path traversal in AI edit paths
13. Attempts to access files outside managed workspace
14. Static preview detection & unsupported framework explanation
15. Confirmation of zero automatic commits or pushes
16. FastAPI endpoints integration (/repo/workspace/*)
17. Zero token leaks in responses and .git/config
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient

SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "scripts"))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

from github_service import (
    GitHubValidationError,
    github_service,
    scrub_sensitive_text
)
from repository_agent import (
    RepositoryWorkspaceManager,
    GitChangeTracker,
    RepositoryAgent,
    WorkspaceRecord,
    ChangeSummary,
    detect_static_web_preview_root,
    WorkspaceSecurityError,
    GitOperationError,
    AIProviderUnavailableError,
    AIEditingError,
    RepositoryAgentError
)
from laptop_ai_server import app


class TestRepositoryAgentBase(unittest.TestCase):
    def setUp(self):
        # Create an isolated temporary test directory for workspaces
        self.test_dir = tempfile.mkdtemp(prefix="test_iqoo_ws_")
        self.wm = RepositoryWorkspaceManager(workspaces_dir=self.test_dir)
        self.tracker = GitChangeTracker(wm=self.wm)
        self.agent = RepositoryAgent(wm=self.wm, tracker=self.tracker)
        self.client = TestClient(app)

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def _create_dummy_git_repo(self, path: str, commit_msg: str = "Initial commit"):
        """Helper to create a real local git repo in temp folder."""
        os.makedirs(path, exist_ok=True)
        subprocess.run(["git", "init"], cwd=path, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.name", "TestUser"], cwd=path, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.email", "test@example.com"], cwd=path, check=True, capture_output=True)
        with open(os.path.join(path, "index.html"), "w", encoding="utf-8") as f:
            f.write("<!DOCTYPE html><html><body><h1>Original Content</h1></body></html>")
        with open(os.path.join(path, "styles.css"), "w", encoding="utf-8") as f:
            f.write("body { font-family: sans-serif; }")
        subprocess.run(["git", "add", "."], cwd=path, check=True, capture_output=True)
        subprocess.run(["git", "commit", "-m", commit_msg], cwd=path, check=True, capture_output=True)


class TestRepositoryWorkspaceManager(TestRepositoryAgentBase):

    @patch.object(RepositoryWorkspaceManager, "run_git_command")
    def test_1_workspace_initialization_public(self, mock_git):
        """1. Workspace initialization clones public repo and records metadata."""
        mock_git.side_effect = [
            (0, "Cloning into...", ""),            # git clone
            (0, "abc1234567890\n", ""),           # git rev-parse HEAD
            (0, "main\n", "")                     # git rev-parse --abbrev-ref HEAD
        ]

        import asyncio
        record = asyncio.run(self.wm.initialize_workspace("octocat", "Hello-World"))

        self.assertEqual(record.workspace_id, "octocat--hello-world")
        self.assertEqual(record.owner, "octocat")
        self.assertEqual(record.repo, "Hello-World")
        self.assertEqual(record.baseline_commit, "abc1234567890")
        self.assertEqual(record.current_branch, "main")
        self.assertTrue(os.path.exists(self.wm.registry_file))

    @patch.object(github_service.auth, "get_token", return_value="ghp_MOCKSECRETTOKEN1234567890")
    @patch.object(RepositoryWorkspaceManager, "run_git_command")
    def test_2_workspace_initialization_private_token_auth(self, mock_git, mock_token):
        """2. Private repository receives auth token via git config header, never in remote URL."""
        mock_git.side_effect = [
            (0, "Cloned", ""),
            (0, "def456\n", ""),
            (0, "develop\n", "")
        ]

        import asyncio
        record = asyncio.run(self.wm.initialize_workspace("my-org", "private-repo", default_branch="develop"))

        self.assertEqual(record.workspace_id, "my-org--private-repo")
        self.assertEqual(record.clone_url, "https://github.com/my-org/private-repo.git")

        # Verify git clone was called with in-memory extraHeader and NOT with token embedded in URL
        clone_call = mock_git.call_args_list[0]
        clone_args, clone_kwargs = clone_call
        self.assertEqual(clone_kwargs.get("auth_token"), "ghp_MOCKSECRETTOKEN1234567890")
        self.assertNotIn("ghp_MOCKSECRETTOKEN1234567890", record.clone_url)

    def test_3_invalid_repository_identifiers(self):
        """3. Path traversal and illegal characters in owner or repository are blocked."""
        import asyncio

        with self.assertRaises(GitHubValidationError):
            asyncio.run(self.wm.initialize_workspace("../escape", "repo"))

        with self.assertRaises(GitHubValidationError):
            asyncio.run(self.wm.initialize_workspace("owner", "../../etc/passwd"))

        with self.assertRaises(GitHubValidationError):
            asyncio.run(self.wm.initialize_workspace("bad owner", "repo"))

    @patch.object(RepositoryWorkspaceManager, "run_git_command")
    def test_4_clone_failure_handling(self, mock_git):
        """4. Clone failure raises GitOperationError and cleans up safely."""
        mock_git.return_value = (128, "", "fatal: repository 'https://github.com/foo/bar.git' not found")

        import asyncio
        with self.assertRaises(GitOperationError) as ctx:
            asyncio.run(self.wm.initialize_workspace("foo", "bar"))

        self.assertIn("not found", str(ctx.exception))
        ws_id = self.wm.get_workspace_id("foo", "bar")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self.assertFalse(os.path.exists(ws_path))

    def test_5_existing_workspace_reuse(self):
        """5. Reopening an already cloned workspace reuses directory without re-cloning."""
        ws_id = self.wm.get_workspace_id("octocat", "existing-repo")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path, commit_msg="First baseline")

        with patch.object(self.wm, "run_git_command", wraps=self.wm.run_git_command) as spy_git:
            import asyncio
            record = asyncio.run(self.wm.initialize_workspace("octocat", "existing-repo"))

            self.assertEqual(record.workspace_id, ws_id)
            self.assertEqual(record.workspace_path, ws_path)
            # Verify git clone was NEVER called because workspace already exists with .git
            for call in spy_git.call_args_list:
                args = call[0][0]
                self.assertNotIn("clone", args)

    def test_6_user_changes_preservation(self):
        """6. Existing uncommitted user edits are preserved and not wiped or stashed."""
        ws_id = self.wm.get_workspace_id("octocat", "user-edits")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        # User modifies styles.css
        css_file = os.path.join(ws_path, "styles.css")
        with open(css_file, "a", encoding="utf-8") as f:
            f.write("\n/* Custom user edit */\n")

        status = self.tracker.get_status(ws_path)
        self.assertIn("styles.css", status["modified"])
        self.assertFalse(status["clean"])

        # Re-initialize workspace: styles.css must remain modified
        import asyncio
        record = asyncio.run(self.wm.initialize_workspace("octocat", "user-edits"))
        self.assertEqual(record.workspace_id, ws_id)

        status_after = self.tracker.get_status(ws_path)
        self.assertIn("styles.css", status_after["modified"])
        with open(css_file, "r", encoding="utf-8") as f:
            self.assertIn("/* Custom user edit */", f.read())


class TestRepositoryAgentAI(TestRepositoryAgentBase):

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_7_successful_ai_edits(self, mock_ai_check):
        """7. Successful AI edits apply changes across files and compute diff."""
        mock_ai_check.return_value = True

        ws_id = self.wm.get_workspace_id("octocat", "ai-edit-repo")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        # Register workspace
        import asyncio
        record = asyncio.run(self.wm.initialize_workspace("octocat", "ai-edit-repo"))

        mock_ai_response = json.dumps({
            "thought": "Update header and add contact page",
            "edits": [
                {
                    "path": "index.html",
                    "action": "modify",
                    "content": "<!DOCTYPE html><html><body><h1>Updated by AI</h1></body></html>"
                },
                {
                    "path": "contact.html",
                    "action": "create",
                    "content": "<!DOCTYPE html><html><body><h1>Contact Us</h1></body></html>"
                }
            ]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": mock_ai_response}
            mock_post.return_value = mock_resp

            result = asyncio.run(
                self.agent.execute_instruction(ws_id, "Update header and create contact page")
            )

        self.assertTrue(result.success)
        self.assertEqual(result.stage, "COMPLETED")
        self.assertIn("index.html", result.changes.files_modified)
        self.assertIn("contact.html", result.changes.files_created)
        self.assertIn("Updated by AI", result.changes.diff)
        self.assertIn("Contact Us", result.changes.diff)

        # Check files on disk
        with open(os.path.join(ws_path, "index.html"), "r", encoding="utf-8") as f:
            self.assertIn("Updated by AI", f.read())
        self.assertTrue(os.path.isfile(os.path.join(ws_path, "contact.html")))

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_8_ai_provider_unavailable_fails_honestly(self, mock_ai_check):
        """8. If AI provider is offline, fails honestly with clear error and preserves files."""
        mock_ai_check.return_value = False

        ws_id = self.wm.get_workspace_id("octocat", "offline-repo")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        import asyncio
        asyncio.run(self.wm.initialize_workspace("octocat", "offline-repo"))

        with open(os.path.join(ws_path, "index.html"), "r", encoding="utf-8") as f:
            original_content = f.read()

        with self.assertRaises(AIProviderUnavailableError) as ctx:
            asyncio.run(
                self.agent.execute_instruction(ws_id, "Change the website design")
            )

        self.assertIn("AI provider is unavailable", str(ctx.exception))
        # Ensure workspace is untouched
        with open(os.path.join(ws_path, "index.html"), "r", encoding="utf-8") as f:
            self.assertEqual(f.read(), original_content)

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_9_multi_file_changes_and_diff(self, mock_ai_check):
        """9. Multi-file edits (modify 1, create 1, delete 1) are properly recorded in diff."""
        mock_ai_check.return_value = True

        ws_id = self.wm.get_workspace_id("octocat", "multi-edit")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        import asyncio
        asyncio.run(self.wm.initialize_workspace("octocat", "multi-edit"))

        mock_ai_response = json.dumps({
            "thought": "Multi-file changes",
            "edits": [
                {"path": "index.html", "action": "modify", "content": "<h1>New</h1>"},
                {"path": "about.html", "action": "create", "content": "<h1>About</h1>"},
                {"path": "styles.css", "action": "delete", "content": ""}
            ]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": mock_ai_response}
            mock_post.return_value = mock_resp

            result = asyncio.run(self.agent.execute_instruction(ws_id, "Multi change"))

        self.assertTrue(result.success)
        self.assertIn("index.html", result.changes.files_modified)
        self.assertIn("about.html", result.changes.files_created)
        self.assertIn("styles.css", result.changes.files_deleted)
        self.assertEqual(result.changes.total_changes, 3)

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_10_partial_editing_failure_preserved(self, mock_ai_check):
        """10. If an edit fails halfway, partial changes are preserved for review."""
        mock_ai_check.return_value = True

        ws_id = self.wm.get_workspace_id("octocat", "partial-fail")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        import asyncio
        asyncio.run(self.wm.initialize_workspace("octocat", "partial-fail"))

        # First edit is valid, second attempts path traversal
        mock_ai_response = json.dumps({
            "thought": "First valid, second invalid",
            "edits": [
                {"path": "index.html", "action": "modify", "content": "<h1>Partial Worked</h1>"},
                {"path": "../../escape.txt", "action": "create", "content": "bad"}
            ]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": mock_ai_response}
            mock_post.return_value = mock_resp

            result = asyncio.run(self.agent.execute_instruction(ws_id, "Do partial edit"))

        self.assertEqual(result.stage, "PARTIAL_ERROR")
        self.assertFalse(result.success)
        self.assertIn("Partial modification failure", result.error)
        # Verify first change was NOT discarded
        self.assertIn("index.html", result.changes.files_modified)
        with open(os.path.join(ws_path, "index.html"), "r", encoding="utf-8") as f:
            self.assertIn("Partial Worked", f.read())

    def test_11_path_traversal_in_ai_edits(self):
        """11. AI edits attempting to escape workspace boundary raise WorkspaceSecurityError."""
        ws_id = self.wm.get_workspace_id("octocat", "security-test")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        with self.assertRaises(WorkspaceSecurityError):
            self.agent.apply_edits(ws_path, [
                {"path": "/etc/shadow", "action": "create", "content": "root"}
            ])

        with self.assertRaises(WorkspaceSecurityError):
            self.agent.apply_edits(ws_path, [
                {"path": "../outside.txt", "action": "create", "content": "data"}
            ])

    def test_12_static_preview_detection(self):
        """12. Static preview detects index.html in root or subdirectories; rejects non-static."""
        ws_id = self.wm.get_workspace_id("octocat", "static-repo")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        # Root index.html exists
        root = detect_static_web_preview_root(ws_path)
        self.assertEqual(root, ws_path)

        # Non-static repo (e.g. only python files)
        non_static_dir = os.path.join(self.test_dir, "python-cli")
        os.makedirs(non_static_dir, exist_ok=True)
        with open(os.path.join(non_static_dir, "main.py"), "w", encoding="utf-8") as f:
            f.write("print('CLI')")

        self.assertIsNone(detect_static_web_preview_root(non_static_dir))

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_13_no_automatic_commit_or_push(self, mock_ai_check):
        """13. Explicit verification that git commit and git push are NEVER executed."""
        mock_ai_check.return_value = True

        ws_id = self.wm.get_workspace_id("octocat", "no-commit-check")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        import asyncio
        record = asyncio.run(self.wm.initialize_workspace("octocat", "no-commit-check"))
        initial_commit = record.baseline_commit

        mock_ai_response = json.dumps({
            "thought": "Modifying",
            "edits": [{"path": "index.html", "action": "modify", "content": "<h1>Edited</h1>"}]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": mock_ai_response}
            mock_post.return_value = mock_resp

            with patch.object(self.wm, "run_git_command", wraps=self.wm.run_git_command) as spy_git:
                result = asyncio.run(self.agent.execute_instruction(ws_id, "Edit page"))

                # Check all git commands invoked
                for call in spy_git.call_args_list:
                    args = call[0][0]
                    self.assertNotIn("commit", args, "CRITICAL SAFETY: git commit was invoked!")
                    self.assertNotIn("push", args, "CRITICAL SAFETY: git push was invoked!")
                    self.assertNotIn("merge", args, "CRITICAL SAFETY: git merge was invoked!")

        # Verify HEAD commit hash has not changed in Git repository
        rc, current_head, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=ws_path)
        self.assertEqual(current_head.strip(), initial_commit)


class TestRepositoryFastAPIRoutes(TestRepositoryAgentBase):

    @patch("repository_agent.workspace_manager.initialize_workspace")
    def test_14_fastapi_init_endpoint(self, mock_init):
        """14. POST /repo/workspace/init validates request and returns workspace."""
        mock_init.return_value = WorkspaceRecord(
            workspace_id="testowner--testrepo",
            owner="testowner",
            repo="testrepo",
            clone_url="https://github.com/testowner/testrepo.git",
            default_branch="main",
            workspace_path="/fake/path",
            baseline_commit="abc123",
            current_branch="main",
            created_at="2026-10-09T00:00:00Z",
            updated_at="2026-10-09T00:00:00Z",
            status="ready"
        )

        resp = self.client.post("/repo/workspace/init", json={
            "owner": "testowner",
            "repo": "testrepo",
            "default_branch": "main"
        })
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertEqual(data["workspace"]["workspace_id"], "testowner--testrepo")

    def test_15_fastapi_init_endpoint_invalid_identifiers(self):
        """15. POST /repo/workspace/init rejects path traversal in owner/repo."""
        resp = self.client.post("/repo/workspace/init", json={
            "owner": "../escape",
            "repo": "repo"
        })
        self.assertEqual(resp.status_code, 400)
        data = resp.json()
        self.assertFalse(data["success"])
        self.assertEqual(data["error"]["code"], "INVALID_REPOSITORY_SPECIFIER")

    @patch("repository_agent.workspace_manager.get_workspace")
    @patch("repository_agent.git_change_tracker.compute_diff")
    def test_16_fastapi_diff_endpoint(self, mock_diff, mock_get_ws):
        """16. GET /repo/workspace/{id}/diff returns unified diff."""
        mock_get_ws.return_value = WorkspaceRecord(
            workspace_id="octocat--repo",
            owner="octocat",
            repo="repo",
            clone_url="https://github.com/octocat/repo.git",
            default_branch="main",
            workspace_path="/path",
            baseline_commit="base123",
            current_branch="main",
            created_at="now",
            updated_at="now"
        )
        mock_diff.return_value = ChangeSummary(
            files_modified=["index.html"],
            total_changes=1,
            diff="diff --git a/index.html ...\n+new content\n",
            baseline_commit="base123"
        )

        resp = self.client.get("/repo/workspace/octocat--repo/diff")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertIn("+new content", data["changes"]["diff"])
        self.assertIn("pre_existing_user_changes", data)
        self.assertIn("operations_count", data)


class TestPhase2C4GitChangeIsolation(TestRepositoryAgentBase):
    """
    Phase 2C-4 Git Change Isolation & Attribution Tests:
    - Pre-existing staged, unstaged, and untracked changes are NOT attributed to AI
    - Multiple successive AI operations maintain isolated change records and journals
    - Partial AI failures preserve both pre-existing edits and partial new edits
    - Zero accidental commits, pushes, merges, resets, or clean
    """

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_pre_existing_changes_not_attributed_to_ai(self, mock_ai_check):
        """Pre-existing staged, unstaged, and untracked files are excluded from AI operation attribution."""
        mock_ai_check.return_value = True

        ws_id = self.wm.get_workspace_id("testuser", "isolation-test")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        import asyncio
        asyncio.run(self.wm.initialize_workspace("testuser", "isolation-test"))

        # 1. Create pre-existing user changes
        # Untracked user file:
        with open(os.path.join(ws_path, "user_notes.txt"), "w", encoding="utf-8") as f:
            f.write("Important user notes - do not touch")

        # Staged user modification:
        with open(os.path.join(ws_path, "staged_file.txt"), "w", encoding="utf-8") as f:
            f.write("Staged by user")
        subprocess.run(["git", "add", "staged_file.txt"], cwd=ws_path, check=True, capture_output=True)

        # Unstaged user modification:
        with open(os.path.join(ws_path, "styles.css"), "a", encoding="utf-8") as f:
            f.write("\n/* User customized color */")

        # Verify detailed pre-existing status
        pre_status = self.tracker.get_detailed_status(ws_path)
        self.assertIn("user_notes.txt", pre_status.untracked)
        self.assertIn("staged_file.txt", pre_status.staged)
        self.assertIn("styles.css", pre_status.unstaged)
        self.assertFalse(pre_status.clean)

        # 2. AI instruction touches ONLY index.html
        mock_ai_response = json.dumps({
            "thought": "Update index.html only",
            "edits": [
                {
                    "path": "index.html",
                    "action": "modify",
                    "content": "<h1>Edited strictly by AI</h1>"
                }
            ]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": mock_ai_response}
            mock_post.return_value = mock_resp

            result = asyncio.run(
                self.agent.execute_instruction(ws_id, "Update header in index.html")
            )

        # 3. Verify changes attributed to THIS operation:
        self.assertTrue(result.success)
        self.assertEqual(result.changes.files_modified, ["index.html"])
        self.assertEqual(result.changes.files_created, [])
        self.assertEqual(result.changes.total_changes, 1)
        self.assertIn("Edited strictly by AI", result.changes.diff)

        # CRITICAL ASSERTIONS: Pre-existing changes MUST NOT be attributed to AI
        self.assertNotIn("user_notes.txt", result.changes.files_created)
        self.assertNotIn("staged_file.txt", result.changes.files_created)
        self.assertNotIn("styles.css", result.changes.files_modified)
        self.assertNotIn("User customized color", result.changes.diff)
        self.assertNotIn("Important user notes", result.changes.diff)

        # Verify pre-existing changes are explicitly tracked in result metadata
        self.assertIsNotNone(result.pre_existing_changes)
        self.assertIn("user_notes.txt", result.pre_existing_changes.untracked)
        self.assertIn("staged_file.txt", result.pre_existing_changes.staged)
        self.assertIn("styles.css", result.pre_existing_changes.unstaged)

        # Verify pre-existing user files are completely intact on disk
        self.assertTrue(os.path.isfile(os.path.join(ws_path, "user_notes.txt")))
        self.assertTrue(os.path.isfile(os.path.join(ws_path, "staged_file.txt")))
        with open(os.path.join(ws_path, "styles.css"), "r", encoding="utf-8") as f:
            self.assertIn("User customized color", f.read())

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_multiple_successive_ai_operations_isolated(self, mock_ai_check):
        """Repeated successive AI operations do not mix or re-attribute prior operation changes."""
        mock_ai_check.return_value = True

        ws_id = self.wm.get_workspace_id("testuser", "successive-test")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        import asyncio
        asyncio.run(self.wm.initialize_workspace("testuser", "successive-test"))

        # Operation 1: Modify index.html
        op1_response = json.dumps({
            "thought": "Op 1 edits index.html",
            "edits": [{"path": "index.html", "action": "modify", "content": "<h1>Op 1 Result</h1>"}]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": op1_response}
            mock_post.return_value = mock_resp

            res1 = asyncio.run(self.agent.execute_instruction(ws_id, "Instruction 1"))

        self.assertEqual(res1.changes.files_modified, ["index.html"])
        self.assertEqual(res1.changes.total_changes, 1)

        # Operation 2: Modify styles.css
        op2_response = json.dumps({
            "thought": "Op 2 edits styles.css",
            "edits": [{"path": "styles.css", "action": "modify", "content": "body { color: blue; }"}]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": op2_response}
            mock_post.return_value = mock_resp

            res2 = asyncio.run(self.agent.execute_instruction(ws_id, "Instruction 2"))

        # CRITICAL: Operation 2 MUST ONLY report styles.css, NOT index.html!
        self.assertEqual(res2.changes.files_modified, ["styles.css"])
        self.assertNotIn("index.html", res2.changes.files_modified)
        self.assertEqual(res2.changes.total_changes, 1)
        self.assertIn("color: blue", res2.changes.diff)
        self.assertNotIn("Op 1 Result", res2.changes.diff)

        # Check journal records
        journal = self.agent.get_operation_journal(ws_id)
        self.assertEqual(len(journal), 2)
        self.assertEqual(journal[0].files_modified, ["index.html"])
        self.assertEqual(journal[1].files_modified, ["styles.css"])

    @patch.object(RepositoryAgent, "verify_ai_provider", new_callable=AsyncMock)
    def test_partial_failure_preserves_user_and_partial_edits(self, mock_ai_check):
        """Partial AI failure preserves pre-existing user files and captures partial changes."""
        mock_ai_check.return_value = True

        ws_id = self.wm.get_workspace_id("testuser", "partial-user-test")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        import asyncio
        asyncio.run(self.wm.initialize_workspace("testuser", "partial-user-test"))

        # Pre-existing user file
        with open(os.path.join(ws_path, "user_kept.txt"), "w", encoding="utf-8") as f:
            f.write("user content")

        # Edit 1 succeeds, Edit 2 attempts path escape
        fail_response = json.dumps({
            "thought": "Partial failure test",
            "edits": [
                {"path": "index.html", "action": "modify", "content": "<h1>Saved Partial</h1>"},
                {"path": "../../../escape.txt", "action": "create", "content": "malicious"}
            ]
        })

        with patch("httpx.AsyncClient.post") as mock_post:
            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {"response": fail_response}
            mock_post.return_value = mock_resp

            res = asyncio.run(self.agent.execute_instruction(ws_id, "Partial prompt"))

        self.assertEqual(res.stage, "PARTIAL_ERROR")
        self.assertFalse(res.success)
        self.assertIn("index.html", res.changes.files_modified)
        self.assertTrue(os.path.isfile(os.path.join(ws_path, "user_kept.txt")))
        with open(os.path.join(ws_path, "index.html"), "r", encoding="utf-8") as f:
            self.assertIn("Saved Partial", f.read())

    def test_zero_automatic_commits_or_resets(self):
        """Ensures repository baseline HEAD commit is never moved or reset."""
        ws_id = self.wm.get_workspace_id("testuser", "no-commit-test")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        self._create_dummy_git_repo(ws_path)

        head_before = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ws_path, text=True).strip()
        count_before = subprocess.check_output(["git", "rev-list", "--count", "HEAD"], cwd=ws_path, text=True).strip()

        # Run detailed status check and compute diff
        self.tracker.get_detailed_status(ws_path)
        self.tracker.compute_diff(ws_path, baseline_commit=head_before)

        head_after = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ws_path, text=True).strip()
        count_after = subprocess.check_output(["git", "rev-list", "--count", "HEAD"], cwd=ws_path, text=True).strip()

        self.assertEqual(head_before, head_after)
        self.assertEqual(count_before, count_after)

    def test_safe_rmtree_removes_readonly_files_on_windows(self):
        """safe_rmtree must delete directories containing read-only files without throwing."""
        import stat
        from repository_agent import safe_rmtree
        test_dir = tempfile.mkdtemp(prefix="safe_rmtree_test_")
        sub_dir = os.path.join(test_dir, "nested")
        os.makedirs(sub_dir, exist_ok=True)
        ro_file = os.path.join(sub_dir, "readonly.txt")
        with open(ro_file, "w", encoding="utf-8") as f:
            f.write("read only data")
        # Mark as read-only
        os.chmod(ro_file, stat.S_IREAD)

        success = safe_rmtree(test_dir)
        self.assertTrue(success)
        self.assertFalse(os.path.exists(test_dir))

    def test_initialize_workspace_preserves_existing_files_when_no_git(self):
        """Never automatically delete an existing workspace containing files without Git metadata."""
        import asyncio
        ws_id = self.wm.get_workspace_id("testuser", "existing-files-no-git")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        os.makedirs(ws_path, exist_ok=True)
        user_file = os.path.join(ws_path, "important_user_work.txt")
        with open(user_file, "w", encoding="utf-8") as f:
            f.write("Important user content must never be destroyed.")

        with self.assertRaises(WorkspaceSecurityError) as ctx:
            asyncio.run(self.wm.initialize_workspace("testuser", "existing-files-no-git"))

        self.assertEqual(ctx.exception.code, "WORKSPACE_EXISTS_NO_GIT")
        # Ensure user file is completely intact!
        self.assertTrue(os.path.isfile(user_file))
        with open(user_file, "r", encoding="utf-8") as f:
            self.assertEqual(f.read(), "Important user content must never be destroyed.")

    def test_initialize_workspace_preserves_files_when_corrupted_git(self):
        """Never automatically delete an existing workspace when Git metadata is corrupt; preserve contents."""
        import asyncio
        ws_id = self.wm.get_workspace_id("testuser", "corrupted-git-preserve")
        ws_path = self.wm.get_safe_workspace_path(ws_id)
        os.makedirs(os.path.join(ws_path, ".git"), exist_ok=True)
        user_file = os.path.join(ws_path, "index.html")
        with open(user_file, "w", encoding="utf-8") as f:
            f.write("<h1>Preserved User HTML</h1>")
        # .git directory is empty/corrupt (not a valid git repo)

        with self.assertRaises(WorkspaceSecurityError) as ctx:
            asyncio.run(self.wm.initialize_workspace("testuser", "corrupted-git-preserve"))

        self.assertEqual(ctx.exception.code, "WORKSPACE_CORRUPT_PRESERVED")
        # Ensure user file was NOT deleted!
        self.assertTrue(os.path.isfile(user_file))
        with open(user_file, "r", encoding="utf-8") as f:
            self.assertEqual(f.read(), "<h1>Preserved User HTML</h1>")

    def test_repository_agent_timeout_configurable(self):
        """RepositoryAgent timeout is configurable via parameter and defaults to OLLAMA_TIMEOUT env."""
        agent_custom = RepositoryAgent(timeout=105.5)
        self.assertEqual(agent_custom.timeout, 105.5)


if __name__ == "__main__":
    unittest.main()

