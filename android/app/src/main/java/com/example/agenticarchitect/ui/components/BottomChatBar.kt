package com.example.agenticarchitect.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agenticarchitect.theme.*

@Composable
fun BottomChatBar(
    promptText: String,
    onPromptChange: (String) -> Unit,
    selectedModel: String,
    onModelClick: () -> Unit,
    onMicClick: () -> Unit,
    onSubmit: () -> Unit,
    isListening: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bottom_mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bottom_mic_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Text Input Capsule
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
            if (promptText.isEmpty()) {
                Text(
                    text = "Ask AI a question or describe your idea",
                    color = Color(0xFF8E766D),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                )
            }
            BasicTextField(
                value = promptText,
                onValueChange = onPromptChange,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(EmberOrangePrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Controls Row: Model Selector Pill (Left) + Glowing Orange Mic Button (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model Selector Pill (e.g. "● Opus 4.6" or "⚡ Qwen 2.5-Coder INT4")
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x40251108))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .clickable { onModelClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFFFF9800), CircleShape)
                    )
                    Text(
                        text = selectedModel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2D6D0)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Model",
                        tint = Color(0xFFB09990),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Glowing Vibrant Orange Mic Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                OrangeMicGradientStart,
                                OrangeMicGradientEnd
                            )
                        )
                    )
                    .clickable { onMicClick() }
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Microphone",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
