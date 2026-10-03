package com.example.agenticarchitect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.agenticarchitect.theme.DarkObsidian

@Composable
fun EmberBackground(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkObsidian)
            .drawBehind {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 1. Primary Warm Radiant Sunset / Ember Glow centered at top-right
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE84D1C),
                            Color(0xFFC03914),
                            Color(0xFF6B1E0A),
                            Color(0xFF260A03),
                            Color(0x000A0503)
                        ),
                        center = Offset(canvasWidth * 0.7f, canvasHeight * 0.10f),
                        radius = canvasWidth * 1.15f
                    )
                )

                // 2. Secondary Top Linear Fill for smooth atmospheric blend
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0x40E84D1C),
                            Color(0x228B2508),
                            Color(0x000A0503)
                        ),
                        startY = 0f,
                        endY = canvasHeight * 0.55f
                    )
                )
            }
    ) {
        content()
    }
}
