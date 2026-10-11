package com.example.agenticarchitect.data.remote

import org.junit.Assert.*
import org.junit.Test

class GitHubModelsAndSelectionTest {

    @Test
    fun gitHubUser_modelPropertiesCorrect() {
        val user = GitHubUser(
            login = "octocat",
            name = "The Octocat",
            avatarUrl = "https://avatars.githubusercontent.com/u/583231",
            htmlUrl = "https://github.com/octocat",
            bio = "Open source developer",
            publicRepos = 42
        )
        assertEquals("octocat", user.login)
        assertEquals("The Octocat", user.name)
        assertEquals(42, user.publicRepos)
    }

    @Test
    fun gitHubStatusResponse_authenticatedAndUnauthenticatedStates() {
        val unauth = GitHubStatusResponse(
            success = true,
            authenticated = false,
            user = null,
            message = "Token not configured"
        )
        assertTrue(unauth.success)
        assertFalse(unauth.authenticated)
        assertNull(unauth.user)

        val auth = GitHubStatusResponse(
            success = true,
            authenticated = true,
            user = GitHubUser(login = "octocat")
        )
        assertTrue(auth.success)
        assertTrue(auth.authenticated)
        assertNotNull(auth.user)
        assertEquals("octocat", auth.user?.login)
    }

    @Test
    fun gitHubRepositoryItem_normalizedAttributes() {
        val repo = GitHubRepositoryItem(
            name = "Idea-Beacon",
            fullName = "octocat/Idea-Beacon",
            isPrivate = false,
            defaultBranch = "main",
            htmlUrl = "https://github.com/octocat/Idea-Beacon",
            cloneUrl = "https://github.com/octocat/Idea-Beacon.git",
            description = "AI Voice to repository agent",
            updatedAt = "2026-10-09T00:00:00Z",
            language = "Kotlin",
            stargazersCount = 99,
            fork = false
        )

        assertEquals("Idea-Beacon", repo.name)
        assertEquals("octocat/Idea-Beacon", repo.fullName)
        assertFalse(repo.isPrivate)
        assertEquals("main", repo.defaultBranch)
        assertEquals("Kotlin", repo.language)
        assertEquals(99, repo.stargazersCount)
    }

    @Test
    fun selectedRepository_conversionAndValues() {
        val repo = GitHubRepositoryItem(
            name = "agentic-pipeline",
            fullName = "my-org/agentic-pipeline",
            isPrivate = true,
            defaultBranch = "develop",
            htmlUrl = "https://github.com/my-org/agentic-pipeline",
            cloneUrl = "https://github.com/my-org/agentic-pipeline.git"
        )

        val parts = repo.fullName.split("/")
        val owner = if (parts.size > 1) parts[0] else ""
        val selected = SelectedRepository(
            owner = owner,
            name = repo.name,
            fullName = repo.fullName,
            htmlUrl = repo.htmlUrl,
            defaultBranch = repo.defaultBranch,
            isPrivate = repo.isPrivate
        )

        assertEquals("my-org", selected.owner)
        assertEquals("agentic-pipeline", selected.name)
        assertEquals("my-org/agentic-pipeline", selected.fullName)
        assertEquals("develop", selected.defaultBranch)
        assertTrue(selected.isPrivate)
    }

    @Test
    fun deviceCodeResponse_containsNoSensitiveTokens() {
        val devCode = GitHubDeviceCodeResponse(
            success = true,
            deviceCode = "internal_dev_code_xyz",
            userCode = "WDWN-VDLG",
            verificationUri = "https://github.com/login/device",
            expiresIn = 900,
            interval = 5
        )

        assertEquals("WDWN-VDLG", devCode.userCode)
        assertEquals("https://github.com/login/device", devCode.verificationUri)
        // Ensure no accessToken property exists on GitHubDeviceCodeResponse
        val fields = devCode.javaClass.declaredFields.map { it.name }
        assertFalse("Access token field must not exist on DeviceCodeResponse", fields.contains("accessToken"))
        assertFalse("Token field must not exist on DeviceCodeResponse", fields.contains("token"))
    }

    @Test
    fun repoWorkspaceRecord_propertiesAndValues() {
        val ws = RepoWorkspaceRecord(
            workspace_id = "octocat--hello-world",
            owner = "octocat",
            repo = "Hello-World",
            clone_url = "https://github.com/octocat/Hello-World.git",
            default_branch = "main",
            workspace_path = "/projects/workspaces/octocat--hello-world",
            baseline_commit = "abc123def456",
            current_branch = "main",
            status = "ready",
            preview_port = 8082,
            preview_url = "http://192.168.29.47:8082"
        )

        assertEquals("octocat--hello-world", ws.workspace_id)
        assertEquals("octocat", ws.owner)
        assertEquals("Hello-World", ws.repo)
        assertEquals("main", ws.default_branch)
        assertEquals("abc123def456", ws.baseline_commit)
        assertEquals(8082, ws.preview_port)
        assertEquals("http://192.168.29.47:8082", ws.preview_url)
    }

    @Test
    fun repoChangeSummary_totalsAndBreakdown() {
        val changes = RepoChangeSummary(
            files_created = listOf("contact.html", "about.html"),
            files_modified = listOf("index.html", "styles.css"),
            files_deleted = listOf("old.js"),
            total_changes = 5,
            diff = "diff --git a/index.html ...\n+new line\n",
            baseline_commit = "baseline123"
        )

        assertEquals(2, changes.files_created.size)
        assertEquals(2, changes.files_modified.size)
        assertEquals(1, changes.files_deleted.size)
        assertEquals(5, changes.total_changes)
        assertTrue(changes.diff.contains("+new line"))
        assertEquals("baseline123", changes.baseline_commit)
    }

    @Test
    fun repoWorkspaceInstructResponse_successfulExecution() {
        val changes = RepoChangeSummary(
            files_modified = listOf("index.html"),
            total_changes = 1,
            diff = "+<h1>Updated</h1>"
        )
        val resp = RepoWorkspaceInstructResponse(
            success = true,
            workspace_id = "octocat--repo",
            stage = "COMPLETED",
            message = "Applied changes across 1 file",
            changes = changes,
            preview_url = "http://192.168.29.47:8081",
            error = null
        )

        assertTrue(resp.success)
        assertEquals("COMPLETED", resp.stage)
        assertEquals(1, resp.changes?.total_changes)
        assertEquals("http://192.168.29.47:8081", resp.preview_url)
        assertNull(resp.error)
    }

    @Test
    fun repoWorkspaceInstructResponse_partialErrorPreservation() {
        val partialChanges = RepoChangeSummary(
            files_modified = listOf("index.html"),
            total_changes = 1,
            diff = "+<h1>Partial</h1>"
        )
        val resp = RepoWorkspaceInstructResponse(
            success = false,
            workspace_id = "octocat--repo",
            stage = "PARTIAL_ERROR",
            message = "Partial edits applied",
            changes = partialChanges,
            error = "Security violation on second edit"
        )

        assertFalse(resp.success)
        assertEquals("PARTIAL_ERROR", resp.stage)
        assertEquals(1, resp.changes?.total_changes)
        assertEquals("Security violation on second edit", resp.error)
    }

    @Test
    fun repoWorkspacePreviewResponse_statusAndLanUrl() {
        val preview = RepoWorkspacePreviewResponse(
            success = true,
            workspace_id = "octocat--repo",
            status = "running",
            preview_url = "http://192.168.29.47:8085",
            port = 8085
        )

        assertTrue(preview.success)
        assertEquals("running", preview.status)
        assertEquals("http://192.168.29.47:8085", preview.preview_url)
        assertEquals(8085, preview.port)
    }

    @Test
    fun phase2cModels_zeroSensitiveTokensReflectionCheck() {
        val modelClasses = listOf(
            RepoWorkspaceRecord::class.java,
            RepoChangeSummary::class.java,
            RepoWorkspaceInitResponse::class.java,
            RepoWorkspaceStatusResponse::class.java,
            RepoWorkspaceInstructResponse::class.java,
            RepoWorkspaceDiffResponse::class.java,
            RepoWorkspacePreviewResponse::class.java
        )

        for (cls in modelClasses) {
            val fieldNames = cls.declaredFields.map { it.name.lowercase() }
            assertFalse(
                "Model ${cls.simpleName} must not contain accessToken field",
                fieldNames.contains("accesstoken")
            )
            assertFalse(
                "Model ${cls.simpleName} must not contain secret field",
                fieldNames.contains("secret")
            )
            assertFalse(
                "Model ${cls.simpleName} must not contain password field",
                fieldNames.contains("password")
            )
        }
    }

    @Test
    fun extractErrorMessage_handlesRfc7807AndFastApiDetails() {
        val repo = LaptopAiRepository(null)

        // Case 1: RFC 7807 error object
        val rfcJson = """{"success": false, "error": {"code": "INVALID_REPO", "message": "Repository not accessible"}}"""
        assertEquals("Repository not accessible", repo.extractErrorMessage(rfcJson, 400))

        // Case 2: FastAPI string detail
        val detailStrJson = """{"detail": "Forbidden: Access restricted to local Wi-Fi / private network."}"""
        assertEquals("Forbidden: Access restricted to local Wi-Fi / private network.", repo.extractErrorMessage(detailStrJson, 403))

        // Case 3: FastAPI array detail (Pydantic validation)
        val detailArrJson = """{"detail": [{"loc": ["body", "prompt"], "msg": "Field required", "type": "missing"}]}"""
        assertEquals("Field required", repo.extractErrorMessage(detailArrJson, 422))

        // Case 4: Plain text / null fallback
        assertEquals("HTTP 500", repo.extractErrorMessage(null, 500))
        assertEquals("HTTP 502: Bad Gateway", repo.extractErrorMessage("Bad Gateway", 502))
    }

    @Test
    fun environmentDetectionResponse_and_planModel_integrity() {
        val env = HostEnvironmentInfo(
            os_name = "Windows",
            os_release = "11",
            architecture = "AMD64",
            lan_ip = "192.168.29.47"
        )
        val stackMap = mapOf(
            "fastapi" to StackEvaluationItem(tech_stack = "fastapi", supported = true),
            "react" to StackEvaluationItem(tech_stack = "react", supported = true)
        )
        val res = EnvironmentDetectionResponse(success = true, environment = env, stack_evaluations = stackMap)

        assertTrue(res.success)
        assertEquals("Windows", res.environment.os_name)
        assertEquals("192.168.29.47", res.environment.lan_ip)
        assertTrue(res.stack_evaluations["fastapi"]?.supported == true)

        val plan = SetupPlanResponse(
            success = true,
            plan_id = "plan-crypto-bot",
            project_id = "crypto-bot",
            project_name = "Crypto Bot",
            tech_stack = "fastapi",
            workspace_path = "C:/projects/crypto-bot",
            summary = "FastAPI setup plan",
            operations = listOf(
                SetupOperationItem(op_id = "op_1", op_type = "CREATE_VENV", title = "Create venv", status = "pending"),
                SetupOperationItem(op_id = "op_2", op_type = "START_DEV_SERVER", title = "Start server", status = "pending")
            ),
            requires_approval = true,
            approved = false,
            status = "planned"
        )

        assertEquals("crypto-bot", plan.project_id)
        assertTrue(plan.requires_approval)
        assertFalse(plan.approved)
        assertEquals(2, plan.operations.size)
    }

    @Test
    fun ideaItem_and_blueprint_integrity() {
        val bp = IdeaBlueprintItem(
            summary = "Crypto trading bot",
            target_audience = "Traders",
            core_features = listOf("Price ingestion", "Signal generation"),
            suggested_architecture = "FastAPI service",
            recommended_tech_stack = "fastapi",
            key_dependencies = listOf("fastapi", "uvicorn")
        )
        val idea = IdeaItem(
            idea_id = "crypto-trading-bot",
            title = "Crypto Trading Bot",
            raw_prompt = "Build a crypto bot",
            status = "refined",
            tech_stack = "fastapi",
            blueprint = bp
        )

        assertEquals("crypto-trading-bot", idea.idea_id)
        assertEquals("refined", idea.status)
        assertEquals("fastapi", idea.blueprint?.recommended_tech_stack)
        assertEquals(2, idea.blueprint?.core_features?.size)
    }
}


