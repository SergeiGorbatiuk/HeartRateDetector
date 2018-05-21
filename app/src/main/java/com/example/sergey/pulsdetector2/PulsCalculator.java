package com.example.sergey.pulsdetector2;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by sergey on 17.05.18.
 */

public class PulsCalculator {

    private int intervals_count = 3;
    private int measure_length = 512;
    private int interval_length = 256;
    private Double sampleFrequency;

    public PulsCalculator(int intervals, Double sampleFrequency){
        this.intervals_count = intervals;
        this.sampleFrequency = sampleFrequency;
    }

    private int ProcessInterval(int[] interval){
        FourierTransformer FFT = new FourierTransformer(interval_length);
        double[] xs = new double[interval_length];
        double[] ys = new double[interval_length];
        for (int i = 0; i < interval_length; i++){
            xs[i] = interval[i];
            ys[i] = 0;
        }
        FFT.fft(xs, ys);
        double[] freqs = new double[interval_length/2];
        double[] amplitudes = new double[interval_length/2];
        FFT.interpretFourier(xs, ys, freqs, amplitudes, sampleFrequency, interval_length);
        ArrayList<Integer> BPMs = new ArrayList<>();
        ArrayList<Double> amps = new ArrayList<>();
        FFT.cleanResults(freqs, amplitudes, BPMs, amps);
        return FFT.getMostProbablePuls(BPMs, amps);
    }

    public ArrayList<Integer> CalculatePuls(ArrayList<Integer> redsums){
        if (measure_length != interval_length + interval_length/2*(intervals_count - 1)){
            throw new RuntimeException("Cannot split measure of length " + measure_length
                    + " into " + intervals_count + " intervals of length " + interval_length);
        }
        //cropping the array
        Integer[] relevant = redsums.subList(redsums.size() - measure_length, redsums.size()).toArray(new Integer[measure_length]);
        final int[][] intervals = SplitInterval(relevant);

        ExecutorService pool = Executors.newFixedThreadPool(intervals_count);
        List<Callable<Object>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < intervals.length; i++){
                final int j = i;
                tasks.add(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        Integer result = ProcessInterval(intervals[j]);
                        return result;
                    }
                });
            }
            List<Future<Object>> invokeAll = pool.invokeAll(tasks);
            ArrayList<Integer> results = new ArrayList<>();
            for (Future<Object> future:
                 invokeAll) {
                Integer pulsval = (Integer)future.get();
                Log.e("INTNAL", pulsval.toString());
                results.add(pulsval);
            }
            return results;
        }
        catch (InterruptedException | ExecutionException e){
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
        return null;//rework
    }

    private int[][] SplitInterval(Integer[] relevant){
        int[][] intervals = new int[intervals_count][interval_length];
        for (int i = 0; i < intervals_count; i++){
            for (int j = 0; j < interval_length; j++){
                intervals[i][j] = relevant[i*interval_length/2 + j];
            }
        }

        return intervals;
    }

}
