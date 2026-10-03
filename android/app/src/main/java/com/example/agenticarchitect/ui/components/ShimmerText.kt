package com.example.agenticarchitect.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun ShimmerText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontFamily: FontFamily = FontFamily.Monospace,
    durationMs: Int = 1000
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer_transition")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x55FFFFFF),
            Color(0xFFFFFFFF),
            Color(0x55FFFFFF)
        ),
        start = Offset(offset * 250f, 0f),
        end = Offset((offset + 0.8f) * 250f, 0f)
    )

    Text(
        text = text,
        fontSize = fontSize,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        style = TextStyle(brush = shimmerBrush),
        modifier = modifier
    )
}

@Composable
fun TextShimmerBasic(
    modifier: Modifier = Modifier
) {
    ShimmerText(
        text = "Generating code...",
        modifier = modifier,
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
        durationMs = 1000
    )
}
