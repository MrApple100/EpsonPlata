package com.alexvas.rtsp.codec

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodec.OnFrameRenderedListener
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.View
import com.alexvas.rtsp.widget.ResultOverlayView
import com.alexvas.utils.DecodeUtil
import com.google.android.exoplayer2.util.Util
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class VideoDecodeThread (
        private val surface: Surface,
        private val mimeType: String,
        private val width: Int,
        private val height: Int,
        private val videoFrameQueue: FrameQueue,
        private val onFrameRenderedListener: OnFrameRenderedListener,
        private val overlayView: ResultOverlayView
) : Thread() {

    private var exitFlag: AtomicBoolean = AtomicBoolean(false)

    fun stopAsync() {
        if (DEBUG) Log.v(TAG, "stopAsync()")
        exitFlag.set(true)
        // Wake up sleep() code
        interrupt()
    }

    private fun getDecoderSafeWidthHeight(decoder: MediaCodec): Pair<Int, Int> {
        val capabilities = decoder.codecInfo.getCapabilitiesForType(mimeType).videoCapabilities
        return if (capabilities.isSizeSupported(width, height)) {
            Pair(width, height)
        } else {
            val widthAlignment = capabilities.widthAlignment
            val heightAlignment = capabilities.heightAlignment
            Pair(
                Util.ceilDivide(width, widthAlignment) * widthAlignment,
                Util.ceilDivide(height, heightAlignment) * heightAlignment)
        }
    }

    override fun run() {
        if (DEBUG) Log.d(TAG, "$name started")

        try {
            val decoder = MediaCodec.createDecoderByType(mimeType)
            val widthHeight = getDecoderSafeWidthHeight(decoder)
            val format = MediaFormat.createVideoFormat(mimeType, widthHeight.first, widthHeight.second)
                //format.setString("allow-frame-drop","true")//проверка ??
           // format.setString("fastdecode","true")
           // println("VDTVDTVDT $mimeType ${widthHeight.first} ${widthHeight.second}")
            decoder.setOnFrameRenderedListener(onFrameRenderedListener, null)

          //  println(" ${widthHeight.first}x${widthHeight.second} w/ '$mimeType', max instances: ${decoder.codecInfo.getCapabilitiesForType(mimeType).maxSupportedInstances}")
            decoder.configure(format, surface, null, 0)

            // TODO: add scale option (ie: FIT, SCALE_CROP, SCALE_NO_CROP)
            //decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)

            decoder.start()
            if (DEBUG) Log.d(TAG, "Started surface decoder")

            val bufferInfo = MediaCodec.BufferInfo()

            // Main loop
            while (!exitFlag.get()) {
                val inIndex: Int = decoder.dequeueInputBuffer(10000L)
                if (inIndex >= 0) {
                    // fill inputBuffers[inputBufferIndex] with valid data
                    val byteBuffer: ByteBuffer? = decoder.getInputBuffer(inIndex)
                    byteBuffer?.rewind()

                    // Preventing BufferOverflowException
                    // if (length > byteBuffer.limit()) throw DecoderFatalException("Error")

                    val frame = videoFrameQueue.pop()
                    if (frame == null) {
                        Log.d(TAG, "Empty video frame")
                        // Release input buffer
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, 0)
                    } else {
                    //    println("${DecodeUtil.byteArrayToHexString(frame.data).subSequence(0,100)}")


                        Log.d("INPUTINPUT","${DecodeUtil.byteArrayToHexString(frame.data).subSequence(0,(if(frame.data.size<100)  (frame.data.size) else 100))}  |||||${frame.offset} |||| ${frame.length}")
                        byteBuffer?.put(frame.data, frame.offset, frame.length)
                        decoder.queueInputBuffer(inIndex, frame.offset, frame.length, frame.timestamp, 0)
                    }
                }

                if (exitFlag.get()) break
                when (val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000L)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Log.d(TAG, "Decoder format changed: ${decoder.outputFormat}")
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (DEBUG) Log.d(TAG, "No output from decoder available")
                    else -> {
                        if (outIndex >= 0) {
                            decoder.releaseOutputBuffer(
                                outIndex,
                                bufferInfo.size != 0 && !exitFlag.get()
                            )
                            val bitmap = getSnapshot(surface)
                            if(bitmap!=null)
                                overlayView.updateResults(bitmap)
                        }

                    }
                }

                // All decoded frames have been rendered, we can stop playing now
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (DEBUG) Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM")
                    break
                }
            }

            // Drain decoder
            val inIndex: Int = decoder.dequeueInputBuffer(5000L)
            if (inIndex >= 0) {
                decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            } else {
                Log.w(TAG, "Not able to signal end of stream")
            }

            decoder.stop()
            decoder.release()
            videoFrameQueue.clear()

        } catch (e: Exception) {
            Log.e(TAG, "$name stopped due to '${e.message}'")
            // While configuring stopAsync can be called and surface released. Just exit.
            if (!exitFlag.get()) e.printStackTrace()
            return
        }

        if (DEBUG) Log.d(TAG, "$name stopped")
    }

    companion object {
        private val TAG: String = VideoDecodeThread::class.java.simpleName
        private const val DEBUG = false
    }

    private fun getSnapshot(surface: Surface): Bitmap? {
        if (DEBUG) Log.v(TAG, "getSnapshot()")
        val surfaceBitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val lock = Object()
        val success = AtomicBoolean(false)
        val thread = HandlerThread("PixelCopyHelper")
        thread.start()
        val sHandler = Handler(thread.looper)
        val listener = PixelCopy.OnPixelCopyFinishedListener { copyResult ->
            success.set(copyResult == PixelCopy.SUCCESS)
            synchronized (lock) {
                lock.notify()
            }
        }
        synchronized (lock) {
            PixelCopy.request(surface, surfaceBitmap, listener, sHandler)///???
            lock.wait()
        }
        thread.quitSafely()
        return if (success.get()) surfaceBitmap else null
    }
}

