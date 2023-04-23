package ru.`object`.epsoncamera.epsonRTSP.input

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.AutoFocusCallback
import android.media.CamcorderProfile
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import com.pedro.encoder.input.video.CameraCallbacks
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.CameraOpenException
import com.pedro.encoder.input.video.GetCameraData

class EpsonApiManager {
    private val TAG = "Camera1ApiManager"
    private var camera: Camera? = null
    private var surfaceView: SurfaceView? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var getCameraData: GetCameraData? = null
    var isRunning = false
        private set
    var isLanternEnabled = false
        private set
    var isVideoStabilizationEnabled = false
        private set
    var isAutoFocusEnabled = false
        private set
    private var cameraSelect = 0
    private var isPortrait = false
    private var context: Context

    //default parameters for camera
    var width = 1920
        private set
    var height = 1080
        private set
    private var fps = 30
    private var rotation = 0
    private val imageFormat = ImageFormat.NV21
    private var yuvBuffer: ByteArray

    private var cameraCallbacks: CameraCallbacks? = null


    private val sensorOrientation = 0


    constructor(surfaceView: SurfaceView, getCameraData: GetCameraData?) {
        this.surfaceView = surfaceView
        this.getCameraData = getCameraData
        context = surfaceView.context
        init()
    }

    private fun init() {

    }

    fun setRotation(rotation: Int) {
        this.rotation = rotation
    }

    fun setSurfaceTexture(surfaceTexture: SurfaceTexture?) {
        this.surfaceTexture = surfaceTexture
    }
    

    fun start(width: Int, height: Int, fps: Int) {
        this.width = width
        this.height = height
        this.fps = fps

        start()
    }

    fun start(facing: Int, width: Int, height: Int, fps: Int) {
        this.width = width
        this.height = height
        this.fps = fps
        start()
    }


    private fun start() {


    }

    fun setPreviewOrientation(orientation: Int) {
        rotation = orientation
        if (camera != null && isRunning) {
            camera!!.stopPreview()
            camera!!.setDisplayOrientation(orientation)
            camera!!.startPreview()
        }
    }



    fun stop() {
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            camera!!.setPreviewCallbackWithBuffer(null)
            camera!!.release()
            camera = null
        }
        isRunning = false
    }

    private fun adaptFpsRange(expectedFps: Int, fpsRanges: List<IntArray>): IntArray {
        var expectedFps = expectedFps
        expectedFps *= 1000
        var closestRange = fpsRanges[0]
        var measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(
            closestRange[1] - expectedFps
        )
        for (range in fpsRanges) {
            if (range[0] <= expectedFps && range[1] >= expectedFps) {
                val curMeasure = Math.abs((range[0] + range[1]) / 2 - expectedFps)
                if (curMeasure < measure) {
                    closestRange = range
                    measure = curMeasure
                } else if (curMeasure == measure) {
                    if (Math.abs(range[0] - expectedFps) < Math.abs(closestRange[1] - expectedFps)) {
                        closestRange = range
                        measure = curMeasure
                    }
                }
            }
        }
        return closestRange
    }

    fun onPreviewFrame(data: ByteArray, camera: Camera) {
        getCameraData.inputYUVData(
            com.pedro.encoder.Frame(
                data,
                rotation,
                facing == CameraHelper.Facing.FRONT && isPortrait,
                imageFormat
            )
        )
        camera.addCallbackBuffer(yuvBuffer)
    }

    fun getCameraSize(width: Int, height: Int): Camera.Size {
        return if (camera != null) {
            camera!!.Size(width, height)
        } else {
            camera = Camera.open(cameraSelect)
            val size = camera.Size(width, height)
            camera.release()
            camera = null
            size
        }
    }

    /**
     * See: https://developer.android.com/reference/android/graphics/ImageFormat.html to know name of
     * constant values
     * Example: 842094169 -> YV12, 17 -> NV21
     */
    val cameraPreviewImageFormatSupported: List<Int>
        get() {
            val formats: List<Int>
            if (camera != null) {
                formats = camera!!.parameters.supportedPreviewFormats
                for (i in formats) {
                    Log.i(TAG, "camera format supported: $i")
                }
            } else {
                camera = Camera.open(cameraSelect)
                formats = camera.getParameters().supportedPreviewFormats
                camera.release()
                camera = null
            }
            return formats
        }

    //discard preview more high than device can record
    private val previewSize: List<Camera.Size>
        private get() {
            val previewSizes: MutableList<Camera.Size>
            val maxSize: Camera.Size
            if (camera != null) {
                maxSize = maxEncoderSizeSupported
                previewSizes = camera!!.parameters.supportedPreviewSizes
            } else {
                camera = Camera.open(cameraSelect)
                maxSize = maxEncoderSizeSupported
                previewSizes = camera.getParameters().supportedPreviewSizes
                camera.release()
                camera = null
            }
            //discard preview more high than device can record
            val iterator = previewSizes.iterator()
            while (iterator.hasNext()) {
                val size = iterator.next()
                if (size.width > maxSize.width || size.height > maxSize.height) {
                    Log.i(
                        TAG,
                        size.width.toString() + "X" + size.height + ", not supported for encoder"
                    )
                    iterator.remove()
                }
            }
            return previewSizes
        }

    /**
     * @return max size that device can record.
     */
    private val maxEncoderSizeSupported: Camera.Size
        private get() {
            return if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_2160P)) {
                camera!!.Size(3840, 2160)
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
                camera!!.Size(1920, 1080)
            } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
                camera!!.Size(1280, 720)
            } else {
                camera!!.Size(640, 480)
            }
        }


    val supportedFps: List<IntArray>
        get() {
            val supportedFps: List<IntArray>
            if (camera != null) {
                supportedFps = camera!!.parameters.supportedPreviewFpsRange
            } else {
                camera = Camera.open(cameraSelect)
                supportedFps = camera.getParameters().supportedPreviewFpsRange
                camera.release()
                camera = null
            }
            for (range in supportedFps) {
                range[0] /= 1000
                range[1] /= 1000
            }
            return supportedFps
        }


    fun setCameraCallbacks(cameraCallbacks: CameraCallbacks?) {
        this.cameraCallbacks = cameraCallbacks
    }

}