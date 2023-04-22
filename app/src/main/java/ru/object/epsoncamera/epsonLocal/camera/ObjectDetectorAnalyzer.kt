package ru.`object`.epsoncamera.epsonLocal.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.constraintlayout.solver.widgets.Rectangle
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.`object`.epsoncamera.epsonLocal.MoverioCameraSampleFragment
import ru.`object`.epsoncamera.epsonLocal.detection.DetectionResult
import ru.`object`.epsoncamera.epsonLocal.detection.ObjectDetector
import ru.`object`.epsoncamera.epsonLocal.usecase.BarcodeImageScanner
import ru.`object`.epsoncamera.epsonLocal.utils.ImageUtil
import ru.`object`.epsoncamera.epsonLocal.utils.YuvToRgbConverter
import ru.`object`.epsoncamera.epsonLocal.view.Scenery
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


class ObjectDetectorAnalyzer private constructor(
    private val context: Context,
    private val config: Config,
    private val mainActivity: MoverioCameraSampleFragment?,
    private val onDetectionResult: (Result, com.google.zxing.Result?, Array<IntArray>?, Boolean, Scenery) -> Boolean,
//    private val setHSVPprogress:(Array<Float>) -> Unit
) {


    companion object {
        private const val TAG = "ObjectDetectorAnalyzer"
        private val DEBUG = false
        val datamass = MutableStateFlow<ByteArray>(byteArrayOf())

        private var instance: ObjectDetectorAnalyzer? = null

        fun getInstance(
            context: Context,
            config: Config,
            mainActivity: MoverioCameraSampleFragment?,
            onDetectionResult: (Result, com.google.zxing.Result?, Array<IntArray>?, Boolean, Scenery) -> Boolean,
        ): ObjectDetectorAnalyzer {
            if (instance == null)  // NOT thread safe!
                instance = ObjectDetectorAnalyzer(context, config, mainActivity, onDetectionResult)

            return instance!!
        }
    }

    private val iterationCounter = AtomicInteger(0)

    private val debugHelper = DebugHelper(
        saveResult = false,
        context = context,
        resultHeight = config.inputSize,
        resultWidth = config.inputSize
    )

    private val yuvToRgbConverter = YuvToRgbConverter(context)

    private val uiHandler = Handler(Looper.getMainLooper())

    private var inputArray = IntArray(config.inputSize * config.inputSize)

    private var objectDetector: ObjectDetector? = null

    private var rgbBitmap: Bitmap? = null

    private var matrixToInput: Matrix? = null

    private var scanDispose = CompositeDisposable()

    private var handbound: Array<IntArray>? = null


    private var myResize = MutableStateFlow<Bitmap>(
        Bitmap.createBitmap(
            config.inputSize,
            config.inputSize,
            Bitmap.Config.ARGB_8888
        )
    )
    private var myDarkflow = MutableStateFlow<Boolean>(false)
    private var myhandboundflow = MutableStateFlow<Array<IntArray>>(arrayOf())
    //private var pictureInCenterflow = MutableStateFlow<Array<IntArray>>(arrayOf())

    private var myRGBpictureflow = MutableStateFlow<Bitmap?>(null)
    private var barcoderesultflow = MutableStateFlow<com.google.zxing.Result?>(null)
    private var objectsflow = MutableStateFlow<List<DetectionResult>>(arrayListOf())

    private var myMinMaxColorsflow = MutableStateFlow<Array<Float>>(arrayOf(0f, 0f, 0f, 0f))
    var myMinMaxColorsState = myMinMaxColorsflow.asStateFlow();

    public fun setMinMaxColor(hsvp: Array<Float>) {
        myMinMaxColorsflow.value = hsvp
    }

    private var scenery = MutableStateFlow<Scenery>(Scenery())
    var sceneryState = scenery.asStateFlow();


    public fun setScenery(sceneryItem: Scenery.ScennaryItem) {
        scenery.value.now = sceneryItem
    }


    fun analyze(datamass: ByteArray, width: Int, height: Int) = runBlocking(Dispatchers.IO) {
        val data = ByteBuffer.wrap(datamass)
        data.rewind()
        val rgbBitmapimage = getArgbBitmap(width, height)
        rgbBitmapimage.copyPixelsFromBuffer(data)


        // resizedBitmap = Bitmap.createBitmap(config.inputSize, config.inputSize, Bitmap.Config.ARGB_8888)
        val iteration = iterationCounter.getAndIncrement()

        //val resizedBitmap = getArgbBitmap(config.inputSize, config.inputSize)

        //yuvToRgbConverter.yuvToRgb(image, rgbBitmap)

        val transformation = getTransformation(0, width, height)


        myRGBpictureflow.value = rgbBitmapimage


        if (myRGBpictureflow.value != null)
            Canvas(myResize.value).drawBitmap(myRGBpictureflow.value!!, transformation, null)
        if (scenery.value.now == Scenery.ScennaryItem.SettingHand) {
            launch {
                myMinMaxColorsflow.value = recognizeColorInCenter(myResize.value)
                mainActivity?.setHSVPprogress(myMinMaxColorsflow.value!!)
                delay(500);

            }

        }

        launch {
            myDarkflow.value = DarkReset(myResize.value)
            delay(1000)
        }
        if (myDarkflow.value) {
            launch {
                delay(2000)
                if (DarkReset(myResize.value)) {
                    scenery.value.now = Scenery.ScennaryItem.Find
                    Log.d("COLORCOLOR4", "SH")
                }
            }
        }
        if (scenery.value.now == Scenery.ScennaryItem.manualSettingHand || scenery.value.now == Scenery.ScennaryItem.SettingHand || scenery.value.now == Scenery.ScennaryItem.Barcode) {
            launch {
                myhandboundflow.value = findhand(myResize.value)
            }
        }
//        launch {
//            pictureInCenterflow.value = PictureInCenter(myResize.value)
//        }
        if (myRGBpictureflow.value != null) {
            launch {
                try {
                    barcoderesultflow.value = BarcodeImageScanner.tryParse(myRGBpictureflow.value!!)
                    Log.d("BARCODE", barcoderesultflow.value!!.text)

                } catch (ex: Exception) {
                    Log.d("BARCODE", ex.stackTraceToString())
                }
            }
        }


        // uiHandler.post {
        // threadToDetect.execute {
        ImageUtil.storePixels(myResize.value, inputArray)
        objectsflow.value = detect(inputArray)

        // }
        // }


        //Log.d(TAG, "detection objects($iteration): $objects")
        val result = Result(
            objects = objectsflow.value,
            imageWidth = config.inputSize,
            imageHeight = config.inputSize,
            imageRotationDegrees = 0
        )
        if (myDarkflow.value) {
            barcoderesultflow.value = null
            myhandboundflow.value = arrayOf()
        }
        uiHandler.post {
            onDetectionResult.invoke(
                result,
                barcoderesultflow.value,
                myhandboundflow.value,
                // pictureInCenterflow.value,
                myDarkflow.value,
                scenery.value
            )
        }

        // Log.d("COLORCOLOR", "End")


    }

    private fun getTransformation(rotationDegrees: Int, srcWidth: Int, srcHeight: Int): Matrix {
        var toInput = matrixToInput
        if (toInput == null) {
            toInput = ImageUtil.getTransformMatrix(
                rotationDegrees,
                srcWidth,
                srcHeight,
                config.inputSize,
                config.inputSize
            )
            matrixToInput = toInput
        }
        return toInput
    }

    private fun getArgbBitmap(width: Int, height: Int): Bitmap {
        var bitmap = rgbBitmap
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) as Bitmap
            rgbBitmap = bitmap
        }
        return bitmap
    }

    private fun detect(inputArray: IntArray): List<DetectionResult> {
        var detector = objectDetector
        if (detector == null) {
            detector = ObjectDetector(
                assetManager = context.assets,
                isModelQuantized = config.isQuantized,
                inputSize = config.inputSize,
                labelFilename = config.labelsFile,
                modelFilename = config.modelFile,
                barcodetextTolink = config.barcodetextTolink,
                numDetections = config.numDetection,
                minimumConfidence = config.minimumConfidence,
                numThreads = 1,
                useNnapi = false
            )
            objectDetector = detector
        }

        return detector.detect(inputArray)
    }

    data class Config(
        val minimumConfidence: Float,
        val numDetection: Int,
        val inputSize: Int,
        val isQuantized: Boolean,
        val modelFile: String,
        val labelsFile: String,
        val barcodetextTolink: String
    )

    data class Result(
        val objects: List<DetectionResult>,
        val imageWidth: Int,
        val imageHeight: Int,
        val imageRotationDegrees: Int
    )

    //temp raster


    //temp raster
    private fun DarkReset(rgbBitmap: Bitmap): Boolean {
        val height = rgbBitmap.height
        val width = rgbBitmap.width

        var index = 0
        var howdark = 0
        var pixelRaster = IntArray(width * height)//pixel raster for initial cam image
        ImageUtil.storePixels(myResize.value!!, pixelRaster)


        //THAT
        //First pass, get all skin pixel
        for (i in 0 until height) {
            var j = 0
            while (j < width) {
                val color: IntArray =
                    hexToRGB(pixelRaster.get(index))!! //convert hex arbg integer to RGB array
                val hsb = FloatArray(3) // HSB array
                RGBtoHSB(color[0], color[1], color[2], hsb) //convert RGB to HSB array

                // Initial pass will use strict skin pixel rule.
                // It will only find skin pixels within smaller section compared to loose pixel rule
                // This will help avoid impurities in the detection
                if (DarkRule(hsb)) {
                    howdark++
                }
                j++
                index++
            }
        }
        //   Log.d("DARKDARK","NowDark "+howdark.toFloat()/(width * height))

        return howdark > (width * height) / 100 * 95
    }

    fun DarkRule(hsb: FloatArray): Boolean {
        //  Log.d("DARK",hsb[2].toString())
        return hsb[2] < 0.04f
    }

    //    private fun PictureInCenter(rgbBitmap: Bitmap): Array<IntArray> {
//
//        val height = rgbBitmap.height
//        val width = rgbBitmap.width
//        val centerX = rgbBitmap.width / 2
//        val centerY = rgbBitmap.height / 2
//        val halfWidth = rgbBitmap.width / 4
//        val halfHeight = rgbBitmap.height / 2
//      //  val bitmap = Bitmap.createBitmap(width/4, height/2, Bitmap.Config.ARGB_8888)
//        val aai =  Array(height/2) { IntArray(width/4)}
//
//
//        var tempRasterFull = IntArray(width * height)
//        var tempRaster = IntArray(width / 4 * height / 2)
//
//        try {
//            rgbBitmap.getPixels(
//                tempRaster,        // массив для хранения цветов пикселей
//                0,             // смещение по оси X
//                halfWidth,     // ширина области
//                centerX - halfWidth/2, // смещение по оси X центра области
//                centerY - halfHeight/2, // смещение по оси Y центра области
//                halfWidth,     // ширина области
//                halfHeight     // высота области
//            )
//                for(j in 0 until height/2){
//                    for(i in 0 until width/4){
//                        aai[j][i] = tempRaster[i * j + i]
//                }
//            }
//        } catch (exeption: Exception) {
//            Log.d("COLORCOLOR2"+"${width / 4} "+" ${height / 2}", exeption.stackTraceToString())
//        }
//
//// Заполняем Bitmap пикселями из списка RGB значений
//        Log.d("COLORCOLORRGB",""+aai.forEach { it->it.toString()+" " })
//
//        return aai
//    }
    private fun recognizeColorInCenter(rgbBitmap: Bitmap): Array<Float> {
        val height = rgbBitmap.height
        val width = rgbBitmap.width
        val centerX = rgbBitmap.width / 2
        val centerY = rgbBitmap.height / 2
        val halfWidth = rgbBitmap.width / 4
        val halfHeight = rgbBitmap.height / 2
        var tempRaster = IntArray(width / 4 * height / 2)
        try {
            rgbBitmap.getPixels(
                tempRaster,        // массив для хранения цветов пикселей
                0,             // смещение по оси X
                halfWidth,     // ширина области
                centerX - halfWidth / 2, // смещение по оси X центра области
                centerY - halfHeight / 2, // смещение по оси Y центра области
                halfWidth,     // ширина области
                halfHeight     // высота области
            )
        } catch (exeption: Exception) {
            Log.d("COLORCOLOR2", exeption.stackTraceToString())
        }
        Log.d("COLORCOLOR3", tempRaster.size.toString())

        var av1 = 0f;
        var av2 = 0f;
        var av3 = 0f;
        var pogr = 0f;


        var av1c = 0;
        var av2c = 0;
        var av3c = 0;

        var min1 = 1f;
        var max1 = 0f;
        var min2 = 1f;
        var max2 = 0f;
        var min3 = 1f;
        var max3 = 0f;

        val maxCountHSB = HashMap<String, Int>();

//        val maxCountH = HashMap<Float, Int>();
//        val maxCountS = HashMap<Float, Int>();
//        val maxCountB = HashMap<Float, Int>();


        var min1c = 255;
        var max1c = 0;
        var min2c = 255;
        var max2c = 0;
        var min3c = 255;
        var max3c = 0;
        for (index in 0 until tempRaster.size) {
            val color: IntArray =
                hexToRGB(tempRaster!![index])!! //convert hex arbg integer to RGB array

            val hsb = FloatArray(3) // HSB array
            RGBtoHSB(color[0], color[1], color[2], hsb)
            val stringHSB = "" + hsb[0] + "/" + hsb[1] + "/" + hsb[2]
            maxCountHSB.put(stringHSB, maxCountHSB.getOrDefault(stringHSB, 0));
//            maxCountH.put(hsb[0], maxCountH.getOrDefault(hsb[0],0))
//            maxCountS.put(hsb[1], maxCountS.getOrDefault(hsb[1],0))
//            maxCountB.put(hsb[2], maxCountB.getOrDefault(hsb[2],0))


            //absolute, but maybe need average?
            min1 = min1.coerceAtMost(hsb[0])
            max1 = max1.coerceAtLeast(hsb[0])
            min2 = min2.coerceAtMost(hsb[1])
            max2 = max2.coerceAtLeast(hsb[1])
            min3 = min3.coerceAtMost(hsb[2])
            max3 = max3.coerceAtLeast(hsb[2])

//            min1c = min1c.coerceAtMost (color[0])
//            max1c = max1c.coerceAtLeast(color[0])
//            min2c = min2c.coerceAtMost (color[1])
//            max2c = max2c.coerceAtLeast(color[1])
//            min3c = min3c.coerceAtMost (color[2])
//            max3c = max3c.coerceAtLeast(color[2])

            // average
            av1 += hsb[0]
            av2 += hsb[1]
            av3 += hsb[2]

//            // average
//            av1c += color[0]
//            av2c += color[1]
//            av3c += color[2]

        }

        var maxPairHSB = maxCountHSB.maxByOrNull { it.value }
        var maxPointsOfHSB = maxPairHSB!!.key.split("/")

        var maxPointsOfH = maxPointsOfHSB[0].toFloat()
        var maxPointsOfS = maxPointsOfHSB[1].toFloat()
        var maxPointsOfB = maxPointsOfHSB[2].toFloat()
        // average
        av1 /= tempRaster.size
        av2 /= tempRaster.size
        av3 /= tempRaster.size
        var maxav = 0f;
        maxav = maxav.coerceAtLeast(av1);
        maxav = maxav.coerceAtLeast(av2);
        maxav = maxav.coerceAtLeast(av3);
        //for example max1
        //pogr = ((max1-maxav)/max1);

        pogr = 0.2f


//        av1c /= tempRaster.size
//        av2c /= tempRaster.size
//        av3c /= tempRaster.size

        Log.d("COLORCOLOR4", "----------------------------------------------------------")
        Log.d(
            "COLORCOLOR4",
            Arrays.toString(arrayOf(maxPointsOfH, maxPointsOfS, maxPointsOfB, pogr))
        )
        Log.d("COLORCOLOR4", Arrays.toString(arrayOf(av1, av2, av3, pogr, maxav)))
        Log.d("COLORCOLOR4", Arrays.toString(arrayOf(min1, max1, min2, max2, min3, max3)))

        Log.d("COLORCOLOR4", Arrays.toString(arrayOf(av1c, av2c, av3c)))
        Log.d("COLORCOLOR4", Arrays.toString(arrayOf(min1c, max1c, min2c, max2c, min3c, max3c)))
        Log.d("COLORCOLOR4", "----------------------------------------------------------")


        return arrayOf(maxPointsOfH, maxPointsOfS, maxPointsOfB, pogr)
    }

    private fun findhand(rgbBitmap: Bitmap): Array<IntArray> {

        val height = rgbBitmap.height
        val width = rgbBitmap.width
        var pixelRaster = IntArray(width * height)//pixel raster for initial cam image
        var tempRaster = IntArray(width * height)
        //Initialize rasters
        //Initialize rasters

        ImageUtil.storePixels(rgbBitmap, pixelRaster!!)
        ImageUtil.storePixels(rgbBitmap, tempRaster!!)

        var pixelRaster2D =
            Array(height) { IntArray(width) } //converting pixelRaster to 2D format to check for surrounding pixels

        var tempRaster2D = Array(height) { IntArray(width) } //temp raster for initial image

        val densityRaster = Array(height) { IntArray(width) } //raster for density

        val clusterRaster = Array(height) { IntArray(width) } //raster for cluster

        var index = 0


        //THAT
        //First pass, get all skin pixel
        for (i in 0 until height) {
            var j = 0
            while (j < width) {
                tempRaster2D!![i][j] = pixelRaster!![index]
                val color: IntArray =
                    hexToRGB(pixelRaster!!.get(index))!! //convert hex arbg integer to RGB array
                val hsb = FloatArray(3) // HSB array
                RGBtoHSB(color[0], color[1], color[2], hsb) //convert RGB to HSB array

                // Initial pass will use strict skin pixel rule.
                // It will only find skin pixels within smaller section compared to loose pixel rule
                // This will help avoid impurities in the detection
                if (strictSkinPixelRule(hsb)) {
                    //  Log.d("HANDBOUND","SKIN is here!")
                    pixelRaster2D!![i][j] = 1 //if found turn pixel white in the 2D array
                } else {
                    pixelRaster2D!![i][j] = 0 //else turn pixel black in the 2D array
                }
                j++
                index++
            }
        }
//testing state because i think it is better
/*

        //Creating a 2D density raster of found initial skin pixels
        //Run through pixel raster 2D array
        for (col in 0 until height) {
            for (row in 0 until width) {

                //IF pixel is white
                if (pixelRaster2D!![col][row] == 1) {

                    //calculate pixel boundary (needed if the pixel is near the edges)
                    val max = 10
                    val lowY = if (col - max >= 0) col - max else 0
                    val highY = if (col + max < height) col + max else height - 1
                    val lowX = if (row - max >= 0) row - max else 0
                    val highX = if (row + max < width) row + max else width - 1

                    //Run through pixels all pixels, at max 10 pixels away from this pixel in a square shape
                    for (i in lowY..highY) {
                        for (j in lowX..highX) {
                            if (pixelRaster2D!![i][j] == 1) {
                                //both work, but i feel like densityRaster[col][row] is a little better
                                densityRaster[i][j]++
                                //densityRaster[col][row]++; //update desnity of  if pixel found is white
                            }
                        }
                    }
                }
            }
        }


        val listOfFoundObjects = Vector<Rectangle>() //list of found objects

        //min and max bounds of the detected box
        //min and max bounds of the detected box
        var minX = 10000
        var maxX = -10000
        var minY = 10000
        var maxY = -10000

        //Now we can use that initial pass to find the general location of the hand in the image
        //Now we can use that initial pass to find the general location of the hand in the image
        for (col in 0 until height) {
            for (row in 0 until width) {
                pixelRaster2D!![col][row] = 0 //make pixel black, since it should not be based upon the density raster

                //if density at this pixel is greater then 60
                if (densityRaster[col][row] > 60) {
                    pixelRaster2D!![col][row] = 1 //turn this pixel white
                    var intersects = false //check if any rectangles intersect with the one about to be created
                    val rect = Rectangle()
                    rect.x = row - 7
                    rect.y = col - 7
                    rect.width = 14
                    rect.height = 14
                    //this pixel's rectangle

                    // check of any previous created rectagles intersect with new rectangle
                    for (i in 0 until listOfFoundObjects.size) {
                        //rectangle does intersect
                        if (rect.intersects(listOfFoundObjects.get(i))) {
                            intersects = true //if a rectangle is found, then this pixel needs to ignored
                            break
                        }
                    }

                    // If no intersection found
                    if (!intersects) {
                        listOfFoundObjects.addElement(rect) //if no rectangles are found, then this rectangle can be added to the list

                        // Update to see if there is a new top left or bottom right corner with this new rectangle
                        if (minX > rect.x) minX = rect.x
                        if (maxX < rect.x + rect.width) maxX = rect.x + rect.width
                        if (minY > rect.y) minY = rect.y
                        if (maxY < rect.y + rect.height) maxY = rect.y + rect.height
                    }
                }
            }
        }
        if(minX==-10000) minX=0
        if(maxX==10000) maxX=0
        if(minY==-10000) minY=0
        if(maxY==10000) maxY=0

*/

        return pixelRaster2D!!
    }

    val koef = 0.20f
    fun strictSkinPixelRule(hsb: FloatArray): Boolean {
        //Log.d("handbound",("${hsb[0]}  ${hsb[1]} ${hsb[2]}").toString())
        return myMinMaxColorsflow.value!![0] - myMinMaxColorsflow.value!![3] < hsb[0] &&
                hsb[0] < myMinMaxColorsflow.value!![0] + myMinMaxColorsflow.value!![3] &&
                hsb[1] > myMinMaxColorsflow.value!![1] - myMinMaxColorsflow.value!![3] &&
                hsb[1] < myMinMaxColorsflow.value!![1] + myMinMaxColorsflow.value!![3] &&
                hsb[2] > myMinMaxColorsflow.value!![2] - myMinMaxColorsflow.value!![3] &&
                myMinMaxColorsflow.value!![2] + myMinMaxColorsflow.value!![3] > hsb[2]


    }

    fun looseSkinPixelRule(hsb: FloatArray): Boolean {
        return hsb[0] < 0.4f && hsb[1] < 1f && hsb[2] < 0.7f
    }


    fun hexToRGB(argbHex: Int): IntArray? {
        val rgb = IntArray(3)
        rgb[0] = argbHex and 0xFF0000 shr 16 //get red
        rgb[1] = argbHex and 0xFF00 shr 8 //get green
        rgb[2] = argbHex and 0xFF //get blue
        return rgb //return array
    }

    fun RGBtoHSB(r: Int, g: Int, b: Int, hsbvals: FloatArray?): FloatArray? {
        var hsbvals = hsbvals
        var hue: Float
        val saturation: Float
        val brightness: Float
        if (hsbvals == null) {
            hsbvals = FloatArray(3)
        }
        var cmax = if (r > g) r else g
        if (b > cmax) cmax = b
        var cmin = if (r < g) r else g
        if (b < cmin) cmin = b
        brightness = cmax.toFloat() / 255.0f
        saturation = if (cmax != 0) (cmax - cmin).toFloat() / cmax.toFloat() else 0f
        if (saturation == 0f) hue = 0f else {
            val redc = (cmax - r).toFloat() / (cmax - cmin).toFloat()
            val greenc = (cmax - g).toFloat() / (cmax - cmin).toFloat()
            val bluec = (cmax - b).toFloat() / (cmax - cmin).toFloat()
            hue =
                if (r == cmax) bluec - greenc else if (g == cmax) 2.0f + redc - bluec else 4.0f + greenc - redc
            hue /= 6.0f
            if (hue < 0) hue = hue + 1.0f
        }
        hsbvals[0] = hue
        hsbvals[1] = saturation
        hsbvals[2] = brightness
        return hsbvals
    }

    fun Rectangle.intersects(bounds: Rectangle): Boolean {
        return this.x >= bounds.x && this.x < bounds.x + bounds.width && this.y >= bounds.y && this.y < bounds.y + bounds.height
    }
}