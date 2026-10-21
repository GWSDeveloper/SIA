package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.example.speech.AssistantVisualState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Native Canvas Nexus orb shared by the floating bubble and immersive voice overlay. */
class FloatingEnergyOrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var visualState = AssistantVisualState.IDLE
    private var phase = 0f
    private val density = resources.displayMetrics.density

    fun setVisualState(state: AssistantVisualState) {
        visualState = state
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val unit = minOf(width, height).toFloat()
        if (unit <= 0f) return

        val active = visualState == AssistantVisualState.LISTENING ||
            visualState == AssistantVisualState.THINKING ||
            visualState == AssistantVisualState.EXECUTING ||
            visualState == AssistantVisualState.SPEAKING
        val speaking = visualState == AssistantVisualState.SPEAKING
        val thinking = visualState == AssistantVisualState.THINKING
        val executing = visualState == AssistantVisualState.EXECUTING
        val error = visualState == AssistantVisualState.ERROR
        val core = when {
            error -> Color.rgb(255, 61, 94)
            executing || visualState == AssistantVisualState.PREPARING -> Color.rgb(255, 174, 76)
            speaking -> Color.rgb(65, 220, 255)
            thinking -> Color.rgb(157, 88, 255)
            visualState == AssistantVisualState.LISTENING -> Color.rgb(32, 224, 255)
            else -> Color.rgb(68, 153, 255)
        }
        val cyan = Color.rgb(42, 225, 255)
        val blue = Color.rgb(38, 112, 255)
        val violet = Color.rgb(161, 73, 255)
        val radius = unit * 0.31f
        val pulse = if (active) 1f + sin(phase * if (speaking) 2.8f else 1.55f) * 0.035f else 1f
        val r = radius * pulse

        // Restrained aura; opaque dock behind the view keeps the ring crisp on any screen.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, unit * 0.49f,
            intArrayOf(Color.argb(if (active) 76 else 44, Color.red(core), Color.green(core), Color.blue(core)),
                Color.argb(25, 35, 72, 180), Color.TRANSPARENT),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, unit * 0.49f, paint)
        paint.shader = null

        // Dark glass core gives the Nexus ring its clear open-centre silhouette.
        paint.shader = RadialGradient(
            cx - r * 0.25f, cy - r * 0.32f, r * 1.42f,
            intArrayOf(Color.rgb(13, 32, 67), Color.rgb(5, 12, 29), Color.rgb(2, 6, 17)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r * 0.78f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.1f * density
        paint.color = Color.argb(180, 92, 180, 255)
        canvas.drawCircle(cx, cy, r * 0.78f, paint)

        // Independently rotating elliptical rings create the Nexus signature.
        for (ring in 0..2) {
            canvas.save()
            val direction = if (ring == 1) -1f else 1f
            val speed = when {
                speaking -> 1.45f
                thinking -> 1.7f
                visualState == AssistantVisualState.LISTENING -> 1.05f
                executing -> 0.82f
                else -> 0.42f
            }
            canvas.rotate(phase * speed * direction + ring * 57f, cx, cy)
            val rx = r * (0.78f + ring * 0.105f)
            val ry = r * (0.40f + ring * 0.065f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (if (ring == 0) 5.0f else if (ring == 1) 3.1f else 1.7f) * density
            paint.shader = null
            paint.color = if (ring == 1) violet else if (ring == 2) blue else cyan
            paint.alpha = if (active) 220 else 180
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
            paint.strokeWidth = (if (ring == 0) 1.5f else 0.8f) * density
            paint.color = Color.WHITE
            paint.alpha = if (active) 150 else 92
            canvas.drawArc(cx - rx, cy - ry, cx + rx, cy + ry,
                (phase * 62f + ring * 100f) % 360f, 42f, false, paint)
            paint.alpha = 255
            canvas.restore()
        }

        // Woven luminous trails, with small differences in phase and direction.
        for (layer in 0..1) {
            val path = Path()
            val steps = 84
            for (i in 0..steps) {
                val angle = (i.toFloat() / steps) * (2f * PI).toFloat()
                val wave = sin(angle * 2f + phase * (if (thinking) 1.7f else 0.8f) + layer * 2.1f).toFloat()
                val wave2 = cos(angle * 3f - phase * 0.7f + layer).toFloat()
                val rr = r * (0.84f + wave * 0.075f + wave2 * 0.035f + layer * 0.035f)
                val x = cx + cos(angle).toFloat() * rr
                val y = cy + sin(angle).toFloat() * rr * 0.88f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (if (layer == 0) 2.5f else 1.5f) * density
            paint.color = if (layer == 0) cyan else violet
            paint.alpha = if (active) 190 else 130
            canvas.drawPath(path, paint)
            paint.color = Color.WHITE
            paint.strokeWidth = 0.7f * density
            paint.alpha = if (active) 120 else 70
            canvas.drawPath(path, paint)
            paint.alpha = 255
        }

        // Four moving particles and a crisp glint provide life without heavy bloom.
        val speed = when {
            speaking -> 1.85f
            thinking -> 1.55f
            visualState == AssistantVisualState.LISTENING -> 1.25f
            executing -> 0.95f
            else -> 0.43f
        }
        for (i in 0..3) {
            val angle = phase * speed + i * (PI.toFloat() / 2f)
            val orbit = r * (1.02f + 0.06f * sin(phase + i.toFloat()).toFloat())
            val x = cx + cos(angle).toFloat() * orbit
            val y = cy + sin(angle * 0.92f).toFloat() * orbit * 0.58f
            val size = unit * if (i == 0) 0.024f else 0.014f
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                x, y, size * 3.1f,
                intArrayOf(Color.argb(175, Color.red(core), Color.green(core), Color.blue(core)), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, size * 3.1f, paint)
            paint.shader = null
            paint.color = if (i == 0) Color.WHITE else core
            canvas.drawCircle(x, y, size, paint)
        }
        val glintAngle = phase * speed * 0.7f
        paint.color = Color.WHITE
        paint.alpha = if (active) 235 else 180
        canvas.drawCircle(cx + cos(glintAngle).toFloat() * r * 0.92f,
            cy + sin(glintAngle).toFloat() * r * 0.50f, unit * 0.018f, paint)
        paint.alpha = 255

        phase += when {
            error -> 0.07f
            speaking -> 0.052f
            thinking -> 0.045f
            visualState == AssistantVisualState.LISTENING -> 0.036f
            executing -> 0.029f
            else -> 0.014f
        }
        postInvalidateDelayed(32L)
    }
}
