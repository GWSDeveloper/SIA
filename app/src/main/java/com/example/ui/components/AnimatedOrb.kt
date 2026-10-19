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
import com.example.ui.theme.SiaAmber
import com.example.ui.theme.SiaCyan
import com.example.ui.theme.SiaGreen
import com.example.ui.theme.SiaRed
import com.example.ui.theme.SiaViolet
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedOrb(
    state: AssistantVisualState,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    audioLevel: Float = 0f,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Rotation angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantVisualState.THINKING, AssistantVisualState.EXECUTING -> 1200
                    AssistantVisualState.LISTENING -> 2200
                    AssistantVisualState.SPEAKING -> 2800
                    AssistantVisualState.PREPARING -> 3500
                    else -> 6000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Base pulse scale
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = when (state) {
            AssistantVisualState.LISTENING -> 1.15f
            AssistantVisualState.SPEAKING -> 1.10f
            AssistantVisualState.THINKING -> 1.06f
            AssistantVisualState.PREPARING -> 1.02f
            AssistantVisualState.ERROR -> 0.94f
            else -> 1.03f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantVisualState.LISTENING -> 550
                    AssistantVisualState.SPEAKING -> 750
                    AssistantVisualState.THINKING -> 650
                    AssistantVisualState.PREPARING -> 800
                    else -> 1600
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Reactive audio expansion scale
    val effectiveScale = pulseScale + (audioLevel * 0.15f)

    // Ripple wave progress
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == AssistantVisualState.LISTENING) 1000 else 2200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    // Palette per state
    val (primaryColor, secondaryColor, coreColor) = when (state) {
        AssistantVisualState.IDLE -> Triple(Color(0xFF8DEBFF), Color(0xFF2879B8), Color(0xFFB9F5FF))
        AssistantVisualState.PREPARING -> Triple(Color(0xFFFFC66D), Color(0xFFB77934), Color(0xFFFFE2AC))
        AssistantVisualState.LISTENING -> Triple(Color(0xFF91F3F0), Color(0xFF278DAA), Color(0xFFD2FFFF))
        AssistantVisualState.THINKING -> Triple(Color(0xFF9BCBFF), Color(0xFF4669B4), Color(0xFFD9E9FF))
        AssistantVisualState.EXECUTING -> Triple(Color(0xFF8DEBFF), Color(0xFF4D80D8), Color(0xFFB9F5FF))
        AssistantVisualState.SPEAKING -> Triple(Color(0xFF9BEAFF), Color(0xFF3B8CC8), Color(0xFFD8FAFF))
        AssistantVisualState.PAUSED -> Triple(Color(0xFFB8C5D6), Color(0xFF667A93), Color(0xFFE6EDF5))
        AssistantVisualState.ERROR -> Triple(Color(0xFFFF8D9B), Color(0xFF9D4557), Color(0xFFFFD7DC))
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("animated_orb_container")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val baseRadius = this.size.minDimension / 2.7f

            // Dynamic audio ripples during listening / speaking
            if (state == AssistantVisualState.LISTENING || state == AssistantVisualState.SPEAKING) {
                val rippleRadius1 = baseRadius + (baseRadius * (0.6f + audioLevel * 0.4f) * rippleProgress)
                val rippleAlpha1 = (1f - rippleProgress) * 0.5f
                drawCircle(
                    color = primaryColor.copy(alpha = rippleAlpha1),
                    radius = rippleRadius1,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                val rippleOffset = (rippleProgress + 0.5f) % 1f
                val rippleRadius2 = baseRadius + (baseRadius * 0.7f * rippleOffset)
                val rippleAlpha2 = (1f - rippleOffset) * 0.35f
                drawCircle(
                    color = secondaryColor.copy(alpha = rippleAlpha2),
                    radius = rippleRadius2,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Outer atmospheric aura glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.38f),
                        secondaryColor.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.55f * effectiveScale
                ),
                radius = baseRadius * 1.55f * effectiveScale,
                center = center
            )

            // Primary Energy Ring with sweeping neon gradient
            val ring1Radius = baseRadius * 1.15f * effectiveScale
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.95f),
                        secondaryColor.copy(alpha = 0.35f),
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.95f)
                    ),
                    center = center
                ),
                radius = ring1Radius,
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Orbiting energy nodes
            val rad1 = Math.toRadians(rotationAngle.toDouble())
            val node1X = center.x + (ring1Radius * cos(rad1)).toFloat()
            val node1Y = center.y + (ring1Radius * sin(rad1)).toFloat()
            drawCircle(
                color = primaryColor,
                radius = (4.dp.toPx() + audioLevel * 2.dp.toPx()),
                center = Offset(node1X, node1Y)
            )

            val rad2 = Math.toRadians((rotationAngle + 180).toDouble())
            val node2X = center.x + (ring1Radius * cos(rad2)).toFloat()
            val node2Y = center.y + (ring1Radius * sin(rad2)).toFloat()
            drawCircle(
                color = secondaryColor,
                radius = 3.dp.toPx(),
                center = Offset(node2X, node2Y)
            )

            // Counter-rotating inner energy ring
            val ring2Radius = baseRadius * 0.92f
            val rad3 = Math.toRadians(-rotationAngle * 1.6)
            drawCircle(
                color = secondaryColor.copy(alpha = 0.45f),
                radius = ring2Radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            val node3X = center.x + (ring2Radius * cos(rad3)).toFloat()
            val node3Y = center.y + (ring2Radius * sin(rad3)).toFloat()
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(node3X, node3Y)
            )

            // Deep Dimensional Core
            val coreRadius = baseRadius * 0.72f * effectiveScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEAFBFF).copy(alpha = 0.92f),
                        coreColor.copy(alpha = 0.88f),
                        primaryColor.copy(alpha = 0.70f),
                        secondaryColor.copy(alpha = 0.35f)
                    ),
                    center = center.copy(
                        x = center.x - (coreRadius * 0.22f),
                        y = center.y - (coreRadius * 0.22f)
                    ),
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // Specular core light glint
            drawCircle(
                color = Color.White.copy(alpha = 0.24f),
                radius = coreRadius * 0.35f,
                center = center.copy(
                    x = center.x - (coreRadius * 0.25f),
                    y = center.y - (coreRadius * 0.25f)
                )
            )
        }
    }
}
