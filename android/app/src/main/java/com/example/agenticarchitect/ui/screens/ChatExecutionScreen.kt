package com.example.agenticarchitect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
    onMicClick: () -> Unit,
    onSendNewPrompt: (String) -> Unit = {},
    onMoreClick: () -> Unit = {},
    onUserAvatarClick: () -> Unit = {},
    onAdaPillClick: () -> Unit = {}
) {
    var isLaunched by remember { mutableStateOf(false) }
    var chatPromptInput by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "chat_screen_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chat_dot_alpha"
    )

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── 1. Top Fixed Header Bar ────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(SurfaceDark.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh.copy(alpha = 0.5f))
                        .clickable { onCloseClick() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OnSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Conversational Chat",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PrimaryContainer.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Live",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryColor
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLow.copy(alpha = 0.6f))
                            .clickable { onMoreClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor)
                            .clickable { onUserAvatarClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User",
                            tint = OnPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // ── 2. Top Secondary Control (Dismiss Pill) ────────────────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CloseChatPill(
                    onClose = onCloseClick,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // ── 3. Chat Message Stream ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Time Anchor & Encryption Badge
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainer.copy(alpha = dotAlpha))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "E2E AUTONOMOUS SESSION • ARB-09",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                        letterSpacing = 1.2.sp
                    )
                }

                // Bubble 1: User Chat Request (Right Aligned, Gradient Background)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            TertiaryContainer,
                                            PrimaryContainer,
                                            SecondaryContainer
                                        )
                                    )
                                )
                                .padding(14.dp)
                        ) {
                            Text(
                                text = promptText.ifEmpty { "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot." },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                lineHeight = 20.sp
                            )
                        }

                        // Timestamp Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
                        ) {
                            Text(
                                text = "14:02",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OnSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Sent",
                                tint = PrimaryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Bubble 2: AI Agent Output Stream
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Miniature Glowing Metallic Orange Orb Avatar
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(PrimaryColor, PrimaryContainer, Color(0xFF511500))
                                )
                            )
                            .border(1.dp, PrimaryColor.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.9f))
                        )
                    }

                    // AI Content Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // AI Header Metadata Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Ada",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(SurfaceContainerHigh.copy(alpha = 0.8f))
                                        .clickable { onAdaPillClick() }
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "v4.2 Pro",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = PrimaryColor
                                        )
                                    }
                                }
                            }

                            Text(
                                text = if (npuResult != null) "${npuResult.telemetry.latencyMs}ms • Hexagon NPU" else "0.42s • 84 tps",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = OnSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        // AI Text Explanation
                        Text(
                            text = if (npuResult != null) {
                                "I've initialized the ${npuResult.config.architecture} architecture (${npuResult.config.frontendFramework} + ${npuResult.config.dataLayer}). Primed for on-device execution with offline Snapdragon NPU acceleration."
                            } else {
                                "I've initialized the tactical liquidity monitor agent. It will track pool depth, slippage thresholds, and sandwich attack vectors across Uniswap v3 and Curve."
                            },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color.White.copy(alpha = 0.95f),
                            lineHeight = 20.sp
                        )

                        // Parameters Set Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.75f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Parameters set:",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryColor
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = PrimaryColor, fontSize = 12.sp)
                                        Text(
                                            text = "Target: Arbitrum & Mainnet DEX pools",
                                            fontSize = 12.sp,
                                            color = OnSurface
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = PrimaryColor, fontSize = 12.sp)
                                        Text(
                                            text = "Execution trigger: >1.8% volatility spread",
                                            fontSize = 12.sp,
                                            color = OnSurface
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = PrimaryColor, fontSize = 12.sp)
                                        Text(
                                            text = "Guardrail: Slippage capped at 0.15% with flashbots RPC",
                                            fontSize = 12.sp,
                                            color = OnSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Embedded Interactive Artifact Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainerLow.copy(alpha = 0.95f))
                                .border(1.dp, Color(0x33FF570B), RoundedCornerShape(16.dp))
                                .clickable {
                                    isLaunched = !isLaunched
                                    onPackageZipClick()
                                }
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SmartToy,
                                            contentDescription = null,
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (npuResult != null) "Contract #${npuResult.config.projectName}" else "Contract #0x89F...7A",
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (isLaunched) Color(0x3310B981) else PrimaryContainer.copy(alpha = 0.15f)
                                            )
                                            .clickable {
                                                isLaunched = !isLaunched
                                                onPackageZipClick()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isLaunched) "Live" else "Staged",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isLaunched) Color(0xFF10B981) else PrimaryColor
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Estimated Gas",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = OnSurfaceVariant
                                        )
                                        Text(
                                            text = "0.0028 ETH",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Sub-routines",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = OnSurfaceVariant
                                        )
                                        Text(
                                            text = "3 Deployed",
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isLaunched) SolidColor(SurfaceContainerHighest) else Brush.horizontalGradient(
                                                    colors = listOf(PrimaryContainer, SecondaryContainer)
                                                )
                                            )
                                            .clickable {
                                                isLaunched = true
                                                onPackageZipClick()
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = if (isLaunched) "✓ Live" else "APPROVE & LAUNCH",
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLaunched) PrimaryColor else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Processing Status Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryColor.copy(alpha = dotAlpha))
                            )
                            Text(
                                text = "Ada is ready • Snapdragon Hexagon NPU",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── 4. Suspended Floating Bottom Chat Bar ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceContainerHigh.copy(alpha = 0.90f))
                    .border(1.dp, Color(0x33FF570B), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Attachment Trigger
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLowest.copy(alpha = 0.5f))
                            .clickable {
                                chatPromptInput = "Deploy high-performance Golang microservice with Kafka"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Text Input
                    BasicTextField(
                        value = chatPromptInput,
                        onValueChange = { chatPromptInput = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.5.sp
                        ),
                        cursorBrush = SolidColor(PrimaryColor),
                        decorationBox = { innerTextField ->
                            if (chatPromptInput.isEmpty()) {
                                Text(
                                    text = "Ask AI a question",
                                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    )

                    // Right Actions: Mic + Send
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerLowest.copy(alpha = 0.6f))
                                .clickable { onMicClick() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Mic",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(PrimaryContainer, SecondaryContainer)
                                    )
                                )
                                .clickable {
                                    val promptToSend = if (chatPromptInput.isNotBlank()) {
                                        chatPromptInput
                                    } else {
                                        "Deploy high-performance Golang microservice with Kafka and PostgreSQL."
                                    }
                                    onSendNewPrompt(promptToSend)
                                    chatPromptInput = ""
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
