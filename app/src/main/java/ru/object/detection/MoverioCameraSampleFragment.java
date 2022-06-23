package ru.object.detection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

import com.epson.moverio.hardware.camera.CameraDevice;
import com.epson.moverio.hardware.camera.CameraManager;
import com.epson.moverio.hardware.camera.CameraProperty;
import com.epson.moverio.hardware.camera.CaptureDataCallback;
import com.epson.moverio.hardware.camera.CaptureDataCallback2;
import com.epson.moverio.hardware.camera.CaptureStateCallback2;
import com.epson.moverio.system.DeviceManager;
import com.epson.moverio.system.HeadsetStateCallback;
import com.epson.moverio.util.PermissionGrantResultCallback;
import com.epson.moverio.util.PermissionHelper;
import com.google.android.material.snackbar.Snackbar;

import org.tensorflow.lite.examples.detection.R;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import ru.object.detection.camera.ObjectDetectorAnalyzer;
import ru.object.detection.detection.DetectionResult;
import ru.object.detection.detection.ObjectDetector;
import ru.object.detection.util.ImageUtil;
import ru.object.detection.util.view.RecognitionResultOverlayView;

public class MoverioCameraSampleFragment extends AppCompatActivity implements CaptureStateCallback2, CaptureDataCallback, CaptureDataCallback2, PermissionGrantResultCallback, HeadsetStateCallback {
    private final String TAG = this.getClass().getSimpleName();

    private Context mContext = null;

    private CameraManager mCameraManager = null;
    private CameraDevice mCameraDevice = null;
    private final CaptureStateCallback2 mCaptureStateCallback2 = this;
    private final CaptureDataCallback mCaptureDataCallback = this;
    private final CaptureDataCallback2 mCaptureDataCallback2 = this;

    private ToggleButton mToggleButton_cameraOpenClose = null;
    private ToggleButton mToggleButton_captureStartStop = null;
    private ToggleButton mToggleButton_previewStartStop = null;

    private SeekBar mSeekBar_brightness = null;

    private SurfaceView mSurfaceView_preview = null;

    private TextView mTextView_captureState = null;
    private Spinner mSpinner_captureInfo = null;



    private TextView mTextView_framerate = null;
    private CalcurationRate mCalcurationRate_framerate = null;

    private TextView mTextView_test = null;

    private PermissionHelper mPermissionHelper = null;
    private DeviceManager mDeviceManager = null;

//ANALYZER
    private RecognitionResultOverlayView result_overlay = null;
    private Bitmap rgbBitmap = null;
    private ObjectDetectorAnalyzer.Config config = new ObjectDetectorAnalyzer.Config(
             0.5f,
            10,
            300,
            true,
            "model_q.tflite",
            "labelmap.txt"
    );
    private ObjectDetectorAnalyzer analyzer=null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_camera);

        mPermissionHelper = new PermissionHelper(this);
        mDeviceManager = new DeviceManager(this);

        result_overlay = findViewById(R.id.result_overlay2);
        mSurfaceView_preview = (SurfaceView) findViewById(R.id.surfaceView_preview);


        mContext = this;
        mCameraManager = new CameraManager(mContext, this);

        mToggleButton_cameraOpenClose = (ToggleButton) findViewById(R.id.toggleButton_cameraOpenClose);
        mToggleButton_cameraOpenClose.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    try {
                        mCameraDevice = mCameraManager.open(
                                (mCaptureStateCallback2),
                                (mCaptureDataCallback),
                                (mSurfaceView_preview.getHolder()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    mCameraManager.close(mCameraDevice);
                    mCameraDevice = null;
                }
            }
        });
        mToggleButton_captureStartStop = (ToggleButton) findViewById(R.id.toggleButton_captureStartStop);
        mToggleButton_captureStartStop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mTextView_test.setText(getCameraProperty());
                if(isChecked){
                    mCameraDevice.startCapture();
                }
                else {
                    mCameraDevice.stopCapture();
                }
            }
        });
        mToggleButton_previewStartStop = (ToggleButton) findViewById(R.id.toggleButton_previewStartStop);
        mToggleButton_previewStartStop.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    mCameraDevice.startPreview();
                }
                else {
                    mCameraDevice.stopPreview();
                }
            }
        });



        mTextView_captureState = (TextView) findViewById(R.id.textView_captureState);
        mSpinner_captureInfo = (Spinner) findViewById(R.id.spinner_cpatureInfo);
        mSpinner_captureInfo.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if(mCameraDevice != null)
                    mSpinner_captureInfo.setAdapter(new CaptureInfoAdapter(mContext, android.R.layout.simple_spinner_item, mCameraDevice.getProperty().getSupportedCaptureInfo()));

                return false;
            }
        });
        mSpinner_captureInfo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int[] item = (int[]) parent.getSelectedItem();
                CameraProperty property = mCameraDevice.getProperty();
                property.setCaptureSize(item[0], item[1]);
                property.setCaptureFps(item[2]);
                mCameraDevice.setProperty(property);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mSeekBar_brightness = (SeekBar) findViewById(R.id.seekBar_brightness);
        mSeekBar_brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                CameraProperty property = mCameraDevice.getProperty();
                property.setBrightness(progress + property.getBrightnessMin());
                int ret = mCameraDevice.setProperty(property);

                mTextView_test.setText(getCameraProperty());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });





        mTextView_framerate = (TextView) findViewById(R.id.textView_framerate);
        mCalcurationRate_framerate = new CalcurationRate(mTextView_framerate);
        mCalcurationRate_framerate.start();

        mTextView_test = (TextView) findViewById(R.id.textView_test);
    }

    @Override
    public void onResume() {
        super.onResume();
        mDeviceManager.registerHeadsetStateCallback(this);

    }

    @Override
    public void onPause() {
        super.onPause();
        mDeviceManager.unregisterHeadsetStateCallback(this);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        mCameraManager.release();
        mCameraManager = null;
        mCalcurationRate_framerate.finish();
        mDeviceManager.release();
        mDeviceManager = null;
    }

    private Handler uiHandler =new Handler(Looper.getMainLooper());


    @Override
    public void onCaptureData(long timestamp, byte[] datamass) {
        mCalcurationRate_framerate.updata();
try {
    ByteBuffer data = ByteBuffer.wrap(datamass);
    data.rewind();
    Bitmap output = getArgbBitmap(mCameraDevice.getProperty().getCaptureSize()[0],mCameraDevice.getProperty().getCaptureSize()[1]);


    output.copyPixelsFromBuffer(data);
    Bitmap resizedBitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);

    Matrix transformation = getTransformation(0, mCameraDevice.getProperty().getCaptureSize()[0], mCameraDevice.getProperty().getCaptureSize()[1]);
    new Canvas(resizedBitmap).drawBitmap(output, transformation, null);
    int[] inputArray = new int[config.getInputSize() * config.getInputSize()];

    ImageUtil.INSTANCE.storePixels(resizedBitmap, inputArray);
    List<DetectionResult> objects = detect(inputArray);
    mTextView_captureState.setText("onCaptureData:1"+timestamp+",size:"+datamass.length+"");



    ObjectDetectorAnalyzer.Result result = new ObjectDetectorAnalyzer.Result(
            objects,
            config.getInputSize(),
            config.getInputSize(),
            0
    );


    uiHandler.post(new Runnable() {
        @Override
        public void run() {
            onDetectionResult(result);

        }
    });

   // mTextView_captureState.setText("onCaptureData(ByteBuffer):" + datamass.length);
}catch (Exception e){
    //Snackbar.make(getWindow().getDecorView(), e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();


}
    }
    private void onDetectionResult(ObjectDetectorAnalyzer.Result result) {
        //Toast.makeText(this,"Hellow",Toast.LENGTH_SHORT).show();
        result_overlay.updateResults(result);
    }

    private ObjectDetector objectDetector = null;

    private List<DetectionResult> detect(int[] inputArray) {
        ObjectDetector detector = objectDetector;
        if (detector == null) {
            detector =new ObjectDetector(mContext.getAssets(),
                    config.getModelFile(),
                    config.getLabelsFile(),
                    false,
                    3,
                    config.getMinimumConfidence(),
                    config.getNumDetection(),
                    config.getInputSize(),
                    config.isQuantized()
            );
            objectDetector = detector;
        }

        return detector.detect(inputArray);
    }
    Matrix matrixToInput = null;
    private Matrix getTransformation(Integer rotationDegrees,Integer srcWidth,Integer srcHeight) {
        Matrix toInput = matrixToInput;
        if (toInput == null) {
            toInput = ImageUtil.INSTANCE.getTransformMatrix(rotationDegrees, srcWidth, srcHeight, 300, 300);
        }
        return toInput;
    }


    @Override
    public void onCameraOpened() {
        Log.d(TAG, "onCameraOpened");
        mTextView_captureState.setText("onCameraOpened");


        initView(mCameraDevice.getProperty());

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onCameraClosed() {
        Log.d(TAG, "onCameraClosed");
        mTextView_captureState.setText("onCameraClosed");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onCaptureStarted() {
        Log.d(TAG, "onCaptureStarted");
        mTextView_captureState.setText("onCaptureStarted");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onCaptureStopped() {
        Log.d(TAG, "onCaptureStopped");
        mTextView_captureState.setText("onCaptureStopped");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onPreviewStarted() {
        Log.d(TAG, "onPreviewStarted");
        mTextView_captureState.setText("onPreviewStarted");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onPreviewStopped() {
        Log.d(TAG, "onPreviewStopped");
        mTextView_captureState.setText("onPreviewStopped");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onRecordStarted() {
        Log.d(TAG, "onRecordStarted");
        mTextView_captureState.setText("onRecordStarted");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onRecordStopped() {
        Log.d(TAG, "onRecordStopped");
        mTextView_captureState.setText("onRecordStopped");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onPictureCompleted() {
        Log.d(TAG, "onPictureCompleted");
        mTextView_captureState.setText("onPictureCompleted");

        mTextView_test.setText(getCameraProperty());
    }

    @Override
    public void onPermissionGrantResult(String permission, int grantResult) {
        Snackbar.make(getWindow().getDecorView(), permission + " is " + (PermissionHelper.PERMISSION_GRANTED == grantResult ? "GRANTED" : "DENIED"), Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onCaptureData(long timestamp, ByteBuffer data) {
        //mTextView_captureState.setText("onCaptureData(ByteBuffer):"+data.limit());


    }

    @Override
    public void onHeadsetAttached() {
        Snackbar.make(getWindow().getDecorView(), "Headset attached...", Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onHeadsetDetached() {
        Snackbar.make(getWindow().getDecorView(), "Headset detached...", Snackbar.LENGTH_SHORT).show();

        mDeviceManager.close();
    }

    @Override
    public void onHeadsetDisplaying() {
        Snackbar.make(getWindow().getDecorView(), "Headset displaying...", Snackbar.LENGTH_SHORT).show();
    }

    @Override
    public void onHeadsetTemperatureError() {
        Snackbar.make(getWindow().getDecorView(), "Headset temperature error...", Snackbar.LENGTH_SHORT).show();
    }

    private class CaptureInfoAdapter extends ArrayAdapter {
        private Context context = null;
        private int textViewResourceId = 0;
        private List<int[]> captureInfoList = null;

        public CaptureInfoAdapter(Context _context, int _textViewResourceId, List<int[]> _captureInfoList) {
            super(_context, _textViewResourceId, _captureInfoList);

            context = _context;
            textViewResourceId = _textViewResourceId;
            captureInfoList = _captureInfoList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView v = (TextView)super.getView(position, convertView, parent);
            if(null != captureInfoList) {
                int[] captureInfo = captureInfoList.get(position);
                v.setText(String.valueOf(captureInfo[0] + "x" + captureInfo[1] + ", " + captureInfo[2] + "[fps]"));
            }
            else {
                v.setText("Unknown");
            }
            return v;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView v = (TextView)super.getDropDownView(position, convertView, parent);
            if(null != captureInfoList) {
                int[] captureInfo = captureInfoList.get(position);
                v.setText(String.valueOf(captureInfo[0] + "x" + captureInfo[1] + ", " + captureInfo[2] + "[fps]"));
            }
            else {
                v.setText("Unknown");
            }
            return v;
        }
    }

    private void initView(CameraProperty property){
        if(null == property) {
            Log.w(TAG, "CameraProperty is null...");
            return ;
        } else ;
        mSpinner_captureInfo.setAdapter(new CaptureInfoAdapter(mContext, android.R.layout.simple_spinner_item, mCameraDevice.getProperty().getSupportedCaptureInfo()));
        mSeekBar_brightness.setMax(property.getBrightnessMax() - property.getBrightnessMin());
        property = mCameraDevice.getProperty();
        property.setBrightness(0);
        property.setCaptureDataFormat(CameraProperty.CAPTURE_DATA_FORMAT_ARGB_8888);
        int ret = mCameraDevice.setProperty(property);

        updateView();
    }

    class CalcurationRate {
        TextView textView = null;
        int count = 0;
        long startTime = 0, endTime = 0;
        float rate = 0;

        public CalcurationRate(TextView _textView){
            textView = _textView;
        }
        public void start(){
            count = 0;
            startTime = System.currentTimeMillis();
            endTime = 0;
            rate = 0;
        }

        public void updata(){
            endTime = System.currentTimeMillis();
            count++;
            if((endTime - startTime) > 1000){
                rate = count*1000/(endTime - startTime);
                startTime = endTime;
                count = 0;
                textView.setText(String.valueOf(rate));
            }
            else ;
        }
        public void finish(){
            count = 0;
            startTime = 0;
            endTime = 0;
            rate = 0;
        }
    }

    private String getCameraProperty(){
        String str = "";

        CameraProperty property = mCameraDevice.getProperty();
        str += "info :" + property.getCaptureSize()[0] + ", " + property.getCaptureSize()[1] + ", " + property.getCaptureFps() + ", " + property.getCaptureDataFormat() + System.lineSeparator();
        str += "expo :" + property.getExposureMode() + ", " + property.getExposureStep() + ", bright:" + property.getBrightness() + System.lineSeparator();
        str += "WB   :" + property.getWhiteBalanceMode() + ", PLF  :" + property.getPowerLineFrequencyControlMode() + ", Indi :" + property.getIndicatorMode() + System.lineSeparator();
        str += "Focus:" + property.getFocusMode() + ", " + property.getFocusDistance() + ", Gain  :" + property.getGain() + System.lineSeparator();

        return str;
    }

    private void updateView() {
        CameraProperty property = mCameraDevice.getProperty();
        if (null == property) return;
        else ;

        // brightness
        mSeekBar_brightness.setProgress(property.getBrightnessMin() + property.getBrightness());


    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

//ANALAZER
    private Bitmap getArgbBitmap(Integer width,Integer height) {
       Bitmap bitmap = rgbBitmap;
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            rgbBitmap = bitmap;
        }
        return bitmap;
    }
}
