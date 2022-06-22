package ru.`object`.detection.util.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.Toast
import ru.`object`.detection.camera.ObjectDetectorAnalyzer

class RecognitionResultOverlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val boxPaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.FILL_AND_STROKE
        alpha = 100
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        textSize = 35f
    }

    private var result: ObjectDetectorAnalyzer.Result? = null

    fun updateResults(result: ObjectDetectorAnalyzer.Result) {
        this.result = result
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val result = result ?: return

        //Snackbar.make(getWindow().getDecorView(), e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();

        val scaleFactorX = measuredWidth / result.imageWidth.toFloat()
        val scaleFactorY = measuredHeight / result.imageHeight.toFloat()

        result.objects.forEach { obj ->
            val left = obj.location.left * scaleFactorX
            val top = obj.location.top * scaleFactorY
            val right = obj.location.right * scaleFactorX
            val bottom = obj.location.bottom * scaleFactorY

            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawText(obj.text, left, top - 25f, textPaint)
        }
    }
}