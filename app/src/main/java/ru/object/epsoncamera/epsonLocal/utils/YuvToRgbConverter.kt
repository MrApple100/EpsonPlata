package ru.`object`.epsoncamera.epsonLocal.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.renderscript.*
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.experimental.and

class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context)
    private val scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    private var pixelCount: Int = -1
    private lateinit var yuvBuffer: ByteBuffer
    private lateinit var inputAllocation: Allocation
    private lateinit var outputAllocation: Allocation

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
            val g2 = (rgb[index + 2].toInt() and 0x07) shl 3 or ((rgb[index + 3].toInt() and 0xe0) shr 5)
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

    fun conver_argb_to_i420(i420: ByteArray, argb: IntArray, width: Int, height: Int) {
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
            for (i in 0 until width) {
                a = argb[index] and -0x1000000 shr 24 //  is not used obviously
                R = argb[index] and 0xff0000 shr 16
                G = argb[index] and 0xff00 shr 8
                B = argb[index] and 0xff shr 0

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
                index++
            }
        }
    }
    fun yuy2ToYuv420(yuy2: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4
        val yuv = ByteArray(ySize + 2 * uvSize)
        var yIndex = 0
        var uIndex = ySize
        var vIndex = ySize + uvSize
        var i = 0
        while (i < yuy2.size) {
            val y1 = yuy2[i].toInt() and 0xFF
            val u = yuy2[i + 1].toInt() and 0xFF
            val y2 = yuy2[i + 2].toInt() and 0xFF
            val v = yuy2[i + 3].toInt() and 0xFF
            yuv[yIndex++] = y1.toByte()
            yuv[yIndex++] = y2.toByte()
            if (i % 4 == 0) {
                yuv[uIndex++] = u.toByte()
                yuv[vIndex++] = v.toByte()
            }
            i += 4
        }
        return yuv
    }





}