package ru.`object`.epsoncamera

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.databinding.DataBindingUtil
import com.epson.moverio.hardware.camera.*
import com.epson.moverio.system.DeviceManager
import com.epson.moverio.system.HeadsetStateCallback
import com.epson.moverio.util.PermissionGrantResultCallback
import com.epson.moverio.util.PermissionHelper
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.Result
import org.tensorflow.lite.examples.detection.R
import org.tensorflow.lite.examples.detection.databinding.FragmentCameraBinding
import ru.`object`.epsoncamera.camera.ObjectDetectorAnalyzer
import ru.`object`.epsoncamera.camera.ObjectDetectorAnalyzer.Companion.getInstance
import ru.`object`.epsoncamera.util.view.RecognitionResultOverlayView
import ru.`object`.epsoncamera.util.view.Scenery
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors

class MoverioCameraSampleFragment : Activity(), CaptureStateCallback2,
    CaptureDataCallback, CaptureDataCallback2, PermissionGrantResultCallback, HeadsetStateCallback {
    private val TAG = this.javaClass.simpleName
    private var mCameraManager: CameraManager? = null
    private var mCameraDevice: CameraDevice? = null
    private val mCaptureStateCallback2: CaptureStateCallback2 = this
    private val mCaptureDataCallback: CaptureDataCallback = this
    private val mCaptureDataCallback2: CaptureDataCallback2 = this
    private var mToggleButton_cameraOpenClose: ToggleButton? = null
    private var mToggleButton_captureStartStop: ToggleButton? = null
    private var mToggleButton_previewStartStop: ToggleButton? = null
    private var mSeekBar_brightness: SeekBar? = null
    private var mSurfaceView_preview: SurfaceView? = null
    private var mTextView_captureState: TextView? = null
    private var mSpinner_captureInfo: Spinner? = null
    private var mTextView_framerate: TextView? = null
    private var mCalcurationRate_framerate: CalcurationRate? = null
    private var mTextView_test: TextView? = null
    private var mPermissionHelper: PermissionHelper? = null
    private var mDeviceManager: DeviceManager? = null

    //ANALYZER
    private var analyzer: ObjectDetectorAnalyzer? = null
    private var rgbBitmap: Bitmap? = null
    private val config = ObjectDetectorAnalyzer.Config(
        0.5f,
        10,  //ругается если больше 10
        300,
        true,
        "model_q.tflite",
        "labelmap.txt",
        "barcodetextTolink.txt"
    )
    private var preferences: SharedPreferences? = null
    private val KEY_H = "KEY_H"
    private val KEY_S = "KEY_S"
    private val KEY_V = "KEY_V"
    private val KEY_P = "KEY_P"
    private var current_H = 0
    private var current_S = 0
    private var current_V = 0
    private var current_P = 0
    private var mSeekBar_colorH: SeekBar? = null
    private var mSeekBar_colorS: SeekBar? = null
    private var mSeekBar_colorV: SeekBar? = null
    private var mSeekBar_pogr: SeekBar? = null
    private var tv_colorH: TextView? = null
    private var tv_colorS: TextView? = null
    private var tv_colorV: TextView? = null
    private var tv_P: TextView? = null
    private var bMenu: Button? = null
    private var burgerMenu: LinearLayout? = null
    private var bCalibrateHand: ToggleButton? = null
    private val isCalibrateNow: Boolean? = null
    private var bCloseBurgerMenu: Button? = null
    private var bSaveHSV: Button? = null
    private var bLoadHSV: Button? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: FragmentCameraBinding =
            DataBindingUtil.setContentView(this, R.layout.fragment_camera)
        val bindingBurgerMenu = binding.Iburgermenu
        mPermissionHelper = PermissionHelper(this)
        mDeviceManager = DeviceManager(this)
        result_overlay = binding.resultOverlay2
        mSurfaceView_preview = binding.surfaceViewPreview
        result_overlay.setDescriptionText(binding.DescriptionText)
        result_overlay.setWebView(binding.PDFViewer)
        mContext = this
        instance = this
        mCameraManager = CameraManager(mContext, this)
        analyzer = getInstance(
            mContext as MoverioCameraSampleFragment,
            config,
            instance
        ) { result: ObjectDetectorAnalyzer.Result, barcoderesult: Result?, handbound: Array<IntArray>?, isDark: Boolean, scenery: Scenery ->
            onDetectionResult(
                result,
                barcoderesult,
                handbound,
                isDark,
                scenery
            )
        }
        mToggleButton_cameraOpenClose = bindingBurgerMenu.toggleButtonCameraOpenClose
        mToggleButton_cameraOpenClose!!.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                try {
                    mCameraDevice = mCameraManager!!.open(
                        mCaptureStateCallback2,
                        mCaptureDataCallback,
                        mSurfaceView_preview!!.holder
                    )
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                mCameraManager!!.close(mCameraDevice)
                mCameraDevice = null
            }
        }
        mToggleButton_captureStartStop = bindingBurgerMenu.toggleButtonCaptureStartStop
        mToggleButton_captureStartStop!!.setOnCheckedChangeListener { buttonView, isChecked ->
            mTextView_test!!.text = cameraProperty
            if (isChecked) {
                mCameraDevice!!.startCapture()
            } else {
                mCameraDevice!!.stopCapture()
            }
        }
        mToggleButton_previewStartStop = bindingBurgerMenu.toggleButtonPreviewStartStop
        mToggleButton_previewStartStop!!.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                mCameraDevice!!.startPreview()
            } else {
                mCameraDevice!!.stopPreview()
            }
        }
        mTextView_captureState = binding.textViewCaptureState
        mSpinner_captureInfo = bindingBurgerMenu.spinnerCaptureInfo
        mSpinner_captureInfo!!.setOnTouchListener { view, motionEvent ->
            if (mCameraDevice != null) mSpinner_captureInfo!!.adapter = CaptureInfoAdapter(
                mContext!!,
                android.R.layout.simple_spinner_item,
                mCameraDevice!!.property.supportedCaptureInfo
            )
            false
        }
        mSpinner_captureInfo!!.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View,
                    position: Int,
                    id: Long
                ) {
                    val item = parent.selectedItem as IntArray
                    val property = mCameraDevice!!.property
                    property.setCaptureSize(item[0], item[1])
                    property.captureFps = item[2]
                    mCameraDevice!!.property = property
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        mSeekBar_brightness = bindingBurgerMenu.seekBarBrightness
        mSeekBar_brightness!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val property = mCameraDevice!!.property
                property.brightness = progress + property.brightnessMin
                val ret = mCameraDevice!!.setProperty(property)
                mTextView_test!!.text = cameraProperty
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        mTextView_framerate = binding.textViewFramerate
        mCalcurationRate_framerate = CalcurationRate(mTextView_framerate)
        mCalcurationRate_framerate!!.start()
        mTextView_test = binding.textViewTest

        //   Toast.makeText(mContext,"Create",Toast.LENGTH_SHORT).show();


        ////////////BurgerMenu

        //INIT HSV CACHE
        preferences = getSharedPreferences(packageName, MODE_PRIVATE)
        current_H = preferences?.getInt(KEY_H, 0)!!
        current_S = preferences?.getInt(KEY_S, 0)!!
        current_V = preferences?.getInt(KEY_V, 0)!!
        current_P = preferences?.getInt(KEY_P, 0)!!
        val hsvp: Array<Float>? = analyzer?.myMinMaxColorsState?.value
        hsvp?.set(0, current_H / 360f)
        hsvp?.set(1, current_S / 360f)
        hsvp?.set(2, current_V / 360f)
        hsvp?.set(3, current_P / 100f)
        if (hsvp != null) {
            analyzer?.setMinMaxColor(hsvp)
        }
        tv_colorH = bindingBurgerMenu.TVCurrentH
        tv_colorH!!.text = current_H.toString() + ""
        mSeekBar_colorH = bindingBurgerMenu.seekBarColorH
        mSeekBar_colorH!!.progress = current_H
        mSeekBar_colorH!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val hsvp: Array<Float>? = analyzer?.myMinMaxColorsState?.value
                hsvp?.set(0, progress / 360f)
                if (hsvp != null) {
                    analyzer?.setMinMaxColor(hsvp)
                }
                current_H = progress
                tv_colorH!!.text = progress.toString() + ""
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        tv_colorS = bindingBurgerMenu.TVCurrentS
        tv_colorS!!.text = current_S.toString() + ""
        mSeekBar_colorS = bindingBurgerMenu.seekBarColorS
        mSeekBar_colorS!!.progress = current_S
        mSeekBar_colorS!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val hsvp: Array<Float>? = analyzer?.myMinMaxColorsState?.value
                hsvp?.set(1, progress / 100f)
                if (hsvp != null) {
                    analyzer?.setMinMaxColor(hsvp)
                }
                current_S = progress
                tv_colorS!!.text = progress.toString() + ""
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        tv_colorV = bindingBurgerMenu.TVCurrentV
        tv_colorV!!.text = current_V.toString() + ""
        mSeekBar_colorV = bindingBurgerMenu.seekBarColorV
        mSeekBar_colorV!!.progress = current_V
        mSeekBar_colorV!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val hsvp: Array<Float>? = analyzer?.myMinMaxColorsState?.value
                hsvp?.set(2, progress / 100f)
                if (hsvp != null) {
                    analyzer?.setMinMaxColor(hsvp)
                }
                current_V = progress
                tv_colorV!!.text = progress.toString() + ""
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        tv_P = bindingBurgerMenu.TVCurrentPogreshnost
        tv_P!!.text = current_P.toString() + ""
        mSeekBar_pogr = bindingBurgerMenu.seekBarColorPogreshnost
        mSeekBar_pogr!!.progress = current_P
        mSeekBar_pogr!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val hsvp: Array<Float>? = analyzer?.myMinMaxColorsState?.value
                hsvp?.set(3, progress / 100f)
                if (hsvp != null) {
                    analyzer?.setMinMaxColor(hsvp)
                }
                current_P = progress
                tv_P!!.text = progress.toString() + ""
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        burgerMenu = bindingBurgerMenu.BurgerMenu
        bMenu = binding.Bmenu
        bMenu!!.setOnClickListener {
            if (burgerMenu!!.visibility != View.VISIBLE) burgerMenu!!.visibility =
                View.VISIBLE else burgerMenu!!.visibility = View.GONE
        }
        bCalibrateHand = bindingBurgerMenu.toggleButtonHandpaint
        bCalibrateHand!!.setOnClickListener(View.OnClickListener {
            if (analyzer?.sceneryState?.value?.now ?: -1 != Scenery.ScennaryItem.SettingHand && analyzer?.sceneryState?.value?.now ?: -1 != Scenery.ScennaryItem.manualSettingHand) {
                analyzer?.setScenery(Scenery.ScennaryItem.SettingHand)
                Toast.makeText(
                    this@MoverioCameraSampleFragment,
                    "Поместите прямоуголник на свою ладонь",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                analyzer?.setScenery(Scenery.ScennaryItem.Find)
            }
        })
        bSaveHSV = bindingBurgerMenu.SaveHSV
        bSaveHSV!!.setOnClickListener {
            val editor = preferences?.edit()
            if (editor != null) {
                editor.putInt(KEY_H, current_H)
                editor.putInt(KEY_S, current_S)
                editor.putInt(KEY_V, current_V)
                editor.putInt(KEY_P, current_P)
                editor.apply()
            }

            Toast.makeText(this@MoverioCameraSampleFragment, "SAVE HSV", Toast.LENGTH_SHORT).show()
        }
        bLoadHSV = bindingBurgerMenu.LoadHSV
        bLoadHSV!!.setOnClickListener {
            current_H = preferences?.getInt(KEY_H, 0)!!
            current_S = preferences?.getInt(KEY_S, 0)!!
            current_V = preferences?.getInt(KEY_V, 0)!!
            current_P = preferences?.getInt(KEY_P, 0)!!
            mSeekBar_colorH!!.progress = current_H
            mSeekBar_colorS!!.progress = current_S
            mSeekBar_colorV!!.progress = current_V
            mSeekBar_pogr!!.progress = current_P
            tv_colorH!!.text = current_H.toString() + ""
            tv_colorS!!.text = current_S.toString() + ""
            tv_colorV!!.text = current_V.toString() + ""
            tv_P!!.text = current_P.toString() + ""
            val hsvp: Array<Float>? = analyzer?.myMinMaxColorsState?.value
            hsvp?.set(0, current_H / 360f)
            hsvp?.set(1, current_S / 360f)
            hsvp?.set(2, current_V / 360f)
            hsvp?.set(3, current_P / 100f)
            if (hsvp != null) {
                analyzer?.setMinMaxColor(hsvp)
            }
            Toast.makeText(this@MoverioCameraSampleFragment, "Load HSV", Toast.LENGTH_SHORT).show()
        }
        bCloseBurgerMenu = bindingBurgerMenu.EndSetting
        bCloseBurgerMenu!!.setOnClickListener {
            if (burgerMenu!!.visibility != View.VISIBLE) burgerMenu!!.visibility =
                View.VISIBLE else burgerMenu!!.visibility = View.GONE
        }
        binding.executePendingBindings()
        bindingBurgerMenu.executePendingBindings()
        setContentView(binding.root)
        //setContentView(bindingBurgerMenu.getRoot());
    }

    fun setHSVPprogress(hsvp: Array<Float>) {
        current_H = (hsvp[0] * 360).toInt()
        current_S = (hsvp[1] * 100).toInt()
        current_V = (hsvp[2] * 100).toInt()
        current_P = (hsvp[3] * 100).toInt()
        mSeekBar_colorH!!.progress = current_H
        mSeekBar_colorS!!.progress = current_S
        mSeekBar_colorV!!.progress = current_V
        mSeekBar_pogr!!.progress = current_P
        tv_colorH!!.text = current_H.toString() + ""
        tv_colorS!!.text = current_S.toString() + ""
        tv_colorV!!.text = current_V.toString() + ""
        tv_P!!.text = current_P.toString() + ""
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

    public override fun onDestroy() {
        super.onDestroy()
        mCameraManager!!.release()
        mCameraManager = null
        mCalcurationRate_framerate!!.finish()
        mDeviceManager!!.release()
        mDeviceManager = null
        //    Toast.makeText(mContext,"Destroy",Toast.LENGTH_SHORT).show();
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    var threadToDATA = Executors.newSingleThreadExecutor()
    var threadEnterDATA = Executors.newSingleThreadExecutor()
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
        Toast.makeText(mContext, "onCameraOpened", Toast.LENGTH_SHORT).show()
        initView(mCameraDevice!!.property)
        mTextView_test!!.text = cameraProperty
    }

    override fun onCameraClosed() {
        Log.d(TAG, "onCameraClosed")
        mTextView_captureState!!.text = "onCameraClosed"
        Toast.makeText(mContext, "onCameraClosed", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onCaptureStarted() {
        Log.d(TAG, "onCaptureStarted")
        mTextView_captureState!!.text = "onCaptureStarted"
        Toast.makeText(mContext, "onCameraStarted", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(mContext, "onPreviewStarted", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(mContext, "onRecordStarted", Toast.LENGTH_SHORT).show()
        mTextView_test!!.text = cameraProperty
    }

    override fun onRecordStopped() {
        Log.d(TAG, "onRecordStopped")
        mTextView_captureState!!.text = "onRecordStopped"
        Toast.makeText(mContext, "onRecordStopped", Toast.LENGTH_SHORT).show()
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
        Snackbar.make(window.decorView, "Headset temperature error...", Snackbar.LENGTH_SHORT)
            .show()
    }

    private inner class CaptureInfoAdapter(
        _context: Context,
        _textViewResourceId: Int,
        _captureInfoList: List<IntArray>
    ) :
        ArrayAdapter<Any?>(_context!!, _textViewResourceId, _captureInfoList!!) {
        private var context2: Context
        private var textViewResourceId = 0
        private var captureInfoList: List<IntArray>

        init {
            context2 = _context
            textViewResourceId = _textViewResourceId
            captureInfoList = _captureInfoList
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent) as TextView
            val captureInfo = captureInfoList.get(position)
            v.text =
                captureInfo[0].toString() + "x" + captureInfo[1] + ", " + captureInfo[2] + "[fps]"
            return v
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getDropDownView(position, convertView, parent) as TextView
            println("VVV"+ v)
            val captureInfo = captureInfoList.get(position)
            println("VVV2"+ captureInfo)

            v.text =
                captureInfo[0].toString() + "x" + captureInfo[1] + ", " + captureInfo[2] + "[fps]"
            println("VVV3"+ v.text)

            return v
        }
    }

    private fun initView(property: CameraProperty) {
        var property: CameraProperty? = property
        if (null == property) {
            Log.w(TAG, "CameraProperty is null...")
            return
        }
        mSpinner_captureInfo!!.adapter = CaptureInfoAdapter(
            mContext,
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

    internal inner class CalcurationRate(_textView: TextView?) {
        var textView: TextView? = null
        var count = 0
        var startTime: Long = 0
        var endTime: Long = 0
        var rate = 0f

        init {
            textView = _textView
        }

        fun start() {
            count = 0
            startTime = System.currentTimeMillis()
            endTime = 0
            rate = 0f
        }

        fun updata() {
            endTime = System.currentTimeMillis()
            count++
            if (endTime - startTime > 1000) {
                rate = (count * 1000 / (endTime - startTime)).toFloat()
                startTime = endTime
                count = 0
                textView!!.text = rate.toString()
            }
        }

        fun finish() {
            count = 0
            startTime = 0
            endTime = 0
            rate = 0f
        }
    }

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

    private fun updateView() {
        val property = mCameraDevice!!.property ?: return

        // brightness
        mSeekBar_brightness!!.progress = property.brightnessMin + property.brightness
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mPermissionHelper!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    //ANALAZER
    private fun getArgbBitmap(width: Int, height: Int): Bitmap? {
        var bitmap = rgbBitmap
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            rgbBitmap = bitmap
        }
        return bitmap
    }

    companion object {
        private lateinit var instance: MoverioCameraSampleFragment
        private lateinit var mContext: Context
        private lateinit var result_overlay: RecognitionResultOverlayView
        private fun onDetectionResult(
            result: ObjectDetectorAnalyzer.Result,
            barcoderesult: Result?,
            handbound: Array<IntArray>?,
            isDark: Boolean,
            scenery: Scenery
        ): Boolean {
            //Toast.makeText(this,"Hellow",Toast.LENGTH_SHORT).show();
            //Toast.makeText(mContext,"onDetectionResult",Toast.LENGTH_SHORT).show();
            Log.d("onDetectionResult", "WORK")
            result_overlay?.updateResults(result, barcoderesult, handbound, isDark, scenery)
            return true
        }
    }
}