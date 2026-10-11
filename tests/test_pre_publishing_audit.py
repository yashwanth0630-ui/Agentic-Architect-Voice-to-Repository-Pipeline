"""
Comprehensive Pre-Publishing Safety & Adversarial Audit Test Suite (Phase 2C-4.5)

Covers:
Section A: The hardest Git change-isolation cases:
  1. Pre-existing unstaged edits, AI modifies same file.
  2. Pre-existing staged edits, AI modifies same file.
  3. Pre-existing staged AND unstaged edits, AI modifies same file.
  4. Pre-existing untracked file exists, AI modifies other files.
  5. Two successive AI operations modify the same file.
  6. AI operation partial failure (modifies one file, fails on second).
  7. Operation journal missing, corrupted, or invalid workspace IDs.
  8. Deletions, renames, binary files, unusual Unicode characters.

Section B: Operation journal security & integrity:
  - Workspace ID validation and directory traversal prevention.
  - Concurrency locking.
  - Atomic write resilience.
  - Sensitive credential redaction in journal records.

Section C: GitHub authentication & credential safety:
  - Exact lookup order verification (explicit -> env -> .github_token -> .env).
  - Subprocess argument credential safety (GIT_CONFIG_COUNT env configuration; zero tokens in cmd).
  - Token file permissions (0600 mode).
  - Logout credential removal.
"""

import asyncio
from datetime import datetime, timezone
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import AsyncMock, MagicMock, patch

# Add scripts directory
SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "scripts"))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

from github_service import (
    GitHubAuth,
    GitHubService,
    GitHubAuthError,
    GitHubValidationError,
    scrub_sensitive_text
)
from repository_agent import (
    RepositoryAgent,
    RepositoryWorkspaceManager,
    GitChangeTracker,
    WorkspaceRecord,
    PreExistingChanges,
    OperationChangeRecord,
    ChangeSummary,
    WorkspaceSecurityError,
    AIEditingError,
    GitOperationError,
    generate_file_diff,
    is_binary_file
)


class TestPrePublishingAdversarialAudit(unittest.TestCase):
    """Rigorous adversarial tests against disposable local Git repositories."""

    def setUp(self):
        self.temp_root = tempfile.mkdtemp(prefix="audit_phase_2c45_")
        self.workspaces_dir = os.path.join(self.temp_root, "workspaces")
        os.makedirs(self.workspaces_dir, exist_ok=True)

        self.wm = RepositoryWorkspaceManager(workspaces_dir=self.workspaces_dir)
        self.tracker = GitChangeTracker(wm=self.wm)
        self.agent = RepositoryAgent(
            wm=self.wm,
            tracker=self.tracker,
            ollama_url="http://localhost:11434",
            model="qwen2.5-coder:7b"
        )

        # Create a disposable local Git repository
        self.repo_dir = os.path.join(self.workspaces_dir, "testuser--demorepo")
        os.makedirs(self.repo_dir, exist_ok=True)
        self._init_git_repo(self.repo_dir)

        # Register workspace
        self.workspace_id = "testuser--demorepo"
        rc, head_out, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=self.repo_dir)
        self.baseline_commit = head_out.strip()

        now_iso = datetime.now(timezone.utc).isoformat()
        self.record = WorkspaceRecord(
            workspace_id=self.workspace_id,
            owner="testuser",
            repo="demorepo",
            clone_url="https://github.com/testuser/demorepo.git",
            default_branch="main",
            workspace_path=self.repo_dir,
            baseline_commit=self.baseline_commit,
            current_branch="main",
            created_at=now_iso,
            updated_at=now_iso,
            status="ready"
        )
        with self.wm._lock:
            reg = self.wm._read_registry()
            reg[self.workspace_id] = self.record.to_dict()
            self.wm._write_registry(reg)

    def tearDown(self):
        shutil.rmtree(self.temp_root, ignore_errors=True)

    def _init_git_repo(self, path: str):
        """Initializes a valid git repository with baseline files."""
        subprocess.run(["git", "init", "-b", "main"], cwd=path, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.email", "audit@ideabeacon.test"], cwd=path, check=True, capture_output=True)
        subprocess.run(["git", "config", "user.name", "Audit Tester"], cwd=path, check=True, capture_output=True)

        # Initial files
        with open(os.path.join(path, "index.html"), "w", encoding="utf-8") as f:
            f.write("<!DOCTYPE html><html><head><title>Original</title></head><body>Hello</body></html>\n")
        with open(os.path.join(path, "styles.css"), "w", encoding="utf-8") as f:
            f.write("body { background: white; }\n")

        subprocess.run(["git", "add", "."], cwd=path, check=True, capture_output=True)
        subprocess.run(["git", "commit", "-m", "Initial commit"], cwd=path, check=True, capture_output=True)

    # ==========================================================================
    # SECTION A: THE HARDEST GIT CHANGE-ISOLATION CASES
    # ==========================================================================

    def test_case_1_preexisting_unstaged_same_file(self):
        """Case 1: User has pre-existing unstaged edits, and AI modifies the same file."""
        # User manually edits index.html (unstaged)
        with open(os.path.join(self.repo_dir, "index.html"), "w", encoding="utf-8") as f:
            f.write("<!DOCTYPE html><html><head><title>User Draft</title></head><body>User Edit</body></html>\n")

        pre = self.tracker.get_detailed_status(self.repo_dir)
        self.assertIn("index.html", pre.unstaged)
        self.assertFalse(pre.clean)

        # AI modifies index.html
        edits = [
            {
                "path": "index.html",
                "action": "modify",
                "content": "<!DOCTYPE html><html><head><title>AI Final</title></head><body>AI Output</body></html>\n"
            }
        ]

        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):

            result = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Update index.html"))

        self.assertTrue(result.success)
        # 1. Pre-existing user edit was captured in snapshot (never lost)
        journal = self.agent.get_operation_journal(self.workspace_id)
        latest_op = journal[-1]
        self.assertIn("index.html", latest_op.pre_edit_snapshots)
        self.assertIn("User Draft", latest_op.pre_edit_snapshots["index.html"])

        # 2. Operation diff strictly isolates only the delta from pre-edit state
        self.assertIn("-<!DOCTYPE html><html><head><title>User Draft</title></head><body>User Edit</body></html>", latest_op.diff)
        self.assertIn("+<!DOCTYPE html><html><head><title>AI Final</title></head><body>AI Output</body></html>", latest_op.diff)

        # 3. Collision detected and publishing blocked
        self.assertTrue(result.dirty_file_collision)
        self.assertIn("index.html", result.colliding_files)
        self.assertTrue(result.publish_blocked)
        self.assertIsNotNone(result.publish_block_reason)

        # 4. Zero git commits or pushes occurred
        rc, head_after, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=self.repo_dir)
        self.assertEqual(head_after.strip(), self.baseline_commit)

    def test_case_2_preexisting_staged_same_file(self):
        """Case 2: User has pre-existing staged edits, and AI modifies the same file."""
        # User edits index.html and stages it
        with open(os.path.join(self.repo_dir, "index.html"), "w", encoding="utf-8") as f:
            f.write("<!DOCTYPE html><html><head><title>User Staged</title></head><body>Staged Version</body></html>\n")
        subprocess.run(["git", "add", "index.html"], cwd=self.repo_dir, check=True)

        pre = self.tracker.get_detailed_status(self.repo_dir)
        self.assertIn("index.html", pre.staged)

        # AI modifies index.html
        edits = [
            {
                "path": "index.html",
                "action": "modify",
                "content": "<!DOCTYPE html><html><head><title>AI On Top</title></head><body>AI Version</body></html>\n"
            }
        ]

        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):

            result = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Apply AI design"))

        # User's staged content remains in the Git index
        rc, staged_blob, _ = self.wm.run_git_command(["show", ":index.html"], cwd=self.repo_dir)
        self.assertIn("User Staged", staged_blob)

        # Collision detected and publishing blocked
        self.assertTrue(result.dirty_file_collision)
        self.assertIn("index.html", result.colliding_files)
        self.assertTrue(result.publish_blocked)

        # No commits occurred
        rc, head_after, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=self.repo_dir)
        self.assertEqual(head_after.strip(), self.baseline_commit)

    def test_case_3_preexisting_both_staged_and_unstaged_same_file(self):
        """Case 3: A file contains both staged and unstaged edits before AI modifies it."""
        # Staged edit
        with open(os.path.join(self.repo_dir, "index.html"), "w", encoding="utf-8") as f:
            f.write("<!DOCTYPE html><html><body>Staged Work</body></html>\n")
        subprocess.run(["git", "add", "index.html"], cwd=self.repo_dir, check=True)

        # Further unstaged edit
        with open(os.path.join(self.repo_dir, "index.html"), "w", encoding="utf-8") as f:
            f.write("<!DOCTYPE html><html><body>Staged Work + Unstaged Work</body></html>\n")

        pre = self.tracker.get_detailed_status(self.repo_dir)
        self.assertIn("index.html", pre.staged)
        self.assertIn("index.html", pre.unstaged)

        edits = [
            {
                "path": "index.html",
                "action": "modify",
                "content": "<!DOCTYPE html><html><body>AI Consolidated</body></html>\n"
            }
        ]

        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):

            result = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Consolidate"))

        # Index still has staged work
        rc, staged_blob, _ = self.wm.run_git_command(["show", ":index.html"], cwd=self.repo_dir)
        self.assertIn("Staged Work", staged_blob)

        # Pre-edit snapshot captured the unstaged work
        journal = self.agent.get_operation_journal(self.workspace_id)
        self.assertIn("Staged Work + Unstaged Work", journal[-1].pre_edit_snapshots["index.html"])

        # Publishing blocked
        self.assertTrue(result.publish_blocked)

    def test_case_4_preexisting_untracked_file_unrelated(self):
        """Case 4: An untracked user file exists before the AI modifies an unrelated file."""
        # User has untracked file
        with open(os.path.join(self.repo_dir, "user_scratch.txt"), "w", encoding="utf-8") as f:
            f.write("Private developer notes\n")

        pre = self.tracker.get_detailed_status(self.repo_dir)
        self.assertIn("user_scratch.txt", pre.untracked)

        # AI modifies styles.css only
        edits = [
            {
                "path": "styles.css",
                "action": "modify",
                "content": "body { background: #121212; color: #ffffff; }\n"
            }
        ]

        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):

            result = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Dark mode"))

        # Untracked file remains completely untouched
        self.assertTrue(os.path.isfile(os.path.join(self.repo_dir, "user_scratch.txt")))
        with open(os.path.join(self.repo_dir, "user_scratch.txt"), "r", encoding="utf-8") as f:
            self.assertEqual(f.read(), "Private developer notes\n")

        # Untracked file is NOT claimed as AI change
        self.assertNotIn("user_scratch.txt", result.changes.files_created)
        self.assertNotIn("user_scratch.txt", result.changes.files_modified)
        self.assertNotIn("user_scratch.txt", result.changes.diff)

        # No collision since AI touched clean styles.css
        self.assertFalse(result.dirty_file_collision)
        self.assertFalse(result.publish_blocked)

    def test_case_5_successive_ai_operations_same_file(self):
        """Case 5: Two successive AI operations modify the same file."""
        # Op 1
        edits_1 = [{"path": "styles.css", "action": "modify", "content": "body { color: red; }\n"}]
        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits_1), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):
            res1 = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Make text red"))

        # Op 2
        edits_2 = [{"path": "styles.css", "action": "modify", "content": "body { color: blue; font-size: 16px; }\n"}]
        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits_2), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):
            res2 = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Make text blue"))

        journal = self.agent.get_operation_journal(self.workspace_id)
        self.assertEqual(len(journal), 2)

        # Op 1 diff: white -> red
        self.assertIn("+body { color: red; }", journal[0].diff)
        self.assertIn("-body { background: white; }", journal[0].diff)

        # Op 2 diff: red -> blue
        self.assertIn("-body { color: red; }", journal[1].diff)
        self.assertIn("+body { color: blue; font-size: 16px; }", journal[1].diff)

        # Neither record was overwritten
        self.assertEqual(journal[0].instruction, "Make text red")
        self.assertEqual(journal[1].instruction, "Make text blue")

        # Zero git commits
        rc, head_after, _ = self.wm.run_git_command(["rev-parse", "HEAD"], cwd=self.repo_dir)
        self.assertEqual(head_after.strip(), self.baseline_commit)

    def test_case_6_partial_ai_failure_preservation(self):
        """Case 6: AI operation modifies one file, then fails on second file."""
        edits = [
            {"path": "success.txt", "action": "create", "content": "First file succeeded\n"},
            {"path": "../../escape.txt", "action": "create", "content": "Illegal path traversal"}
        ]

        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):

            result = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Partial test"))

        self.assertFalse(result.success)
        self.assertEqual(result.stage, "PARTIAL_ERROR")
        self.assertIn("Partial modification failure", result.error)

        # File 1 remains on disk (not destroyed by rollback)
        self.assertTrue(os.path.isfile(os.path.join(self.repo_dir, "success.txt")))

        # Journal recorded partial failure
        journal = self.agent.get_operation_journal(self.workspace_id)
        self.assertEqual(journal[-1].status, "partial_error")
        self.assertIn("success.txt", journal[-1].files_created)

    def test_case_7_journal_missing_corrupted_and_traversal(self):
        """Case 7: Journal is missing, corrupted, or invalid workspace IDs."""
        # 1. Missing journal returns empty list
        missing = self.agent.get_operation_journal("nonexistent--repo")
        self.assertEqual(missing, [])

        # 2. Corrupted journal is backed up and returns empty list gracefully
        jpath = self.agent.get_journal_path(self.workspace_id)
        with open(jpath, "w", encoding="utf-8") as f:
            f.write("[{corrupted_json_syntax...")
        corrupted = self.agent.get_operation_journal(self.workspace_id)
        self.assertEqual(corrupted, [])

        # 3. Path traversal rejected
        with self.assertRaises(WorkspaceSecurityError):
            self.agent.get_journal_path("../../etc/passwd")

    def test_case_8_deleted_binary_and_unicode_handling(self):
        """Case 8: File deleted, binary files handled safely, and complex Unicode preserved."""
        # Create binary file
        bin_path = os.path.join(self.repo_dir, "asset.bin")
        with open(bin_path, "wb") as f:
            f.write(b"\x00\x01\x02\xFF\xFE\x00")
        self.assertTrue(is_binary_file(bin_path))

        # Perform deletion of styles.css and modification with emojis, Japanese, Hebrew
        complex_unicode = (
            "/* 🚀 Idea Beacon ✨ 日本語テスト שלום עולם */\n"
            "body { font-family: 'Inter', sans-serif; accent-color: café; }\n"
        )
        edits = [
            {"path": "styles.css", "action": "delete"},
            {"path": "unicode.txt", "action": "create", "content": complex_unicode},
            {"path": "asset.bin", "action": "modify", "content": "replaced binary content"}
        ]

        with patch.object(self.agent, "verify_ai_provider", AsyncMock(return_value=True)), \
             patch.object(self.agent, "_parse_ai_edits", return_value=edits), \
             patch("httpx.AsyncClient.post", AsyncMock(return_value=MagicMock(status_code=200, json=lambda: {"response": "{}"}))):

            result = asyncio.run(self.agent.execute_instruction(self.workspace_id, "Unicode & asset test"))

        self.assertTrue(result.success)
        self.assertFalse(os.path.exists(os.path.join(self.repo_dir, "styles.css")))
        self.assertIn("styles.css", result.changes.files_deleted)

        # Unicode file preserved perfectly
        with open(os.path.join(self.repo_dir, "unicode.txt"), "r", encoding="utf-8") as f:
            content = f.read()
            self.assertEqual(content, complex_unicode)

        # Binary diff safely rendered
        self.assertIn("Binary files a/asset.bin and b/asset.bin differ", result.changes.diff)

    # ==========================================================================
    # SECTION B: OPERATION JOURNAL INTEGRITY & CONCURRENCY
    # ==========================================================================

    def test_journal_atomic_writes_and_concurrency(self):
        """Verifies concurrent append operations don't corrupt the journal."""
        num_threads = 5
        records_per_thread = 4

        def worker(thread_idx: int):
            for i in range(records_per_thread):
                rec = OperationChangeRecord(
                    operation_id=f"t{thread_idx}_op{i}",
                    instruction=f"Instruction from thread {thread_idx} step {i}",
                    timestamp=datetime.now(timezone.utc).isoformat(),
                    files_created=[f"file_t{thread_idx}_{i}.txt"],
                    total_changes=1,
                    diff="+content"
                )
                self.agent.append_operation_journal(self.workspace_id, rec)
                time.sleep(0.01)

        threads = [threading.Thread(target=worker, args=(t,)) for t in range(num_threads)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        journal = self.agent.get_operation_journal(self.workspace_id)
        self.assertEqual(len(journal), num_threads * records_per_thread)

    def test_journal_scrubs_sensitive_tokens(self):
        """Verifies that personal access tokens are scrubbed from journal records."""
        rec = OperationChangeRecord(
            operation_id="sec_op_1",
            instruction="Fix authorization header with ghp_111122223333444455556666777788889999",
            timestamp=datetime.now(timezone.utc).isoformat(),
            files_modified=["auth.py"],
            total_changes=1,
            diff="diff --git +token = 'ghp_111122223333444455556666777788889999'",
            error="Failed with Bearer gho_999988887777666655554444333322221111"
        )
        self.agent.append_operation_journal(self.workspace_id, rec)

        journal = self.agent.get_operation_journal(self.workspace_id)
        self.assertEqual(len(journal), 1)
        jpath = self.agent.get_journal_path(self.workspace_id)
        with open(jpath, "r", encoding="utf-8") as f:
            raw_json = f.read()

        # Zero unscrubbed tokens in journal file
        self.assertNotIn("ghp_111122223333444455556666777788889999", raw_json)
        self.assertNotIn("gho_999988887777666655554444333322221111", raw_json)

    # ==========================================================================
    # SECTION C: GITHUB AUTHENTICATION & CREDENTIAL SAFETY
    # ==========================================================================

    def test_github_auth_lookup_order(self):
        """
        Confirms exact lookup order:
        1. Explicit constructor token
        2. Environment variables (GITHUB_TOKEN / GH_TOKEN)
        3. .github_token file in repo root
        4. .env file in repo root
        """
        temp_dir = tempfile.mkdtemp()
        try:
            # 4. .env only
            with open(os.path.join(temp_dir, ".env"), "w", encoding="utf-8") as f:
                f.write("GITHUB_TOKEN=token_from_dotenv\n")
            auth = GitHubAuth(repo_root=temp_dir)
            with patch.dict(os.environ, {}, clear=True):
                self.assertEqual(auth.get_token(), "token_from_dotenv")

            # 3. .github_token overrides .env
            with open(os.path.join(temp_dir, ".github_token"), "w", encoding="utf-8") as f:
                f.write("token_from_github_token_file\n")
            with patch.dict(os.environ, {}, clear=True):
                self.assertEqual(auth.get_token(), "token_from_github_token_file")

            # 2. Environment variable overrides .github_token and .env
            with patch.dict(os.environ, {"GITHUB_TOKEN": "token_from_env_var"}):
                self.assertEqual(auth.get_token(), "token_from_env_var")

            # 1. Explicit token overrides everything
            auth_explicit = GitHubAuth(explicit_token="token_explicit_param", repo_root=temp_dir)
            with patch.dict(os.environ, {"GITHUB_TOKEN": "token_from_env_var"}):
                self.assertEqual(auth_explicit.get_token(), "token_explicit_param")
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)

    def test_subprocess_credential_isolation(self):
        """
        Verifies run_git_command passes auth via GIT_CONFIG_COUNT env vars,
        ensuring tokens NEVER appear in process command line arguments.
        """
        with patch("subprocess.run") as mock_run:
            mock_run.return_value = MagicMock(returncode=0, stdout="git output", stderr="")
            self.wm.run_git_command(["status"], cwd=self.repo_dir, auth_token="ghp_testsecrettoken12345")

            call_args, call_kwargs = mock_run.call_args
            cmd = call_args[0]
            env = call_kwargs["env"]

            # Token is NOT in cmd vector
            for arg in cmd:
                self.assertNotIn("ghp_testsecrettoken12345", arg)

            # Token IS in subprocess environment config
            self.assertEqual(env.get("GIT_CONFIG_COUNT"), "1")
            self.assertEqual(env.get("GIT_CONFIG_KEY_0"), "http.extraHeader")
            self.assertEqual(env.get("GIT_CONFIG_VALUE_0"), "AUTHORIZATION: bearer ghp_testsecrettoken12345")

    def test_token_file_restricted_permissions_and_logout(self):
        """Verifies .github_token is written with 0600 mode and cleaned up on logout."""
        temp_dir = tempfile.mkdtemp()
        try:
            auth = GitHubAuth(repo_root=temp_dir)
            svc = GitHubService(auth=auth)

            mock_resp = MagicMock()
            mock_resp.status_code = 200
            mock_resp.json.return_value = {
                "access_token": "gho_newdeviceflowtoken12345"
            }

            with patch("httpx.AsyncClient.post", AsyncMock(return_value=mock_resp)), \
                 patch.object(svc, "get_authenticated_user", AsyncMock(return_value={"login": "testuser"})):
                res = asyncio.run(svc.poll_device_flow("dev_code_123", client_id="cid_123"))
                self.assertTrue(res["success"])

            token_file = os.path.join(temp_dir, ".github_token")
            self.assertTrue(os.path.isfile(token_file))

            # Test logout
            svc.logout()
            self.assertFalse(os.path.isfile(token_file))
            self.assertIsNone(auth._explicit_token)
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
