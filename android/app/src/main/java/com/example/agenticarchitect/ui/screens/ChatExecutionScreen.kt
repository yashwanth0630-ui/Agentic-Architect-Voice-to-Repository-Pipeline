package com.example.agenticarchitect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.generator.MobilePromptIngestionResult
import com.example.agenticarchitect.theme.*
import com.example.agenticarchitect.ui.components.CloseChatPill
import com.example.agenticarchitect.ui.components.EmberBackground
import java.io.File

@Composable
fun ChatExecutionScreen(
    promptText: String,
    npuResult: MobilePromptIngestionResult?,
    savedZipFile: File?,
    isInferring: Boolean,
    onCloseClick: () -> Unit,
    onPackageZipClick: () -> Unit,
    onMicClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chat_screen_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── 1. Top Close Chat Pill ────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CloseChatPill(
                    onClose = onCloseClick,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            // ── 2. Chat Message Stream ────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Bubble 1: User Request (Right Aligned)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFB83A14),
                                        Color(0xFF8B2508)
                                    )
                                )
                            )
                            .border(1.dp, Color(0x33FFA726), RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = promptText.ifEmpty { "Deploy an autonomous AI agent to monitor liquidity pools" },
                            fontSize = 13.5.sp,
                            color = Color.White,
                            lineHeight = 19.sp
                        )
                    }
                }

                // Bubble 2: AI Agent Response (Left Aligned with Avatar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    // Small circular glowing AI avatar
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(EmberOrangePrimary, EmberDeepRust)
                                )
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .clip(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                            .background(Color(0x5923120A))
                            .border(1.dp, Color(0x2BFF8A50), RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = if (isInferring) {
                                "Qualcomm Hexagon NPU is processing prompt locally on-device..."
                            } else {
                                "Deployed ${npuResult?.config?.architecture ?: "AlphaReactor v4"} to your agent network. The automation loop is active. Need to configure risk mitigation parameters?"
                            },
                            fontSize = 13.5.sp,
                            color = Color(0xFFF3ECE8),
                            lineHeight = 19.sp
                        )
                    }
                }

                // Bubble 3: Follow-up user parameter request (Right Aligned)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x40B83A14))
                            .border(1.dp, Color(0x33FFA726), RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Show me the active core parameters",
                            fontSize = 13.sp,
                            color = Color(0xFFF0DDD5)
                        )
                    }
                }

                // Bubble 4: AI Response + Status + On-Device NPU Telemetry
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FF5722))
                            .border(1.dp, Color(0x4DFF5722), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmberOrangePrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(0.92f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                                .background(Color(0x4D23120A))
                                .border(1.dp, Color(0x2BFF8A50), RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Core agent parameters initialized.",
                                    fontSize = 13.sp,
                                    color = Color.White
                                )

                                // Status Badge: "Aris is working..."
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(EmberOrangePrimary.copy(alpha = dotAlpha), CircleShape)
                                    )
                                    Text(
                                        text = if (isInferring) "Aris is working..." else "Hexagon NPU (45 TOPS) compiled manifests",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFC7AAA0)
                                    )
                                }
                            }
                        }

                        // Hardware NPU Telemetry & Packaging Card
                        npuResult?.let { result ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x3D1F0E08))
                                    .border(1.dp, Color(0x33FFA07A), RoundedCornerShape(16.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "⚡ QUALCOMM GENIEX NPU TELEMETRY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmberOrangeLight
                                    )
                                    Text("• NPU Accelerator: ${result.telemetry.hardwareAccelerator}", fontSize = 11.5.sp, color = Color.White)
                                    Text("• On-Device Latency: ${result.telemetry.latencyMs} ms (Zero Cloud Egress)", fontSize = 11.5.sp, color = Color(0xFF81C784))
                                    Text("• Quantization: ${result.telemetry.quantization} • ${result.telemetry.memoryFootprintMb} MB RAM", fontSize = 11.5.sp, color = Color(0xFFFFD54F))
                                    Text("• Architecture: ${result.config.architecture}", fontSize = 11.5.sp, color = Color(0xFFD7CCC8))
                                }
                            }

                            // Action Button: Package & Save bundle.zip
                            Button(
                                onClick = onPackageZipClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmberOrangePrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Package & Save bundle.zip on Phone",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Save Confirmation Banner
                        savedZipFile?.let { file ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x590E3820))
                                    .border(1.dp, Color(0x804CAF50), RoundedCornerShape(14.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF81C784),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Ready for iQOO Office Kit Transfer",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = file.absolutePath,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFA5D6A7)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── 3. Bottom Input Bar ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x381C0E08))
                        .border(1.dp, Color(0x2BFF8A50), RoundedCornerShape(24.dp))
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ask AI a question",
                            color = Color(0xFF8E766D),
                            fontSize = 13.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onMicClick() }
                        )
                    }
                }
            }
        }
    }
}
