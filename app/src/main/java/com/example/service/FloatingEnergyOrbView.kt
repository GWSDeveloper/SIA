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

/** Lightweight native-Canvas version of SIA's hollow fluid-glass loop for the overlay. */
class FloatingEnergyOrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
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
        val r = unit * 0.37f
        val active = visualState == AssistantVisualState.LISTENING ||
            visualState == AssistantVisualState.THINKING ||
            visualState == AssistantVisualState.EXECUTING ||
            visualState == AssistantVisualState.SPEAKING
        val mint = when (visualState) {
            AssistantVisualState.ERROR -> Color.rgb(255, 165, 150)
            AssistantVisualState.PREPARING -> Color.rgb(255, 220, 176)
            else -> Color.rgb(130, 244, 218)
        }
        val deep = Color.rgb(38, 148, 153)

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, r * 1.7f, intArrayOf(Color.argb(40, 35, 220, 200), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.7f, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * density
        paint.color = if (active) Color.argb(110, 143, 255, 232) else Color.argb(65, 143, 255, 232)
        canvas.drawCircle(cx, cy, r * 1.20f, paint)

        // Two fluid translucent contours, deliberately hollow rather than marble-like.
        for (layer in 0..1) {
            val path = Path()
            val points = 80
            for (i in 0..points) {
                val a = (i.toFloat() / points) * (2f * PI).toFloat()
                val radius = r * (0.82f +
                    sin(a * 2f + phase + layer * 1.7f).toFloat() * 0.11f +
                    sin(a * 3f - phase * 1.1f + layer).toFloat() * 0.045f +
                    cos(a * 5f + phase * 0.5f).toFloat() * 0.02f)
                val x = cx + cos(a).toFloat() * radius
                val y = cy + sin(a).toFloat() * radius
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            paint.strokeWidth = if (layer == 0) r * 0.13f else r * 0.075f
            paint.color = Color.argb(if (layer == 0) 46 else 28, Color.red(mint), Color.green(mint), Color.blue(mint))
            canvas.drawPath(path, paint)
            paint.strokeWidth = (if (layer == 0) 1.4f else 0.8f) * density
            paint.color = Color.argb(if (layer == 0) 190 else 105, Color.red(mint), Color.green(mint), Color.blue(mint))
            canvas.drawPath(path, paint)
        }

        // Exactly two bright nodes moving on different inner paths.
        paint.style = Paint.Style.FILL
        val d1x = cx + cos(phase * if (visualState == AssistantVisualState.SPEAKING) 1.6f else 0.8f + 0.3f).toFloat() * r * 0.40f
        val d1y = cy + sin(phase * 0.8f + 0.3f).toFloat() * r * 0.25f
        val d2x = cx + cos(-phase * 0.57f + 2.4f).toFloat() * r * 0.28f
        val d2y = cy + sin(-phase * 0.57f + 2.4f).toFloat() * r * 0.42f
        for ((x, y, size) in listOf(Triple(d1x, d1y, unit * 0.045f), Triple(d2x, d2y, unit * 0.032f))) {
            paint.shader = RadialGradient(x, y, size * 3.2f, intArrayOf(Color.argb(if (active) 150 else 95, Color.red(mint), Color.green(mint), Color.blue(mint)), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, size * 3.2f, paint)
            paint.shader = null
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, size * 0.38f, paint)
        }

        // A restrained travelling highlight on the circular edge.
        val angle = phase * 0.65f
        val gx = cx + cos(angle).toFloat() * r * 1.20f
        val gy = cy + sin(angle).toFloat() * r * 1.20f
        paint.color = mint
        canvas.drawCircle(gx, gy, unit * 0.018f, paint)
        phase += if (active) 0.035f else 0.012f
        postInvalidateDelayed(32L)
    }
}
