package com.example.sergey.pulsdetector2;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HeartRateMonitor";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    CountDownTimer timerToStart, timerToGo, additionalTimer;
    TextView countDownField, resultTextField;
    Button startButton;
    GraphView graph, graphFur;
    Boolean measuring = false;
    ArrayList<Integer> redsums = new ArrayList<>();
    FourierTransformer FFT = new FourierTransformer(256);
    Integer timePassed = 0;
    Double sampleFrequency;
    ProgressBar progressBar;

    private static PowerManager.WakeLock wakeLock = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        //previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");


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

        timerToGo = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                timePassed += 10;
                if (redsums.size() < 300){
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
                if (redsums.size() < 300){
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
                    progressBar.setVisibility(View.INVISIBLE);
                    timerToStart.cancel();
                    timerToGo.cancel();
                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
                    countDownField.setText("");
                    camera.setParameters(parameters);
                    startButton.setText(R.string.startMeasuring);
                    measuring = !measuring;
                }
            }
        });

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
        while (redsums.size() > 256){ //ineffective!
            redsums.remove(0);
        }
        graph.removeAllSeries();
        LineGraphSeries<DataPoint> red_series = new LineGraphSeries<>(getDataPoints(redsums));
        red_series.setColor(Color.RED);
        graph.addSeries(red_series);
        graph.setVisibility(View.VISIBLE);

        double[] ys = new double[256];
        double[] xs = new double[256];
        for (int i = 0; i < 256; i++){
            ys[i] = 0;
            xs[i] = redsums.get(i);
        }

        FFT.fft(xs, ys);
        double[] freqs = new double[128];
        double[] amplitudes = new double[128];

        FFT.interpretFourier(xs, ys, freqs, amplitudes, sampleFrequency, 256);

        ArrayList<Long> BPMs = new ArrayList<>();
        ArrayList<Double> amps = new ArrayList<>();
        FFT.cleanResults(freqs, amplitudes, BPMs, amps);
        long[] results = FFT.getMostProbablePuls(BPMs, amps);
        resultTextField.setText("Most probable results: " + results[0] + " and " + results[1]);
        resultTextField.setVisibility(View.VISIBLE);

        graphFur.removeAllSeries();
        LineGraphSeries<DataPoint> fur_series = new LineGraphSeries<>(getSignalSpecterPoints(BPMs, amps));
        graphFur.addSeries(fur_series);
        graphFur.setVisibility(View.VISIBLE);


        startButton.setText(R.string.startMeasuring);
        measuring = !measuring;
    }

    @Override
    public void onResume() {
        super.onResume();

        wakeLock.acquire();

        camera = Camera.open();
    }

    @Override
    public void onPause() {
        super.onPause();

        wakeLock.release();

        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;
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
            Double progress = (double)redsums.size()/300*100;
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

    private DataPoint[] getDataPoints(ArrayList<Integer> sums){
        DataPoint[] points = new DataPoint[sums.size()];
        for (int i = 0; i < sums.size(); i++){
            points[i] = new DataPoint(i, sums.get(i));
        }
        return points;
    }

    private DataPoint[] getSignalSpecterPoints(ArrayList<Long> bpms, ArrayList<Double> amps){
        DataPoint[] points = new DataPoint[bpms.size()];
        for (int i = 0; i < bpms.size(); i++){
            points[i] = new DataPoint(bpms.get(i), amps.get(i));
        }
        return points;
    }
}
