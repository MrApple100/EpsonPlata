package ru.`object`.detection.camera

import android.R
import android.R.attr.height
import android.R.attr.width
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.constraintlayout.solver.widgets.Rectangle
import com.google.zxing.Result
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import ru.`object`.detection.detection.DetectionResult
import ru.`object`.detection.detection.ObjectDetector
import ru.`object`.detection.usecase.BarcodeImageScanner
import ru.`object`.detection.util.ImageUtil
import ru.`object`.detection.util.YuvToRgbConverter
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList


class ObjectDetectorAnalyzer(
    private val context: Context,
    private val config: Config,
    private val onDetectionResult: (Result,com.google.zxing.Result?,Array<IntArray>?) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "ObjectDetectorAnalyzer"
        private val DEBUG = false
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
    private var resizedBitmap = Bitmap.createBitmap(config.inputSize, config.inputSize, Bitmap.Config.ARGB_8888)

    private var matrixToInput: Matrix? = null

    private var scanDispose = CompositeDisposable()

    override fun analyze(image: ImageProxy) {
        val rotationDegrees = image.imageInfo.rotationDegrees


        val iteration = iterationCounter.getAndIncrement()

        val rgbBitmap = getArgbBitmap(image.width, image.height)

        yuvToRgbConverter.yuvToRgb(image, rgbBitmap)

        val transformation = getTransformation(rotationDegrees, image.width, image.height)

        image.close()

        Canvas(resizedBitmap).drawBitmap(rgbBitmap, transformation, null)

        var handbound:Array<IntArray>? = null

        runBlocking {
            coroutineScope {
                try {
                    handbound = findhand(resizedBitmap)
                }catch(ex:Exception){

                }
            }
        }

        BarcodeImageScanner
            .parse(rgbBitmap)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { barcoderesult ->


                    ImageUtil.storePixels(resizedBitmap, inputArray)

                    val objects = detect(inputArray)

                    if (DEBUG) {
                        debugHelper.saveResult(iteration, resizedBitmap, objects)
                    }

                    Log.d(TAG, "detection objects($iteration): $objects")

                    val result = Result(
                        objects = objects,
                        imageWidth = config.inputSize,
                        imageHeight = config.inputSize,
                        imageRotationDegrees = rotationDegrees
                    )
                    uiHandler.post {
                        onDetectionResult.invoke(result,barcoderesult,handbound)
                    }

                },
                {

                    ImageUtil.storePixels(resizedBitmap, inputArray)

                    val objects = detect(inputArray)

                    if (DEBUG) {
                        debugHelper.saveResult(iteration, resizedBitmap, objects)
                    }

                    Log.d(TAG, "detection objects($iteration): $objects")

                    val result = Result(
                        objects = objects,
                        imageWidth = config.inputSize,
                        imageHeight = config.inputSize,
                        imageRotationDegrees = rotationDegrees
                    )
                    // Log.d("Hand",handbound?.bottom.toString()+" "+handbound?.left.toString())
                    uiHandler.post {
                        onDetectionResult.invoke(result,null,handbound)
                    }
                    Log.d("Barcode","error")
                }

            ).addTo(scanDispose)



    }

    private fun getTransformation(rotationDegrees: Int, srcWidth: Int, srcHeight: Int): Matrix {
        var toInput = matrixToInput
        if (toInput == null) {
            toInput = ImageUtil.getTransformMatrix(rotationDegrees, srcWidth, srcHeight, config.inputSize, config.inputSize)
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
        val barcodetextTolink:String
    )

    data class Result(
        val objects: List<DetectionResult>,
        val imageWidth: Int,
        val imageHeight: Int,
        val imageRotationDegrees: Int
    )

    //temp raster


    private fun findhand(rgbBitmap :Bitmap): Array<IntArray> {

        val height = rgbBitmap.height
        val width = rgbBitmap.width
        var pixelRaster = IntArray( width * height)//pixel raster for initial cam image
        var tempRaster = IntArray(width * height)
        //Initialize rasters
        //Initialize rasters

        ImageUtil.storePixels(resizedBitmap, pixelRaster)
        ImageUtil.storePixels(resizedBitmap, tempRaster)

        var pixelRaster2D = Array(height) { IntArray(width)} //converting pixelRaster to 2D format to check for surrounding pixels

        var tempRaster2D = Array(height) { IntArray(width) } //temp raster for initial image

        val densityRaster = Array(height) { IntArray(width) } //raster for density

        val clusterRaster = Array(height) { IntArray(width) } //raster for cluster

        var index = 0


        //THAT
        //First pass, get all skin pixel
        for (i in 0 until height) {
            var j = 0
            while (j < width) {
                tempRaster2D[i][j] = pixelRaster[index]
                val color: IntArray = hexToRGB(pixelRaster.get(index))!! //convert hex arbg integer to RGB array
                val hsb = FloatArray(3) // HSB array
                RGBtoHSB(color[0], color[1], color[2], hsb) //convert RGB to HSB array

                // Initial pass will use strict skin pixel rule.
                // It will only find skin pixels within smaller section compared to loose pixel rule
                // This will help avoid impurities in the detection
                if (strictSkinPixelRule(hsb)) {
                    pixelRaster2D[i][j] = 1 //if found turn pixel white in the 2D array
                } else {
                    pixelRaster2D[i][j] = 0 //else turn pixel black in the 2D array
                }
                j++
                index++
            }
        }


        //Creating a 2D density raster of found initial skin pixels
        //Run through pixel raster 2D array
        for (col in 0 until height) {
            for (row in 0 until width) {

                //IF pixel is white
                if (pixelRaster2D[col][row] == 1) {

                    //calculate pixel boundary (needed if the pixel is near the edges)
                    val max = 10
                    val lowY = if (col - max >= 0) col - max else 0
                    val highY = if (col + max < height) col + max else height - 1
                    val lowX = if (row - max >= 0) row - max else 0
                    val highX = if (row + max < width) row + max else width - 1

                    //Run through pixels all pixels, at max 10 pixels away from this pixel in a square shape
                    for (i in lowY..highY) {
                        for (j in lowX..highX) {
                            if (pixelRaster2D[i][j] == 1) {
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
                pixelRaster2D[col][row] = 0 //make pixel black, since it should not be based upon the density raster

                //if density at this pixel is greater then 60
                if (densityRaster[col][row] > 60) {
                    pixelRaster2D[col][row] = 1 //turn this pixel white
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


        return pixelRaster2D
    }

    fun strictSkinPixelRule(hsb: FloatArray): Boolean {
        // Log.d("handbound",("${hsb[0]}  ${hsb[1]} ${hsb[1]}").toString())
        return hsb[0] < 0.11f && hsb[1] > 0.3f && hsb[1] < 0.73f && hsb[2] >0.6f
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
            hue = if (r == cmax) bluec - greenc else if (g == cmax) 2.0f + redc - bluec else 4.0f + greenc - redc
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