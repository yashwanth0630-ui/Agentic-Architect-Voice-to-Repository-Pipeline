package com.example.agenticarchitect.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.agenticarchitect.theme.EmberOrangePrimary
import kotlin.math.*

/**
 * Rose Orbit mathematical curve loader:
 * r(t) = 7.0 - 2.7s cos(7t)
 * x(t) = 50 + cos t · r(t) · 3.9
 * y(t) = 50 + sin t · r(t) · 3.9
 */
@Composable
fun RoseOrbitLoader(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    color: Color = EmberOrangePrimary,
    particleCount: Int = 72
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rose_orbit_anim")

    // Rotation: 28000ms counter-clockwise
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rose_rotation"
    )

    // Progress: 5200ms along the curve
    val curveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "curve_progress"
    )

    // Pulse: 4600ms breathing detail scale
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    // Detail scale calculated from pulse
    val pulseAngle = pulseProgress * 2 * PI
    val detailScale = (0.52 + ((sin(pulseAngle + 0.55) + 1.0) / 2.0) * 0.48).toFloat()

    val orbitRadius = 7.0f
    val detailAmplitude = 2.7f
    val petalCount = 7.0f
    val curveScale = 3.9f
    val trailSpan = 0.42f

    fun point(progress: Float, detScale: Float): Offset {
        val t = progress * 2f * PI.toFloat()
        val r = orbitRadius - detailAmplitude * detScale * cos(petalCount * t)
        val x = 50f + cos(t) * r * curveScale
        val y = 50f + sin(t) * r * curveScale
        return Offset(x, y)
    }

    fun normalizeProgress(p: Float): Float {
        return ((p % 1f) + 1f) % 1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val scale = canvasWidth / 100f
            val centerPivot = Offset(canvasWidth / 2f, canvasHeight / 2f)

            withTransform({
                rotate(degrees = rotationAngle, pivot = centerPivot)
            }) {
                // 1. Draw base faint mathematical Rose path
                val path = Path()
                val steps = 240
                for (i in 0..steps) {
                    val p = point(i.toFloat() / steps, detailScale)
                    val scaledX = p.x * scale
                    val scaledY = p.y * scale
                    if (i == 0) {
                        path.moveTo(scaledX, scaledY)
                    } else {
                        path.lineTo(scaledX, scaledY)
                    }
                }

                drawPath(
                    path = path,
                    color = color.copy(alpha = 0.12f),
                    style = Stroke(
                        width = 4.8f * (scale / 3.5f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // 2. Draw 72 particles traveling along the rose curve with trailing fade
                for (i in 0 until particleCount) {
                    val tailOffset = i.toFloat() / (particleCount - 1)
                    val p = point(
                        normalizeProgress(curveProgress - tailOffset * trailSpan),
                        detailScale
                    )
                    val fade = (1f - tailOffset).toDouble().pow(0.56).toFloat()
                    val radius = (0.9f + fade * 2.7f) * (scale / 2.8f)
                    val opacity = (0.04f + fade * 0.96f).coerceIn(0f, 1f)

                    drawCircle(
                        color = color.copy(alpha = opacity),
                        radius = radius,
                        center = Offset(p.x * scale, p.y * scale)
                    )
                }
            }
        }
    }
}
