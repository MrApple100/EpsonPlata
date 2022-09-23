package ru.object.detection;

import android.app.Activity;
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
import androidx.constraintlayout.solver.widgets.Rectangle;

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
import com.google.zxing.Result;

import org.tensorflow.lite.examples.detection.R;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import io.reactivex.android.schedulers.AndroidSchedulers;
import ru.object.detection.camera.ObjectDetectorAnalyzer;
import ru.object.detection.detection.DetectionResult;
import ru.object.detection.detection.ObjectDetector;
import ru.object.detection.usecase.BarcodeImageScanner;
import ru.object.detection.util.ImageUtil;
import ru.object.detection.util.view.RecognitionResultOverlayView;

public class MoverioCameraSampleFragment extends Activity implements CaptureStateCallback2, CaptureDataCallback, CaptureDataCallback2, PermissionGrantResultCallback, HeadsetStateCallback {
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
            "labelmap.txt",
            "barcodetextTolink.txt"

    );
    private ObjectDetectorAnalyzer analyzer=null;


    private Bitmap resizedBitmap = Bitmap.createBitmap(config.getInputSize(), config.getInputSize(), Bitmap.Config.ARGB_8888);

    private Matrix matrixToInput = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_camera);

        mPermissionHelper = new PermissionHelper(this);
        mDeviceManager = new DeviceManager(this);

        result_overlay = findViewById(R.id.result_overlay2);
        mSurfaceView_preview = (SurfaceView) findViewById(R.id.surfaceView_preview);

        result_overlay.setDescriptionText(findViewById(R.id.DescriptionText));
        result_overlay.setWebView(findViewById(R.id.PDFViewer));


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
       // mDeviceManager.unregisterHeadsetStateCallback(this);
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

    mTextView_captureState.setText("onCaptureData:1"+timestamp+",size:"+datamass.length+"");


    final int[][][] handbound = {null};

    new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                handbound[0] = findhand(resizedBitmap);
            }catch(Exception ex){

            }
        }
    });
    int rotationDegrees = 0;

    BarcodeImageScanner.INSTANCE
            .parse(rgbBitmap)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                    (barcoderesult) -> {

                        ImageUtil.INSTANCE.storePixels(resizedBitmap, inputArray);
                        List<DetectionResult> objects = detect(inputArray);
                        Log.d(TAG, "detection objects($iteration): $objects");

                        ObjectDetectorAnalyzer.Result result = new ObjectDetectorAnalyzer.Result(objects, config.getInputSize(), config.getInputSize(), rotationDegrees);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onDetectionResult(result, barcoderesult, handbound[0]);
                            }
                        });

                    } ,
                    t -> {

                        ImageUtil.INSTANCE.storePixels(resizedBitmap, inputArray);
                        List<DetectionResult> objects = detect(inputArray);


                        Log.d(TAG, "detection objects($iteration): $objects");

                        ObjectDetectorAnalyzer.Result result = new ObjectDetectorAnalyzer.Result(objects, config.getInputSize(), config.getInputSize(), rotationDegrees);
                        uiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onDetectionResult(result, null, handbound[0]);
                            }
                        });
                        Log.d("Barcode", "error");


                    }).dispose();


    /*ObjectDetectorAnalyzer.Result result = new ObjectDetectorAnalyzer.Result(
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
    });*/

   // mTextView_captureState.setText("onCaptureData(ByteBuffer):" + datamass.length);
}catch (Exception e){
    //Snackbar.make(getWindow().getDecorView(), e.getLocalizedMessage(), Snackbar.LENGTH_SHORT).show();


}
    }
    private void onDetectionResult(ObjectDetectorAnalyzer.Result result, Result barcoderesult,int[][] handbound) {
        //Toast.makeText(this,"Hellow",Toast.LENGTH_SHORT).show();
        result_overlay.updateResults(result,barcoderesult,handbound);
    }

    private ObjectDetector objectDetector = null;

    private List<DetectionResult> detect(int[] inputArray) {
        ObjectDetector detector = objectDetector;
        if (detector == null) {
            detector =new ObjectDetector(mContext.getAssets(),
                    config.getModelFile(),
                    config.getLabelsFile(),
                    config.getBarcodetextTolink(),
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

    private int[][] findhand(Bitmap rgbBitmap) {

        int height = rgbBitmap.getHeight();
        int width = rgbBitmap.getWidth();
        int[] pixelRaster = new int[ width * height];//pixel raster for initial cam image
        int[] tempRaster = new int[ width * height];
        //Initialize rasters
        //Initialize rasters

        ImageUtil.INSTANCE.storePixels(resizedBitmap, pixelRaster);
        ImageUtil.INSTANCE.storePixels(resizedBitmap, tempRaster);

        int[][] pixelRaster2D = new int[height][width]; //converting pixelRaster to 2D format to check for surrounding pixels

        int[][] tempRaster2D = new int[height][width]; //temp raster for initial image

        int[][] densityRaster = new int[height][width]; //raster for density

        int[][] clusterRaster = new int[height][width]; //raster for cluster

        int index = 0;


        //THAT
        //First pass, get all skin pixel
        for (int i=0; i<height;i++) {
            int j = 0;
            while (j < width) {
                tempRaster2D[i][j] = pixelRaster[index];
                int[] color = hexToRGB(pixelRaster[index]);//convert hex arbg integer to RGB array
                        float[] hsb = new float[3]; // HSB array
                RGBtoHSB(color[0], color[1], color[2], hsb); //convert RGB to HSB array

                // Initial pass will use strict skin pixel rule.
                // It will only find skin pixels within smaller section compared to loose pixel rule
                // This will help avoid impurities in the detection
                if (strictSkinPixelRule(hsb)) {
                    pixelRaster2D[i][j] = 1; //if found turn pixel white in the 2D array
                } else {
                    pixelRaster2D[i][j] = 0; //else turn pixel black in the 2D array
                }
                j++;
                index++;
            }
        }


        //Creating a 2D density raster of found initial skin pixels
        //Run through pixel raster 2D array
        for (int col= 0;col<height;col++) {
            for (int row= 0;row<width;row++) {

                //IF pixel is white
                if (pixelRaster2D[col][row] == 1) {

                    //calculate pixel boundary (needed if the pixel is near the edges)
                    int max = 10;
                    int lowY = Math.max(col - max, 0);
                    int highY = Math.min(col + max, height);
                    int lowX = Math.max(row - max, 0);
                    int highX = Math.min(row + max, width);

                    //Run through pixels all pixels, at max 10 pixels away from this pixel in a square shape
                    for (int i=lowY;i<highY;i++) {
                        for (int j=lowX;j<highX;j++) {
                            if (pixelRaster2D[i][j] == 1) {
                                //both work, but i feel like densityRaster[col][row] is a little better
                                densityRaster[i][j]++;
                                //densityRaster[col][row]++; //update desnity of  if pixel found is white
                            }
                        }
                    }
                }
            }
        }


        Vector<Rectangle> listOfFoundObjects = new Vector<Rectangle>();//list of found objects

        //min and max bounds of the detected box
        //min and max bounds of the detected box
        /*var minX = 10000
        var maxX = -10000
        var minY = 10000
        var maxY = -10000*/

        //Now we can use that initial pass to find the general location of the hand in the image
        //Now we can use that initial pass to find the general location of the hand in the image
        for (int col= 0;col<height;col++) {
            for (int row= 0;row<width;row++) {
                pixelRaster2D[col][row] = 0; //make pixel black, since it should not be based upon the density raster

                //if density at this pixel is greater then 60
                if (densityRaster[col][row] > 60) {
                    pixelRaster2D[col][row] = 1; //turn this pixel white
                    boolean intersects = false;//check if any rectangles intersect with the one about to be created
                    Rectangle rect = new Rectangle();
                    rect.x = row - 7;
                    rect.y = col - 7;
                    rect.width = 14;
                    rect.height = 14;
                    //this pixel's rectangle

                    /*// check of any previous created rectagles intersect with new rectangle
                    for (int i=0;i< listOfFoundObjects.size();i++) {
                        //rectangle does intersect
                        if (intersects(rect, listOfFoundObjects.get(i))) {
                            intersects = true; //if a rectangle is found, then this pixel needs to ignored
                            break;
                        }
                    }*/

                    /*// If no intersection found
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
        if(minX==-10000) minX=0;
        if(maxX==10000) maxX=0;
        if(minY==-10000) minY=0;
        if(maxY==10000) maxY=0;*/
                }}}

        return pixelRaster2D;
    }


    Boolean strictSkinPixelRule(float[] hsb) {
        // Log.d("handbound",("${hsb[0]}  ${hsb[1]} ${hsb[1]}").toString())
        return hsb[0] < 0.11f && hsb[1] > 0.3f && hsb[1] < 0.73f && hsb[2] >0.6f;
    }
    /*fun looseSkinPixelRule(hsb: FloatArray): Boolean {
        return hsb[0] < 0.4f && hsb[1] < 1f && hsb[2] < 0.7f
    }*/


    int[] hexToRGB(Integer argbHex) {
        int[] rgb = new int[3];
        rgb[0] = argbHex & 0xFF0000 >>> 16; //get red
        rgb[1] = argbHex & 0xFF00 >>> 8; //get green
        rgb[2] = argbHex & 0xFF;//get blue
        return rgb; //return array
    }

    float[] RGBtoHSB(int r,int g,int b,float[] hsbvals2) {
        float[] hsbvals = hsbvals2;
        Float hue;
        Float saturation;
        Float brightness;
        if (hsbvals == null) {
            hsbvals = new float[3];
        }
        int cmax = Math.max(r, g);
        if (b > cmax) cmax = b;
        int cmin = Math.min(r, g);
        if (b < cmin) cmin = b;
        brightness = ((float)cmax) / 255.0f;
        saturation = (cmax != 0) ? ((float)(cmax - cmin) / (float)cmax) : 0f;
        if (saturation == 0f)
            hue = 0f;
        else {
            float redc = (float)(cmax - r) / (float)(cmax - cmin);
            float greenc = (float)(cmax - g) / (float)(cmax - cmin);
            float bluec = (float)(cmax - b) / (float)(cmax - cmin);
            hue = (r == cmax) ? bluec - greenc : (g == cmax) ? 2.0f + redc - bluec : 4.0f + greenc - redc;
            hue = hue / 6.0f;
            if (hue < 0) hue = hue + 1.0f;
        }
        hsbvals[0] = hue;
        hsbvals[1] = saturation;
        hsbvals[2] = brightness;
        return hsbvals;
    }
    Boolean intersects(androidx.constraintlayout.solver.widgets.Rectangle this2,Rectangle bounds) {
        return this2.x >= bounds.x && this2.x < bounds.x + bounds.width && this2.y >= bounds.y && this2.y < bounds.y + bounds.height;
    }

}
