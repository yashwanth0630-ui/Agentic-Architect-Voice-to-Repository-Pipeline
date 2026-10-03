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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.agenticarchitect.generator.AgentManifestGenerator
import com.example.agenticarchitect.generator.MobilePromptIngestionResult
import com.example.agenticarchitect.npu.QualcommGenieXNpuEngine
import com.example.agenticarchitect.voice.VoicePromptManager
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF080C14),
                    surface = Color(0xFF0F172A),
                    primary = Color(0xFF06B6D4),
                    secondary = Color(0xFFF59E0B)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF080C14)
                ) {
                    AgenticArchitectScreen()
                }
            }
        }
    }
}

@Composable
fun AgenticArchitectScreen() {
    val context = LocalContext.current
    val voiceManager = remember { VoicePromptManager(context) }
    val npuEngine = remember { QualcommGenieXNpuEngine() }
    val manifestGenerator = remember { AgentManifestGenerator() }

    var promptText by remember { mutableStateOf("FastAPI backend, Next.js frontend, Tailwind UI, Prisma ORM") }
    var isListening by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(QualcommGenieXNpuEngine.MODEL_QWEN_7B) }
    var npuResult by remember { mutableStateOf<MobilePromptIngestionResult?>(null) }
    var savedZipFile by remember { mutableStateOf<File?>(null) }
    var isInferring by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
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

    // Microphone Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    val micBgColor by animateColorAsState(
        targetValue = if (isListening) Color(0xFFEF4444) else Color(0xFF06B6D4),
        label = "mic_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Header ──────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Text(
                    text = "Agentic Architect",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Text(
                text = "iQOO 13 Flagship • Snapdragon 8 Gen 3 / 8 Elite",
                fontSize = 12.sp,
                color = Color(0xFFF59E0B),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Qualcomm GenieX SDK • 45 TOPS Hexagon NPU",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // ── 2. Large Microphone Button ────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(120.dp)
        ) {
            // Pulsing Ring
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulseScale)
                        .background(Color(0x33EF4444), CircleShape)
                )
            }

            // Main Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        Brush.radialGradient(
                            colors = if (isListening) listOf(Color(0xFFFF5252), Color(0xFFD32F2F))
                            else listOf(Color(0xFF22D3EE), Color(0xFF0284C7))
                        ),
                        shape = CircleShape
                    )
                    .clickable {
                        if (isListening) {
                            voiceManager.stopListening()
                            isListening = false
                        } else {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                isListening = true
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
                    }
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Input Microphone",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Text(
            text = if (isListening) "🎙️ Listening... Speak your prompt" else "Tap microphone to speak requirement",
            fontSize = 13.sp,
            color = if (isListening) Color(0xFFEF4444) else Color(0xFF94A3B8),
            fontWeight = FontWeight.SemiBold
        )

        // ── 3. Spoken Prompt Display & Editor ─────────────────
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Recognized Developer Prompt:",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 13.sp,
                        color = Color.White
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF080C14),
                        unfocusedContainerColor = Color(0xFF080C14),
                        focusedIndicatorColor = Color(0xFF06B6D4),
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                )
            }
        }

        // Quick Preset Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "FastAPI + Next.js" to "FastAPI backend, Next.js frontend, Tailwind UI, Prisma ORM",
                "Spring Boot + Kotlin" to "Spring Boot 3.4 microservice with Kotlin 2.1, PostgreSQL, Next.js",
                "Go Gin + Vue" to "Go 1.24 Gin backend, Vue 3 frontend, Vanilla CSS"
            ).forEach { (label, preset) ->
                SuggestionChip(
                    onClick = { promptText = preset },
                    label = { Text(label, fontSize = 11.sp, color = Color(0xFFCBD5E1)) }
                )
            }
        }

        // ── 4. Qualcomm GenieX NPU Trigger & Model Selector ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedModel == QualcommGenieXNpuEngine.MODEL_QWEN_7B,
                onClick = { selectedModel = QualcommGenieXNpuEngine.MODEL_QWEN_7B },
                label = { Text("Qwen 2.5-Coder INT4", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedModel == QualcommGenieXNpuEngine.MODEL_PHI_4,
                onClick = { selectedModel = QualcommGenieXNpuEngine.MODEL_PHI_4 },
                label = { Text("Phi-4-Mini INT4", fontSize = 11.sp) }
            )
        }

        Button(
            onClick = {
                isInferring = true
                val result = npuEngine.inferProjectFromPrompt(promptText, selectedModel)
                npuResult = result
                isInferring = false
                Toast.makeText(context, "NPU Inference Complete: ${result.telemetry.latencyMs}ms", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF59E0B)
            )
        ) {
            Text(
                text = if (isInferring) "NPU Inferring..." else "⚡ Run Qualcomm GenieX NPU",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // ── 5. Telemetry & Hardware Card ──────────────────────
        npuResult?.let { result ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ON-DEVICE NPU TELEMETRY (GENIEX SDK)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF06B6D4)
                    )
                    Text("• Hardware: ${result.telemetry.hardwareAccelerator}", fontSize = 12.sp, color = Color.White)
                    Text("• Latency: ${result.telemetry.latencyMs} ms (Sub-second turnaround)", fontSize = 12.sp, color = Color(0xFF10B981))
                    Text("• Quantization: ${result.telemetry.quantization} • ${result.telemetry.memoryFootprintMb} MB RAM", fontSize = 12.sp, color = Color(0xFFF59E0B))
                    Text("• Network: 100% OFFLINE (Zero Cloud Egress)", fontSize = 12.sp, color = Color(0xFF10B981))
                    Text("• Inferred Architecture: ${result.config.architecture}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }

            // ── 6. Save bundle.zip on Phone ────────────────────
            Button(
                onClick = {
                    try {
                        val file = manifestGenerator.writeBundleZipToStorage(context, result.config)
                        savedZipFile = file
                        Toast.makeText(context, "Saved to ${file.absolutePath}!", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Write error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF06B6D4)
                )
            ) {
                Text(
                    text = "📦 Package & Save bundle.zip on Phone",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // ── 7. File Status & iQOO Office Kit Transfer Banner ─
        savedZipFile?.let { file ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "✅ bundle.zip Ready for iQOO Office Kit",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Saved Path: ${file.absolutePath}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFA7F3D0)
                    )
                    Text(
                        text = "Size: ${file.length()} bytes • Contains AGENTS.md, .cursorrules, CLAUDE.md, GEMINI.md, skills/ & package.json",
                        fontSize = 11.sp,
                        color = Color(0xFFD1FAE5)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Next: On laptop, run ./scripts/bootstrap.sh or .\\scripts\\bootstrap.ps1 to unpack and boot IDE!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFDE68A)
                    )
                }
            }
        }
    }
}
