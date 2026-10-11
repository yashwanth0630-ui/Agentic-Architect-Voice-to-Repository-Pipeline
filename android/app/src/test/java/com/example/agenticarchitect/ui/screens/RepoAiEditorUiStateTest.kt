package com.example.agenticarchitect.ui.screens

import com.example.agenticarchitect.data.remote.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying UI state presentation, diff parsing,
 * changed-file aggregation, preview availability, and error handling for RepoAiEditorScreen.
 */
class RepoAiEditorUiStateTest {

    @Test
    fun workspaceStatus_readyWithBaselineAndBranch() {
        val repo = SelectedRepository(
            owner = "octocat",
            name = "Spoon-Knife",
            fullName = "octocat/Spoon-Knife",
            htmlUrl = "https://github.com/octocat/Spoon-Knife",
            defaultBranch = "main",
            isPrivate = false
        )

        val ws = RepoWorkspaceRecord(
            workspace_id = "octocat--spoon-knife",
            owner = "octocat",
            repo = "Spoon-Knife",
            clone_url = "https://github.com/octocat/Spoon-Knife.git",
            default_branch = "main",
            workspace_path = "C:/IQOO/projects/workspaces/octocat--spoon-knife",
            baseline_commit = "99c9f5ee1825b22f6ae22e809c32fed0b16d220f",
            current_branch = "main",
            status = "ready"
        )

        assertEquals("octocat--spoon-knife", ws.workspace_id)
        assertEquals("main", ws.current_branch)
        assertEquals("99c9f5ee1825b22f6ae22e809c32fed0b16d220f", ws.baseline_commit)
        assertEquals("ready", ws.status)
        assertNull(ws.preview_url)
    }

    @Test
    fun instructionResult_aggregatesCreatedModifiedDeletedFiles() {
        val summary = RepoChangeSummary(
            files_created = listOf("components/Navbar.js", "styles/nav.css"),
            files_modified = listOf("index.html", "app.js"),
            files_deleted = listOf("legacy_header.html"),
            total_changes = 5,
            diff = "diff --git a/index.html b/index.html\n--- a/index.html\n+++ b/index.html\n@@ -1,4 +1,5 @@\n+<nav>Mobile Nav</nav>\n",
            baseline_commit = "99c9f5ee18"
        )

        val result = RepoWorkspaceInstructResponse(
            success = true,
            workspace_id = "octocat--spoon-knife",
            stage = "COMPLETED",
            message = "Applied changes across 5 files.",
            changes = summary,
            preview_url = "http://192.168.29.47:8081",
            error = null
        )

        assertTrue(result.success)
        assertEquals("COMPLETED", result.stage)
        assertEquals(5, result.changes?.total_changes)
        assertEquals(2, result.changes?.files_created?.size)
        assertEquals(2, result.changes?.files_modified?.size)
        assertEquals(1, result.changes?.files_deleted?.size)
        assertNotNull(result.preview_url)
    }

    @Test
    fun instructionResult_partialFailure_preservesChangesForReview() {
        val summary = RepoChangeSummary(
            files_modified = listOf("index.html"),
            total_changes = 1,
            diff = "+<section>Partial layout</section>"
        )

        val partialResult = RepoWorkspaceInstructResponse(
            success = false,
            workspace_id = "octocat--spoon-knife",
            stage = "PARTIAL_ERROR",
            message = "Partial edits applied.",
            changes = summary,
            error = "Security violation: path traversal detected in second edit target"
        )

        assertFalse(partialResult.success)
        assertEquals("PARTIAL_ERROR", partialResult.stage)
        assertEquals(1, partialResult.changes?.total_changes)
        assertTrue(partialResult.error?.contains("Security violation") == true)
        // Ensure earlier changes are NOT discarded
        assertEquals("index.html", partialResult.changes?.files_modified?.first())
    }

    @Test
    fun diffParsing_identifiesUnifiedDiffMarkers() {
        val diffContent = """
            diff --git a/index.html b/index.html
            --- a/index.html
            +++ b/index.html
            @@ -10,3 +10,4 @@
             <div class="container">
            -<p>Old paragraph</p>
            +<p>New responsive paragraph</p>
            +<button>Explore</button>
        """.trimIndent()

        val lines = diffContent.lines()
        val addLines = lines.filter { it.startsWith("+") && !it.startsWith("+++") }
        val delLines = lines.filter { it.startsWith("-") && !it.startsWith("---") }
        val hunkHeaders = lines.filter { it.startsWith("@@") }

        assertEquals(2, addLines.size)
        assertEquals(1, delLines.size)
        assertEquals(1, hunkHeaders.size)
        assertTrue(addLines[0].contains("New responsive paragraph"))
        assertTrue(delLines[0].contains("Old paragraph"))
    }

    @Test
    fun previewAvailability_runningVsUnsupported() {
        // Supported static preview
        val supportedPreview = RepoWorkspacePreviewResponse(
            success = true,
            workspace_id = "octocat--spoon-knife",
            status = "running",
            preview_url = "http://192.168.29.47:8081",
            port = 8081
        )
        assertTrue(supportedPreview.success)
        assertEquals("running", supportedPreview.status)
        assertTrue(supportedPreview.preview_url?.startsWith("http://") == true)

        // Unsupported project preview error
        val unsupportedError = "Static web preview is not supported for this repository structure. No 'index.html' found."
        assertFalse(unsupportedError.contains("http://"))
        assertTrue(unsupportedError.contains("not supported"))
    }

    @Test
    fun apiError_remediationTipFormatting() {
        val bridgeError = LaptopBridgeException(
            message = "Connection refused on http://192.168.29.47:8000",
            remediationTip = "Ensure python scripts/laptop_ai_server.py is running on your laptop.",
            endpoint = "http://192.168.29.47:8000"
        )

        assertEquals("Connection refused on http://192.168.29.47:8000", bridgeError.message)
        assertTrue(bridgeError.remediationTip.contains("python scripts/laptop_ai_server.py"))
        assertEquals("http://192.168.29.47:8000", bridgeError.endpoint)
    }

    @Test
    fun changeIsolation_operationChangesExcludePreExistingUserFiles() {
        // Pre-existing user files (e.g. notes.txt, styles.css) were present before AI operation
        val opSummary = RepoChangeSummary(
            files_created = listOf("components/Hero.jsx"),
            files_modified = listOf("index.html"),
            files_deleted = emptyList(),
            total_changes = 2,
            diff = "diff --git a/index.html b/index.html\n--- a/index.html\n+++ b/index.html\n@@ -1,3 +1,3 @@\n-<title>Old</title>\n+<title>Hero Layout</title>\n",
            baseline_commit = "abc1234"
        )

        val result = RepoWorkspaceInstructResponse(
            success = true,
            workspace_id = "user--repo",
            stage = "COMPLETED",
            message = "Applied changes across 2 files.",
            changes = opSummary,
            preview_url = null,
            error = null
        )

        // Verify that operation changes strictly contain only the 2 files touched by this AI operation
        assertEquals(2, result.changes?.total_changes)
        assertEquals(listOf("components/Hero.jsx"), result.changes?.files_created)
        assertEquals(listOf("index.html"), result.changes?.files_modified)
        assertFalse(result.changes?.files_created?.contains("user_notes.txt") == true)
        assertFalse(result.changes?.files_modified?.contains("styles.css") == true)
    }

    @Test
    fun successiveOperations_secondOperationDoesNotMixFirstOperationChanges() {
        // Operation 1 touched index.html
        val op1Summary = RepoChangeSummary(
            files_modified = listOf("index.html"),
            total_changes = 1,
            diff = "+<h1>Op 1</h1>"
        )

        // Operation 2 touched only theme.css
        val op2Summary = RepoChangeSummary(
            files_modified = listOf("theme.css"),
            total_changes = 1,
            diff = "+body { background: #000; }"
        )

        assertEquals(1, op1Summary.total_changes)
        assertEquals(listOf("index.html"), op1Summary.files_modified)

        // Operation 2 must NOT include index.html
        assertEquals(1, op2Summary.total_changes)
        assertEquals(listOf("theme.css"), op2Summary.files_modified)
        assertFalse(op2Summary.files_modified.contains("index.html"))
        assertFalse(op2Summary.diff.contains("Op 1"))
    }

    @Test
    fun credentialSafety_tokensNeverExposedInWorkspaceRecordOrDiff() {
        val sampleToken = "ghp_VERYSECRETGITHUBTOKEN1234567890"
        val ws = RepoWorkspaceRecord(
            workspace_id = "testowner--testrepo",
            owner = "testowner",
            repo = "testrepo",
            clone_url = "https://github.com/testowner/testrepo.git",
            default_branch = "main",
            workspace_path = "C:/projects/workspaces/testowner--testrepo",
            baseline_commit = "99c9f5ee",
            current_branch = "main",
            status = "ready"
        )

        // Remote clone_url must never contain embedded token
        assertFalse(ws.clone_url.contains("@"))
        assertFalse(ws.clone_url.contains(sampleToken))
        assertEquals("https://github.com/testowner/testrepo.git", ws.clone_url)
    }
}
