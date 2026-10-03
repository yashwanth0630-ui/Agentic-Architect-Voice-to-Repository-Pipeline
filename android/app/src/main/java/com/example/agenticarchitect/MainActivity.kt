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
import com.example.agenticarchitect.generator.AgentManifestGenerator
import com.example.agenticarchitect.generator.MobilePromptIngestionResult
import com.example.agenticarchitect.npu.QualcommGenieXNpuEngine
import com.example.agenticarchitect.theme.DarkObsidian
import com.example.agenticarchitect.theme.EmberOrangePrimary
import com.example.agenticarchitect.ui.screens.ChatExecutionScreen
import com.example.agenticarchitect.ui.screens.HomeScreen
import com.example.agenticarchitect.ui.screens.VoiceListeningScreen
import com.example.agenticarchitect.voice.VoicePromptManager
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

    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
    var promptText by remember { 
        mutableStateOf("Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot.") 
    }
    var isListening by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf("● Opus 4.6") }
    var npuResult by remember { mutableStateOf<MobilePromptIngestionResult?>(null) }
    var savedZipFile by remember { mutableStateOf<File?>(null) }
    var isInferring by remember { mutableStateOf(false) }

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

    fun startListeningFlow() {
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
                onListeningStateChange = { state -> isListening = state }
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

        val modelId = if (selectedModel.contains("Phi", ignoreCase = true)) {
            QualcommGenieXNpuEngine.MODEL_PHI_4
        } else {
            QualcommGenieXNpuEngine.MODEL_QWEN_7B
        }

        val result = npuEngine.inferProjectFromPrompt(promptText, modelId)
        npuResult = result
        isInferring = false
    }

    fun toggleModelSelection() {
        selectedModel = when (selectedModel) {
            "● Opus 4.6" -> "⚡ Qwen 2.5-Coder INT4"
            "⚡ Qwen 2.5-Coder INT4" -> "⚡ Phi-4-Mini INT4"
            else -> "● Opus 4.6"
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
                    currentScreen = ScreenState.LISTENING
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
                    } else {
                        startListeningFlow()
                    }
                },
                onSendClick = {
                    executeNpuInference()
                }
            )
        }

        ScreenState.CHAT -> {
            ChatExecutionScreen(
                promptText = promptText,
                npuResult = npuResult,
                savedZipFile = savedZipFile,
                isInferring = isInferring,
                onCloseClick = {
                    currentScreen = ScreenState.HOME
                },
                onPackageZipClick = {
                    npuResult?.let { res ->
                        try {
                            val file = manifestGenerator.writeBundleZipToStorage(context, res.config)
                            savedZipFile = file
                            Toast.makeText(context, "Saved to ${file.absolutePath}!", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Write error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onMicClick = {
                    startListeningFlow()
                }
            )
        }
    }
}
