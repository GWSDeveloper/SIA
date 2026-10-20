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
        val cx = width / 2f; val cy = height / 2f; val unit = minOf(width, height).toFloat()
        if (unit <= 0f) return
        val r = unit * 0.31f
        val active = visualState == AssistantVisualState.LISTENING || visualState == AssistantVisualState.THINKING || visualState == AssistantVisualState.EXECUTING || visualState == AssistantVisualState.SPEAKING
        val speaking = visualState == AssistantVisualState.SPEAKING
        val thinking = visualState == AssistantVisualState.THINKING || visualState == AssistantVisualState.EXECUTING
        val core = when (visualState) { AssistantVisualState.ERROR -> Color.rgb(255,112,135); AssistantVisualState.PREPARING -> Color.rgb(255,199,130); AssistantVisualState.SPEAKING -> Color.rgb(153,133,255); else -> Color.rgb(92,239,224) }
        val blue = Color.rgb(78,166,255); val violet = Color.rgb(133,103,255)
        paint.style=Paint.Style.FILL
        paint.shader=RadialGradient(cx,cy,r*2.05f,intArrayOf(Color.argb(if(active)88 else 48,Color.red(core),Color.green(core),Color.blue(core)),Color.argb(24,75,120,255),Color.TRANSPARENT),floatArrayOf(0f,0.52f,1f),Shader.TileMode.CLAMP)
        canvas.drawCircle(cx,cy,r*2.05f,paint);paint.shader=null
        paint.style=Paint.Style.STROKE
        for(ring in 0..2){canvas.save();canvas.rotate(phase*(if(ring==1)-18f else 12f)+ring*58f,cx,cy);paint.strokeWidth=(if(ring==0)1.1f else 0.7f)*density;paint.color=Color.argb(if(active)90 else 48,Color.red(core),Color.green(core),Color.blue(core));canvas.drawOval(android.graphics.RectF(cx-r*(1.28f+ring*0.08f),cy-r*(0.35f+ring*0.045f),cx+r*(1.28f+ring*0.08f),cy+r*(0.35f+ring*0.045f)),paint);canvas.restore()}
        val pulse=if(active)1f+sin(phase*(if(speaking)2.7f else 1.5f)).toFloat()*0.035f else 1f;val cr=r*pulse
        paint.style=Paint.Style.FILL;paint.shader=RadialGradient(cx-cr*0.27f,cy-cr*0.34f,cr*1.65f,intArrayOf(Color.rgb(231,255,255),core,if(speaking||thinking)violet else blue,Color.rgb(7,19,43)),floatArrayOf(0f,0.23f,0.66f,1f),Shader.TileMode.CLAMP);canvas.drawCircle(cx,cy,cr,paint);paint.shader=null
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.2f*density;paint.color=Color.argb(190,221,255,255);canvas.drawCircle(cx,cy,cr,paint)
        paint.style=Paint.Style.FILL;paint.shader=RadialGradient(cx-cr*0.32f,cy-cr*0.43f,cr*0.66f,intArrayOf(Color.argb(155,255,255,255),Color.argb(34,255,255,255),Color.TRANSPARENT),floatArrayOf(0f,0.22f,1f),Shader.TileMode.CLAMP);canvas.drawCircle(cx,cy,cr*0.92f,paint);paint.shader=null
        canvas.save();canvas.clipRect(cx-cr,cy-cr,cx+cr,cy+cr)
        for(layer in 0..2){val path=Path();val steps=64;for(i in 0..steps){val x=-0.88f+1.76f*i/steps;val wave=sin(x*4.2f+phase*(if(thinking)1.8f else 1.15f)+layer*1.9f).toFloat();val wave2=cos(x*6.1f-phase*0.8f+layer).toFloat();val y=wave*(0.12f+layer*0.025f)+wave2*0.045f+(layer-1)*0.15f;val px=cx+x*cr;val py=cy+y*cr;if(i==0)path.moveTo(px,py)else path.lineTo(px,py)};paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND;paint.strokeWidth=(if(speaking)2.6f else 1.8f)*density;paint.color=Color.argb(if(active)135-layer*25 else 70-layer*12,if(layer==1)255 else 150,255,250);canvas.drawPath(path,paint)}
        canvas.restore();paint.style=Paint.Style.FILL
        val speed=when{speaking->1.7f;thinking->1.35f;visualState==AssistantVisualState.LISTENING->1.1f;else->0.45f}
        for(i in 0..3){val angle=phase*speed+i*(PI.toFloat()/2f);val orbit=cr*(1.17f+0.07f*sin(phase+i).toFloat());val x=cx+cos(angle).toFloat()*orbit;val y=cy+sin(angle*0.92f).toFloat()*orbit*0.58f;val size=unit*(if(i==0)0.027f else 0.016f);paint.shader=RadialGradient(x,y,size*3.3f,intArrayOf(Color.argb(190,Color.red(core),Color.green(core),Color.blue(core)),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);canvas.drawCircle(x,y,size*3.3f,paint);paint.shader=null;paint.color=if(i==0)Color.WHITE else core;canvas.drawCircle(x,y,size,paint)}
        phase+=when{speaking->0.052f;thinking->0.043f;visualState==AssistantVisualState.LISTENING->0.034f;active->0.027f;else->0.012f};postInvalidateDelayed(32L)
    }
}
