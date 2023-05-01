package ru.`object`.epsoncamera.epsonRTSP.input

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import com.epson.moverio.hardware.camera.*
import com.epson.moverio.system.DeviceManager
import com.epson.moverio.system.HeadsetStateCallback
import com.epson.moverio.util.PermissionGrantResultCallback
import com.epson.moverio.util.PermissionHelper
import com.pedro.encoder.input.video.CameraCallbacks
import com.pedro.encoder.input.video.GetCameraData
import ru.`object`.epsoncamera.domain.CalcurationRate
import java.io.IOException
import java.nio.ByteBuffer

class EpsonApiManager : CaptureStateCallback2, CaptureDataCallback, CaptureDataCallback2,
    HeadsetStateCallback, PermissionGrantResultCallback {
    private var mCalcurationRate_framerate: CalcurationRate? = null

    private var mCameraManager: CameraManager? = null
    private var mCameraDevice: CameraDevice? = null
    private var mDeviceManager: DeviceManager? = null

    private val mCaptureStateCallback2: CaptureStateCallback2 = this
    private val mCaptureDataCallback: CaptureDataCallback = this

    private val TAG = "EpsonApiManager"
    private lateinit var surfaceView: SurfaceView
    private var surfaceTexture: SurfaceTexture? = null
    private lateinit var getCameraData: GetCameraData
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
    private val imageFormat = ImageFormat.FLEX_RGBA_8888//n21   ///yuy2=20
    private lateinit var yuvBuffer: ByteArray
    private val cameraProperty: String
        private get() {
            var str = ""
            val property = mCameraDevice!!.property
            str += "info :" + property.captureSize[0] + ", " + property.captureSize[1] + ", " + property.captureFps + ", " + property.captureDataFormat + System.lineSeparator()
            str += "expo :" + property.exposureMode + ", " + property.exposureStep + ", bright:" + property.brightness + System.lineSeparator()
            str += "WB   :" + property.whiteBalanceMode + ", PLF  :" + property.powerLineFrequencyControlMode + ", Indi :" + property.indicatorMode + System.lineSeparator()
            str += "Focus:" + property.focusMode + ", " + property.focusDistance + ", Gain  :" + property.gain + System.lineSeparator()
            return str
        }

    private var cameraCallbacks: CameraCallbacks? = null


    private val sensorOrientation = 0


    constructor(surfaceView: SurfaceView, getCameraData: GetCameraData) {
        this.surfaceView = surfaceView
        this.getCameraData = getCameraData
        context = surfaceView.context
        init()
    }

    private fun init() {
        mDeviceManager = DeviceManager(context)
        mCameraManager = CameraManager(context, this)

    }

    fun setRotation(rotation: Int) {
        this.rotation = rotation
    }

//    fun setSurfaceTexture(surfaceTexture: SurfaceTexture?) {
//        this.surfaceTexture = surfaceTexture
//    }


    fun start(width: Int, height: Int, fps: Int) {
        this.width = width
        this.height = height
        this.fps = fps

        start()
    }

    fun registerHeadsetStateCallback() {
        mDeviceManager!!.registerHeadsetStateCallback(this)

    }

    fun unregisterHeadsetStateCallback() {
        mDeviceManager!!.unregisterHeadsetStateCallback(this)
    }


    private fun start() {
        mCalcurationRate_framerate = CalcurationRate(null)
        mCalcurationRate_framerate!!.start()

        //open
        try {
//            if(mCameraDevice!=null) {
//                mCameraDevice!!.stopPreview()
//                mCameraDevice!!.stopCapture()
//                mCameraDevice=null
//            }
//            mCameraDevice!!.stopCapture()
//            mCameraDevice=null;
            mCameraDevice = mCameraManager!!.open(
                mCaptureStateCallback2,
                mCaptureDataCallback,
                surfaceView.holder
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
        isRunning = true

    }
//
//    fun setPreviewOrientation(orientation: Int) {
//        rotation = orientation
//        if (camera != null && isRunning) {
//            camera!!.stopPreview()
//            camera!!.setDisplayOrientation(orientation)
//            camera!!.startPreview()
//        }
//    }


    fun stop() {
        //StopPreview
        if (mCameraDevice != null) {
            mCameraDevice!!.stopPreview()
            mCameraDevice!!.stopCapture()
            mCameraDevice = null
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


    fun setCameraCallbacks(cameraCallbacks: CameraCallbacks?) {
        this.cameraCallbacks = cameraCallbacks
    }

    override fun onCaptureData(timestamp: Long, datamass: ByteArray) {
        if (mCameraDevice != null) {
            mCalcurationRate_framerate!!.updata()

            getCameraData.inputYUVData(
                com.pedro.encoder.Frame(datamass, 0, false, imageFormat)
            )
        }
    }

    override fun onCaptureData(timestamp: Long, data: ByteBuffer) {
        //mTextView_captureState.setText("onCaptureData(ByteBuffer):"+data.limit());
    }

    override fun onCameraOpened() {

        //Frame 1920x1080 30fps
        val item = mCameraDevice!!.property.supportedCaptureInfo[6]

        val property = mCameraDevice!!.property
        property.setCaptureSize(item[0], item[1])
        property.captureFps = item[2]
        mCameraDevice!!.property = property

        initView(mCameraDevice!!.property)

        //StartCapture
        mCameraDevice!!.startCapture()


        // Log.d(TAG, "onCameraOpened")
        //mTextView_captureState!!.text = "onCameraOpened"
        //   Toast.makeText(context, "onCameraOpened", Toast.LENGTH_SHORT).show()
        //  mTextView_test!!.text = cameraProperty
    }

    private fun initView(property: CameraProperty) {
        var property: CameraProperty? = property
        if (null == property) {
            Log.w(TAG, "CameraProperty is null...")
            return
        }

        property = mCameraDevice!!.property
        property.brightness = 0
        // property.captureDataFormat = CameraProperty.CAPTURE_DATA_FORMAT_H264
        property.captureDataFormat = CameraProperty.CAPTURE_DATA_FORMAT_ARGB_8888

        val ret = mCameraDevice!!.setProperty(property)
        updateView()
    }

    private fun updateView() {
        val property = mCameraDevice!!.property ?: return

    }

    override fun onCameraClosed() {
        //   Log.d(TAG, "onCameraClosed")
        // mTextView_captureState!!.text = "onCameraClosed"
        //   Toast.makeText(context, "onCameraClosed", Toast.LENGTH_SHORT).show()
        //  mTextView_test!!.text = cameraProperty


        //StopCapture
        if (mCameraDevice != null) {
            mCameraDevice!!.stopCapture()
            mCameraDevice = null
        }


    }

    override fun onCaptureStarted() {
        //StartPreview
        mCameraDevice!!.startPreview()

        //  Log.d(TAG, "onCaptureStarted")
        //   mTextView_captureState!!.text = "onCaptureStarted"
        //  Toast.makeText(context, "onCameraStarted", Toast.LENGTH_SHORT).show()
        //  mTextView_test!!.text = cameraProperty
    }

    override fun onCaptureStopped() {
        //  Log.d(TAG, "onCaptureStopped")
        //  mTextView_captureState!!.text = "onCaptureStopped"
        //  Toast.makeText(context, "onCameraStopped", Toast.LENGTH_SHORT).show();
        //  mTextView_test!!.text = cameraProperty

        //close
        mCameraManager!!.close(mCameraDevice)
        mCameraDevice = null
    }

    override fun onPreviewStarted() {
        Log.d(TAG, "onPreviewStarted")
        // mTextView_captureState!!.text = "onPreviewStarted"
        Toast.makeText(context, "onPreviewStarted", Toast.LENGTH_SHORT).show()
        //  mTextView_test!!.text = cameraProperty
    }

    override fun onPreviewStopped() {
        Log.d(TAG, "onPreviewStopped")
        // mTextView_captureState!!.text = "onPreviewStopped"
        Toast.makeText(context, "onPreviewStopped", Toast.LENGTH_SHORT).show();
        // mTextView_test!!.text = cameraProperty
    }

    override fun onRecordStarted() {
        //  Log.d(TAG, "onRecordStarted")
        //  mTextView_captureState!!.text = "onRecordStarted"
        // Toast.makeText(context, "onRecordStarted", Toast.LENGTH_SHORT).show()
        //  mTextView_test!!.text = cameraProperty
    }

    override fun onRecordStopped() {
        //Log.d(TAG, "onRecordStopped")
        // mTextView_captureState!!.text = "onRecordStopped"
        // Toast.makeText(context, "onRecordStopped", Toast.LENGTH_SHORT).show()
        // mTextView_test!!.text = cameraProperty
    }

    override fun onPictureCompleted() {
        //  Log.d(TAG, "onPictureCompleted")
        // mTextView_captureState!!.text = "onPictureCompleted"
        // mTextView_test!!.text = cameraProperty
    }

    override fun onHeadsetAttached() {
        //open
        try {
            if (mCameraDevice != null) {
                mCameraDevice!!.stopPreview()
                mCameraDevice!!.stopCapture()
                mCameraDevice = null
            }
//            mCameraDevice!!.stopCapture()
//            mCameraDevice=null;
            mCameraDevice = mCameraManager!!.open(
                mCaptureStateCallback2,
                mCaptureDataCallback,
                surfaceView.holder
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
        Toast.makeText(context, "Headset attached...", Toast.LENGTH_SHORT).show()
    }

    override fun onHeadsetDetached() {
        //StopPreview
        if (mCameraDevice != null) {
            mCameraDevice!!.stopPreview()
            mCameraDevice!!.stopCapture()
            mCameraDevice = null
        }
        isRunning = false
        Toast.makeText(context, "Headset detached...", Toast.LENGTH_SHORT).show()
        mDeviceManager!!.close()
    }

    override fun onHeadsetDisplaying() {
        Toast.makeText(context, "Headset displaying...", Toast.LENGTH_SHORT).show()
    }

    override fun onHeadsetTemperatureError() {
        Toast.makeText(context, "Headset temperature error...", Toast.LENGTH_SHORT).show()
    }

    override fun onPermissionGrantResult(permission: String, grantResult: Int) {
        Toast.makeText(
            context,
            permission + " is " + if (PermissionHelper.PERMISSION_GRANTED == grantResult) "GRANTED" else "DENIED",
            Toast.LENGTH_SHORT
        ).show()
    }


}