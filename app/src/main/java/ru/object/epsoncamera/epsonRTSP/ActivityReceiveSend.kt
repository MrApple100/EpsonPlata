package ru.`object`.epsoncamera.epsonRTSP

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.epson.moverio.hardware.camera.CameraProperty
import com.epson.moverio.hardware.camera.CaptureDataCallback
import com.epson.moverio.hardware.camera.CaptureDataCallback2
import com.epson.moverio.hardware.camera.CaptureStateCallback2
import com.epson.moverio.system.HeadsetStateCallback
import com.epson.moverio.util.PermissionGrantResultCallback
import com.epson.moverio.util.PermissionHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.pedro.sample.R
import ru.`object`.epsoncamera.epsonLocal.MoverioCameraSampleFragment
import ru.`object`.epsoncamera.epsonLocal.camera.ObjectDetectorAnalyzer
import java.nio.ByteBuffer
import java.util.*

class ActivityReceiveSend : AppCompatActivity(), CaptureStateCallback2,
    CaptureDataCallback, CaptureDataCallback2, PermissionGrantResultCallback, HeadsetStateCallback {

    private val TAG = this.javaClass.simpleName

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

    }

    override fun onCaptureData(timestamp: Long, datamass: ByteArray) {
        mCalcurationRate_framerate!!.updata()
        try {
            ObjectDetectorAnalyzer.datamass.value = datamass
            threadToDATA.execute {
                try {
                    if (ObjectDetectorAnalyzer.datamass.value.size > 0) analyzer?.analyze(
                        ObjectDetectorAnalyzer.datamass.value,
                        mCameraDevice!!.property.captureSize[0],
                        mCameraDevice!!.property.captureSize[1]
                    )
                } catch (ex: Exception) {
                    println(Arrays.toString(ex.stackTrace))
                }
            }
        } catch (e: Exception) {
            Log.d("ERRORGLOBAL", e.localizedMessage)
        }
    }

    override fun onCameraOpened() {
        Log.d(TAG, "onCameraOpened")
        mTextView_captureState!!.text = "onCameraOpened"
        Toast.makeText(MoverioCameraSampleFragment.mContext, "onCameraOpened", Toast.LENGTH_SHORT).show()
        initView(mCameraDevice!!.property)
        mTextView_test!!.text = cameraProperty
    }

    override fun onCameraClosed() {
        Log.d(TAG, "onCameraClosed")
        mTextView_captureState!!.text = "onCameraClosed"
        Toast.makeText(MoverioCameraSampleFragment.mContext, "onCameraClosed", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onCaptureStarted() {
        Log.d(TAG, "onCaptureStarted")
        mTextView_captureState!!.text = "onCaptureStarted"
        Toast.makeText(MoverioCameraSampleFragment.mContext, "onCameraStarted", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(MoverioCameraSampleFragment.mContext, "onPreviewStarted", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(MoverioCameraSampleFragment.mContext, "onRecordStarted", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onRecordStopped() {
        Log.d(TAG, "onRecordStopped")
        mTextView_captureState!!.text = "onRecordStopped"
        Toast.makeText(MoverioCameraSampleFragment.mContext, "onRecordStopped", Toast.LENGTH_SHORT).show()
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
        mSpinner_captureInfo!!.adapter = CaptureInfoAdapter(
            MoverioCameraSampleFragment.mContext,
            android.R.layout.simple_spinner_item,
            mCameraDevice!!.property.supportedCaptureInfo
        )
        mSeekBar_brightness!!.max = property.brightnessMax - property.brightnessMin
        property = mCameraDevice!!.property
        property.brightness = 0
        property.captureDataFormat = CameraProperty.CAPTURE_DATA_FORMAT_ARGB_8888
        val ret = mCameraDevice!!.setProperty(property)
        updateView()
    }

    private fun updateView() {
        val property = mCameraDevice!!.property ?: return

        // brightness
        mSeekBar_brightness!!.progress = property.brightnessMin + property.brightness
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
}