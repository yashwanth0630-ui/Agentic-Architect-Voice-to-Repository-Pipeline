package com.example.agenticarchitect.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.agenticarchitect.theme.*

@Composable
fun CelestialGlowingOrb(
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    isListening: Boolean = true
) {
    // Pulse animation for organic breathing effect
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (isListening) 1.05f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 1200 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = if (isListening) 0.85f else 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 1000 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size * 1.4f)
    ) {
        // 1. Ambient Breathing Halo Glow behind Orb
        Canvas(
            modifier = Modifier
                .size(size * 1.35f)
                .scale(pulseScale)
        ) {
            val centerOffset = Offset(this.size.width / 2f, this.size.height / 2f)
            val glowRadius = this.size.width / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        EmberOrangePrimary.copy(alpha = haloAlpha * 0.7f),
                        EmberOrangeGlow.copy(alpha = haloAlpha * 0.35f),
                        EmberBurntAmber.copy(alpha = haloAlpha * 0.15f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = centerOffset
            )
        }

        // 2. 3D-Shaded Metallic Celestial Sphere
        Canvas(
            modifier = Modifier
                .size(size)
                .scale(pulseScale)
        ) {
            val orbRadius = this.size.width / 2f
            val centerOffset = Offset(orbRadius, orbRadius)
            
            // Specular light source offset towards top-left / center-top
            val lightSource = Offset(orbRadius * 0.75f, orbRadius * 0.68f)

            // Primary 3D Sphere Radial Shader
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.00f to OrbSpecular,
                        0.15f to OrbGold,
                        0.36f to OrbOrangeCore,
                        0.62f to Color(0xFFE64A19),
                        0.85f to OrbCopperDark,
                        1.00f to OrbDeepShadow
                    ),
                    center = lightSource,
                    radius = orbRadius * 1.08f
                ),
                radius = orbRadius,
                center = centerOffset
            )

            // Subtle top-left rim specular glow highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x80FFFFFF),
                        Color(0x33FFD54F),
                        Color.Transparent
                    ),
                    center = Offset(orbRadius * 0.65f, orbRadius * 0.55f),
                    radius = orbRadius * 0.5f
                ),
                radius = orbRadius * 0.45f,
                center = Offset(orbRadius * 0.65f, orbRadius * 0.55f)
            )
        }

        // 3. Central Rotating Gyro Wave Core (Matching Stitch Design)
        val gyroRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "gyro_spin"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size * 0.32f)
                .clip(CircleShape)
                .background(Color(0x330E0E11))
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Core Waveform",
                tint = PrimaryColor,
                modifier = Modifier
                    .size(size * 0.22f)
                    .rotate(gyroRotation)
            )
        }
    }
}
