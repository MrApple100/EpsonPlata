package ru.`object`.detection.util.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.tensorflow.lite.examples.detection.R
import ru.`object`.detection.camera.ObjectDetectorAnalyzer
import ru.`object`.detection.util.DetectorUtils

class RecognitionResultOverlayView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val scenery:Scenery = Scenery(context = context)
    private val maintext:TextView = findViewById<TextView>(R.id.MainText)
    private val reset:Button= findViewById<Button>(R.id.Reset)


    init{
        maintext.text = context.getString(R.string.Find_processor)
        reset.setOnClickListener(View.OnClickListener {
            scenery.reset()
        })
    }
    private val allLabels = DetectorUtils.loadLabelsFile(context.assets, "labelmap.txt")

    private val boxPaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.FILL_AND_STROKE
        alpha = 100 //from 0(transparent) to 255
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
        val idProcessor = 9
        val idProcessorPlace = 10
        result.objects.forEach { obj ->

//scenery
            if(obj.id == idProcessor && !scenery.FindStep){
                scenery.checkFind()
                maintext.text = context.getString(R.string.Insert_to_place)
            }
            if(scenery.FindStep && !scenery.InsertStep){

                val left = obj.location.left * scaleFactorX
                val top = obj.location.top * scaleFactorY
                val right = obj.location.right * scaleFactorX
                val bottom = obj.location.bottom * scaleFactorY

                val currentIndex = allLabels.indexOf( obj.title )
                //красным горит место куда надо вставить
                if(obj.id==idProcessorPlace) {
                    boxPaint.color = Color.RED
                    textPaint.color = Color.RED
                    canvas.drawRect(left, top, right, bottom, boxPaint)
                    canvas.drawText(obj.text, left, top - 25f, textPaint)
                }
                //зеленым процессор который надо вставить
                if(obj.id==idProcessor) {
                    boxPaint.color = Color.GREEN
                    textPaint.color = Color.GREEN
                    canvas.drawRect(left, top, right, bottom, boxPaint)
                    canvas.drawText(obj.text, left, top - 25f, textPaint)
                }
            }else {
                //вне сценария горят все элементы
                val left = obj.location.left * scaleFactorX
                val top = obj.location.top * scaleFactorY
                val right = obj.location.right * scaleFactorX
                val bottom = obj.location.bottom * scaleFactorY

                val currentIndex = allLabels.indexOf(obj.title)
                boxPaint.color =
                    Color.parseColor(ColorsEnum.values()[currentIndex / ColorsEnum.values().size].name)
                textPaint.color =
                    Color.parseColor(ColorsEnum.values()[currentIndex / ColorsEnum.values().size].name)

                canvas.drawRect(left, top, right, bottom, boxPaint)
                canvas.drawText(obj.text, left, top - 25f, textPaint)
            }
        }
    }
}
enum class ColorsEnum{
    magenta,red,blue,green,cyan,yellow,darkgray
}
class Scenery (
    var FindStep:Boolean = false,
    var InsertStep: Boolean = false,
    val context: Context
        ){

    fun checkFind(): Unit {
        FindStep=true
    }
    fun checkInsert(): Unit {
        InsertStep=true
    }

    fun reset(): Unit {
        FindStep=false
        InsertStep=false
    }


}