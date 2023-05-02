package com.alexvas.rtsp.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View

class ResultOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
    fun updateResults(bitmap: Bitmap) {
        this.bitmap=bitmap

        invalidate()
    }
    var alphaColor = 70
    var ar: Int = Color.argb(alphaColor, 255, 0, 0)
    var ag: Int = Color.argb(alphaColor, 0, 255, 0)
    var ab: Int = Color.argb(alphaColor, 0, 0, 255)
    var halfdark: Int = Color.argb(120, 0, 0, 0)
    private val boxPaint = Paint().apply {
        color = ag
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        val scalew = canvas!!.width.toFloat() / bitmap.width
        val scaleh = canvas!!.height.toFloat() / bitmap.height
        Log.d("ROVROV"," "+scalew+" "+scaleh)

        val matrix = Matrix()
        matrix.postScale(scalew, scaleh)

        canvas!!.drawBitmap(bitmap,matrix,boxPaint)
    }


}