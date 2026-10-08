package com.example.agenticarchitect

import android.Manifest
import android.content.pm.PackageManager
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
import com.example.agenticarchitect.theme.DarkObsidian
import com.example.agenticarchitect.theme.EmberOrangePrimary
import com.example.agenticarchitect.ui.screens.ChatExecutionScreen
import com.example.agenticarchitect.ui.screens.HomeScreen
import com.example.agenticarchitect.ui.screens.VoiceListeningScreen
import com.example.agenticarchitect.voice.VoicePromptManager
import kotlinx.coroutines.launch
import java.io.File

enum class ScreenState {
    HOME,
    LISTENING,
    CHAT
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

    LaunchedEffect(Unit) {
        if (npuResult == null) {
            val modelId = QualcommGenieXNpuEngine.MODEL_QWEN_7B
            npuResult = npuEngine.inferProjectFromPrompt(promptText, modelId)
        }
        // Auto-discover laptop bridge on shared Wi-Fi
        connectionInfo = laptopRepository.checkConnection()
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
                val fileName = res.filename ?: "AutonomousAgentPipeline.kt"
                val syntaxBadge = if (res.syntaxValid) "✓" else "⚠"
                laptopSyncResult = "$syntaxBadge Saved to Laptop: $fileName (${res.latencyMs.toInt()}ms)"
                Toast.makeText(context, "Injected into laptop IDE: $fileName", Toast.LENGTH_SHORT).show()
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
                onManualAddressSave = { setManualAddress(it) }
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

    }
}
