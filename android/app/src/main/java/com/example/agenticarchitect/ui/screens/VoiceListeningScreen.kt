package com.example.agenticarchitect.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
    onSendClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_screen_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.14f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    EmberBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── 1. Top Close Chat Pill ────────────────────────────
            CloseChatPill(
                onClose = onCloseClick,
                modifier = Modifier.padding(top = 10.dp)
            )

            // ── 2. Center: 3D Celestial Glowing Orb & Subtitle ─────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CelestialGlowingOrb(
                    size = 210.dp,
                    isListening = isListening
                )

                // Subtitle: "Aris is listening..."
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(EmberOrangePrimary.copy(alpha = dotAlpha), CircleShape)
                    )
                    Text(
                        text = if (isListening) "Aris is listening..." else "Ready to run on Snapdragon NPU",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFFCBB2A9)
                    )
                }
            }

            // ── 3. Spoken Prompt Transcription ────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = promptText.ifEmpty { "Speak your requirements..." },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 32.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            // ── 4. Bottom Floating Controls Bar ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Button: Waveform / Equalizer
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0x382B140A))
                        .border(1.dp, Color(0x33FFA07A), CircleShape)
                        .clickable { /* Audio visualization toggle */ }
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Waveform",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Center Button: Glowing Orange Microphone
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    OrangeMicGradientStart,
                                    OrangeMicGradientEnd
                                )
                            )
                        )
                        .clickable { onMicToggle() }
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Right Button: Send / Arrow
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0x382B140A))
                        .border(1.dp, Color(0x33FFA07A), CircleShape)
                        .clickable { onSendClick() }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send / Infer",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
