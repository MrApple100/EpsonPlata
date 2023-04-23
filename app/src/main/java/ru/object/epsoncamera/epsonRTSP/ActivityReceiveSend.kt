package ru.`object`.epsoncamera.epsonRTSP

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.epson.moverio.hardware.camera.*
import com.epson.moverio.system.DeviceManager
import com.epson.moverio.system.HeadsetStateCallback
import com.epson.moverio.util.PermissionGrantResultCallback
import com.epson.moverio.util.PermissionHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.Result
import com.pedro.sample.R
import ru.`object`.epsoncamera.domain.CalcurationRate
import ru.`object`.epsoncamera.epsonLocal.MoverioCameraSampleFragment
import ru.`object`.epsoncamera.epsonLocal.camera.ObjectDetectorAnalyzer
import ru.`object`.epsoncamera.epsonLocal.view.RecognitionResultOverlayView
import ru.`object`.epsoncamera.epsonLocal.view.Scenery
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

class ActivityReceiveSend : AppCompatActivity(), CaptureStateCallback2,
    CaptureDataCallback, CaptureDataCallback2, PermissionGrantResultCallback, HeadsetStateCallback {

    private val TAG = this.javaClass.simpleName
    private var mCameraManager: CameraManager? = null
    private var mCameraDevice: CameraDevice? = null
    private var mDeviceManager: DeviceManager? = null

    private val mCaptureStateCallback2: CaptureStateCallback2 = this
    private val mCaptureDataCallback: CaptureDataCallback = this

    private var mTextView_framerate: TextView? = null
    private var mCalcurationRate_framerate: CalcurationRate? = null

    private var mTextView_captureState: TextView? = null
    private var mTextView_test: TextView? = null

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



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive_send)
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)

        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // In landscape
        } else {
            val navController = findNavController(R.id.nav_host_fragment)
            navView.setupWithNavController(navController)
        }

        ActivityReceiveSend.mContext=this
        ActivityReceiveSend.instance=this
        mTextView_framerate = binding.textViewFramerate
        mCalcurationRate_framerate = CalcurationRate(mTextView_framerate)
        mCalcurationRate_framerate!!.start()
    }

    override fun onStart() {
        super.onStart()
        //open
        try {
            mCameraDevice = mCameraManager!!.open(
                mCaptureStateCallback2,
                mCaptureDataCallback,
                mSurfaceView_preview!!.holder
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }


    }
    public override fun onResume() {
        super.onResume()
        mDeviceManager!!.registerHeadsetStateCallback(this)
        //  Toast.makeText(mContext,"Resume",Toast.LENGTH_SHORT).show();
    }

    public override fun onPause() {
        super.onPause()
        mDeviceManager!!.unregisterHeadsetStateCallback(this) //было закомменчено WHY?????
        //   Toast.makeText(mContext,"Pause",Toast.LENGTH_SHORT).show();
    }

    override fun onStop() {
        super.onStop()

        //StopPreview
        mCameraDevice!!.stopPreview()

        //StopCapture
        mCameraDevice!!.stopCapture()


        //close
        mCameraManager!!.close(mCameraDevice)
        mCameraDevice = null

    }

    override fun onCaptureData(timestamp: Long, datamass: ByteArray) {
        mCalcurationRate_framerate!!.updata()
        try {

                            mCameraDevice!!.property.captureSize[0]
                            mCameraDevice!!.property.captureSize[1]


        } catch (e: Exception) {
            Log.d("ERRORGLOBAL", e.localizedMessage)
        }
    }

    override fun onCameraOpened() {

        //Frame 1920x1080 30fps
        val item= mCameraDevice!!.property.supportedCaptureInfo[6]

        val property = mCameraDevice!!.property
        property.setCaptureSize(item[0], item[1])
        property.captureFps = item[2]
        mCameraDevice!!.property = property

        //StartCapture
        mCameraDevice!!.startCapture()


        Log.d(TAG, "onCameraOpened")
        mTextView_captureState!!.text = "onCameraOpened"
        Toast.makeText(ActivityReceiveSend.mContext, "onCameraOpened", Toast.LENGTH_SHORT).show()
        initView(mCameraDevice!!.property)
        mTextView_test!!.text = cameraProperty
    }

    override fun onCameraClosed() {
        Log.d(TAG, "onCameraClosed")
        mTextView_captureState!!.text = "onCameraClosed"
        Toast.makeText(ActivityReceiveSend.mContext, "onCameraClosed", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onCaptureStarted() {
        //StartPreview
        mCameraDevice!!.startPreview()

        Log.d(TAG, "onCaptureStarted")
        mTextView_captureState!!.text = "onCaptureStarted"
        Toast.makeText(ActivityReceiveSend.mContext, "onCameraStarted", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onCaptureStopped() {
        Log.d(TAG, "onCaptureStopped")
        mTextView_captureState!!.text = "onCaptureStopped"
        //Toast.makeText(mContext,"onCameraStopped",Toast.LENGTH_SHORT).show();
        mTextView_test!!.text = cameraProperty
    }

    override fun onPreviewStarted() {
        Log.d(TAG, "onPreviewStarted")
        mTextView_captureState!!.text = "onPreviewStarted"
        Toast.makeText(ActivityReceiveSend.mContext, "onPreviewStarted", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onPreviewStopped() {
        Log.d(TAG, "onPreviewStopped")
        mTextView_captureState!!.text = "onPreviewStopped"
        //Toast.makeText(mContext,"onPreviewStopped",Toast.LENGTH_SHORT).show();
        mTextView_test!!.text = cameraProperty
    }

    override fun onRecordStarted() {
        Log.d(TAG, "onRecordStarted")
        mTextView_captureState!!.text = "onRecordStarted"
        Toast.makeText(ActivityReceiveSend.mContext, "onRecordStarted", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onRecordStopped() {
        Log.d(TAG, "onRecordStopped")
        mTextView_captureState!!.text = "onRecordStopped"
        Toast.makeText(ActivityReceiveSend.mContext, "onRecordStopped", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onPictureCompleted() {
        Log.d(TAG, "onPictureCompleted")
        mTextView_captureState!!.text = "onPictureCompleted"
        mTextView_test!!.text = cameraProperty
    }

    override fun onPermissionGrantResult(permission: String, grantResult: Int) {
        Snackbar.make(
            window.decorView,
            permission + " is " + if (PermissionHelper.PERMISSION_GRANTED == grantResult) "GRANTED" else "DENIED",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onCaptureData(timestamp: Long, data: ByteBuffer) {
        //mTextView_captureState.setText("onCaptureData(ByteBuffer):"+data.limit());
    }


    private fun initView(property: CameraProperty) {
        var property: CameraProperty? = property
        if (null == property) {
            Log.w(TAG, "CameraProperty is null...")
            return
        }

        property = mCameraDevice!!.property
        property.brightness = 0
        property.captureDataFormat = CameraProperty.CAPTURE_DATA_FORMAT_ARGB_8888
        val ret = mCameraDevice!!.setProperty(property)
        updateView()
    }

    private fun updateView() {
        val property = mCameraDevice!!.property ?: return

    }

    override fun onHeadsetAttached() {
        Snackbar.make(window.decorView, "Headset attached...", Snackbar.LENGTH_SHORT).show()
    }

    override fun onHeadsetDetached() {
        Snackbar.make(window.decorView, "Headset detached...", Snackbar.LENGTH_SHORT).show()
        mDeviceManager!!.close()
    }

    override fun onHeadsetDisplaying() {
        Snackbar.make(window.decorView, "Headset displaying...", Snackbar.LENGTH_SHORT).show()
    }

    override fun onHeadsetTemperatureError() {
        Snackbar.make(window.decorView, "Headset temperature error...", Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        private lateinit var instance: ActivityReceiveSend
        private lateinit var mContext: Context

    }
}