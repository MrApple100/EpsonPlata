package ru.`object`.epsoncamera.epsonLocal.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import ru.`object`.epsoncamera.epsonLocal.R
import ru.`object`.epsoncamera.epsonLocal.camera.ObjectDetectorAnalyzer
import ru.`object`.epsoncamera.epsonLocal.detection.DetectionResult
import ru.`object`.epsoncamera.epsonLocal.utils.DetectorUtils
import java.util.*


class RecognitionResultOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    var alphaColor = 70
    var ar: Int = Color.argb(alphaColor, 255, 0, 0)
    var ag: Int = Color.argb(alphaColor, 0, 255, 0)
    var ab: Int = Color.argb(alphaColor, 0, 0, 255)
    var halfdark: Int = Color.argb(120, 0, 0, 0)


    private var objectsDetectedOld = HashMap<String, Int>()
    private val objectsDetectedNew = HashMap<String, Int>()
    private var objectsDetectedWait = HashMap<String, Int>()

    private val objectsAngles = HashMap<String, DetectionResult>()

    private var listObjectForDescribe = HashMap<String, String>()
    private var listDescriptionOnDisplay = arrayListOf<String>()

    private val Platas = HashMap<String, Boolean>().apply {
        put("PlataGreenWithProcessor", false)
        put("PlataGreenWithoutProcessor", false)
        put("PlataBlue", false)
        put("PlataOrange", false)
        put("Platamicroboard", false)

    }


    private val allLabels = DetectorUtils.loadLabelsFile(context.assets, "labelmap.txt")

    private val allDescribes = DetectorUtils.loadLabelsFile(context.assets, "descriptionmap.txt")

    private val bartextTolink =
        DetectorUtils.loadBarcodetextToLinkFile(context.assets, "barcodetextTolink.txt")


    private lateinit var textView: TextView
    private lateinit var webView: WebView
    private var currentY = MutableStateFlow(500)


    init {
        for (i in 0 until allLabels.size) {
            Log.d("Tag", allLabels[i])
            listObjectForDescribe.put(allLabels[i], allDescribes[i])
        }
    }

    fun setDescriptionText(text: TextView) {
        textView = text
    }

    fun setWebView(web: WebView) {
        webView = web
    }

    private val HalfDarkPaint = Paint().apply {
        color = halfdark
        style = Paint.Style.FILL
        strokeWidth = 5f
    }
    private val boxPaint = Paint().apply {
        color = ab
        style = Paint.Style.FILL_AND_STROKE
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
    private var barcoderesult: Result? = null

    private var scenery: Scenery? = null

    private var handbound: Array<IntArray>? = null
    private var FirstUp = false
    private var FirstBottom = false
    private var time = 0

    private var handboundVsplesk = FloatArray(300) { 0.0f }
    private var intArray = IntArray(300 * 800)

    var scaleFactorX = 1f
    var scaleFactorY = 1f

    var webViewheight = 4000;

    fun updateResults(
        result: ObjectDetectorAnalyzer.Result,
        barcoderesult: Result?,
        handbound: Array<IntArray>?,
        isDark: Boolean,
        scenery: Scenery
    ) {

        scaleFactorX = measuredWidth / result.imageWidth.toFloat()
        scaleFactorY = measuredHeight / result.imageHeight.toFloat()
        // Log.d("SECSEC2","1")
        // Log.d("SECSEC2","1"+scenery.now.name)
        this.scenery = scenery
        this.result = result
        this.barcoderesult = barcoderesult
        this.handbound = handbound
        if (isDark) {
            hidePdfOnPage()
            this.result = null
            this.barcoderesult = null
            this.handbound = null
            FirstUp = false
            FirstBottom = false
            time = 0
            handboundVsplesk = FloatArray(300) { 0.0f }
            intArray = IntArray(300 * 800)


            // scenery.now = Scenery.ScennaryItem.SettingHand
            sec.value = 0
        }
        invalidate()
    }

    var MainText: String = context.getString(R.string.Find_processor)
    private var counter = 0
    val idProcessor = allLabels[8] //
    val idProcessorPlace = allLabels[9] //

    var sec = MutableStateFlow(0);


    //barcode article
    private var onthescene = false;
    override fun onDraw(canvas: Canvas) {
        // Log.d("SECSEC",scenery.toString() +result.toString() + barcoderesult.toString())
        if (scenery != null && result != null) {
            //reset
            objectsDetectedNew.clear()

            val result = result ?: return
            Log.d("FACEFACE", result.objects.toString())
            var newresult = result.objects as MutableList<DetectionResult>
            //К данным которые на экране добавили данные которые были в прошлом фрейме но еще живут в массиве wait
            for (i in objectsAngles.values.filter { obj ->
                objectsDetectedWait.get(obj.title)!! >= 0 && objectsDetectedWait.get(
                    obj.title
                )!! <= 3 //10
            }) {
                if (newresult.find { it.title == i.title } == null) {
                    newresult = newresult.plus(i) as MutableList<DetectionResult>

                }
            }
            //Snackbar.make(getWindow().getDecorView(), e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();

            Platas.apply {
                set("PlataGreenWithProcessor", false)
                set("PlataGreenWithoutProcessor", false)
                set("PlataBlue", false)
                set("PlataOrange", false)
                set("Platamicroboard", false)

            }
            result.objects.forEach { obj ->
                //Обработка того, какая плата сейчас видна на экране
                if (Platas.containsKey(obj.title)) {
                    Platas.set(obj.title, true)
                } else {
                    //добавляем все объекты в новый массив
                    objectsDetectedNew.put(obj.title, 1)
                    //если для объекта еще не создан ключ значение в массиве параметров углов, то создаем
                    if (!objectsAngles.contains(obj.title)) {
                        objectsAngles.put(obj.title, obj)

                    }
                }
            }

            val objectsSetOld = objectsDetectedOld.keys
            val objectsSetNew = objectsDetectedNew.keys

            //Если элемента нет в старом списке, то инициализируем его
            for (i in objectsSetNew) {
                if (!objectsSetOld.contains(i)) {
                    objectsDetectedOld.put(i, 1)
                }
                //также инициализируем в списке на подождать вдруг появится
                if (!objectsDetectedWait.keys.contains(i)) {
                    objectsDetectedWait.put(i, 0)
                }
            }
            for (i in objectsSetOld) {
                //Проверяем если есть в новом массиве элемент, то увеличиваем на +1
                // Если нет, то обнуляем
                if (objectsSetNew.contains(i)) {
                    objectsDetectedOld.set(i, objectsDetectedOld.get(i)?.plus(1)!!)
                    objectsDetectedWait.set(i, 0)

                } else {
                    if (objectsDetectedWait.get(i)!! <= 3) {//10
                        objectsDetectedWait.set(i, objectsDetectedWait.get(i)?.plus(1)!!)
                    }
                    Log.d("WAITWAIT", objectsDetectedWait.toString())
                    Log.d("WAITWAIT---", objectsDetectedOld.toString())
                    if (objectsDetectedWait.get(i)!! >= 3) {//2 sec wait 30 fps//10
                        objectsDetectedOld[i] = 0
                    }
                }

            }
            Log.d("WAITWAIT---new", newresult.toString())

            // Log.d("SECSEC", scenery!!.now.name)

            when (scenery!!.now) {
                Scenery.ScennaryItem.SettingHand -> {

                    MainText = when (sec.value) {
                        4 -> {
                            runBlocking(Dispatchers.IO) {
                                delay(1000)
                                sec.value++;
                            }
                            "3"

                        }
                        5 -> {
                            runBlocking(Dispatchers.IO) {
                                delay(1000)
                                sec.value++;
                            }
                            "2"
                        }
                        6 -> {
                            runBlocking(Dispatchers.IO) {
                                delay(1000)
                                scenery!!.now = Scenery.ScennaryItem.manualSettingHand
                                sec.value = 0

                            }
                            ""

                        }
                        else -> {
                            runBlocking(Dispatchers.IO) {
                                delay(1000)
                                sec.value++;
                            }
                            context.getString(R.string.RecognizeColor)
                        }
                    }
                    Log.d("SECSEC", sec.value.toString())

                    canvas.drawRect(
                        width / 2f + width / 2 / 4f,
                        0f,
                        width / 1f,
                        height / 1f,
                        HalfDarkPaint
                    )
                    canvas.drawRect(0f, 0f, width / 2f - width / 2 / 4f, height / 1f, HalfDarkPaint)
                    canvas.drawRect(
                        width / 2f - width / 2 / 4f,
                        0f,
                        width / 2f + width / 2 / 4f,
                        height / 2f - height / 2 / 2f,
                        HalfDarkPaint
                    )
                    canvas.drawRect(
                        width / 2f + width / 2 / 4f,
                        height / 2f + height / 2 / 2f,
                        width / 2f - width / 2 / 4f,
                        height / 1f,
                        HalfDarkPaint
                    )

                }
                Scenery.ScennaryItem.manualSettingHand -> {
                    if (handbound != null) {
                        for (i in 0 until handbound!!.size) {
                            for (j in 0 until handbound!![i].size) {

                                if (handbound!![i][j] == 1) {

                                    centerPaint.color = Color.WHITE
                                    canvas.drawRect(
                                        canvas.width / 2 + j.toFloat(),
                                        canvas.height / 4 + i.toFloat(),
                                        canvas.width / 2 + j.toFloat() + 1,
                                        canvas.height / 4 + i.toFloat() + 1,
                                        centerPaint
                                    )
                                } else {
                                    centerPaint.color = Color.BLACK
                                    canvas.drawRect(
                                        canvas.width / 2 + j.toFloat(),
                                        canvas.height / 4 + i.toFloat(),
                                        canvas.width / 2 + j.toFloat() + 1,
                                        canvas.height / 4 + i.toFloat() + 1,
                                        centerPaint
                                    )
                                }
//
                            }

                        }
                    }
                }
                Scenery.ScennaryItem.Barcode -> {
                    textView.visibility = INVISIBLE

                    MainText = context.getString(R.string.WorkWithBarcode)

                    //работа с рукой
                    if (webView.visibility == VISIBLE) {
                        if (time > 0) {
                            time--
                        }
                        if (handbound != null) {
                            for (i in 0 until handbound!!.size) {
                                for (j in 0 until handbound!![i].size) {

                                    handboundVsplesk[i] += handbound!![i][j].toFloat()
                                    intArray.set(i * j + j, handbound!![i][j] * 1200000)
                                }
                                if (handboundVsplesk[i] > 0) {
                                    /*Log.d(
                            "Edin1",
                            handboundVsplesk[i].toString() + " " + handbound!![i].size.toString()
                        )*/
                                }
                                handboundVsplesk[i] /= handbound!![i].size.toFloat()
                                // Log.d("Edin2", handboundVsplesk[i].toString())

                            }

                            var header = 0.0f
                            for (i in 0 until handboundVsplesk.size / 3) {
                                header += handboundVsplesk[i]
                            }
                            header /= (handboundVsplesk.size / 3)

                            var footer = 0.0f
                            var padding = handboundVsplesk.size * 2 / 3
                            for (i in 0 until handboundVsplesk.size / 3) {
                                footer += handboundVsplesk[i + padding]
                            }
                            footer /= (handboundVsplesk.size / 3)
                            if (header > 0.04) {
                                FirstUp = true
                                if (FirstBottom && time > 0) {
                                    swipeUp(canvas)
                                    FirstUp = false
                                    FirstBottom = false
                                    time = 0
                                } else {
                                    time = 30
                                }
                            }
                            if (footer > 0.04) {
                                FirstBottom = true
                                if (FirstUp && time > 0) {
                                    swipeDown(canvas)
                                    FirstUp = false
                                    FirstBottom = false
                                    time = 0
                                } else {
                                    time = 30
                                }
                            }
                            for (i in handboundVsplesk.indices) {
                                // Log.d("VSPLESK", handboundVsplesk[i].toString())
                                if (handboundVsplesk[i] > 0.035f) {
                                    canvas.drawCircle(10.0f, 10.0f * i + 2.0f, 2.0f, boxPaint)

                                } else {
                                    canvas.drawCircle(10.0f, 10.0f * i + 2.0f, 2.0f, centerPaint)
                                }
                            }
                            if (header > 0.02) {
                                canvas.drawCircle(50.0f, 10.0f, 5.0f, boxPaint)
                            } else {
                                canvas.drawCircle(50.0f, 10.0f, 5.0f, centerPaint)

                            }
                            if (footer > 0.02) {
                                canvas.drawCircle(70.0f, 10.0f, 5.0f, boxPaint)
                            } else {
                                canvas.drawCircle(70.0f, 10.0f, 5.0f, centerPaint)

                            }
                            if (handbound!!.size > 0) {
//
                            }
                        }
                    }
                }
                else -> {

                    newresult.forEach { obj ->

//scenery
                        // Log.d("RECREC", "${objectsDetectedOld.get(idProcessor)!!}")
                        try {

                            if (!Platas.containsKey(obj.title)) {
                                //Каждый 15 фрейм берем и записываем                   после 15 фрейма
                                if (objectsDetectedOld.get(obj.title)!! % 3 == 0 && (objectsDetectedOld.get(
                                        obj.title
                                    )!! >= 3)
                                ) {
                                    Log.d("SCALE", "$scaleFactorX $scaleFactorY")
                                    objectsAngles.get(obj.title)?.location?.left =
                                        obj.location.left * scaleFactorX
                                    objectsAngles.get(obj.title)?.location?.top =
                                        obj.location.top * scaleFactorY
                                    objectsAngles.get(obj.title)?.location?.right =
                                        obj.location.right * scaleFactorX
                                    objectsAngles.get(obj.title)?.location?.bottom =
                                        obj.location.bottom * scaleFactorY
                                }
                                //Считываем данные
                                val left = objectsAngles.get(obj.title)?.location?.left!!
                                val top = objectsAngles.get(obj.title)?.location?.top!!
                                val right = objectsAngles.get(obj.title)?.location?.right!!
                                val bottom = objectsAngles.get(obj.title)?.location?.bottom!!
                                Log.d("SECSEC", scenery!!.now.name)

                                when (scenery!!.now) {
                                    //Ищем процессор
                                    Scenery.ScennaryItem.Find -> {

                                        MainText = context.getString(R.string.Find_processor)

                                        val currentIndex = allLabels.indexOf(obj.title)
                                        boxPaint.color = ab
                                        textPaint.color = Color.WHITE
                                        if (obj.title != "click") {
                                            canvas.drawRect(left, top, right, bottom, boxPaint)
                                            canvas.drawText(obj.text, left, top + 25f, textPaint)
                                        }

                                        if (objectsDetectedOld.contains(idProcessor)) {
                                            if (objectsDetectedOld.get(idProcessor)!! >= 5) {//30
                                                scenery!!.now = Scenery.ScennaryItem.Insert
                                                MainText =
                                                    context.getString(R.string.Insert_to_place)
                                            }
                                        }
                                    }
                                    Scenery.ScennaryItem.Insert -> {
                                        //Выполнение шага по вставлению элемента в плату
                                        /*if (scenery.FindStep && !scenery.InsertStep) {*/

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
                                            if (obj.title != "click") {
                                                canvas.drawRect(left, top, right, bottom, boxPaint)
                                                canvas.drawText(
                                                    obj.text,
                                                    left,
                                                    top + 25f,
                                                    textPaint
                                                )
                                            }
                                        }
                                        //if (scenery.FindStep && !scenery.InsertStep) {
                                        if (Platas.get("PlataGreenWithProcessor")!!) {
                                            scenery!!.now = Scenery.ScennaryItem.InsertFinish
                                            MainText = context.getString(R.string.Congratulation)

                                        }
                                        //}
                                    }
                                    Scenery.ScennaryItem.InsertFinish -> {
                                        //if (scenery.FindStep && scenery.InsertStep) {
                                        boxPaint.color = ab
                                        textPaint.color = Color.WHITE
                                        if (obj.title != "click") {
                                            canvas.drawRect(left, top, right, bottom, boxPaint)
                                            canvas.drawText(obj.text, left, top + 25f, textPaint)
                                        }
                                        // }
                                    }
                                    else -> {

                                        MainText = context.getString(R.string.Find_processor)

                                        //вне сценария горят все элементы

                                        val currentIndex = allLabels.indexOf(obj.title)
                                        boxPaint.color = ab
                                        textPaint.color = Color.WHITE
                                        if (obj.title != "click") {

                                            canvas.drawRect(left, top, right, bottom, boxPaint)
                                            canvas.drawText(obj.text, left, top + 25f, textPaint)
                                        }

                                    }
                                }

                                //выводим инфу в вправую область
                                if (checkPointInRect(
                                        left,
                                        top,
                                        right,
                                        bottom,
                                        (canvas.width / 2).toFloat(),
                                        (canvas.height / 2).toFloat()
                                    )
                                ) {
                                    //Очищаем каждый раз описание всправа когда находим новый объект
                                    listDescriptionOnDisplay.clear()
                                    listDescriptionOnDisplay.add(listObjectForDescribe.get(obj.title)!!)
                                }
                                // }

                            }

                        } catch (e: Exception) {
                            Toast.makeText(
                                this.context,
                                Arrays.toString(e.stackTrace),
                                Toast.LENGTH_LONG
                            )
                                .show()
                            canvas.drawText(
                                Arrays.toString(e.stackTrace),
                                canvas.width / 2 - 100f,
                                canvas.height - 25f,
                                MaintextPaint
                            )
                        }


                    }

                    //Свернули описание справа

                    textView.text = Arrays.toString(listDescriptionOnDisplay.toArray())
                    textView.visibility = VISIBLE


                    //Показ точки по середине
                    canvas.drawCircle(
                        (canvas.width / 2).toFloat(),
                        (canvas.height / 2).toFloat(),
                        8f,
                        centerPaint
                    )

                    //работа с barcoderesult
                    if (barcoderesult != null) {
                        scenery!!.now = Scenery.ScennaryItem.Barcode
                        if (barcoderesult!!.text.isNotEmpty() && webView.visibility == INVISIBLE) {
                            Log.d("barcodetext", barcoderesult!!.text)
                            if (barcoderesult!!.text.contains("http")) {
                                // runBlocking(Dispatchers.Default){
                                val pair =
                                    bartextTolink.find { it -> it.first == (barcoderesult?.text) }
                                if (pair != null) {
                                    showPdfOnPage(pair.second)

                                }
                                // }
                            }
                        }
                    }


                }
            }
            //Вывод подсказки внизу
            canvas.drawText(
                MainText,
                canvas.width / 2 - 100f,
                canvas.height - 25f,
                MaintextPaint
            )
        }
    }

    private fun swipeUp(canvas: Canvas) {
        Log.d("SWIPE", "UP" + " ${currentY.value}")
        canvas.drawCircle(100.0f, 100.0f, 10.0f, centerPaint)
        currentY.value -= 500
        if (currentY.value - 500 < 0)
            currentY.value = 0
        webView.scrollY = currentY.value

    }

    private fun swipeDown(canvas: Canvas) {
        Log.d("SWIPE", "DOWN" + " ${currentY.value}")

        canvas.drawCircle(100.0f, 100.0f, 10.0f, boxPaint)
        currentY.value += 500
        if (currentY.value + 500 > webViewheight) {
            currentY.value = webViewheight
        }
        webView.scrollY = currentY.value


    }

    private fun showPdfOnPage(LinkTo: String) {
        webView.settings.javaScriptEnabled = true
        webView.settings.pluginState = WebSettings.PluginState.ON
        webView.visibility = VISIBLE
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }


        }

        webView.loadUrl(LinkTo)
        webView.scrollY = currentY.value

    }

    private fun hidePdfOnPage() {
        webView.visibility = INVISIBLE
    }


    // function to find if given point
    // lies inside a given rectangle or not.
    private fun checkPointInRect(
        x1: Float, y1: Float, x2: Float,
        y2: Float, x: Float, y: Float
    ): Boolean {
        if (x > x1 && x < x2 &&
            y > y1 && y < y2
        )
            return true;

        return false;
    }
}

enum class ColorsEnum {
    magenta, red, blue, green, cyan, yellow, darkgray
}

class Scenery(
    var now: ScennaryItem = ScennaryItem.Find,
    var settingHand: Boolean = false,
    var FindStep: Boolean = false,
    var InsertStep: Boolean = false,
) {

    fun checkFind(): Unit {
        FindStep = true

    }

    fun checkInsert(): Unit {

        InsertStep = true
    }

    fun reset(): Unit {
        FindStep = false
        InsertStep = false

    }

    enum class ScennaryItem {
        SettingHand, manualSettingHand, Find, Insert, InsertFinish, Barcode
    }


}