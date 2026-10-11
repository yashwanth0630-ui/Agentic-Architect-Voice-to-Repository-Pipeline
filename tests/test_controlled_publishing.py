"""
Controlled Git Commit and Publishing Pipeline Test Suite (Phase 2D)

Tests:
1. Change Review & Attribution:
   - Review summarizes target repo, current branch, redacted remote URL, changed files, and unified diff.
   - Disaggregates AI-generated changes from pre-existing user changes and collisions.
   - Flags pre-existing unstaged/staged collisions and sets publish_blocked=True.
   - Identifies untracked files and deleted files correctly.
2. Exact Commit Staging & Invariant Verification:
   - Only selected files are staged; never broad 'git add .' or 'git add -A'.
   - Rejection when unrelated staged changes exist in the index (UNRELATED_STAGED_CHANGES).
   - Rejection when a selected file has a collision with pre-existing user edits (DIRTY_FILE_COLLISION).
   - Safe staging of file deletions (git rm) and additions (git add -- <file>).
   - User unstaged files outside the selection are NEVER discarded, modified, or swept into the commit.
   - Commits contain signed author metadata (author_name, author_email).
3. Push Safety & Divergence Protection:
   - Push fails closed without explicit confirmation (confirmed=False).
   - Remote URL is scrubbed of any credentials or tokens.
   - Remote divergence check detects remote ahead and blocks push (REMOTE_DIVERGED).
   - Clean push succeeds when local branch is ahead of remote and remote has not diverged.
   - Never force-pushes or silently overwrites remote history.
4. Concurrency & Security:
   - Workspace publishing lock prevents concurrent commit/push operations.
   - Credentials remain redacted in error messages and return models.
"""

from datetime import datetime, timezone
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

# Add scripts directory
SCRIPTS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "scripts"))
if SCRIPTS_DIR not in sys.path:
    sys.path.insert(0, SCRIPTS_DIR)

from github_service import GitHubAuth, scrub_sensitive_text
from repository_agent import (
    RepositoryWorkspaceManager,
    RepositoryAgent,
    GitChangeTracker,
    GitPublisher,
    PublishError,
    FileReviewItem,
    PublishReviewSummary,
    CommitResult,
    PushResult,
    WorkspaceRecord,
    PreExistingChanges,
    OperationChangeRecord
)


class TestControlledPublishingPipeline(unittest.TestCase):
    """Rigorous tests for Phase 2D publishing pipeline using disposable local repositories."""

    def setUp(self):
        self.temp_root = tempfile.mkdtemp(prefix="phase_2d_publish_")
        self.workspaces_dir = os.path.join(self.temp_root, "workspaces")
        self.remotes_dir = os.path.join(self.temp_root, "remotes")
        os.makedirs(self.workspaces_dir, exist_ok=True)
        os.makedirs(self.remotes_dir, exist_ok=True)

        self.wm = RepositoryWorkspaceManager(workspaces_dir=self.workspaces_dir)
        self.tracker = GitChangeTracker(wm=self.wm)
        self.agent = RepositoryAgent(wm=self.wm, tracker=self.tracker)
        self.publisher = GitPublisher(wm=self.wm, agent=self.agent, tracker=self.tracker)

        # Set up a disposable bare remote and a cloned workspace
        self.ws_id = "test-owner--test-repo"
        self.remote_path = os.path.join(self.remotes_dir, "test-repo.git")
        self.ws_path = os.path.join(self.workspaces_dir, self.ws_id)

        # 1. Initialize bare remote repository
        subprocess.run(["git", "init", "--bare", self.remote_path], check=True, capture_output=True)

        # 2. Initialize a seed clone, create initial commit on main, push to bare remote
        seed_dir = os.path.join(self.temp_root, "seed")
        os.makedirs(seed_dir, exist_ok=True)
        subprocess.run(["git", "init", "-b", "main", seed_dir], check=True, capture_output=True)
        subprocess.run(["git", "-C", seed_dir, "config", "user.name", "Tester"], check=True)
        subprocess.run(["git", "-C", seed_dir, "config", "user.email", "tester@example.com"], check=True)
        
        # Add initial files
        with open(os.path.join(seed_dir, "README.md"), "w", encoding="utf-8") as f:
            f.write("# Seed Project\nInitial baseline content.\n")
        with open(os.path.join(seed_dir, "index.html"), "w", encoding="utf-8") as f:
            f.write("<!DOCTYPE html><html><body><h1>Seed</h1></body></html>\n")
        
        subprocess.run(["git", "-C", seed_dir, "add", "."], check=True, capture_output=True)
        subprocess.run(["git", "-C", seed_dir, "commit", "-m", "Initial commit"], check=True, capture_output=True)
        subprocess.run(["git", "-C", seed_dir, "remote", "add", "origin", self.remote_path], check=True)
        subprocess.run(["git", "-C", seed_dir, "push", "-u", "origin", "main"], check=True, capture_output=True)
        
        # Set default branch on bare remote to main
        subprocess.run(["git", "--git-dir", self.remote_path, "symbolic-ref", "HEAD", "refs/heads/main"], check=True)

        # Get baseline commit hash
        res = subprocess.run(["git", "-C", seed_dir, "rev-parse", "HEAD"], check=True, capture_output=True, text=True)
        self.baseline_commit = res.stdout.strip()
        shutil.rmtree(seed_dir, ignore_errors=True)

        # 3. Clone bare repo into workspace directory
        subprocess.run(["git", "clone", "-b", "main", self.remote_path, self.ws_path], check=True, capture_output=True)
        subprocess.run(["git", "-C", self.ws_path, "config", "user.name", "IdeaBeacon"], check=True)
        subprocess.run(["git", "-C", self.ws_path, "config", "user.email", "ideabeacon@example.com"], check=True)

        # Register workspace in manager
        now_iso = datetime.now(timezone.utc).isoformat()
        record = WorkspaceRecord(
            workspace_id=self.ws_id,
            owner="test-owner",
            repo="test-repo",
            clone_url=self.remote_path,
            default_branch="main",
            workspace_path=self.ws_path,
            baseline_commit=self.baseline_commit,
            current_branch="main",
            created_at=now_iso,
            updated_at=now_iso,
            status="ready"
        )
        with self.wm._lock:
            reg = self.wm._read_registry()
            reg[self.ws_id] = record.to_dict()
            self.wm._write_registry(reg)

    def tearDown(self):
        shutil.rmtree(self.temp_root, ignore_errors=True)

    # -------------------------------------------------------------------------
    # 1. REVIEW & ATTRIBUTION TESTS
    # -------------------------------------------------------------------------

    def test_review_no_changes(self):
        """Review cleanly reports zero changes when workspace is clean."""
        review = self.publisher.get_publish_review(self.ws_id)
        self.assertEqual(self.ws_id, review.workspace_id)
        self.assertEqual("test-owner/test-repo", review.target_repo)
        self.assertEqual("main", review.current_branch)
        self.assertEqual(0, review.total_changed_files)
        self.assertFalse(review.has_ai_changes)
        self.assertFalse(review.has_user_changes)
        self.assertFalse(review.publish_blocked)
        self.assertEqual(0, review.unpushed_commits)
        self.assertEqual("", review.diff)

    def test_review_separates_ai_and_preexisting_user_changes(self):
        """Review marks AI files as eligible and untracked user files as ineligible."""
        # 1. User creates a notes file outside AI
        user_file = os.path.join(self.ws_path, "user_notes.txt")
        with open(user_file, "w", encoding="utf-8") as f:
            f.write("User private notes.\n")

        # 2. Record an AI operation editing index.html
        html_file = os.path.join(self.ws_path, "index.html")
        with open(html_file, "a", encoding="utf-8") as f:
            f.write("<footer>AI Footer</footer>\n")

        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id="op_101",
            instruction="Add footer",
            timestamp=now_iso,
            files_created=[],
            files_modified=["index.html"],
            files_deleted=[],
            total_changes=1,
            diff="+<footer>AI Footer</footer>",
            pre_existing_changes=PreExistingChanges(staged=[], unstaged=[], untracked=[], clean=True),
            dirty_file_collision=False,
            colliding_files=[],
            publish_blocked=False
        )
        self.agent.append_operation_journal(self.ws_id, op_record)

        review = self.publisher.get_publish_review(self.ws_id)
        self.assertEqual(2, review.total_changed_files)
        self.assertTrue(review.has_ai_changes)
        self.assertTrue(review.has_user_changes)
        self.assertFalse(review.has_preexisting_collisions)
        self.assertFalse(review.publish_blocked)

        file_map = {f.path: f for f in review.files}
        self.assertIn("index.html", file_map)
        self.assertIn("user_notes.txt", file_map)

        self.assertEqual("ai_operation", file_map["index.html"].source.lower())
        self.assertTrue(file_map["index.html"].eligible_for_commit)

        self.assertIn(file_map["user_notes.txt"].source.lower(), ("untracked", "preexisting_user", "user_pre_existing"))
        self.assertFalse(file_map["user_notes.txt"].eligible_for_commit)
        self.assertIsNotNone(file_map["user_notes.txt"].ineligibility_reason)

    def test_review_blocks_on_preexisting_collision(self):
        """Review flags collision when AI edited a file that already had user edits."""
        # Pre-existing user edit on index.html
        html_file = os.path.join(self.ws_path, "index.html")
        with open(html_file, "a", encoding="utf-8") as f:
            f.write("<!-- User header -->\n")

        # AI operation modifies index.html too
        with open(html_file, "a", encoding="utf-8") as f:
            f.write("<!-- AI footer -->\n")

        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id="op_102",
            instruction="Append footer",
            timestamp=now_iso,
            files_created=[],
            files_modified=["index.html"],
            files_deleted=[],
            total_changes=1,
            diff="+<!-- AI footer -->",
            pre_existing_changes=PreExistingChanges(staged=[], unstaged=["index.html"], untracked=[], clean=False),
            dirty_file_collision=True,
            colliding_files=["index.html"],
            publish_blocked=True,
            publish_block_reason="Pre-existing edits on index.html"
        )
        self.agent.append_operation_journal(self.ws_id, op_record)

        review = self.publisher.get_publish_review(self.ws_id)
        self.assertTrue(review.has_preexisting_collisions)
        self.assertTrue(review.publish_blocked)
        self.assertTrue(any("Collision detected" in r or "Pre-existing" in r for r in review.block_reasons))

        file_map = {f.path: f for f in review.files}
        self.assertEqual("collision", file_map["index.html"].source.lower())
        self.assertFalse(file_map["index.html"].eligible_for_commit)

    # -------------------------------------------------------------------------
    # 2. COMMIT SAFETY & EXACT STAGING TESTS
    # -------------------------------------------------------------------------

    def test_commit_exact_staging_only_approved_files(self):
        """Commit stages ONLY approved files and leaves user files untouched in working tree."""
        # AI modifies index.html and creates new_component.js
        with open(os.path.join(self.ws_path, "index.html"), "a", encoding="utf-8") as f:
            f.write("<script src='new_component.js'></script>\n")
        with open(os.path.join(self.ws_path, "new_component.js"), "w", encoding="utf-8") as f:
            f.write("console.log('Component initialized');\n")

        # User creates unrelated draft file
        with open(os.path.join(self.ws_path, "draft.txt"), "w", encoding="utf-8") as f:
            f.write("My secret unfinished notes\n")

        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id="op_103",
            instruction="Add component",
            timestamp=now_iso,
            files_created=["new_component.js"],
            files_modified=["index.html"],
            files_deleted=[],
            total_changes=2,
            diff="...",
            pre_existing_changes=PreExistingChanges(staged=[], unstaged=[], untracked=[], clean=True),
            dirty_file_collision=False,
            colliding_files=[],
            publish_blocked=False
        )
        self.agent.append_operation_journal(self.ws_id, op_record)

        # User commits ONLY new_component.js (excluding index.html and draft.txt)
        res = self.publisher.create_commit(
            workspace_id=self.ws_id,
            commit_message="feat: add component file only",
            selected_files=["new_component.js"],
            author_name="Finley Vance",
            author_email="finley@example.com"
        )

        self.assertTrue(res.success)
        self.assertEqual(["new_component.js"], res.files_committed)
        self.assertEqual("main", res.branch)

        # Verify commit in Git history
        log_res = subprocess.run(
            ["git", "-C", self.ws_path, "show", "--name-only", "--format=%s|%an|%ae", "HEAD"],
            check=True, capture_output=True, text=True
        )
        stdout = log_res.stdout.strip()
        self.assertIn("feat: add component file only|Finley Vance|finley@example.com", stdout)
        self.assertIn("new_component.js", stdout)
        self.assertNotIn("index.html", stdout)
        self.assertNotIn("draft.txt", stdout)

        # Verify index and working tree: draft.txt and index.html are STILL present and intact!
        self.assertTrue(os.path.exists(os.path.join(self.ws_path, "draft.txt")))
        self.assertTrue(os.path.exists(os.path.join(self.ws_path, "index.html")))

    def test_commit_aborts_if_unrelated_staged_changes_in_index(self):
        """Commit fails closed if the Git index already contained manual staged changes."""
        # User manually stages draft.txt
        with open(os.path.join(self.ws_path, "draft.txt"), "w", encoding="utf-8") as f:
            f.write("Staged by user manually.\n")
        subprocess.run(["git", "-C", self.ws_path, "add", "draft.txt"], check=True)

        # AI creates app.js
        with open(os.path.join(self.ws_path, "app.js"), "w", encoding="utf-8") as f:
            f.write("console.log('app');\n")

        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id="op_104",
            instruction="Add app",
            timestamp=now_iso,
            files_created=["app.js"],
            files_modified=[],
            files_deleted=[],
            total_changes=1,
            diff="...",
            pre_existing_changes=PreExistingChanges(staged=["draft.txt"], unstaged=[], untracked=[], clean=False),
            dirty_file_collision=False,
            colliding_files=[],
            publish_blocked=False
        )
        self.agent.append_operation_journal(self.ws_id, op_record)

        # Attempt to commit app.js without addressing draft.txt in index
        with self.assertRaises(PublishError) as ctx:
            self.publisher.create_commit(
                workspace_id=self.ws_id,
                commit_message="feat: add app",
                selected_files=["app.js"]
            )

        self.assertEqual("UNRELATED_STAGED_CHANGES", ctx.exception.error_code)
        self.assertIn("draft.txt", ctx.exception.message)

    def test_commit_aborts_on_dirty_file_collision(self):
        """Commit blocks publishing if selected file collided with pre-existing user edits."""
        with open(os.path.join(self.ws_path, "index.html"), "a", encoding="utf-8") as f:
            f.write("collision line\n")

        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id="op_105",
            instruction="Colliding edit",
            timestamp=now_iso,
            files_created=[],
            files_modified=["index.html"],
            files_deleted=[],
            total_changes=1,
            diff="...",
            pre_existing_changes=PreExistingChanges(staged=[], unstaged=["index.html"], untracked=[], clean=False),
            dirty_file_collision=True,
            colliding_files=["index.html"],
            publish_blocked=True,
            publish_block_reason="Collision on index.html"
        )
        self.agent.append_operation_journal(self.ws_id, op_record)

        with self.assertRaises(PublishError) as ctx:
            self.publisher.create_commit(
                workspace_id=self.ws_id,
                commit_message="feat: overwrite index",
                selected_files=["index.html"]
            )

        self.assertEqual("DIRTY_FILE_COLLISION", ctx.exception.error_code)

    def test_commit_handles_file_deletion_cleanly(self):
        """Commit safely stages and records file deletions."""
        # Delete index.html
        os.remove(os.path.join(self.ws_path, "index.html"))

        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id="op_106",
            instruction="Remove legacy index",
            timestamp=now_iso,
            files_created=[],
            files_modified=[],
            files_deleted=["index.html"],
            total_changes=1,
            diff="...",
            pre_existing_changes=PreExistingChanges(staged=[], unstaged=[], untracked=[], clean=True),
            dirty_file_collision=False,
            colliding_files=[],
            publish_blocked=False
        )
        self.agent.append_operation_journal(self.ws_id, op_record)

        res = self.publisher.create_commit(
            workspace_id=self.ws_id,
            commit_message="chore: remove legacy index.html",
            selected_files=["index.html"]
        )

        self.assertTrue(res.success)
        self.assertEqual(["index.html"], res.files_committed)

        # Verify index.html was removed from git tree
        status_res = subprocess.run(["git", "-C", self.ws_path, "status", "--porcelain=v1"], capture_output=True, text=True)
        self.assertEqual("", status_res.stdout.strip())

    # -------------------------------------------------------------------------
    # 3. PUSH SAFETY & DIVERGENCE TESTS
    # -------------------------------------------------------------------------

    def test_push_requires_explicit_confirmation(self):
        """Push fails closed with error code PUSH_CONFIRMATION_REQUIRED if confirmed=False."""
        with self.assertRaises(PublishError) as ctx:
            self.publisher.push_to_remote(
                workspace_id=self.ws_id,
                remote_name="origin",
                branch="main",
                confirmed=False
            )
        self.assertEqual("PUSH_CONFIRMATION_REQUIRED", ctx.exception.error_code)

    def test_push_blocks_when_remote_diverged(self):
        """Push detects when the remote repository has advanced and blocks push cleanly."""
        # 1. Create a local commit
        with open(os.path.join(self.ws_path, "local_update.txt"), "w", encoding="utf-8") as f:
            f.write("Local commit content\n")
        subprocess.run(["git", "-C", self.ws_path, "add", "local_update.txt"], check=True)
        subprocess.run(["git", "-C", self.ws_path, "commit", "-m", "Local commit"], check=True)

        # 2. Simulate another contributor pushing a commit to the bare remote
        contributor_dir = os.path.join(self.temp_root, "contributor")
        subprocess.run(["git", "clone", "-b", "main", self.remote_path, contributor_dir], check=True, capture_output=True)
        subprocess.run(["git", "-C", contributor_dir, "config", "user.name", "Colleague"], check=True)
        subprocess.run(["git", "-C", contributor_dir, "config", "user.email", "colleague@example.com"], check=True)
        with open(os.path.join(contributor_dir, "remote_feature.txt"), "w", encoding="utf-8") as f:
            f.write("Pushed by colleague to remote directly\n")
        subprocess.run(["git", "-C", contributor_dir, "add", "."], check=True)
        subprocess.run(["git", "-C", contributor_dir, "commit", "-m", "Remote commit from colleague"], check=True)
        subprocess.run(["git", "-C", contributor_dir, "push", "origin", "main"], check=True, capture_output=True)
        shutil.rmtree(contributor_dir, ignore_errors=True)

        # 3. Attempt push from our workspace — must be blocked with REMOTE_DIVERGED!
        with self.assertRaises(PublishError) as ctx:
            self.publisher.push_to_remote(
                workspace_id=self.ws_id,
                remote_name="origin",
                branch="main",
                confirmed=True
            )

        self.assertEqual("REMOTE_DIVERGED", ctx.exception.error_code)
        self.assertIn("ahead of local HEAD", ctx.exception.message)
        self.assertIn("Fetch remote updates", ctx.exception.details.get("recovery_steps", ""))

    def test_push_succeeds_cleanly_when_not_diverged(self):
        """Push executes successfully to local bare remote when branches are aligned."""
        # Create a local commit
        with open(os.path.join(self.ws_path, "feature.txt"), "w", encoding="utf-8") as f:
            f.write("Feature content\n")
        subprocess.run(["git", "-C", self.ws_path, "add", "feature.txt"], check=True)
        subprocess.run(["git", "-C", self.ws_path, "commit", "-m", "feat: new feature"], check=True)

        res = self.publisher.push_to_remote(
            workspace_id=self.ws_id,
            remote_name="origin",
            branch="main",
            confirmed=True
        )

        self.assertTrue(res.success)
        self.assertFalse(res.diverged)
        self.assertEqual(1, res.commits_ahead)
        self.assertEqual(0, res.commits_behind)
        self.assertEqual("main", res.branch)

        # Verify bare remote received the commit
        verify_res = subprocess.run(
            ["git", "--git-dir", self.remote_path, "log", "-1", "--format=%s"],
            check=True, capture_output=True, text=True
        )
        self.assertEqual("feat: new feature", verify_res.stdout.strip())

    # -------------------------------------------------------------------------
    # 4. CONCURRENCY & SECURITY TESTS
    # -------------------------------------------------------------------------

    def test_workspace_publishing_concurrency_lock(self):
        """Concurrent publishing requests on the same workspace are rejected."""
        lock = self.publisher._get_lock(self.ws_id)
        # Manually acquire the lock to simulate a running operation
        lock.acquire()

        try:
            with self.assertRaises(PublishError) as ctx:
                self.publisher.create_commit(
                    workspace_id=self.ws_id,
                    commit_message="Concurrent attempt",
                    selected_files=["index.html"]
                )
            self.assertEqual("CONCURRENT_PUBLISH_OPERATION", ctx.exception.error_code)
        finally:
            lock.release()

    def test_credential_scrubbing_in_remote_urls(self):
        """Scrubber removes tokens and passwords from remote URLs."""
        test_cases = [
            ("https://ghp_abc123xyz456@github.com/org/repo.git", "https://***@github.com/org/repo.git"),
            ("https://user:gho_secretToken789@github.com/org/repo.git", "https://***@github.com/org/repo.git"),
            ("https://github.com/org/repo.git", "https://github.com/org/repo.git")
        ]
        for raw, expected in test_cases:
            scrubbed = scrub_sensitive_text(raw)
            self.assertNotIn("ghp_", scrubbed)
            self.assertNotIn("gho_", scrubbed)
            self.assertEqual(expected, scrubbed)


    def test_commit_aborts_if_ineligible_user_preexisting_file_selected(self):
        """Commit fails closed if an ineligible pre-existing file or untracked file is selected."""
        # 1. User writes an uncommitted manual edit outside AI
        user_file = os.path.join(self.ws_path, "user_scratchpad.txt")
        with open(user_file, "w", encoding="utf-8") as f:
            f.write("Manual draft content\n")

        # 2. Record an AI operation for a different file
        html_file = os.path.join(self.ws_path, "index.html")
        with open(html_file, "a", encoding="utf-8") as f:
            f.write("<p>AI content</p>\n")

        now_iso = datetime.now(timezone.utc).isoformat()
        op_record = OperationChangeRecord(
            operation_id="op_108",
            instruction="Add paragraph",
            timestamp=now_iso,
            files_created=[],
            files_modified=["index.html"],
            files_deleted=[],
            total_changes=1,
            diff="+<p>AI content</p>",
            pre_existing_changes=PreExistingChanges(staged=[], unstaged=[], untracked=["user_scratchpad.txt"], clean=False),
            dirty_file_collision=False,
            colliding_files=[],
            publish_blocked=False
        )
        self.agent.append_operation_journal(self.ws_id, op_record)

        # 3. Attempting to commit user_scratchpad.txt MUST fail closed
        with self.assertRaises(PublishError) as ctx:
            self.publisher.create_commit(
                workspace_id=self.ws_id,
                commit_message="Illegal commit of user file",
                selected_files=["user_scratchpad.txt"]
            )
        self.assertEqual("INELIGIBLE_FILES_SELECTED", ctx.exception.error_code)


if __name__ == "__main__":
    unittest.main()
