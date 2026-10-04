package com.example.agenticarchitect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.agenticarchitect.theme.*
import com.example.agenticarchitect.ui.components.EmberBackground
import com.example.agenticarchitect.ui.components.PresetActionCard

@Composable
fun HomeScreen(
    promptText: String,
    onPromptChange: (String) -> Unit,
    selectedModel: String,
    onModelClick: () -> Unit,
    onMicClick: () -> Unit,
    onPresetSelect: (String) -> Unit,
    onNavigateTab: (String) -> Unit = {},
    onTuneClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onUserAvatarClick: () -> Unit = {},
    onTelemetryClick: (String) -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "home_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "home_dot_alpha"
    )

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── 1. Dashboard Top Header Bar (Matching Stitch Header) ─────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(SurfaceDark.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHighest.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PrimaryColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Dashboard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            letterSpacing = (-0.3).sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer.copy(alpha = dotAlpha))
                            )
                            Text(
                                text = "ONLINE",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerLow.copy(alpha = 0.6f))
                            .clickable { onMenuClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
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
                            contentDescription = "User Avatar",
                            tint = OnPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // ── 2. Scrollable Dashboard Body ────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Profile Sub-Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(SurfaceContainerHighest, SurfaceContainer)
                                    )
                                )
                                .clickable { onUserAvatarClick() }
                        ) {
                            Text(
                                text = "F",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryContainer)
                                    .border(1.5.dp, SurfaceDark, CircleShape)
                            )
                        }

                        Column {
                            Text(
                                text = "WELCOME BACK",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Finley Vance",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerLow.copy(alpha = 0.7f))
                                .clickable { onMenuClick() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Apps",
                                tint = OnSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Hero Header Block
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                            .clickable { onTelemetryClick("NPU") }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryColor)
                            )
                            Text(
                                text = "CORE NEURAL ENGINE V4.8 • QUALCOMM GENIEX SDK",
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryColor,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Text(
                        text = "What are we building today?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 34.sp,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = "High-concurrency autonomous pipelines armed for multimodal generation and telemetry.",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                // Rapid Directives Horizontal Scroll
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RAPID DIRECTIVES",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Swipe to view",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryColor
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PresetActionCard(
                            title = "Deploy autonomous agent swarm",
                            prompt = "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot.",
                            icon = Icons.Default.SmartToy,
                            onClick = onPresetSelect
                        )

                        PresetActionCard(
                            title = "Optimize gas for ZK-proofs",
                            prompt = "Optimize gas consumption and prover pipelines for ZK-rollups on EVM L2s.",
                            icon = Icons.Default.Bolt,
                            onClick = onPresetSelect
                        )

                        PresetActionCard(
                            title = "Audit smart contract security",
                            prompt = "Perform comprehensive security audit and invariant checks on Solidity smart contracts.",
                            icon = Icons.Default.Security,
                            onClick = onPresetSelect
                        )

                        PresetActionCard(
                            title = "Analyze liquidity pool depth",
                            prompt = "Analyze liquidity pool depth, slippage thresholds, and sandwich attack vectors across Uniswap v3 and Curve.",
                            icon = Icons.Default.ShowChart,
                            onClick = onPresetSelect
                        )
                    }
                }

                // Active Agent Threads Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE AGENT THREADS",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.clickable { onMenuClick() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryContainer)
                            )
                            Text(
                                text = "3 Running",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SecondaryColor
                            )
                        }
                    }

                    // Vector-Sync Orchestrator Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceContainerLow.copy(alpha = 0.9f))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                            .clickable { onNavigateTab("CHAT") }
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceContainerHigh)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SmartToy,
                                            contentDescription = null,
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Vector-Sync Orchestrator",
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Job #0981 · 4,200 tok/sec",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = OnSurfaceVariant
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(PrimaryContainer.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Active",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryColor
                                    )
                                }
                            }

                            // Inner Progress Pill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceContainerLowest.copy(alpha = 0.85f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(PrimaryColor.copy(alpha = dotAlpha))
                                        )
                                        Text(
                                            text = "Compiling recursive SNARK artifacts for mainnet...",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = OnSurface,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = "91%",
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryColor
                                    )
                                }
                            }

                            // Card Footer Stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Memory,
                                            contentDescription = null,
                                            tint = PrimaryColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "1.4 GB",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = OnSurfaceVariant
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AltRoute,
                                            contentDescription = null,
                                            tint = SecondaryColor,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "12 nodes",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = OnSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.clickable { onNavigateTab("CHAT") }
                                ) {
                                    Text(
                                        text = "Inspect",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = PrimaryColor
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Secondary Visual Telemetry Panel (2 Columns)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Latency Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainer.copy(alpha = 0.6f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .clickable { onTelemetryClick("LATENCY") }
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LATENCY",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "18.4",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "ms",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(SurfaceContainerHighest)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.75f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(SecondaryColor, PrimaryColor)
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        // Compute Alloc Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceContainer.copy(alpha = 0.6f))
                                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
                                .clickable { onTelemetryClick("COMPUTE") }
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "COMPUTE ALLOC",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = null,
                                        tint = TertiaryColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "88.2",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "TFLOPS",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = OnSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(SurfaceContainerHighest)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(TertiaryContainer, PrimaryContainer)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Bottom Suspended Floating Command Input & Nav Rail ──────
            Column {
                // Suspended Floating Input Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceContainerLow.copy(alpha = 0.95f))
                        .border(1.dp, Color(0x33FF570B), RoundedCornerShape(20.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Text Field
                        BasicTextField(
                            value = promptText,
                            onValueChange = onPromptChange,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(PrimaryColor),
                            decorationBox = { innerTextField ->
                                if (promptText.isEmpty()) {
                                    Text(
                                        text = "Ask AI a question or describe your idea",
                                        color = OnSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceContainerLowest.copy(alpha = 0.8f))
                                .padding(10.dp)
                        )

                        // Bottom Row: Model Pill + Mic + Send
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(SurfaceContainerHighest.copy(alpha = 0.6f))
                                    .clickable { onModelClick() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryColor)
                                    )
                                    Text(
                                        text = selectedModel,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Elevated Glowing Mic Button
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(PrimaryContainer, OrangeMicGradientEnd)
                                            )
                                        )
                                        .clickable { onMicClick() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice Input",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Send Action Button
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainerHighest.copy(alpha = 0.6f))
                                        .clickable {
                                            val textToSend = if (promptText.isNotBlank()) promptText else "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot."
                                            onPresetSelect(textToSend)
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Send",
                                        tint = PrimaryColor,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom 3-Tab Navigation Rail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(SurfaceContainerLowest.copy(alpha = 0.85f))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(0.dp)),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateTab("HOME") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BubbleChart,
                            contentDescription = "Assistant",
                            tint = PrimaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Assistant", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryColor)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateTab("LISTENING") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schema,
                            contentDescription = "Agents",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Agents", fontSize = 10.sp, color = OnSurfaceVariant)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateTab("CHAT") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Artifacts",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text("Artifacts", fontSize = 10.sp, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}
