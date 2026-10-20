package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.speech.AssistantVisualState
import kotlin.math.cos
import kotlin.math.sin

/**
 * A softly illuminated glass-like sphere. The sphere itself is built from layered
 * radial/sweep gradients and a moving specular highlight; fine orbital strokes are
 * intentionally subtle so it reads as a physical object, not a neon HUD.
 */
@Composable
fun AnimatedOrb(
    state: AssistantVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    audioLevel: Float = 0f,
    onClick: () -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "sia_glass_orb")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> 7200
                    AssistantVisualState.LISTENING -> 4400
                    AssistantVisualState.SPEAKING -> 5600
                    else -> 18000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = when (state) {
            AssistantVisualState.LISTENING -> 1.045f
            AssistantVisualState.SPEAKING -> 1.035f
            AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> 1.018f
            AssistantVisualState.ERROR -> 0.985f
            else -> 1.012f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantVisualState.LISTENING -> 1250
                    AssistantVisualState.SPEAKING -> 1500
                    else -> 3400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantVisualState.LISTENING) 1800 else 3200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_ripple"
    )

    val (light, mid, deep) = when (state) {
        AssistantVisualState.PREPARING -> Triple(Color(0xFFF0D5A7), Color(0xFFC4A477), Color(0xFF665B4C))
        AssistantVisualState.ERROR -> Triple(Color(0xFFF0B3A8), Color(0xFFC78076), Color(0xFF684A48))
        AssistantVisualState.PAUSED -> Triple(Color(0xFFC5CCC4), Color(0xFF8F9B91), Color(0xFF424A44))
        AssistantVisualState.LISTENING -> Triple(Color(0xFFC7E5D6), Color(0xFF83BDA6), Color(0xFF345C50))
        AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> Triple(Color(0xFFC7DDD3), Color(0xFF8EB6A6), Color(0xFF3B5F55))
        AssistantVisualState.SPEAKING -> Triple(Color(0xFFE4D6BB), Color(0xFF9BC5B0), Color(0xFF46695C))
        AssistantVisualState.IDLE -> Triple(Color(0xFFE1E5D8), Color(0xFF9BC9B5), Color(0xFF3C6658))
    }

    Box(
        modifier = modifier.size(size).testTag("animated_orb_container").clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension * 0.345f
            val scale = breathe + (audioLevel.coerceIn(0f, 1f) * 0.035f)
            val r = radius * scale

            // Atmospheric halo: broad and soft, never a hard neon ring.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to light.copy(alpha = if (state == AssistantVisualState.LISTENING) 0.22f else 0.14f),
                    0.56f to mid.copy(alpha = 0.075f),
                    1f to Color.Transparent,
                    center = c,
                    radius = r * 2.0f
                ),
                radius = r * 2.0f,
                center = c
            )

            if (state == AssistantVisualState.LISTENING || state == AssistantVisualState.SPEAKING) {
                val waveRadius = r * (1.16f + ripple * 0.56f)
                drawCircle(
                    color = light.copy(alpha = (1f - ripple) * 0.24f),
                    radius = waveRadius,
                    center = c,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // Sphere body: dark rim + layered volume shading.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to light.copy(alpha = 0.96f),
                    0.24f to mid.copy(alpha = 0.94f),
                    0.63f to mid.copy(alpha = 0.60f),
                    0.86f to deep.copy(alpha = 0.82f),
                    1f to Color(0xFF111713),
                    center = Offset(c.x + r * 0.24f, c.y + r * 0.27f),
                    radius = r * 1.42f
                ),
                radius = r,
                center = c
            )

            // Translucent flowing ribbons across the sphere.
            val angle = Math.toRadians(rotation.toDouble())
            val ribbonCenter = Offset(
                c.x + (cos(angle) * r * 0.20).toFloat(),
                c.y + (sin(angle) * r * 0.17).toFloat()
            )
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.12f to light.copy(alpha = 0.12f),
                    0.23f to Color.White.copy(alpha = 0.46f),
                    0.31f to mid.copy(alpha = 0.08f),
                    0.58f to deep.copy(alpha = 0.24f),
                    0.78f to Color.Transparent,
                    1f to Color.Transparent,
                    center = ribbonCenter
                ),
                radius = r * 0.98f,
                center = c,
                style = Stroke(width = r * 0.22f)
            )
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.20f to mid.copy(alpha = 0.05f),
                    0.34f to light.copy(alpha = 0.58f),
                    0.42f to Color.White.copy(alpha = 0.16f),
                    0.68f to deep.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                    center = c
                ),
                radius = r * 0.72f,
                center = c,
                style = Stroke(width = r * 0.105f)
            )

            // Glass rim with a moving reflection.
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.White.copy(alpha = 0.05f),
                    0.12f to light.copy(alpha = 0.70f),
                    0.22f to Color.White.copy(alpha = 0.12f),
                    0.52f to deep.copy(alpha = 0.50f),
                    0.76f to Color.Transparent,
                    0.91f to light.copy(alpha = 0.42f),
                    1f to Color.White.copy(alpha = 0.05f),
                    center = c
                ),
                radius = r,
                center = c,
                style = Stroke(width = 2.2.dp.toPx())
            )

            // Specular reflection, intentionally soft and offset from center.
            val highlight = Offset(
                c.x - r * (0.31f + cos(angle).toFloat() * 0.045f),
                c.y - r * (0.38f + sin(angle).toFloat() * 0.035f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.70f),
                    0.28f to light.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                    center = highlight,
                    radius = r * 0.62f
                ),
                radius = r * 0.62f,
                center = highlight
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.78f),
                radius = r * 0.025f,
                center = Offset(highlight.x - r * 0.09f, highlight.y - r * 0.055f)
            )
        }
    }
}
