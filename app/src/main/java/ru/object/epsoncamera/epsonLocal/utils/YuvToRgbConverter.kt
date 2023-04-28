package ru.`object`.epsoncamera.epsonLocal.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.icu.lang.UCharacter.GraphemeClusterBreak.V
import android.renderscript.*
import android.util.Log
import androidx.camera.core.ImageProxy
import com.epson.moverio.library.usb.u
import com.epson.moverio.library.usb.v
import com.epson.moverio.library.usb.y
import com.pedro.DecodeUtil
import java.nio.ByteBuffer
import kotlin.experimental.and

class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var pixelCount: Int = -1
    private lateinit var yuvBuffer: ByteBuffer
    private lateinit var inputAllocation: Allocation
    private lateinit var outputAllocation: Allocation

    fun convertARGB8888ToYUV420P(argb: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4

        val yuv = ByteArray(ySize + uvSize * 2)

        var yIndex = 0
        var uIndex = ySize // U statt index
        var vIndex = ySize * 5 / 4 // V start index: w*h*5/4

        for (i in 0 until width * height*4 step 4) {
            var r = argb[i].toInt()
            val stR = DecodeUtil.byteArrayToHexString(byteArrayOf(argb[i].toByte()))
            var g = argb[i + 1].toInt()
            var b = argb[i + 2].toInt()
            if(r<0){
                r+=256;
            }
            if(g<0){
                g+=256;
            }
            if(b<0){
                b+=256;
            }

            // Convert RGB to YUV
            val Y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
            val U = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
            val V = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

//            // Store Y values
//            yuv[yIndex++] = y.toByte()
//
//            // Store U and V values every other pixel
//            if (((i/4)/width) % 2 == 0 && ((i/4)%width) % 2 == 0) {
//                yuv[uIndex++] = u.toByte()
//                yuv[vIndex++] = v.toByte()
//            }
// I420(YUV420p) -> YYYYYYYY UU VV
            yuv[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
            if ((((i/4)/width) % 2 == 0) && (((i/4)%width) % 2 == 0)) {
                yuv[uIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                yuv[vIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
            }

        }

        return yuv
    }


    // argb 8888 to i420
    fun conver_argb_to_i420(argb: ByteArray, width: Int, height: Int): ByteArray {

        var i420: ByteArray = ByteArray(width * height * 3 / 2)
        val frameSize = width * height
        var yIndex = 0 // Y start index
        var uIndex = frameSize // U statt index
        var vIndex = frameSize * 5 / 4 // V start index: w*h*5/4
        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width step 4) {
                a = argb[index].toInt() //  is not used obviously
                R = argb[index + 1].toInt()
                G = argb[index + 2].toInt()
                B = argb[index + 3].toInt()

                // well known RGB to YUV algorithm
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                U = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128
                V = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128

                // I420(YUV420p) -> YYYYYYYY UU VV
                i420[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    i420[uIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    i420[vIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                }
                index + 4
            }
        }
        return i420
    }


    @Synchronized
    fun yuvToRgb(image: ImageProxy, output: Bitmap) {

        // Ensure that the intermediate output byte buffer is allocated
        if (!::yuvBuffer.isInitialized) {
            pixelCount = image.cropRect.width() * image.cropRect.height()
            // Bits per pixel is an average for the whole image, so it's useful to compute the size
            // of the full buffer but should not be used to determine pixel offsets
            val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
            yuvBuffer = ByteBuffer.allocateDirect(pixelCount * pixelSizeBits / 8)
        }

        // Rewind the buffer; no need to clear it since it will be filled
        yuvBuffer.rewind()

        // Get the YUV data in byte array form using NV21 format
        imageToByteBuffer(image, yuvBuffer.array())

        // Ensure that the RenderScript inputs and outputs are allocated
        if (!::inputAllocation.isInitialized) {
            // Explicitly create an element with type NV21, since that's the pixel format we use
            val elemType = Type.Builder(rs, Element.YUV(rs)).setYuvFormat(ImageFormat.NV21).create()
            inputAllocation = Allocation.createSized(rs, elemType.element, yuvBuffer.array().size)
        }
        if (!::outputAllocation.isInitialized) {
            outputAllocation = Allocation.createFromBitmap(rs, output)
        }

        // Convert NV21 format YUV to RGB
        inputAllocation.copyFrom(yuvBuffer.array())
        scriptYuvToRgb.setInput(inputAllocation)
        scriptYuvToRgb.forEach(outputAllocation)
        outputAllocation.copyTo(output)
    }

    fun convertYUY2ToYUV420P(yuy2Data: ByteArray, width: Int, height: Int): ByteArray? {
        val ySize = width * height
        val uvSize = ySize / 4
        val yuv420pData = ByteArray(ySize + 2 * uvSize)
        for (i in 0 until ySize) {
            yuv420pData[i] = yuy2Data[i * 2]
        }
        var i = 0
        var j = 0
        while (i < uvSize) {
            val u = yuy2Data[ySize + j]
            val y1 = yuy2Data[ySize + j + 1]
            val v = yuy2Data[ySize + j + 2]
            val y2 = yuy2Data[ySize + j + 3]
            yuv420pData[ySize + i] = ((u + v) / 2).toByte()
            yuv420pData[ySize + uvSize + i] = ((y1 + y2) / 2).toByte()
            i++
            j += 4
        }
        return yuv420pData
    }

    private fun imageToByteBuffer(image: ImageProxy, outputBuffer: ByteArray) {
        /*if (BuildConfig.DEBUG && image.format != ImageFormat.YUV_420_888) {
            error("Assertion failed")
        }*/

        val imageCrop = image.cropRect
        val imagePlanes = image.planes

        imagePlanes.forEachIndexed { planeIndex, plane ->
            // How many values are read in input for each output value written
            // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
            //
            // Y Plane            U Plane    V Plane
            // ===============    =======    =======
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y    U U U U    V V V V
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            val outputStride: Int

            // The index in the output buffer the next value will be written at
            // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
            //
            // First chunk        Second chunk
            // ===============    ===============
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y    V U V U V U V U
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            // Y Y Y Y Y Y Y Y
            var outputOffset: Int

            when (planeIndex) {
                0 -> {
                    outputStride = 1
                    outputOffset = 0
                }
                1 -> {
                    outputStride = 2
                    // For NV21 format, U is in odd-numbered indices
                    outputOffset = pixelCount + 1
                }
                2 -> {
                    outputStride = 2
                    // For NV21 format, V is in even-numbered indices
                    outputOffset = pixelCount
                }
                else -> {
                    // Image contains more than 3 planes, something strange is going on
                    return@forEachIndexed
                }
            }

            val planeBuffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            // We have to divide the width and height by two if it's not the Y plane
            val planeCrop = if (planeIndex == 0) {
                imageCrop
            } else {
                Rect(
                    imageCrop.left / 2,
                    imageCrop.top / 2,
                    imageCrop.right / 2,
                    imageCrop.bottom / 2
                )
            }

            val planeWidth = planeCrop.width()
            val planeHeight = planeCrop.height()

            // Intermediate buffer used to store the bytes of each row
            val rowBuffer = ByteArray(plane.rowStride)

            // Size of each row in bytes
            val rowLength = if (pixelStride == 1 && outputStride == 1) {
                planeWidth
            } else {
                // Take into account that the stride may include data from pixels other than this
                // particular plane and row, and that could be between pixels and not after every
                // pixel:
                //
                // |---- Pixel stride ----|                    Row ends here --> |
                // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
                //
                // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
                (planeWidth - 1) * pixelStride + 1
            }

            for (row in 0 until planeHeight) {
                // Move buffer position to the beginning of this row
                planeBuffer.position(
                    (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
                )

                if (pixelStride == 1 && outputStride == 1) {
                    // When there is a single stride value for pixel and output, we can just copy
                    // the entire row in a single step
                    planeBuffer.get(outputBuffer, outputOffset, rowLength)
                    outputOffset += rowLength
                } else {
                    // When either pixel or output have a stride > 1 we must copy pixel by pixel
                    planeBuffer.get(rowBuffer, 0, rowLength)
                    for (col in 0 until planeWidth) {
                        outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
                        outputOffset += outputStride
                    }
                }
            }
        }
    }

    //    fun convertARGB8888ToYUV420(input: ByteArray, width: Int, height: Int): ByteArray {
//        val ySize = width * height
//        val uvSize = ySize / 4
//        val yBuffer = ByteArray(ySize)
//        val uBuffer = ByteArray(uvSize)
//        val vBuffer = ByteArray(uvSize)
//        var yIndex = 0
//        var uIndex = 0
//        var vIndex = 0
//
//        for (i in input.indices step 4) {
//            val alpha = input[i].toInt() and 0xFF
//            val red = input[i + 1].toInt() and 0xFF
//            val green = input[i + 2].toInt() and 0xFF
//            val blue = input[i + 3].toInt() and 0xFF
//
//            val y = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
//            val u = (-0.14713 * red - 0.28886 * green + 0.436 * blue + 128).toInt()
//            val v = (0.615 * red - 0.51498 * green - 0.10001 * blue + 128).toInt()
//
//            yBuffer[yIndex++] = y.toByte()
//
//            if (i % 8 == 0 && uIndex < uvSize && vIndex < uvSize) {
//                uBuffer[uIndex++] = u.toByte()
//                vBuffer[vIndex++] = v.toByte()
//            }
//        }
//        val output = ByteArray(ySize + 2 * uvSize)
//        System.arraycopy(yBuffer, 0, output, 0, ySize)
//        System.arraycopy(uBuffer, 0, output, ySize, uvSize)
//        System.arraycopy(vBuffer, 0, output, ySize + uvSize, uvSize)
//
//        return output
//    }
    fun convertRGB565ToYUV420(rgb: ByteArray, width: Int, height: Int): ByteArray {
        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uIndex = width * height
        var vIndex = uIndex + (uIndex / 4)
        var index = 0
        for (row in 0 until height) {
            for (col in 0 until width step 2) {
                val pixel1 = rgb[index].toInt() and 0xff
                val pixel2 = rgb[index + 1].toInt() and 0xff
                val r1 = (pixel1 and 0xf8) shr 3
                val g1 = (pixel1 and 0x07) shl 3 or ((pixel2 and 0xe0) shr 5)
                val b1 = pixel2 and 0x1f
                val r2 = (rgb[index + 2].toInt() and 0xf8) shr 3
                val g2 =
                    (rgb[index + 2].toInt() and 0x07) shl 3 or ((rgb[index + 3].toInt() and 0xe0) shr 5)
                val b2 = rgb[index + 3].toInt() and 0x1f
                val y1 = ((66 * r1 + 129 * g1 + 25 * b1 + 128) shr 8) + 16
                val y2 = ((66 * r2 + 129 * g2 + 25 * b2 + 128) shr 8) + 16
                val u = ((-38 * r1 - 74 * g1 + 112 * b1 + 128) shr 8) + 128
                val v = ((112 * r1 - 94 * g1 - 18 * b1 + 128) shr 8) + 128
                yuv[yIndex++] = y1.toByte()
                yuv[yIndex++] = y2.toByte()
                if (row % 2 == 0 && col % 4 == 0) {
                    yuv[uIndex++] = u.toByte()
                    yuv[vIndex++] = v.toByte()
                }
                index += 4
            }
        }
        return yuv
    }

    fun convertARGB8888ToYUV420(argb: ByteArray, width: Int, height: Int): ByteArray {
        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uIndex = width * height
        var vIndex = uIndex + (uIndex / 4)
        var index = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                val pixel = argb[index]
                val alpha = (pixel.toInt() shr 24) and 0xff
                val red = (pixel.toInt() shr 16) and 0xff
                val green = (pixel.toInt() shr 8) and 0xff
                val blue = pixel and 0xff.toByte()
                val y = ((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16
                val u = ((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128
                val v = ((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128
                yuv[yIndex++] = y.toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    yuv[uIndex++] = u.toByte()
                    yuv[vIndex++] = v.toByte()
                }
                index++
            }
        }
        return yuv
    }

    fun convertYUY2ToYUV420(yuy2: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4
        val yuv = ByteArray(ySize + 2 * uvSize)

        var i = 0
        var yIndex = 0
        var uvIndex = ySize

        while (i < yuy2.size) {
            val y1 = yuy2[i].toInt() and 0xff
            val u = yuy2[i + 1].toInt() and 0xff
            val y2 = yuy2[i + 2].toInt() and 0xff
            val v = yuy2[i + 3].toInt() and 0xff

            // Y plane
            yuv[yIndex++] = y1.toByte()
            yuv[yIndex++] = y2.toByte()

            // U and V planes
            if (yIndex % 4 == 0) {
                yuv[uvIndex++] = u.toByte()
                yuv[uvIndex++] = v.toByte()
            }

            i += 4
        }

        return yuv
    }

    fun getImageFormat(byteArray: ByteArray): String? {
        if (byteArray.size < 2) {
            // Массив должен содержать как минимум 2 байта для определения формата.
            return null
        }

        val firstByte = byteArray[0].toInt()
        val secondByte = byteArray[1].toInt()
        Log.d(
            "EpsonApiManager",
            DecodeUtil.byteArrayToHexString(
                byteArrayOf(
                    firstByte.toByte(),
                    secondByte.toByte(),
                    byteArray[3]
                )
            )
        )

        return when {
            // Проверяем, соответствует ли первый и второй байты массива формату h264.
            firstByte == 0 && secondByte == 0 && byteArray.size > 3 && byteArray[2].toInt() == 0x01 -> "h264"

            // Проверяем, соответствует ли первый и второй байты массива формату rgb565.
            firstByte and 0xF8 == 0x38 && secondByte and 0xFC == 0x80 -> "rgb565"

            // Проверяем, соответствует ли первый и второй байты массива формату argb8888.
            firstByte == 0xFF && secondByte == 0 && byteArray.size > 3 && byteArray[2].toInt() == 0xFF -> "argb8888"

            // Проверяем, соответствует ли первый и второй байты массива формату yuy2.
            firstByte == 0x59 && secondByte == 0x55 -> "yuy2"

            // Проверяем, соответствует ли первые 4 байта массива формату jpeg.
            firstByte == 0xFF && secondByte == 0xD8 && byteArray.size > 3 && byteArray[2].toInt() == 0xFF && byteArray[3].toInt() == 0xE0 -> "jpeg"

            // Проверяем, соответствует ли первые 8 байт массива формату png.
            firstByte == 0x89 && secondByte == 0x50 && byteArray.size > 7 && byteArray[2].toInt() == 0x4E && byteArray[3].toInt() == 0x47 && byteArray[4].toInt() == 0x0D && byteArray[5].toInt() == 0x0A && byteArray[6].toInt() == 0x1A && byteArray[7].toInt() == 0x0A -> "png"

            // Проверяем, соответствует ли первые 2 байта массива формату bmp.
            firstByte == 0x42 && secondByte == 0x4D -> "bmp"

            else -> "NOTHING KNOW"
        }
    }


}