package com.example.sergey.pulsdetector2;

import android.content.Context;
import android.content.pm.PackageManager;
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
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HeartRateMonitor";
    private static final AtomicBoolean processing = new AtomicBoolean(false);

    private static SurfaceView preview = null;
    private static SurfaceHolder previewHolder = null;
    private static Camera camera = null;
    CountDownTimer timerToStart, timerToGo;
    TextView countDownField;
    Button startButton;
    GraphView graph;
    Boolean measuring = false;
    ArrayList<Integer> redsums = new ArrayList<>();

    private static PowerManager.WakeLock wakeLock = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        preview = (SurfaceView) findViewById(R.id.preview);
        previewHolder = preview.getHolder();
        previewHolder.addCallback(surfaceCallback);
        //previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");


        countDownField = (TextView)findViewById(R.id.countDownField);
        startButton = (Button)findViewById(R.id.buttonStart);
        graph = (GraphView)findViewById(R.id.graph);

        timerToStart = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                Integer secsToGo = Math.round(millisUntilFinished/1000);
                countDownField.setText(getText(R.string.tillStart)+ secsToGo.toString());
            }

            @Override
            public void onFinish() {
                countDownField.setText(getText(R.string.tillStart)+ "0");
                camera.setPreviewCallback(previewCallback);
                timerToGo.start();
            }
        };

        timerToGo = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                Integer secsToGo = Math.round(millisUntilFinished/1000);
                countDownField.setText(getText(R.string.toGo)+ secsToGo.toString());
            }

            @Override
            public void onFinish() {
                countDownField.setText("DONE");
                Camera.Parameters parameters = camera.getParameters();
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
                camera.stopPreview();
                previewHolder.getSurface().release();
                preview.setVisibility(View.GONE);
                DataPoint[] points = new DataPoint[redsums.size()];
                for (int i = 0; i < redsums.size(); i++){
                    points[i] = new DataPoint(i, redsums.get(i));
                }
                LineGraphSeries<DataPoint> series = new LineGraphSeries<>(points);
                graph.addSeries(series);
                graph.setVisibility(View.VISIBLE);
                startButton.setText(R.string.startMeasuring);
            }
        };

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!measuring){
                    graph.setVisibility(View.GONE);
                    preview.setVisibility(View.VISIBLE);
                    Camera.Parameters parameters = camera.getParameters();
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    camera.setParameters(parameters);
                    countDownField.setText(getText(R.string.toGo) + "3");
                    startButton.setText(getText(R.string.stopMeasuring));
                    timerToStart.start();
                    measuring = !measuring;
                }
                else{
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

    @Override
    public void onResume() {
        super.onResume();

        wakeLock.acquire();

        camera = Camera.open();
    }

    /**
     * {@inheritDoc}
     */
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

            Integer imgAvg = ImageProcessing.decodeYUV420SPtoRedAvg(data.clone(), width, height);
            redsums.add(imgAvg);

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
}
