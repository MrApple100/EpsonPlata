package ru.`object`.detection.util.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.tensorflow.lite.examples.detection.R
import ru.`object`.detection.camera.ObjectDetectorAnalyzer
import ru.`object`.detection.detection.DetectionResult
import ru.`object`.detection.util.DetectorUtils
import java.util.*
import kotlin.collections.HashMap

class RecognitionResultOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var alphaColor =70
    var ar: Int = Color.argb(alphaColor, 255, 0, 0)
    var ag: Int = Color.argb(alphaColor, 0, 255, 0)
    var ab: Int = Color.argb(alphaColor, 0, 0, 255)

    private var objectsDetectedOld = HashMap<String,Int>()
    private val objectsDetectedNew = HashMap<String,Int>()
    private val objectsAngles = HashMap<String,DetectionResult>()

    private var listObjectForDescribe = HashMap<String,String>()
    private var listDescriptionOnDisplay = arrayListOf<String>()

    private val scenery: Scenery = Scenery(context = context)

    private val allLabels = DetectorUtils.loadLabelsFile(context.assets, "labelmap.txt")

    private val allDescribes = DetectorUtils.loadLabelsFile(context.assets, "descriptionmap.txt")

    private lateinit var textView:TextView

    init{
        for( i in 0 until allLabels.size){
            Log.d("Tag",allLabels[i])
            listObjectForDescribe.put(allLabels[i],allDescribes[i])
        }
    }

    fun setDescriptionText(text:TextView){
        textView = text
    }


    private val boxPaint = Paint().apply {
        color = ab
        style = Paint.Style.FILL_AND_STROKE
        //alpha = 220 //from 0(transparent) to 255
        strokeWidth = 5f
    }
    private val centerPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
        //alpha = 220 //from 0(transparent) to 255
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 35f
    }
    private val MaintextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 55f
    }

    private var result: ObjectDetectorAnalyzer.Result? = null

    fun updateResults(result: ObjectDetectorAnalyzer.Result) {
        this.result = result
        invalidate()
    }
    var MainText:String = context.getString(R.string.Find_processor)
    private var counter =0
    val idProcessor = allLabels[8]
    val idProcessorPlace = allLabels[9]
    override fun onDraw(canvas: Canvas) {
        val result = result ?: return
        //Snackbar.make(getWindow().getDecorView(), e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
        val scaleFactorX = measuredWidth / result.imageWidth.toFloat()
        val scaleFactorY = measuredHeight / result.imageHeight.toFloat()
        result.objects.forEach { obj ->

            objectsDetectedNew.put(obj.title,1)

            if(!objectsAngles.contains(obj.title)){
                objectsAngles.put(obj.title,obj)

            }

        }

        val objectsSetOld = objectsDetectedOld.keys
        val objectsSetNew = objectsDetectedNew.keys
        for(i in objectsSetNew){
            if(!objectsSetOld.contains(i)){
                objectsDetectedOld.put(i,1)

            }
        }
        for(i in objectsSetOld){
            if (objectsSetNew.contains(i)){
                objectsDetectedOld.set(i,objectsDetectedOld.get(i)?.plus(1)!!)
            }else{
                objectsDetectedOld[i] = 0

            }

        }


        result.objects.forEach { obj ->
            listDescriptionOnDisplay.clear()

//scenery
            // Log.d("RECREC", "${objectsDetectedOld.get(idProcessor)!!}")
            try {

//вне сценария горят все элементы
                if (objectsDetectedOld.get(obj.title)!! % 15 == 0 && objectsDetectedOld.get(obj.title)!! >= 15) {
                    objectsAngles.get(obj.title)?.location?.left =
                        obj.location.left * scaleFactorX
                    objectsAngles.get(obj.title)?.location?.top =
                        obj.location.top * scaleFactorY
                    objectsAngles.get(obj.title)?.location?.right =
                        obj.location.right * scaleFactorX
                    objectsAngles.get(obj.title)?.location?.bottom =
                        obj.location.bottom * scaleFactorY
                }
                val left = objectsAngles.get(obj.title)?.location?.left!!
                val top = objectsAngles.get(obj.title)?.location?.top!!
                val right = objectsAngles.get(obj.title)?.location?.right!!
                val bottom = objectsAngles.get(obj.title)?.location?.bottom!!

                if(objectsDetectedOld.contains(idProcessor)){
                    if (objectsDetectedOld.get(idProcessor)!! >= 30 && !scenery.FindStep) {
                        scenery.checkFind()
                        MainText = context.getString(R.string.Insert_to_place)
                    }
                    if (scenery.FindStep && !scenery.InsertStep) {

                        val currentIndex = allLabels.indexOf(obj.title)
                        //красным горит место куда надо вставить
                        if (obj.title == idProcessorPlace) {
                            boxPaint.color = ar
                            textPaint.color = Color.WHITE
                            canvas.drawRect(left, top, right, bottom, boxPaint)
                            canvas.drawText(obj.text, left, top + 25f, textPaint)
                        } else if (obj.title == idProcessor) {                     //зеленым процессор который надо вставить

                            boxPaint.color = ag
                            textPaint.color = Color.WHITE
                            canvas.drawRect(left, top, right, bottom, boxPaint)
                            canvas.drawText(obj.text, left, top + 25f, textPaint)
                        } else {
                            boxPaint.color = ab
                            textPaint.color = Color.WHITE
                            canvas.drawRect(left, top, right, bottom, boxPaint)
                            canvas.drawText(obj.text, left, top + 25f, textPaint)
                        }
                    }
                } else {

                    MainText = context.getString(R.string.Find_processor)

                    //вне сценария горят все элементы

                    val currentIndex = allLabels.indexOf(obj.title)
                    boxPaint.color = ab
                    textPaint.color = Color.WHITE

                    canvas.drawRect(left, top, right, bottom, boxPaint)
                    canvas.drawText(obj.text, left, top + 25f, textPaint)


                }
                if (checkPointInRect(
                        left,
                        top,
                        right,
                        bottom,
                        (canvas.width / 2).toFloat(),
                        (canvas.height/2).toFloat()
                    )
                ) {
                    listDescriptionOnDisplay.add(listObjectForDescribe.get(obj.title)!!)

                }

            } catch (e: Exception) {
                Toast.makeText(this.context,Arrays.toString(e.stackTrace),Toast.LENGTH_LONG).show()
                canvas.drawText(
                    Arrays.toString(e.stackTrace),
                    canvas.width / 2 - 100f,
                    canvas.height - 25f,
                    MaintextPaint
                )
            }


        }
        if(textView!=null){
            if(listDescriptionOnDisplay.size>0){
                textView.text = Arrays.toString(listDescriptionOnDisplay.toArray())
                textView.visibility = VISIBLE
            }else{
                textView.visibility = INVISIBLE
            }
        }
        canvas.drawText(
            MainText,
            canvas.width / 2 - 100f,
            canvas.height - 25f,
            MaintextPaint
        )
        canvas.drawCircle((canvas.width/2).toFloat(), (canvas.height/2).toFloat(),8f,centerPaint)

    }

    // function to find if given point
    // lies inside a given rectangle or not.
    private fun checkPointInRect(
        x1: Float, y1: Float, x2: Float,
        y2: Float, x: Float, y: Float
    ): Boolean {
        if (x > x1 && x < x2 &&
            y > y1 && y < y2)
            return true;

        return false;
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
