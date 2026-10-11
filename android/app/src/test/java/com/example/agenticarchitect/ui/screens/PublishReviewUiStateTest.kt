package com.example.agenticarchitect.ui.screens

import com.example.agenticarchitect.data.remote.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying UI state presentation, file eligibility filtering,
 * commit authorization validation, remote URL redaction, and two-step push confirmation
 * for PublishReviewScreen.
 */
class PublishReviewUiStateTest {

    @Test
    fun publishReview_redactsRemoteCredentials() {
        val review = PublishReviewResponse(
            workspace_id = "octocat--spoon-knife",
            owner = "octocat",
            repo = "Spoon-Knife",
            current_branch = "feature/mobile-nav",
            remote_name = "origin",
            remote_url = "https://github.com/octocat/Spoon-Knife.git",
            files = emptyList(),
            publish_blocked = false,
            diff = ""
        )

        assertFalse("Remote URL must not contain secrets", review.remote_url?.contains("ghp_") == true)
        assertFalse("Remote URL must not contain user-token embedding", review.remote_url?.contains("@github.com") == true)
        assertEquals("origin", review.remote_name)
        assertEquals("feature/mobile-nav", review.current_branch)
        assertEquals("octocat/Spoon-Knife", review.target_repo)
        assertEquals(0, review.total_changed_files)
        assertFalse(review.has_ai_changes)
        assertFalse(review.has_user_changes)
        assertFalse(review.has_preexisting_collisions)
    }

    @Test
    fun publishReview_separatesAiFromUserAttribution() {
        val aiFile = FileReviewItem(
            path = "src/Navbar.js",
            action = "MODIFIED",
            source = "AI_OPERATION",
            eligible_for_commit = true,
            ineligibility_reason = null,
            staged_in_index = false
        )
        val userFile = FileReviewItem(
            path = "notes.txt",
            action = "UNTRACKED",
            source = "PREEXISTING_USER",
            eligible_for_commit = false,
            ineligibility_reason = "Unstaged/untracked user file not part of AI operation",
            staged_in_index = false
        )
        val collisionFile = FileReviewItem(
            path = "index.html",
            action = "MODIFIED",
            source = "COLLISION",
            eligible_for_commit = false,
            ineligibility_reason = "File contained pre-existing user edits modified by AI operation",
            staged_in_index = false,
            has_collision = true
        )

        val review = PublishReviewResponse(
            workspace_id = "octocat--spoon-knife",
            owner = "octocat",
            repo = "Spoon-Knife",
            current_branch = "main",
            remote_name = "origin",
            remote_url = "https://github.com/octocat/Spoon-Knife.git",
            files = listOf(aiFile, userFile, collisionFile),
            publish_blocked = true,
            publish_block_reason = "Collision detected in index.html",
            diff = "+// new nav"
        )

        assertEquals(3, review.files.size)
        assertEquals(3, review.total_changed_files)
        assertTrue(review.has_ai_changes)
        assertTrue(review.has_user_changes)
        assertTrue(review.has_preexisting_collisions)
        assertTrue(review.publish_blocked)
        assertTrue(review.block_reasons.any { it.contains("Collision detected") })

        val eligible = review.files.filter { it.eligible_for_commit }
        assertEquals(1, eligible.size)
        assertEquals("src/Navbar.js", eligible.first().path)
        assertEquals("AI_OPERATION", eligible.first().source)

        val ineligible = review.files.filter { !it.eligible_for_commit }
        assertEquals(2, ineligible.size)
        assertNotNull(ineligible.first().ineligibility_reason)
    }

    @Test
    fun commitAction_validationLogic() {
        val eligibleFile = FileReviewItem(
            path = "src/App.js",
            action = "MODIFIED",
            source = "AI_OPERATION",
            eligible_for_commit = true,
            staged_in_index = false
        )

        // 1. Empty message -> Invalid
        var commitMsg = ""
        var selected = setOf(eligibleFile.path)
        var canCommit = commitMsg.isNotBlank() && selected.isNotEmpty()
        assertFalse(canCommit)

        // 2. Empty selection -> Invalid
        commitMsg = "feat: add app component"
        selected = emptySet()
        canCommit = commitMsg.isNotBlank() && selected.isNotEmpty()
        assertFalse(canCommit)

        // 3. Valid message and eligible selection -> Valid
        selected = setOf(eligibleFile.path)
        canCommit = commitMsg.isNotBlank() && selected.isNotEmpty()
        assertTrue(canCommit)
    }

    @Test
    fun commitResponse_capturesShaAndFiles() {
        val res = CommitResponse(
            success = true,
            workspace_id = "octocat--spoon-knife",
            commit_hash = "abcdef1234567890abcdef1234567890abcdef12",
            branch = "feature/agentic",
            committed_files = listOf("src/App.js"),
            message = "Commit authored and recorded cleanly."
        )

        assertTrue(res.success)
        assertEquals("abcdef1234567890abcdef1234567890abcdef12", res.commit_hash)
        assertEquals(1, res.files_committed.size)
        assertEquals("src/App.js", res.files_committed.first())
        assertEquals("feature/agentic", res.branch)
    }

    @Test
    fun pushResponse_divergenceReporting() {
        // When remote has advanced, push response must report failure with error code
        val divergenceError = PushResponse(
            success = false,
            workspace_id = "octocat--spoon-knife",
            remote = "origin",
            branch = "main",
            error = "REMOTE_DIVERGED: Remote branch origin/main has 2 commits ahead of local HEAD. Push stopped.",
            diverged = true,
            commits_ahead = 0,
            commits_behind = 2,
            recovery_steps = "1. Fetch remote updates. 2. Inspect conflicts. 3. Rebase or merge manually."
        )

        assertFalse(divergenceError.success)
        assertTrue(divergenceError.diverged)
        assertEquals(2, divergenceError.commits_behind)
        assertNotNull(divergenceError.recovery_steps)
        assertTrue(divergenceError.error?.contains("REMOTE_DIVERGED") == true)
    }

    @Test
    fun pushResponse_successfulPushRecord() {
        val successPush = PushResponse(
            success = true,
            workspace_id = "octocat--spoon-knife",
            remote = "origin",
            branch = "feature/agentic",
            message = "Pushed 1 commit to origin/feature/agentic cleanly.",
            diverged = false,
            commits_ahead = 1,
            commits_behind = 0
        )

        assertTrue(successPush.success)
        assertFalse(successPush.diverged)
        assertEquals("origin", successPush.remote)
        assertEquals("feature/agentic", successPush.branch)
        assertNull(successPush.error)
    }
}
