package ru.`object`.epsoncamera.util.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.Result
import org.tensorflow.lite.examples.detection.R
import ru.`object`.epsoncamera.camera.ObjectDetectorAnalyzer
import ru.`object`.epsoncamera.detection.DetectionResult
import ru.`object`.epsoncamera.utils.DetectorUtils
import java.util.*


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
    private var objectsDetectedWait = HashMap<String,Int>()

    private val objectsAngles = HashMap<String, DetectionResult>()

    private var listObjectForDescribe = HashMap<String,String>()
    private var listDescriptionOnDisplay = arrayListOf<String>()

    private val Platas = HashMap<String,Boolean>().apply {
        put("PlataGreenWithProcessor",false)
        put("PlataGreenWithoutProcessor",false)
        put("PlataBlue",false)
        put("PlataOrange",false)
        put("Platamicroboard",false)

    }
    private val scenery: Scenery = Scenery(context = context)

    private val allLabels = DetectorUtils.loadLabelsFile(context.assets, "labelmap.txt")

    private val allDescribes = DetectorUtils.loadLabelsFile(context.assets, "descriptionmap.txt")

    private val bartextTolink = DetectorUtils.loadBarcodetextToLinkFile(context.assets, "barcodetextTolink.txt")


    private lateinit var textView: TextView
    private lateinit var webView: WebView
    private var currentY = 500


    init{
        for( i in 0 until allLabels.size){
            Log.d("Tag",allLabels[i])
            listObjectForDescribe.put(allLabels[i],allDescribes[i])
        }
    }

    fun setDescriptionText(text:TextView){
        textView = text
    }
    fun setWebView(web: WebView){
        webView = web
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
    private var barcoderesult: Result? = null


    private var handbound: Array<IntArray>? = null
    private var FirstUp = false
    private var FirstBottom = false
    private var time = 0

    private var handboundVsplesk = FloatArray(300) { 0.0f }
    private var intArray = IntArray(300 * 800)


    fun updateResults(result: ObjectDetectorAnalyzer.Result, barcoderesult: Result?,handbound:Array<IntArray>?,isDark:Boolean) {
        if(isDark){
            hidePdfOnPage()
            scenery.reset()
            this.result = null
            this.barcoderesult  = null
            this.handbound = null
            FirstUp = false
            FirstBottom = false
            time = 0
            handboundVsplesk = FloatArray(300) { 0.0f }
            intArray = IntArray(300 * 800)
        }
        this.result = result
        this.barcoderesult =  barcoderesult
        this.handbound = handbound
        invalidate()
    }
    var MainText:String = context.getString(R.string.Find_processor)
    private var counter =0
    val idProcessor = allLabels[8] //
    val idProcessorPlace = allLabels[9] //


    //barcode article
    private var onthescene=false;
    override fun onDraw(canvas: Canvas) {
        //reset
        objectsDetectedNew.clear()

        val result = result ?: return
        Log.d("FACEFACE",result.objects.toString())
        var newresult= result.objects as MutableList<DetectionResult>
        //К данным которые на экране добавили данные которые были в прошлом фрейме но еще живут в массиве wait
        for( i in objectsAngles.values.filter { obj -> objectsDetectedWait.get(obj.title)!! >=0 && objectsDetectedWait.get(obj.title)!! <=10 }){
            if(newresult.find { it.title==i.title }==null){
                newresult=newresult.plus(i) as MutableList<DetectionResult>

            }
        }
        //Snackbar.make(getWindow().getDecorView(), e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();
        val scaleFactorX = measuredWidth / result.imageWidth.toFloat()
        val scaleFactorY = measuredHeight / result.imageHeight.toFloat()
        Platas.apply {
            set("PlataGreenWithProcessor",false)
            set("PlataGreenWithoutProcessor",false)
            set("PlataBlue",false)
            set("PlataOrange",false)
            set("Platamicroboard",false)

        }
        result.objects.forEach { obj ->
            //Обработка того, какая плата сейчас видна на экране
            if(Platas.containsKey(obj.title)){
                Platas.set(obj.title,true)
            }else {
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
        for(i in objectsSetNew){
            if(!objectsSetOld.contains(i)){
                objectsDetectedOld.put(i,1)
            }
            //также инициализируем в списке на подождать вдруг появится
            if(!objectsDetectedWait.keys.contains(i)) {
                objectsDetectedWait.put(i, 0)
            }
        }
        for(i in objectsSetOld){
            //Проверяем если есть в новом массиве элемент, то увеличиваем на +1
            // Если нет, то обнуляем
            if (objectsSetNew.contains(i)){
                objectsDetectedOld.set(i,objectsDetectedOld.get(i)?.plus(1)!!)
                objectsDetectedWait.set(i,0)

            }else{
                if(objectsDetectedWait.get(i)!!<=10){
                    objectsDetectedWait.set(i,objectsDetectedWait.get(i)?.plus(1)!!)
                }
                Log.d("WAITWAIT",objectsDetectedWait.toString())
                Log.d("WAITWAIT---",objectsDetectedOld.toString())
                if(objectsDetectedWait.get(i)!! >= 10) {//2 sec wait 30 fps
                    objectsDetectedOld[i] = 0
                }
            }

        }
        Log.d("WAITWAIT---new",newresult.toString())


        newresult.forEach { obj ->

//scenery
            // Log.d("RECREC", "${objectsDetectedOld.get(idProcessor)!!}")
            try {

//вне сценария горят все элементы
                if (!Platas.containsKey(obj.title)) {
                    //Каждый 15 фрейм берем и записываем                   после 15 фрейма
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
                    //Считываем данные
                    val left = objectsAngles.get(obj.title)?.location?.left!!
                    val top = objectsAngles.get(obj.title)?.location?.top!!
                    val right = objectsAngles.get(obj.title)?.location?.right!!
                    val bottom = objectsAngles.get(obj.title)?.location?.bottom!!

                    //Ищем процессор
                    if (objectsDetectedOld.contains(idProcessor)) {
                        if (objectsDetectedOld.get(idProcessor)!! >= 30 && !scenery.FindStep) {
                            scenery.checkFind()
                            MainText = context.getString(R.string.Insert_to_place)
                        }
                        //Выполнение шага по вставлению элемента в плату
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
                                if (obj.title != "click") {
                                    canvas.drawRect(left, top, right, bottom, boxPaint)
                                    canvas.drawText(obj.text, left, top + 25f, textPaint)
                                }
                            }
                        }
                        if (Platas.get("PlataGreenWithProcessor")!!) {
                            if (scenery.FindStep && !scenery.InsertStep) {
                                scenery.checkInsert()
                                MainText = context.getString(R.string.Congratulation)

                            }
                        }
                        if(scenery.FindStep && scenery.InsertStep){
                            boxPaint.color = ab
                            textPaint.color = Color.WHITE
                            if (obj.title != "click") {
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
                        if (obj.title != "click") {

                            canvas.drawRect(left, top, right, bottom, boxPaint)
                            canvas.drawText(obj.text, left, top + 25f, textPaint)
                        }

                    }
                    //Очищаем каждый раз описание всправа
                    listDescriptionOnDisplay.clear()
                    //выводим инфу в вправую область
                    if (checkPointInRect(
                            left,
                            top,
                            right,
                            bottom,
                            (canvas.width / 2).toFloat(),
                            (canvas.height/ 2).toFloat()
                        )
                    ) {
                        listDescriptionOnDisplay.add(listObjectForDescribe.get(obj.title)!!)
                    }
                    // }

                }
            } catch (e: Exception) {
                Toast.makeText(this.context, Arrays.toString(e.stackTrace), Toast.LENGTH_LONG)
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
        if(textView!=null){
            if(listDescriptionOnDisplay.size>0){
                textView.text = Arrays.toString(listDescriptionOnDisplay.toArray())
                textView.visibility = VISIBLE
            }else{
                textView.visibility = INVISIBLE
            }
        }

        //Вывод подсказки внизу
        canvas.drawText(
            MainText,
            canvas.width / 2 - 100f,
            canvas.height - 25f,
            MaintextPaint
        )
        //Показ точки по середине
        canvas.drawCircle((canvas.width/2).toFloat(), (canvas.height/2).toFloat(),8f,centerPaint)

        //работа с barcoderesult
        if(barcoderesult!=null) {
            if(barcoderesult!!.text.isNotEmpty() && !onthescene) {
                onthescene = true
                Log.d("barcodetext", barcoderesult!!.text)
                if(barcoderesult!!.text.contains("http")){
                    // runBlocking(Dispatchers.Default){
                    val pair = bartextTolink.find{it -> it.first==(barcoderesult?.text)}
                    if(pair!=null) {
                        showPdfOnPage(pair.second)
                    }
                    // }
                }
            }
        }

        //работа с рукой
        if(time>0){
            time--
        }
        if(handbound!=null) {
            for (i in 0 until handbound!!.size) {
                for (j in 0 until handbound!![i].size) {

                    handboundVsplesk[i] +=handbound!![i][j].toFloat()
                    intArray.set(i * j + j, handbound!![i][j]*1200000)

                    /* if(handbound!![i][j]==1) {
                            centerPaint.color = Color.WHITE
                            canvas.drawCircle(i.toFloat(), j.toFloat(), 3.0f, centerPaint)
                        }
                        else {
                            centerPaint.color = Color.BLACK
                            canvas.drawCircle(i.toFloat(), j.toFloat(), 3.0f, centerPaint)
                        }*/
                }
                if(handboundVsplesk[i]>0){
                    Log.d("Edin1",handboundVsplesk[i].toString() + " "+handbound!![i].size.toString())
                }
                handboundVsplesk[i] /=handbound!![i].size.toFloat()
                Log.d("Edin2",handboundVsplesk[i].toString())

            }

            var header =0.0f
            for(i in 0 until handboundVsplesk.size/3){
                header+=handboundVsplesk[i]
            }
            header/=(handboundVsplesk.size/3)

            var footer =0.0f
            var padding = handboundVsplesk.size*2/3
            for(i in 0 until handboundVsplesk.size/3){
                footer+=handboundVsplesk[i+padding]
            }
            footer/=(handboundVsplesk.size/3)
            if(header>0.04){
                FirstUp=true
                if(FirstBottom && time>0){
                    swipeUp(canvas)
                    FirstUp=false
                    FirstBottom=false
                    time=0
                }else {
                    time = 30
                }
            }
            if(footer>0.04){
                FirstBottom=true
                if(FirstUp && time>0){
                    swipeDown(canvas)
                    FirstUp=false
                    FirstBottom=false
                    time=0
                }else {
                    time = 30
                }
            }
            for(i in handboundVsplesk.indices){
                Log.d("VSPLESK",handboundVsplesk[i].toString())
                if(handboundVsplesk[i]>0.035f){
                    canvas.drawCircle(10.0f,10.0f*i+2.0f,2.0f,boxPaint)

                }else {
                    canvas.drawCircle(10.0f,10.0f*i+2.0f,2.0f,centerPaint)
                }
            }
            if(header>0.02){
                canvas.drawCircle(50.0f,10.0f,5.0f,boxPaint)
            }else{
                canvas.drawCircle(50.0f,10.0f,5.0f,centerPaint)

            }
            if(footer>0.02){
                canvas.drawCircle(70.0f,10.0f,5.0f,boxPaint)
            }else{
                canvas.drawCircle(70.0f,10.0f,5.0f,centerPaint)

            }
            canvas.drawBitmap(
                Bitmap.createBitmap(
                    intArray,
                    handbound!!.size,
                    handbound!![0].size,
                    Bitmap.Config.ARGB_8888
                ), 0.0f, 0.0f, centerPaint
            )
        }
    }

    private fun swipeUp(canvas: Canvas){
        canvas.drawCircle(100.0f,100.0f,10.0f,centerPaint)
        currentY -=500
        if(currentY-500<0)
            currentY=0
        webView.scrollY = currentY

    }
    private fun swipeDown(canvas: Canvas){
        canvas.drawCircle(100.0f,100.0f,10.0f,boxPaint)
        currentY +=500

        webView.scrollY = currentY


    }
    private fun showPdfOnPage(LinkTo :String){
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
        webView.scrollY = currentY

    }
    private fun hidePdfOnPage(){
        webView.visibility = INVISIBLE
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


/*
    private var rgbBitmap: Bitmap? = null
    private var matrixToInput: Matrix? = null


    private val TEXT_SIZE_DIP = 18f
    private val MIN_SIZE = 16.0f
    private val COLORS = intArrayOf(
        Color.BLUE,
        Color.RED,
        Color.GREEN,
        Color.YELLOW,
        Color.CYAN,
        Color.MAGENTA,
        Color.WHITE,
        Color.parseColor("#55FF55"),
        Color.parseColor("#FFA500"),
        Color.parseColor("#FF8888"),
        Color.parseColor("#AAAAFF"),
        Color.parseColor("#FFFFAA"),
        Color.parseColor("#55AAAA"),
        Color.parseColor("#AA33AA"),
        Color.parseColor("#0D0068")
    )
    val screenRects: List<Pair<Float, RectF>> = LinkedList()
    private val availableColors: Queue<Int> = LinkedList()
    private val trackedObjects: List<org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker.TrackedRecognition> =
        LinkedList<org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker.TrackedRecognition>()
    private val boxPaint = Paint().apply {

        setColor(Color.RED)
        setStyle(Paint.Style.STROKE)
        setStrokeWidth(10.0f)
        setStrokeCap(Cap.ROUND)
        setStrokeJoin(Join.ROUND)
        setStrokeMiter(100f)


    }
    private val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        18F,
        context.resources.displayMetrics
    )
    private val borderedText: BorderedText? = BorderedText(textSizePx)
    private var frameToCanvasMatrix: Matrix? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private val sensorOrientation = 0

    @Synchronized
    fun setFrameConfiguration(
        width: Int, height: Int, sensorOrientation: Int
    ) {
        frameWidth = width
        frameHeight = height
        this.sensorOrientation = sensorOrientation
    }

    @Synchronized
    fun trackResults(results: List<SimilarityClassifier.Recognition>, timestamp: Long) {
        logger.i("Processing %d results from %d", results.size, timestamp)
        processResults(results)
    }

    private fun getFrameToCanvasMatrix(): Matrix {
        return frameToCanvasMatrix!!
    }

    private var lastsq: Float? = null
    private var nowsq: Float? = null

    @Synchronized
    fun draw(canvas: Canvas, context: Context) {
        val rotated = sensorOrientation % 180 == 90
        val multiplier = Math.min(
            canvas.height / (if (rotated) frameWidth else frameHeight).toFloat(),
            canvas.width / (if (rotated) frameHeight else frameWidth).toFloat()
        )
        frameToCanvasMatrix = ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (multiplier * if (rotated) frameHeight else frameWidth).toInt(),
            (multiplier * if (rotated) frameWidth else frameHeight).toInt(),
            sensorOrientation,
            false
        )
        for (recognition in trackedObjects) {
            val trackedPos = RectF(recognition.location)
            val SQUERE = trackedPos.width() * trackedPos.height()
            if (lastsq == null) {
                lastsq = SQUERE
            }
            nowsq = SQUERE
            val SQUEREMAX = 300000.0f
            val canvasSQUERE = canvas.width * canvas.height
            getFrameToCanvasMatrix().mapRect(trackedPos)
            boxPaint.color = recognition.color
            val cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f
            // canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
            @SuppressLint("DefaultLocale") val strConfidence =
                if (recognition.detectionConfidence < 0) "" else String.format(
                    "%.2f",
                    recognition.detectionConfidence
                ) + ""
            val labelString = if (!TextUtils.isEmpty(recognition.title)) String.format(
                "%s %s",
                recognition.title,
                strConfidence
            ) else strConfidence
            //label up box
            //borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.top, (Math.pow(SQUERE/SQUEREMAX,0.5f))+" ", boxPaint);

            //borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, canvasSQUERE+"", boxPaint);
            val mine = BitmapFactory.decodeResource(context.resources, R.drawable.mine)
            val matrix = Matrix()
            plavno(canvas, mine, matrix, lastsq, nowsq, SQUEREMAX, trackedPos)
            *//*  matrix.postScale((float)(1.0f*0.5/Math.pow(SQUERE/SQUEREMAX,0.05f)),(float)(1.0f*0.5/Math.pow(SQUERE/SQUEREMAX,0.05f)));
      canvas.drawBitmap(mine,matrix,boxPaint);*//*
        }
        lastsq = nowsq
    }

    private fun plavno(
        canvas: Canvas,
        mine: Bitmap,
        matrix: Matrix,
        lastsq: Float?,
        nowsq: Float?,
        SQUEREMAX: Float,
        trackedPos: RectF
    ) {
        *//* if(lastsq<nowsq) {
      for (float i = lastsq; i < nowsq; i +=(nowsq-lastsq)/1) {
        matrix.postScale((float) (1.0f * 0.5 / Math.pow(i / SQUEREMAX, 0.05f)), (float) (1.0f * 0.5 / Math.pow(i / SQUEREMAX, 0.05f)));

      }
    }else{
      for (float i = lastsq; i > nowsq; i -=(lastsq-nowsq)/1) {
        matrix.postScale((float) (1.0f * 0.5 / Math.pow(i / SQUEREMAX, 0.05f)), (float) (1.0f * 0.5 / Math.pow(i / SQUEREMAX, 0.05f)));
        canvas.drawBitmap(mine,matrix,boxPaint);

      }
    }*//*

        // Bitmap rgbBitmap = getArgbBitmap((float)(1.0f*0.5/Math.pow(nowsq/SQUEREMAX,0.05f)), (float)(1.0f*0.5/Math.pow(nowsq/SQUEREMAX,0.05f)));

        // yuvToRgbConverter.yuvToRgb(image, rgbBitmap)
        var toInput = matrixToInput!!
        if (toInput == null) {
            toInput = ImageUtils2().getTransformMatrix(
                0, mine.width, mine.height,
                (mine.width * (1.0f * 0.1 / Math.pow(
                    (nowsq!! / SQUEREMAX).toDouble(),
                    0.05
                ))).toInt(),
                (mine.height * (1.0f * 0.1 / Math.pow(
                    (nowsq / SQUEREMAX).toDouble(),
                    0.05
                ))).toInt()
            )
            matrixToInput = toInput
        }
        val transformation = toInput
        var resizedBitmap = Bitmap.createBitmap(
            (mine.width * (1.0f * 0.5 / Math.pow((nowsq!! / SQUEREMAX).toDouble(), 0.05))).toInt(),
            (mine.height * (1.0f * 0.5 / Math.pow((nowsq / SQUEREMAX).toDouble(), 0.05))).toInt(),
            Bitmap.Config.ARGB_8888
        )

        //new Canvas(resizedBitmap).drawBitmap(mine, transformation, null);
        resizedBitmap = Bitmap.createScaledBitmap(
            mine,
            (mine.width * (1.0f * 0.1 / Math.pow((nowsq / SQUEREMAX).toDouble(), 0.3))).toInt(),
            (mine.height * (1.0f * 0.1 / Math.pow((nowsq / SQUEREMAX).toDouble(), 0.3))).toInt(),
            false
        )
        val centerx = canvas.width / 2
        val centery = canvas.height / 2
        val angleX = (-((centery - trackedPos.centerY()) / centery * 40)).toInt()
        val angleY = ((centerx - trackedPos.centerX()) / centerx * 10).toInt()
        Log.d("BoxBox1", angleX.toString() + "")
        Log.d("BoxBox1", trackedPos.centerY().toString() + "")
        Log.d("BoxBox1", centery.toString() + "")
        Log.d("BoxBox", centery.toString() + "")
        Log.d("BoxBox", resizedBitmap.width.toString() + "")
        // matrix.postScale((float)(1.0f*0.5/Math.pow(nowsq/SQUEREMAX,0.05f)),(float)(1.0f*0.5/Math.pow(nowsq/SQUEREMAX,0.05f)));
        try {
            //Snippet from a function used to handle a draw
            val camera = Camera()
            val matrix2 = Matrix()
            canvas.save() //save a 'clean' matrix that doesn't have any camera rotation in it's matrix
            ApplyMatrix(
                canvas,
                camera,
                matrix2,
                angleX,
                angleY *//*,x,y,z*//*
            ) //apply rotated matrix to canvas
            canvas.drawBitmap(
                resizedBitmap,
                (centerx - resizedBitmap.width / 2).toFloat(),
                (centery - resizedBitmap.height / 2).toFloat(),
                boxPaint
            )
            //      camera.applyToCanvas(canvas);
            canvas.restore() //restore clean matrix
        } catch (e: Exception) {
        }


//
    }

    fun ApplyMatrix(
        mCanvas: Canvas,
        mCamera: Camera,
        mMatrix: Matrix,
        angleX: Int,
        angleY: Int *//*,float x,float y,float z*//*
    ) {
        mCamera.save()
        mCamera.rotateX(angleX.toFloat())
        mCamera.rotateY(angleY.toFloat())
        mCamera.rotateZ(0f)
        mCamera.getMatrix(mMatrix)
        *//*  mCamera.setLocation(x,y,z);*//*
        val CenterX = mCanvas.width / 2
        val CenterY = mCanvas.height / 2
        mMatrix.preTranslate(
            -CenterX.toFloat(),
            -CenterY.toFloat()
        ) //This is the key to getting the correct viewing perspective
        mMatrix.postTranslate(CenterX.toFloat(), CenterY.toFloat())
        mCanvas.concat(mMatrix)
        mCamera.restore()
    }

    private fun getArgbBitmap(width: Float, height: Float): Bitmap? {
        var bitmap = rgbBitmap!!
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
            rgbBitmap = bitmap
        }
        return bitmap
    }
    *//*private Matrix getTransformation(Integer srcWidth,Integer srcHeight) {
    Matrix toInput = matrixToInput;
    if (toInput == null) {
      toInput = ImageUtil.INSTANCE.getTransformMatrix(0, srcWidth, srcHeight, config.inputSize, config.inputSize);
      matrixToInput = toInput;
    }
    return toInput;
  }*//*

    *//*private Matrix getTransformation(Integer srcWidth,Integer srcHeight) {
    Matrix toInput = matrixToInput;
    if (toInput == null) {
      toInput = ImageUtil.INSTANCE.getTransformMatrix(0, srcWidth, srcHeight, config.inputSize, config.inputSize);
      matrixToInput = toInput;
    }
    return toInput;
  }*//*
    private fun processResults(results: List<SimilarityClassifier.Recognition>) {
        val rectsToTrack: MutableList<Pair<Float, SimilarityClassifier.Recognition>> =
            LinkedList<Pair<Float, SimilarityClassifier.Recognition>>()
        screenRects.clear()
        val rgbFrameToScreen = Matrix(getFrameToCanvasMatrix())
        for (result in results) {
            if (result.getLocation() == null) {
                continue
            }
            val detectionFrameRect = RectF(result.getLocation())
            val detectionScreenRect = RectF()
            rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect)
            logger.v(
                "Result! Frame: " + result.getLocation()
                    .toString() + " mapped to screen:" + detectionScreenRect
            )
            screenRects.add(Pair<Float, RectF>(result.getDistance(), detectionScreenRect))
            if (detectionFrameRect.width() < org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker.MIN_SIZE || detectionFrameRect.height() < org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker.MIN_SIZE) {
                logger.w("Degenerate rectangle! $detectionFrameRect")
                continue
            }
            rectsToTrack.add(Pair<Float, SimilarityClassifier.Recognition>(result.getDistance(), result))
        }
        trackedObjects.clear()
        if (rectsToTrack.isEmpty()) {
            logger.v("Nothing to track, aborting.")
            return
        }
        for (potential in rectsToTrack) {
            val trackedRecognition = TrackedRecognition()
            trackedRecognition.detectionConfidence = potential.first
            trackedRecognition.location = RectF(potential.second.getLocation())
            trackedRecognition.title = potential.second.getTitle()
            if (potential.second.getColor() != null) {
                trackedRecognition.color = potential.second.getColor()
            } else {
                trackedRecognition.color =
                    org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker.COLORS.get(
                        trackedObjects.size
                    )
            }
            trackedObjects.add(trackedRecognition)
            if (trackedObjects.size >= org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker.COLORS.size) {
                break
            }
        }
    }

    private class TrackedRecognition {
        var location: RectF? = null
        var detectionConfidence = 0f
        var color = 0
        var title: String? = null
    }*/

}
