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

/** Hollow fluid-glass energy loop with two moving light nodes, drawn in Canvas. */
@Composable
fun AnimatedOrb(
    state: AssistantVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    audioLevel: Float = 0f,
    onClick: () -> Unit = {}
) {
    val motion = rememberInfiniteTransition(label = "sia_energy_loop")
    val drift by motion.animateFloat(
        0f, (2f * PI).toFloat(),
        infiniteRepeatable(tween(
            when (state) {
                AssistantVisualState.LISTENING -> 2300
                AssistantVisualState.SPEAKING -> 1850
                AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> 4200
                else -> 8500
            }, easing = LinearEasing
        )), label = "energy_drift"
    )
    val breath by motion.animateFloat(
        0.97f,
        if (state == AssistantVisualState.LISTENING) 1.035f
        else if (state == AssistantVisualState.SPEAKING) 1.045f else 1.008f,
        infiniteRepeatable(tween(
            if (state == AssistantVisualState.SPEAKING) 700
            else if (state == AssistantVisualState.LISTENING) 850 else 2800,
            easing = FastOutSlowInEasing
        ), repeatMode = RepeatMode.Reverse),
        label = "breathing"
    )
    val pulse by motion.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(
            if (state == AssistantVisualState.LISTENING) 1300 else 2500,
            easing = LinearEasing
        )), label = "pulse"
    )

    val (bright, teal, deep) = when (state) {
        AssistantVisualState.PREPARING -> Triple(Color(0xFFFFE3B8), Color(0xFFB5DCCB), Color(0xFF4B9C91))
        AssistantVisualState.ERROR -> Triple(Color(0xFFFFB6A8), Color(0xFFE18C86), Color(0xFF804D64))
        AssistantVisualState.PAUSED -> Triple(Color(0xFFD2D9D4), Color(0xFF8BAFAE), Color(0xFF466675))
        AssistantVisualState.LISTENING -> Triple(Color(0xFFE0FFE8), Color(0xFF76F0D2), Color(0xFF21AFA8))
        AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> Triple(Color(0xFFD7FFF2), Color(0xFF61DCCB), Color(0xFF318DB0))
        AssistantVisualState.SPEAKING -> Triple(Color(0xFFE6FFE0), Color(0xFF8AE8D0), Color(0xFF48B8B4))
        AssistantVisualState.IDLE -> Triple(Color(0xFFE7F8D9), Color(0xFF91E3C6), Color(0xFF368F87))
    }

    Box(
        modifier.size(size).testTag("animated_orb_container").clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, onClick = onClick
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val unit = this.size.minDimension
            val r = unit * 0.385f * breath
            val audio = audioLevel.coerceIn(0f, 1f)
            val active = state == AssistantVisualState.LISTENING ||
                state == AssistantVisualState.SPEAKING ||
                state == AssistantVisualState.THINKING ||
                state == AssistantVisualState.EXECUTING

            drawCircle(
                Brush.radialGradient(
                    0f to teal.copy(alpha = 0.06f + audio * 0.05f),
                    0.65f to deep.copy(alpha = 0.07f), 1f to Color.Transparent,
                    center = c, radius = r * 1.75f
                ), r * 1.75f, c
            )

            // Fine circular boundary, not a filled sphere.
            drawCircle(
                Brush.sweepGradient(
                    0f to bright.copy(alpha = 0.08f),
                    0.23f to teal.copy(alpha = if (active) 0.34f else 0.19f),
                    0.47f to Color.White.copy(alpha = 0.22f),
                    0.72f to deep.copy(alpha = 0.12f), 1f to bright.copy(alpha = 0.08f),
                    center = c
                ), r * 1.16f, c, style = Stroke(1.2.dp.toPx())
            )
            drawCircle(
                teal.copy(alpha = 0.045f + pulse * 0.025f),
                r * (1.20f + pulse * if (active) 0.09f else 0.025f),
                c, style = Stroke(0.8.dp.toPx())
            )

            // Three irregular translucent ribbons form an open-centre energy loop.
            for (layer in 0..2) {
                val path = Path()
                val points = 112
                for (i in 0..points) {
                    val a = (i.toFloat() / points) * (2f * PI).toFloat()
                    val phase = drift + layer * 1.75f
                    val radius = r * (
                        0.83f +
                        sin(a * 2f + phase * 0.78f).toFloat() * (0.105f + layer * 0.012f) +
                        sin(a * 3f - phase * 1.15f + layer).toFloat() * 0.042f +
                        cos(a * 5f + phase * 0.5f).toFloat() * 0.018f
                    )
                    val p = Offset(c.x + cos(a).toFloat() * radius, c.y + sin(a).toFloat() * radius)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(
                    path,
                    Brush.sweepGradient(
                        0f to deep.copy(alpha = 0.12f),
                        0.16f to teal.copy(alpha = 0.20f + audio * 0.08f),
                        0.30f to bright.copy(alpha = 0.52f + audio * 0.15f),
                        0.49f to teal.copy(alpha = 0.16f),
                        0.68f to deep.copy(alpha = 0.28f),
                        0.83f to bright.copy(alpha = 0.38f), 1f to deep.copy(alpha = 0.12f),
                        center = c
                    ),
                    style = Stroke(r * if (layer == 0) 0.17f else 0.10f)
                )
                drawPath(
                    path,
                    Brush.sweepGradient(
                        0f to Color.Transparent,
                        0.18f to bright.copy(alpha = 0.70f + audio * 0.18f),
                        0.35f to teal.copy(alpha = 0.38f),
                        0.56f to Color.Transparent,
                        0.78f to bright.copy(alpha = 0.55f),
                        0.92f to teal.copy(alpha = 0.42f), 1f to Color.Transparent,
                        center = c
                    ),
                    style = Stroke(if (layer == 0) 1.6.dp.toPx() else 0.85.dp.toPx())
                )
            }

            // Two bright nodes travel independently inside the open loop.
            val a = drift * if (state == AssistantVisualState.SPEAKING) 1.65f else 0.78f
            val dot1 = Offset(c.x + cos(a + 0.4f).toFloat() * r * 0.40f,
                c.y + sin(a + 0.4f).toFloat() * r * 0.24f)
            val dot2 = Offset(c.x + cos(-drift * 0.57f + 2.5f).toFloat() * r * 0.28f,
                c.y + sin(-drift * 0.57f + 2.5f).toFloat() * r * 0.43f)
            listOf(dot1 to unit * (0.014f + audio * 0.004f), dot2 to unit * (0.010f + audio * 0.003f))
                .forEachIndexed { index, pair ->
                    val dot = pair.first
                    val dotRadius = pair.second
                    val glow = dotRadius * if (active) 8f else 6f
                    drawCircle(
                        Brush.radialGradient(
                            0f to bright.copy(alpha = if (active) 0.50f else 0.32f),
                            0.28f to teal.copy(alpha = 0.18f), 1f to Color.Transparent,
                            center = dot, radius = glow
                        ), glow, dot
                    )
                    drawCircle(Color.White.copy(alpha = if (index == 0) 0.95f else 0.78f), dotRadius, dot)
                }

            val angle = drift * 0.62f - 0.8f
            val glint = Offset(c.x + cos(angle).toFloat() * r * 1.16f,
                c.y + sin(angle).toFloat() * r * 1.16f)
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.72f), 0.25f to bright.copy(alpha = 0.42f),
                    1f to Color.Transparent, center = glint, radius = unit * 0.055f
                ), unit * 0.055f, glint
            )
        }
    }
}
