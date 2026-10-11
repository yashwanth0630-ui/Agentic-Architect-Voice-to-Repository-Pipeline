package com.example.agenticarchitect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.agenticarchitect.data.remote.ConnectionInfo
import com.example.agenticarchitect.data.remote.ConnectionState
import com.example.agenticarchitect.data.remote.LaptopAiRepository
import com.example.agenticarchitect.data.remote.LaptopBridgeException
import com.example.agenticarchitect.generator.AgentManifestGenerator
import com.example.agenticarchitect.generator.MobilePromptIngestionResult
import com.example.agenticarchitect.npu.QualcommGenieXNpuEngine
import com.example.agenticarchitect.data.remote.GitHubDeviceCodeResponse
import com.example.agenticarchitect.data.remote.GitHubRepositoryItem
import com.example.agenticarchitect.data.remote.GitHubStatusResponse
import com.example.agenticarchitect.data.remote.SelectedRepository
import com.example.agenticarchitect.data.remote.RepoWorkspaceRecord
import com.example.agenticarchitect.data.remote.RepoWorkspaceInstructResponse
import com.example.agenticarchitect.data.remote.RepoWorkspaceDiffResponse
import com.example.agenticarchitect.data.remote.RepoWorkspacePreviewResponse
import com.example.agenticarchitect.data.remote.PublishReviewResponse
import com.example.agenticarchitect.data.remote.CommitResponse
import com.example.agenticarchitect.data.remote.PushResponse
import com.example.agenticarchitect.data.remote.EnvironmentDetectionResponse
import com.example.agenticarchitect.data.remote.SetupPlanResponse
import com.example.agenticarchitect.data.remote.SetupStatusResponse
import com.example.agenticarchitect.data.local.IdeaSyncStatus
import com.example.agenticarchitect.data.local.IdeaVaultEntity
import com.example.agenticarchitect.data.local.IdeaVaultRepository
import com.example.agenticarchitect.data.remote.IdeaRecordItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agenticarchitect.theme.DarkObsidian
import com.example.agenticarchitect.theme.EmberOrangePrimary
import com.example.agenticarchitect.ui.screens.ChatExecutionScreen
import com.example.agenticarchitect.ui.screens.EnvironmentSetupScreen
import com.example.agenticarchitect.ui.screens.GitHubReposScreen
import com.example.agenticarchitect.ui.screens.HomeScreen
import com.example.agenticarchitect.ui.screens.RepoAiEditorScreen
import com.example.agenticarchitect.ui.screens.PublishReviewScreen
import com.example.agenticarchitect.ui.screens.VoiceListeningScreen
import com.example.agenticarchitect.voice.VoicePromptManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class ScreenState {
    HOME,
    LISTENING,
    CHAT,
    GITHUB_REPOS,
    REPO_AI_EDITOR,
    PUBLISH_REVIEW,
    ENVIRONMENT_SETUP
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = DarkObsidian,
                    surface = Color(0xFF140804),
                    primary = EmberOrangePrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkObsidian
                ) {
                    AgenticArchitectApp()
                }
            }
        }
    }
}

@Composable
fun AgenticArchitectApp() {
    val context = LocalContext.current
    val voiceManager = remember { VoicePromptManager(context) }
    val npuEngine = remember { QualcommGenieXNpuEngine() }
    val manifestGenerator = remember { AgentManifestGenerator() }
    val laptopRepository = remember { LaptopAiRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var connectionInfo by remember { mutableStateOf(laptopRepository.connectionInfo) }
    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
    var promptText by remember { 
        mutableStateOf("Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot.") 
    }
    var isListening by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("● Opus 4.0") }
    var npuResult by remember { mutableStateOf<MobilePromptIngestionResult?>(null) }
    var savedZipFile by remember { mutableStateOf<File?>(null) }
    var isInferring by remember { mutableStateOf(false) }
    var isSyncingLaptop by remember { mutableStateOf(false) }
    var laptopSyncResult by remember { mutableStateOf<String?>(null) }

    var gitHubStatus by remember { mutableStateOf<GitHubStatusResponse?>(null) }
    var gitHubRepos by remember { mutableStateOf<List<GitHubRepositoryItem>>(emptyList()) }
    var isGitHubLoading by remember { mutableStateOf(false) }
    var gitHubError by remember { mutableStateOf<String?>(null) }
    var selectedRepo by remember { mutableStateOf(laptopRepository.selectedRepository) }
    var deviceCodeInfo by remember { mutableStateOf<GitHubDeviceCodeResponse?>(null) }
    var isDevicePolling by remember { mutableStateOf(false) }
    var pollingJob by remember { mutableStateOf<Job?>(null) }

    // Phase 2C: Workspace & AI Studio State
    var workspaceRecord by remember { mutableStateOf<RepoWorkspaceRecord?>(null) }
    var isWorkspaceLoading by remember { mutableStateOf(false) }
    var workspaceError by remember { mutableStateOf<String?>(null) }
    var isInstructing by remember { mutableStateOf(false) }
    var instructionResult by remember { mutableStateOf<RepoWorkspaceInstructResponse?>(null) }
    var diffResponse by remember { mutableStateOf<RepoWorkspaceDiffResponse?>(null) }
    var isDiffLoading by remember { mutableStateOf(false) }
    var previewResponse by remember { mutableStateOf<RepoWorkspacePreviewResponse?>(null) }
    var instructionJob by remember { mutableStateOf<Job?>(null) }

    // Phase 2D: Controlled Git Commit & Publishing State
    var publishReviewResponse by remember { mutableStateOf<PublishReviewResponse?>(null) }
    var isPublishReviewLoading by remember { mutableStateOf(false) }
    var publishReviewError by remember { mutableStateOf<String?>(null) }
    var isCommitting by remember { mutableStateOf(false) }
    var commitResponse by remember { mutableStateOf<CommitResponse?>(null) }
    var isPushing by remember { mutableStateOf(false) }
    var pushResponse by remember { mutableStateOf<PushResponse?>(null) }

    // Phase 3 & 4: Environment Setup Agent & Idea Vault State
    val ideaVaultRepo = remember { IdeaVaultRepository.getInstance(context, laptopRepository) }
    val savedIdeas by ideaVaultRepo.getAllIdeas().collectAsStateWithLifecycle(initialValue = emptyList())

    var environmentDetection by remember { mutableStateOf<EnvironmentDetectionResponse?>(null) }
    var isDetectingEnvironment by remember { mutableStateOf(false) }
    var activeIdea by remember { mutableStateOf<IdeaRecordItem?>(null) }
    var isRefiningIdea by remember { mutableStateOf(false) }
    var activePlan by remember { mutableStateOf<SetupPlanResponse?>(null) }
    var isPlanningSetup by remember { mutableStateOf(false) }
    var setupStatus by remember { mutableStateOf<SetupStatusResponse?>(null) }
    var isExecutingSetup by remember { mutableStateOf(false) }
    var setupPollingJob by remember { mutableStateOf<Job?>(null) }

    fun captureIdeaLocally(prompt: String, title: String? = null) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        coroutineScope.launch {
            val entity = ideaVaultRepo.captureIdea(prompt = clean, title = title)
            Toast.makeText(context, "Idea saved locally: ${entity.title}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateIdeaLocally(localId: String, title: String, prompt: String) {
        coroutineScope.launch {
            val updated = ideaVaultRepo.updateIdea(localId = localId, prompt = prompt, title = title)
            if (updated != null) {
                Toast.makeText(context, "Idea updated: ${updated.title}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteIdeaLocally(localId: String) {
        coroutineScope.launch {
            val ok = ideaVaultRepo.deleteIdea(localId)
            if (ok) {
                Toast.makeText(context, "Idea removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun retrySyncIdea(idea: IdeaVaultEntity) {
        coroutineScope.launch {
            Toast.makeText(context, "Retrying sync to laptop...", Toast.LENGTH_SHORT).show()
            val ok = ideaVaultRepo.syncSingleIdea(idea)
            if (ok) {
                Toast.makeText(context, "✓ Idea synced to laptop!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Sync failed. Will retry when connected.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun syncAllPendingIdeas() {
        coroutineScope.launch {
            val res = ideaVaultRepo.syncAllPending()
            if (res.totalPending == 0) {
                Toast.makeText(context, "All ideas are up to date.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Synced ${res.syncedCount}/${res.totalPending} ideas.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun detectHostEnvironment() {
        isDetectingEnvironment = true
        coroutineScope.launch {
            val res = laptopRepository.detectEnvironment()
            isDetectingEnvironment = false
            res.onSuccess { detection ->
                environmentDetection = detection
            }.onFailure { err ->
                Toast.makeText(context, "Environment detection: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun captureAndRefineIdea(title: String, prompt: String) {
        isRefiningIdea = true
        coroutineScope.launch {
            val capRes = laptopRepository.captureIdea(prompt = prompt, title = title)
            capRes.onSuccess { idea ->
                val refRes = laptopRepository.refineIdea(idea.idea_id)
                isRefiningIdea = false
                refRes.onSuccess { refined ->
                    activeIdea = refined
                    Toast.makeText(context, "Blueprint: ${refined.blueprint?.suggested_architecture}", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    activeIdea = idea
                    Toast.makeText(context, "Refinement notice: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                isRefiningIdea = false
                Toast.makeText(context, "Idea capture error: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun generateSetupPlan(prompt: String, techStack: String) {
        isPlanningSetup = true
        coroutineScope.launch {
            val res = laptopRepository.planEnvironmentSetup(prompt = prompt, techStack = techStack)
            isPlanningSetup = false
            res.onSuccess { plan ->
                activePlan = plan
                Toast.makeText(context, "Plan: ${plan.project_name} (${plan.operations.size} steps)", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Planning error: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeSetupPlan(planId: String) {
        val plan = activePlan ?: return
        isExecutingSetup = true
        coroutineScope.launch {
            val res = laptopRepository.executeEnvironmentPlan(projectId = plan.project_id, approved = true)
            res.onSuccess { updatedPlan ->
                activePlan = updatedPlan
                setupPollingJob?.cancel()
                setupPollingJob = coroutineScope.launch {
                    val pId = plan.project_id
                    var isDone = false
                    while (!isDone) {
                        delay(1200)
                        val statusRes = laptopRepository.getEnvironmentStatus(pId)
                        statusRes.onSuccess { st ->
                            setupStatus = st
                            if (st.status == "completed" || st.status == "failed" || st.status == "cancelled") {
                                isDone = true
                                isExecutingSetup = false
                                if (st.status == "completed") {
                                    Toast.makeText(context, "Environment verified! Server running.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }.onFailure {
                            isDone = true
                            isExecutingSetup = false
                        }
                    }
                }
            }.onFailure { err ->
                isExecutingSetup = false
                Toast.makeText(context, "Setup execution: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelSetup(projectId: String) {
        coroutineScope.launch {
            laptopRepository.cancelEnvironmentSetup(projectId)
            setupPollingJob?.cancel()
            isExecutingSetup = false
            Toast.makeText(context, "Setup cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopServer(projectId: String) {
        coroutineScope.launch {
            laptopRepository.stopDevServer(projectId)
            setupStatus = setupStatus?.copy(server_running = false)
            Toast.makeText(context, "Dev server stopped", Toast.LENGTH_SHORT).show()
        }
    }


    fun refreshGitHub(fetchRepos: Boolean = true) {
        isGitHubLoading = true
        gitHubError = null
        coroutineScope.launch {
            val statusRes = laptopRepository.getGitHubStatus()
            statusRes.onSuccess { status ->
                gitHubStatus = status
                if (status.authenticated && fetchRepos) {
                    val reposRes = laptopRepository.getGitHubRepositories()
                    reposRes.onSuccess { list ->
                        gitHubRepos = list
                        isGitHubLoading = false
                    }.onFailure { err ->
                        gitHubError = err.message
                        isGitHubLoading = false
                    }
                } else {
                    isGitHubLoading = false
                }
            }.onFailure { err ->
                gitHubError = err.message
                isGitHubLoading = false
            }
        }
    }

    fun startDeviceAuthFlow() {
        coroutineScope.launch {
            isGitHubLoading = true
            gitHubError = null
            val codeRes = laptopRepository.requestDeviceCode()
            isGitHubLoading = false
            codeRes.onSuccess { info ->
                deviceCodeInfo = info
                val dCode = info.deviceCode
                if (dCode != null) {
                    isDevicePolling = true
                    pollingJob?.cancel()
                    pollingJob = coroutineScope.launch {
                        val intervalMs = (info.interval.coerceAtLeast(5) * 1000L)
                        var attempts = 0
                        while (isDevicePolling && attempts < 60) {
                            delay(intervalMs)
                            attempts++
                            val pollRes = laptopRepository.pollDeviceCode(dCode)
                            pollRes.onSuccess { poll ->
                                if (poll.status == "complete" && poll.authenticated) {
                                    isDevicePolling = false
                                    deviceCodeInfo = null
                                    gitHubStatus = GitHubStatusResponse(
                                        success = true,
                                        authenticated = true,
                                        user = poll.user,
                                        message = "Authenticated successfully"
                                    )
                                    Toast.makeText(context, "GitHub connected as @${poll.user?.login}", Toast.LENGTH_LONG).show()
                                    refreshGitHub(fetchRepos = true)
                                    return@launch
                                } else if (poll.status in listOf("expired", "denied")) {
                                    isDevicePolling = false
                                    deviceCodeInfo = null
                                    gitHubError = poll.message ?: "Authorization ${poll.status}"
                                    return@launch
                                }
                            }
                        }
                        isDevicePolling = false
                    }
                }
            }.onFailure { err ->
                gitHubError = err.message ?: "Could not request device code"
                Toast.makeText(context, gitHubError, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cancelDeviceAuthFlow() {
        isDevicePolling = false
        pollingJob?.cancel()
        pollingJob = null
        deviceCodeInfo = null
    }

    fun handleLogout() {
        coroutineScope.launch {
            laptopRepository.logoutGitHub()
            gitHubStatus = GitHubStatusResponse(success = true, authenticated = false, user = null)
            gitHubRepos = emptyList()
            selectedRepo = null
            workspaceRecord = null
            instructionResult = null
            diffResponse = null
            previewResponse = null
            Toast.makeText(context, "GitHub credentials cleared", Toast.LENGTH_SHORT).show()
        }
    }

    fun initializeWorkspace(repo: SelectedRepository) {
        coroutineScope.launch {
            isWorkspaceLoading = true
            workspaceError = null
            val res = laptopRepository.initRepoWorkspace(repo.owner, repo.name, repo.defaultBranch)
            isWorkspaceLoading = false
            res.onSuccess { ws ->
                workspaceRecord = ws
                coroutineScope.launch {
                    isDiffLoading = true
                    val dRes = laptopRepository.getRepoWorkspaceDiff(ws.workspace_id)
                    isDiffLoading = false
                    dRes.onSuccess { diffResponse = it }
                }
            }.onFailure { err ->
                workspaceError = err.message ?: "Failed to initialize workspace"
            }
        }
    }

    fun executeInstruction(instruction: String) {
        val ws = workspaceRecord ?: return
        instructionJob?.cancel()
        instructionJob = coroutineScope.launch {
            isInstructing = true
            val res = laptopRepository.instructRepoWorkspace(ws.workspace_id, instruction)
            isInstructing = false
            res.onSuccess { result ->
                instructionResult = result
                if (result.success) {
                    Toast.makeText(context, "Edits applied across ${result.changes?.total_changes ?: 0} files", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Notice: ${result.message}", Toast.LENGTH_LONG).show()
                }
                coroutineScope.launch {
                    isDiffLoading = true
                    val dRes = laptopRepository.getRepoWorkspaceDiff(ws.workspace_id)
                    isDiffLoading = false
                    dRes.onSuccess { diffResponse = it }
                }
            }.onFailure { err ->
                instructionResult = RepoWorkspaceInstructResponse(
                    success = false,
                    workspace_id = ws.workspace_id,
                    stage = "FAILED",
                    message = err.message ?: "Instruction failed",
                    error = err.message
                )
                Toast.makeText(context, "Instruction failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun refreshDiff() {
        val ws = workspaceRecord ?: return
        coroutineScope.launch {
            isDiffLoading = true
            val dRes = laptopRepository.getRepoWorkspaceDiff(ws.workspace_id)
            isDiffLoading = false
            dRes.onSuccess { diffResponse = it }
        }
    }

    fun startWorkspacePreview() {
        val ws = workspaceRecord ?: return
        coroutineScope.launch {
            val res = laptopRepository.startRepoWorkspacePreview(ws.workspace_id)
            res.onSuccess {
                previewResponse = it
                Toast.makeText(context, "Preview started: ${it.preview_url}", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Preview notice: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun stopWorkspacePreview() {
        val ws = workspaceRecord ?: return
        coroutineScope.launch {
            laptopRepository.stopRepoWorkspacePreview(ws.workspace_id)
            previewResponse = null
            Toast.makeText(context, "Preview stopped", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadPublishReview() {
        val wsId = workspaceRecord?.workspace_id ?: return
        isPublishReviewLoading = true
        publishReviewError = null
        coroutineScope.launch {
            val res = laptopRepository.getPublishReview(wsId)
            isPublishReviewLoading = false
            res.onSuccess { review ->
                publishReviewResponse = review
            }.onFailure { err ->
                publishReviewError = err.message ?: "Failed to load review"
            }
        }
    }

    fun handleCommit(message: String, files: List<String>) {
        val wsId = workspaceRecord?.workspace_id ?: return
        isCommitting = true
        publishReviewError = null
        coroutineScope.launch {
            val res = laptopRepository.createCommit(wsId, message, files)
            isCommitting = false
            res.onSuccess { cRes ->
                commitResponse = cRes
                Toast.makeText(context, "Commit created: ${cRes.commit_hash?.take(7)}", Toast.LENGTH_SHORT).show()
                loadPublishReview()
                refreshDiff()
            }.onFailure { err ->
                publishReviewError = err.message ?: "Commit rejected"
            }
        }
    }

    fun handlePush() {
        val wsId = workspaceRecord?.workspace_id ?: return
        isPushing = true
        publishReviewError = null
        coroutineScope.launch {
            val res = laptopRepository.pushToRemote(wsId, confirmed = true)
            isPushing = false
            res.onSuccess { pRes ->
                pushResponse = pRes
                Toast.makeText(context, "Pushed cleanly to ${pRes.branch}", Toast.LENGTH_SHORT).show()
                loadPublishReview()
            }.onFailure { err ->
                publishReviewError = err.message ?: "Push halted"
            }
        }
    }


    LaunchedEffect(Unit) {
        if (npuResult == null) {
            val modelId = QualcommGenieXNpuEngine.MODEL_QWEN_7B
            npuResult = npuEngine.inferProjectFromPrompt(promptText, modelId)
        }
        // Auto-discover laptop bridge on shared Wi-Fi
        connectionInfo = laptopRepository.checkConnection()
        if (connectionInfo.state == ConnectionState.CONNECTED) {
            ideaVaultRepo.syncAllPending()
        }
        // Check GitHub status in background
        val ghRes = laptopRepository.getGitHubStatus()
        ghRes.onSuccess { gitHubStatus = it }
    }



    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            currentScreen = ScreenState.LISTENING
            voiceManager.startListening(
                onResult = { result -> promptText = result },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    isListening = false
                },
                onListeningStateChange = { state -> isListening = state }
            )
        } else {
            Toast.makeText(context, "Microphone permission required for voice prompt ingestion", Toast.LENGTH_LONG).show()
        }
    }

    fun startListeningFlow(isResume: Boolean = false) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            isListening = true
            currentScreen = ScreenState.LISTENING
            voiceManager.startListening(
                onResult = { result -> promptText = result },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    isListening = false
                },
                onListeningStateChange = { state ->
                    isListening = state
                    if (state && isResume) {
                        Toast.makeText(context, "Listening resumed", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun executeNpuInference() {
        if (isListening) {
            voiceManager.stopListening()
            isListening = false
        }
        isInferring = true
        currentScreen = ScreenState.CHAT

        val modelId = when {
            selectedModel.contains("Phi", ignoreCase = true) -> QualcommGenieXNpuEngine.MODEL_PHI_4
            selectedModel.contains("Qwen", ignoreCase = true) -> QualcommGenieXNpuEngine.MODEL_QWEN_7B
            selectedModel.contains("Opus", ignoreCase = true) -> QualcommGenieXNpuEngine.MODEL_OPUS_4
            else -> QualcommGenieXNpuEngine.MODEL_QWEN_7B
        }

        val result = npuEngine.inferProjectFromPrompt(promptText, modelId)
        npuResult = result
        isInferring = false
    }

    fun toggleModelSelection() {
        selectedModel = when (selectedModel) {
            "● Opus 4.0" -> "⚡ Qwen 2.5-Coder INT4"
            "⚡ Qwen 2.5-Coder INT4" -> "⚡ Phi-4-Mini INT4"
            else -> "● Opus 4.0"
        }
        Toast.makeText(context, "Active NPU Engine: $selectedModel", Toast.LENGTH_SHORT).show()
    }

    fun dispatchToLaptopBridge() {
        if (isSyncingLaptop) return
        isSyncingLaptop = true
        laptopSyncResult = "Syncing to Laptop Bridge..."
        coroutineScope.launch {
            val result = laptopRepository.generateFromPrompt(promptText)
            isSyncingLaptop = false
            connectionInfo = laptopRepository.connectionInfo
            result.onSuccess { res ->
                if (!res.previewUrl.isNullOrEmpty()) {
                    val pName = res.projectName ?: res.projectId ?: "Project"
                    laptopSyncResult = "✓ Live at: ${res.previewUrl} ($pName)"
                    Toast.makeText(context, "Opening Live Preview: ${res.previewUrl}", Toast.LENGTH_LONG).show()
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(res.previewUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(browserIntent)
                    } catch (_: Exception) {}
                } else {
                    val fileName = res.filename ?: "AutonomousAgentPipeline.kt"
                    val syntaxBadge = if (res.syntaxValid) "✓" else "⚠"
                    laptopSyncResult = "$syntaxBadge Saved to Laptop: $fileName (${res.latencyMs.toInt()}ms)"
                    Toast.makeText(context, "Injected into laptop IDE: $fileName", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                val tip = if (err is LaptopBridgeException) err.remediationTip else "Run: python scripts/laptop_ai_server.py"
                laptopSyncResult = "⚠ Laptop Bridge: ${err.message}"
                Toast.makeText(context, "${err.message}\n$tip", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun triggerScan() {
        coroutineScope.launch {
            val info = laptopRepository.checkConnection()
            connectionInfo = info
            if (info.state == ConnectionState.CONNECTED) {
                Toast.makeText(context, "Connected to laptop at ${info.endpointUrl}", Toast.LENGTH_SHORT).show()
                ideaVaultRepo.syncAllPending()
            } else {
                Toast.makeText(context, "Laptop not found on Wi-Fi. Check bridge terminal.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun setManualAddress(raw: String) {
        val res = laptopRepository.setManualAddress(raw)
        res.onSuccess { validUrl ->
            coroutineScope.launch {
                val info = laptopRepository.checkConnection()
                connectionInfo = info
                val msg = if (validUrl.isEmpty()) "Restored Auto-Discovery" else "Saved laptop address: $validUrl"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }.onFailure { err ->
            Toast.makeText(context, "Invalid address: ${err.message}", Toast.LENGTH_LONG).show()
        }
    }

    when (currentScreen) {
        ScreenState.HOME -> {
            HomeScreen(
                promptText = promptText,
                onPromptChange = { promptText = it },
                selectedModel = selectedModel,
                onModelClick = { toggleModelSelection() },
                onMicClick = { startListeningFlow() },
                onPresetSelect = { selectedPrompt ->
                    promptText = selectedPrompt
                    captureIdeaLocally(selectedPrompt)
                    executeNpuInference()
                },
                onNavigateTab = { tab ->
                    when (tab) {
                        "LISTENING" -> startListeningFlow()
                        "CHAT" -> executeNpuInference()
                        else -> currentScreen = ScreenState.HOME
                    }
                },
                onMenuClick = {
                    Toast.makeText(context, "Active Threads: Vector-Sync Orchestrator (Job #0981 • 91%)", Toast.LENGTH_SHORT).show()
                },
                onUserAvatarClick = {
                    Toast.makeText(context, "Finley Vance • Lead Architect • Qualcomm Snapdragon NPU", Toast.LENGTH_SHORT).show()
                },
                onTelemetryClick = { type ->
                    val msg = when (type) {
                        "LATENCY" -> "Snapdragon Hexagon NPU Latency: 18.4ms (Target < 25ms)"
                        "COMPUTE" -> "Compute Allocation: 88.2 TFLOPS INT4/FP16 Dedicated"
                        else -> "Core Neural Engine v4.8 • Qualcomm GenieX SDK Online"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                connectionInfo = connectionInfo,
                onRefreshScan = { triggerScan() },
                onManualAddressSave = { setManualAddress(it) },
                selectedRepo = selectedRepo,
                onOpenGitHubClick = {
                    currentScreen = ScreenState.GITHUB_REPOS
                    refreshGitHub(fetchRepos = true)
                },
                onNewProjectClick = {
                    currentScreen = ScreenState.ENVIRONMENT_SETUP
                    detectHostEnvironment()
                },
                onOpenRepoStudio = {
                    val repo = selectedRepo
                    if (repo != null) {
                        currentScreen = ScreenState.REPO_AI_EDITOR
                        initializeWorkspace(repo)
                    } else {
                        currentScreen = ScreenState.GITHUB_REPOS
                        refreshGitHub(fetchRepos = true)
                    }
                },
                savedIdeas = savedIdeas,
                onSelectIdea = { idea ->
                    promptText = idea.rawPrompt
                    Toast.makeText(context, "Loaded into prompt: ${idea.title}", Toast.LENGTH_SHORT).show()
                },
                onEditIdea = { localId, newTitle, newPrompt ->
                    updateIdeaLocally(localId, newTitle, newPrompt)
                },
                onDeleteIdea = { localId ->
                    deleteIdeaLocally(localId)
                },
                onRetrySyncIdea = { idea ->
                    retrySyncIdea(idea)
                },
                onSyncAllIdeas = {
                    syncAllPendingIdeas()
                },
                onSaveIdea = { text ->
                    captureIdeaLocally(text)
                    promptText = ""
                }
            )

        }


        ScreenState.LISTENING -> {
            VoiceListeningScreen(
                promptText = promptText,
                isListening = isListening,
                onCloseClick = {
                    if (isListening) {
                        voiceManager.stopListening()
                        isListening = false
                    }
                    currentScreen = ScreenState.HOME
                },
                onMicToggle = {
                    if (isListening) {
                        voiceManager.stopListening()
                        isListening = false
                        Toast.makeText(context, "Listening paused", Toast.LENGTH_SHORT).show()
                    } else {
                        startListeningFlow(isResume = true)
                    }
                },
                onSendClick = {
                    if (promptText.isNotBlank()) {
                        captureIdeaLocally(promptText)
                    }
                    executeNpuInference()
                },
                onSamplePromptSelect = { sample ->
                    promptText = sample
                    Toast.makeText(context, "Parameter appended to prompt", Toast.LENGTH_SHORT).show()
                },
                onUserAvatarClick = {
                    Toast.makeText(context, "Finley Vance • Audio Pipeline Active", Toast.LENGTH_SHORT).show()
                }
            )
        }


        ScreenState.CHAT -> {
            ChatExecutionScreen(
                promptText = promptText,
                npuResult = npuResult,
                savedZipFile = savedZipFile,
                isInferring = isInferring,
                isSyncingLaptop = isSyncingLaptop,
                laptopSyncResult = laptopSyncResult,
                onSyncToLaptop = { dispatchToLaptopBridge() },
                onCloseClick = {
                    currentScreen = ScreenState.HOME
                },
                onPackageZipClick = {
                    npuResult?.let { res ->
                        try {
                            val file = manifestGenerator.writeBundleZipToStorage(context, res.config)
                            savedZipFile = file
                            Toast.makeText(context, "Packaging complete: ${file.name} saved!", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Write error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } ?: run {
                        Toast.makeText(context, "Packaging contract manifests...", Toast.LENGTH_SHORT).show()
                    }
                },
                onMicClick = {
                    startListeningFlow()
                },
                onSendNewPrompt = { newPrompt ->
                    promptText = newPrompt
                    executeNpuInference()
                },
                onMoreClick = {
                    Toast.makeText(context, "Session Options: Export ZIP • Re-run NPU • Telemetry Active", Toast.LENGTH_SHORT).show()
                },
                onUserAvatarClick = {
                    Toast.makeText(context, "Finley Vance • Autonomous Session ARB-09", Toast.LENGTH_SHORT).show()
                },
                onAdaPillClick = {
                    Toast.makeText(context, "Ada v4.2 Pro • Qualcomm Hexagon NPU Runtime • 138ms Latency", Toast.LENGTH_SHORT).show()
                },
                connectionInfo = connectionInfo,
                onRefreshScan = { triggerScan() },
                onManualAddressSave = { setManualAddress(it) }
            )
        }

        ScreenState.GITHUB_REPOS -> {
            GitHubReposScreen(
                statusResponse = gitHubStatus,
                repositories = gitHubRepos,
                isLoading = isGitHubLoading,
                errorMessage = gitHubError,
                selectedRepo = selectedRepo,
                deviceCodeInfo = deviceCodeInfo,
                isDevicePolling = isDevicePolling,
                onBack = {
                    cancelDeviceAuthFlow()
                    currentScreen = ScreenState.HOME
                },
                onRefresh = { refreshGitHub(fetchRepos = true) },
                onSelectRepo = { repo ->
                    laptopRepository.saveSelectedRepository(repo)
                    selectedRepo = laptopRepository.selectedRepository
                },
                onClearSelectedRepo = {
                    laptopRepository.clearSelectedRepository()
                    selectedRepo = null
                    Toast.makeText(context, "Target repository cleared", Toast.LENGTH_SHORT).show()
                },
                onRequestDeviceCode = { startDeviceAuthFlow() },
                onCancelDeviceCode = { cancelDeviceAuthFlow() },
                onLogout = { handleLogout() },
                onOpenStudio = {
                    val repo = selectedRepo
                    if (repo != null) {
                        currentScreen = ScreenState.REPO_AI_EDITOR
                        initializeWorkspace(repo)
                    }
                }
            )
        }

        ScreenState.REPO_AI_EDITOR -> {
            val repo = selectedRepo
            if (repo == null) {
                currentScreen = ScreenState.GITHUB_REPOS
            } else {
                RepoAiEditorScreen(
                    selectedRepo = repo,
                    workspaceRecord = workspaceRecord,
                    isWorkspaceLoading = isWorkspaceLoading,
                    workspaceError = workspaceError,
                    isInstructing = isInstructing,
                    instructionResult = instructionResult,
                    diffResponse = diffResponse,
                    isDiffLoading = isDiffLoading,
                    previewResponse = previewResponse,
                    onBack = { currentScreen = ScreenState.HOME },
                    onInitializeWorkspace = { initializeWorkspace(repo) },
                    onSubmitInstruction = { executeInstruction(it) },
                    onRefreshDiff = { refreshDiff() },
                    onStartPreview = { startWorkspacePreview() },
                    onStopPreview = { stopWorkspacePreview() },
                    onCancelInstruction = {
                        instructionJob?.cancel()
                        isInstructing = false
                        Toast.makeText(context, "Instruction cancelled", Toast.LENGTH_SHORT).show()
                    },
                    onReviewPublish = {
                        currentScreen = ScreenState.PUBLISH_REVIEW
                        loadPublishReview()
                    }
                )
            }
        }

        ScreenState.PUBLISH_REVIEW -> {
            val repo = selectedRepo
            val ws = workspaceRecord
            if (repo == null || ws == null) {
                currentScreen = ScreenState.REPO_AI_EDITOR
            } else {
                PublishReviewScreen(
                    workspaceId = ws.workspace_id,
                    reviewResponse = publishReviewResponse,
                    isLoadingReview = isPublishReviewLoading,
                    isCommitting = isCommitting,
                    commitResponse = commitResponse,
                    isPushing = isPushing,
                    pushResponse = pushResponse,
                    errorMessage = publishReviewError,
                    onBack = { currentScreen = ScreenState.REPO_AI_EDITOR },
                    onRefreshReview = { loadPublishReview() },
                    onCommit = { message, files -> handleCommit(message, files) },
                    onPush = { _, _ -> handlePush() }
                )
            }
        }

        ScreenState.ENVIRONMENT_SETUP -> {
            EnvironmentSetupScreen(
                detection = environmentDetection,
                isDetecting = isDetectingEnvironment,
                onDetectEnvironment = { detectHostEnvironment() },
                activeIdea = activeIdea,
                isRefiningIdea = isRefiningIdea,
                onCaptureAndRefineIdea = { t, p -> captureAndRefineIdea(t, p) },
                activePlan = activePlan,
                isPlanning = isPlanningSetup,
                onGeneratePlan = { p, s -> generateSetupPlan(p, s) },
                setupStatus = setupStatus,
                isExecuting = isExecutingSetup,
                onExecutePlan = { executeSetupPlan(it) },
                onCancelSetup = { cancelSetup(it) },
                onStopServer = { stopServer(it) },
                onBack = { currentScreen = ScreenState.HOME }
            )
        }
    }
}

