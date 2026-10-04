package com.example.agenticarchitect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.theme.*
import com.example.agenticarchitect.ui.components.CelestialGlowingOrb
import com.example.agenticarchitect.ui.components.CloseChatPill
import com.example.agenticarchitect.ui.components.EmberBackground

@Composable
fun VoiceListeningScreen(
    promptText: String,
    isListening: Boolean,
    onCloseClick: () -> Unit,
    onMicToggle: () -> Unit,
    onSendClick: () -> Unit,
    onSamplePromptSelect: (String) -> Unit = {},
    onUserAvatarClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_screen_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice_dot_alpha"
    )

    // Waveform heights animations (9 bars)
    val bar1 by infiniteTransition.animateFloat(initialValue = 8f, targetValue = 22f, animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "b1")
    val bar2 by infiniteTransition.animateFloat(initialValue = 14f, targetValue = 30f, animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "b2")
    val bar3 by infiniteTransition.animateFloat(initialValue = 20f, targetValue = 42f, animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "b3")
    val bar4 by infiniteTransition.animateFloat(initialValue = 28f, targetValue = 48f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "b4")
    val bar5 by infiniteTransition.animateFloat(initialValue = 32f, targetValue = 54f, animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "b5")
    val bar6 by infiniteTransition.animateFloat(initialValue = 26f, targetValue = 44f, animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "b6")
    val bar7 by infiniteTransition.animateFloat(initialValue = 18f, targetValue = 36f, animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "b7")
    val bar8 by infiniteTransition.animateFloat(initialValue = 12f, targetValue = 26f, animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "b8")
    val bar9 by infiniteTransition.animateFloat(initialValue = 6f, targetValue = 18f, animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse), label = "b9")

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── 1. Top Header Bar ──────────────────────────────────────────
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
                        text = "Active Listening",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PrimaryContainer.copy(alpha = 0.15f))
                            .clickable { onMicToggle() }
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

            // ── 2. Top Secondary Dismiss Pill ──────────────────────────────
            CloseChatPill(
                onClose = onCloseClick,
                modifier = Modifier.padding(top = 4.dp)
            )

            // ── 3. 3D Volumetric Celestial Glowing Orb ─────────────────────
            CelestialGlowingOrb(
                size = 200.dp,
                isListening = isListening,
                modifier = Modifier.clickable { onMicToggle() }
            )

            // ── 4. Transcription Typography & Audio Waveform ───────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Ada is listening... Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor.copy(alpha = dotAlpha))
                    )
                    Text(
                        text = "ADA IS LISTENING...",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.2.sp
                    )
                }

                // Live Spoken Prompt Text
                Text(
                    text = "“${promptText.ifEmpty { "Deploy an autonomous AI agent to monitor liquidity pools. Make it a tactical trading bot." }}”",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 27.sp
                )

                // 9-Bar Frequency Visualizer Waveform
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceContainerHigh.copy(alpha = 0.5f))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                        .clickable { onMicToggle() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(bar1, bar2, bar3, bar4, bar5, bar6, bar7, bar8, bar9).forEachIndexed { idx, barHeight ->
                            val barColor = when (idx) {
                                in 3..5 -> PrimaryContainer
                                in 2..6 -> TertiaryColor
                                else -> PrimaryColor
                            }
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(barColor)
                            )
                        }
                    }
                }

                // Contextual Quick Action Capsule Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                            .clickable {
                                onSamplePromptSelect("$promptText targeting Arbitrum & Mainnet DEX pools")
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "+ Add target DEX",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(50))
                            .clickable {
                                onSamplePromptSelect("$promptText with slippage capped at 0.5%")
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "⚡ Max Slippage 0.5%",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            // ── 5. Floating Glassmorphic Control Dock ──────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(50))
                        .background(SurfaceContainerLowest.copy(alpha = 0.85f))
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Unmute Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainerHigh.copy(alpha = 0.6f))
                            .clickable { onMicToggle() }
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Toggle Mute",
                            tint = if (isListening) OnSurfaceVariant else PrimaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Crimson Stop Recording Button with Sharp Solid Square
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFFE53935), Color(0xFFB71C1C))
                                )
                            )
                            .clickable { onSendClick() }
                    ) {
                        // Sharp Solid Square Stop Icon
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White)
                        )
                    }

                    // Direct Send Action Arrow Button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(PrimaryContainer, SecondaryContainer)
                                )
                            )
                            .clickable { onSendClick() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
