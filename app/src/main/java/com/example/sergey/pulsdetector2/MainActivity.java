package com.example.sergey.pulsdetector2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HeartRateMonitor";

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    private CountDownTimer timerToStart;
    private CountDownTimer timerToGo;
    private CountDownTimer additionalTimer;
    private TextView countDownField;
    private TextView resultTextField;
    private Button startButton;
    private GraphView graph;
    private GraphView graphFur;
    private Boolean measuring = false;
    private ArrayList<Integer> redsums = new ArrayList<>();
    private Integer intervals_count = 3;
    private Integer timePassed = 0;
    private Double sampleFrequency;
    private ProgressBar progressBar;


    private static PowerManager.WakeLock wakeLock = null;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("Permission", "just been granted");
                    this.recreate();
                }
                else {

                    Toast.makeText(MainActivity.this, "Camera permission denied, closing app", Toast.LENGTH_SHORT).show();
                    this.finishAffinity();
                }
            }
        }
    }

    private void initialize(){

        Log.e("initialize", "start");

        preview = (SurfaceView)findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        //previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");
        }


        countDownField = (TextView)findViewById(R.id.countDownField);
        resultTextField = (TextView)findViewById(R.id.resultTextField);
        progressBar = (ProgressBar)findViewById(R.id.progress_bar);
        progressBar.setMax(100);
        //progressBar.setProgressTintList(ColorStateList.valueOf(0xff7733));
        startButton = (Button)findViewById(R.id.buttonStart);
        graph = (GraphView)findViewById(R.id.graph);
        graphFur = (GraphView)findViewById(R.id.graphFur);

        timerToStart = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                Integer secsToGo = Math.round(millisUntilFinished/1000);
                countDownField.setText(getText(R.string.tillStart)+ secsToGo.toString());
            }

            @Override
            public void onFinish() {
                countDownField.setText("Measuring. Hold still!");
                progressBar.setVisibility(View.VISIBLE);
                camera.setPreviewCallback(previewCallback);
                timerToGo.start();
            }
        };

        timerToGo = new CountDownTimer(18000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timePassed += 1;
                if (redsums.size() < 500){
                    if (redsums.size() >= 256){
                        double sampleFreq = 256.0/timePassed;
                        final ArrayList<Integer> redcopy = (ArrayList<Integer>)redsums.clone();
                        new PreResultCalculator(256, sampleFreq).execute(redcopy);
                    }
                    else if(redsums.size() >= 128){
                        double sampleFreq = 128.0/timePassed;
                        final ArrayList<Integer> redcopy = (ArrayList<Integer>)redsums.clone();
                        new PreResultCalculator(128, sampleFreq).execute(redcopy);
                    }
                    else if (redsums.size() >= 64){
                        double sampleFreq = 64.0/timePassed;
                        final ArrayList<Integer> redcopy = (ArrayList<Integer>)redsums.clone();
                        new PreResultCalculator(64, sampleFreq).execute(redcopy);
                    }
                }
            }

            @Override
            public void onFinish() {
                if (redsums.size() < 530){
                    additionalTimer.start();
                }
                else {
                    onMeasureFinish();
                }
            }
        };

        additionalTimer = new CountDownTimer(1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                timePassed += 1;
                if (redsums.size() < 530){
                    additionalTimer.start();
                }
                else {
                    camera.setPreviewCallback(null);
                    onMeasureFinish();
                }
            }
        };

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!measuring){
                    graph.setVisibility(View.GONE);
                    graphFur.setVisibility(View.GONE);
                    resultTextField.setVisibility(View.GONE);
                    preview.setVisibility(View.VISIBLE);

                    progressBar.setProgress(0);
                    redsums.clear();
                    timePassed = 0;

                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    camera.setParameters(parameters);
                    countDownField.setText(getText(R.string.toGo) + "3");
                    startButton.setText(getText(R.string.stopMeasuring));
                    timerToStart.start();
                    measuring = !measuring;
                }
                else{
                    cancellationMotions();
                    measuring = !measuring;
                }
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        int permission = PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (permission == PermissionChecker.PERMISSION_GRANTED) {
            Log.e("PERMISSION", "granted");
            initialize();
        } else {
            Log.e("PERMISSION", "not granted");
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.CAMERA},
                    1);
        }


    }


    @Override
    public void onResume() {
        super.onResume();
        Log.e("onResume", "here");
        int permission = PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permission == PermissionChecker.PERMISSION_GRANTED){
            Log.e("onResume", "permission granted");
            if (wakeLock != null){
                wakeLock.acquire(10*60*1000L /*10 minutes*/);
            }
            if (camera == null){
                camera = Camera.open();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e("onPause", "here");
        int permission = PermissionChecker.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (permission == PermissionChecker.PERMISSION_GRANTED){
            if (measuring){
                cancellationMotions();
                measuring = !measuring;
            }
            if (wakeLock != null){
                wakeLock.release();
            }
            if (camera != null){
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
    }

    private SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                camera.setPreviewDisplay(previewHolder);
            } catch (Throwable t) {
                Log.e("surfaceCallback", "Exception in setPreviewDisplay()", t);
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = getSmallestPreviewSize(width, height, parameters);
            if (size != null) {
                parameters.setPreviewSize(size.width, size.height);
                Log.d(TAG, "Using width=" + size.width + " height=" + size.height);
            }
            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);
            camera.startPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            //Doing nothing
        }
    };

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera cam) {

            if (data == null) throw new NullPointerException();
            Camera.Size size = cam.getParameters().getPreviewSize();
            if (size == null) throw new NullPointerException();

            int width = size.width;
            int height = size.height;

            int[] imgAvgs = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), width, height);
            redsums.add(imgAvgs[0]);
            Double progress = (double)redsums.size()/530*100;
            progressBar.setProgress(progress.intValue());
        }
    };

    private Camera.Size getSmallestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;

        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if (size.width <= width && size.height <= height) {
                if (result == null) {
                    result = size;
                } else {
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;

                    if (newArea < resultArea) result = size;
                }
            }
        }

        return result;
    }

    private void onMeasureFinish(){
        countDownField.setText("DONE");
        Camera.Parameters parameters = camera.getParameters();
        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(parameters);
        camera.stopPreview();
        previewHolder.getSurface().release();
        preview.setVisibility(View.GONE);
        progressBar.setVisibility(View.INVISIBLE);

        sampleFrequency = .0 + redsums.size()/timePassed;
        Log.e("FPS", sampleFrequency.toString());


        while (redsums.size() > 512){ //ineffective!
            redsums.remove(0);
        }

        // process intervals
        PulseCalculator PulseCalculator = new PulseCalculator(intervals_count, sampleFrequency);
        ArrayList<Integer> intervalResults = PulseCalculator.CalculatePulseOverIntervals(redsums);


        // processing main interval
        ArrayList<Integer> BPMs = new ArrayList<>();
        ArrayList<Double> amps = new ArrayList<>();
        Integer result = PulseCalculator.CalculatePulseNoIntervals(redsums, BPMs ,amps);


        // visualize
        result = GetPulseIfPossible(result, intervalResults);
        if (result == null){
            resultTextField.setText("Bad measurement");
        }
        else{
            resultTextField.setText("Most probable result: " + result);
        }

        resultTextField.setVisibility(View.VISIBLE);

        graph.removeAllSeries();
        LineGraphSeries<DataPoint> red_series = new LineGraphSeries<>(getDataPoints(redsums));
        red_series.setColor(Color.RED);
        graph.addSeries(red_series);
        graph.setVisibility(View.VISIBLE);

        graphFur.removeAllSeries();
        LineGraphSeries<DataPoint> fur_series = new LineGraphSeries<>(getSignalSpecterPoints(BPMs, amps));
        graphFur.addSeries(fur_series);
        graphFur.setVisibility(View.VISIBLE);




        startButton.setText(R.string.startMeasuring);
        measuring = !measuring;
    }

    private void cancellationMotions(){
        progressBar.setVisibility(View.INVISIBLE);
        timerToStart.cancel();
        timerToGo.cancel();
        camera.setPreviewCallback(null);
        resultTextField.setVisibility(View.INVISIBLE);
        Camera.Parameters parameters = camera.getParameters();
        parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
        countDownField.setText("");
        camera.setParameters(parameters);
        startButton.setText(R.string.startMeasuring);
    }

    private DataPoint[] getDataPoints(ArrayList<Integer> sums){
        DataPoint[] points = new DataPoint[sums.size()];
        for (int i = 0; i < sums.size(); i++){
            points[i] = new DataPoint(i, sums.get(i));
        }
        return points;
    }

    private DataPoint[] getSignalSpecterPoints(ArrayList<Integer> bpms, ArrayList<Double> amps){
        DataPoint[] points = new DataPoint[bpms.size()];
        for (int i = 0; i < bpms.size(); i++){
            points[i] = new DataPoint(bpms.get(i), amps.get(i));
        }
        return points;
    }

    private Integer GetPulseIfPossible(Integer wholeInterval, ArrayList<Integer> intervals){
        // hardcoded for 3 intervals for now
        Integer m1 = intervals.get(0);
        Integer m2 = intervals.get(1);
        Integer m3 = intervals.get(2);
        Integer common;
        if (m1.equals(m2) && m2.equals(m3)) {
            return m1;
        }

        if (m1.equals(m2)){
            common = m1;
        }
        else if (m1.equals(m3)){
            common = m1;
        }
        else if (m2.equals(m3)){
            common = m2;
        }
        else return null;

        if (common >= wholeInterval - 7 || common <= wholeInterval + 7){
            return (common + wholeInterval)/2;
        }
        else return null;
    }

    class PreResultCalculator extends AsyncTask<ArrayList<Integer>, Void, Integer>{

        private Double sampleFreq;
        private Integer interlength;

        public PreResultCalculator(Integer interlength, Double sampleFreq){
            this.sampleFreq = sampleFreq;
            this.interlength = interlength;
        }

        @Override
        protected Integer doInBackground(ArrayList<Integer>[] params) {
            ArrayList<Integer> redsums = params[0];
            while (redsums.size() > interlength){ //twf??
                redsums.remove(0);
            }
            PulseCalculator pc = new PulseCalculator(3, this.sampleFreq);
            return pc.CalculatePulseNoIntervals(redsums);
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result != null){
                resultTextField.setText("preliminary result: " + result.toString());
            }
            else {
                resultTextField.setText("preliminary result: unclear");
            }
            resultTextField.setVisibility(View.VISIBLE);
        }
    }
}
