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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.speech.AssistantVisualState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Organic animated light-form for SIA. The silhouette gently morphs while
 * luminous currents drift through it; no hard sci-fi rings or static marble look.
 */
@Composable
fun AnimatedOrb(
    state: AssistantVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    audioLevel: Float = 0f,
    onClick: () -> Unit = {}
) {
    val motion = rememberInfiniteTransition(label = "sia_living_light")
    val drift by motion.animateFloat(
        initialValue = 0f, targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(
            durationMillis = when (state) {
                AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> 5000
                AssistantVisualState.LISTENING -> 2400
                AssistantVisualState.SPEAKING -> 3000
                else -> 9000
            }, easing = LinearEasing
        )), label = "drifting_currents"
    )
    val breathe by motion.animateFloat(
        initialValue = 0.94f,
        targetValue = when (state) {
            AssistantVisualState.LISTENING -> 1.09f
            AssistantVisualState.SPEAKING -> 1.075f
            AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> 1.045f
            AssistantVisualState.PREPARING -> 1.025f
            AssistantVisualState.ERROR -> 0.97f
            else -> 1.015f
        },
        animationSpec = infiniteRepeatable(tween(
            durationMillis = when (state) {
                AssistantVisualState.LISTENING -> 760
                AssistantVisualState.SPEAKING -> 900
                AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> 1400
                else -> 2600
            }, easing = FastOutSlowInEasing
        ), repeatMode = RepeatMode.Reverse), label = "breathing"
    )
    val wave by motion.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(
            durationMillis = if (state == AssistantVisualState.LISTENING) 1200 else 2500,
            easing = LinearEasing
        )), label = "ambient_wave"
    )
    val (highlight, mid, shadow) = when (state) {
        AssistantVisualState.PREPARING -> Triple(Color(0xFFF0D5B0), Color(0xFFC5A27B), Color(0xFF6C5140))
        AssistantVisualState.ERROR -> Triple(Color(0xFFF1B8AA), Color(0xFFC98275), Color(0xFF69413C))
        AssistantVisualState.PAUSED -> Triple(Color(0xFFD0D2C6), Color(0xFF969D8E), Color(0xFF454B42))
        AssistantVisualState.LISTENING -> Triple(Color(0xFFD4E8C9), Color(0xFF8CC7A7), Color(0xFF315C4A))
        AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> Triple(Color(0xFFD2E2CF), Color(0xFF91BFA8), Color(0xFF3B6757))
        AssistantVisualState.SPEAKING -> Triple(Color(0xFFE6D7B9), Color(0xFFA4C8A9), Color(0xFF426A55))
        AssistantVisualState.IDLE -> Triple(Color(0xFFE3E4D0), Color(0xFF9FC8A7), Color(0xFF3E6B56))
    }

    Box(
        modifier = modifier.size(size).testTag("animated_orb_container").clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension * 0.355f
            val audio = audioLevel.coerceIn(0f, 1f)
            val scale = breathe + audio * 0.055f
            val r = radius * scale

            drawCircle(
                brush = Brush.radialGradient(
                    0f to mid.copy(alpha = 0.22f + audio * 0.10f),
                    0.48f to shadow.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r * (2.15f + wave * 0.10f)
                ),
                radius = r * (2.15f + wave * 0.10f), center = center
            )

            if (state == AssistantVisualState.LISTENING || state == AssistantVisualState.SPEAKING) {
                val waveR = r * (1.18f + wave * 0.58f)
                drawCircle(
                    color = mid.copy(alpha = (1f - wave) * 0.22f),
                    radius = waveR, center = center, style = Stroke(width = 1.4.dp.toPx())
                )
            }

            // Morph the silhouette subtly instead of showing a perfectly static sphere.
            val silhouette = Path()
            val points = 72
            for (i in 0 until points) {
                val a = (i.toFloat() / points) * (2f * PI).toFloat()
                val wobble = 1f +
                    0.055f * sin(a * 3f + drift).toFloat() +
                    0.026f * sin(a * 5f - drift * 1.37f).toFloat() +
                    0.014f * cos(a * 2f + drift * 0.72f).toFloat()
                val rr = r * wobble
                val x = center.x + cos(a).toFloat() * rr
                val y = center.y + sin(a).toFloat() * rr
                if (i == 0) silhouette.moveTo(x, y) else silhouette.lineTo(x, y)
            }
            silhouette.close()
            drawPath(
                path = silhouette,
                brush = Brush.radialGradient(
                    0f to highlight.copy(alpha = 0.96f),
                    0.28f to mid.copy(alpha = 0.96f),
                    0.62f to mid.copy(alpha = 0.80f),
                    0.86f to shadow.copy(alpha = 0.94f),
                    1f to Color(0xFF17221A),
                    center = Offset(center.x - r * 0.26f, center.y - r * 0.32f),
                    radius = r * 1.55f
                )
            )

            // Three soft currents drift across the form and brighten with live audio.
            for (i in 0..2) {
                val y = center.y + (i - 1) * r * 0.34f + sin(drift + i * 1.7f).toFloat() * r * 0.12f
                val path = Path().apply {
                    moveTo(center.x - r * 0.82f, y + r * 0.18f)
                    cubicTo(
                        center.x - r * 0.24f, y - r * (0.52f + 0.06f * i),
                        center.x + r * 0.18f, y + r * (0.52f - 0.05f * i),
                        center.x + r * 0.82f, y - r * 0.16f
                    )
                }
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, highlight.copy(alpha = 0.18f + audio * 0.16f), Color.White.copy(alpha = 0.24f), mid.copy(alpha = 0.18f), Color.Transparent),
                        startX = center.x - r, endX = center.x + r
                    ),
                    style = Stroke(width = r * (0.10f - i * 0.015f))
                )
            }

            val glint = Offset(
                center.x - r * (0.30f + cos(drift * 0.65f).toFloat() * 0.12f),
                center.y - r * (0.37f + sin(drift * 0.80f).toFloat() * 0.09f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.66f),
                    0.22f to highlight.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                    center = glint, radius = r * 0.58f
                ),
                radius = r * 0.58f, center = glint
            )
            drawCircle(Color.White.copy(alpha = 0.70f), radius = r * 0.022f, center = glint)
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.White.copy(alpha = 0.06f),
                    0.20f to highlight.copy(alpha = 0.48f),
                    0.44f to shadow.copy(alpha = 0.32f),
                    0.72f to Color.Transparent,
                    0.91f to mid.copy(alpha = 0.32f),
                    1f to Color.White.copy(alpha = 0.06f),
                    center = center
                ),
                radius = r, center = center, style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
