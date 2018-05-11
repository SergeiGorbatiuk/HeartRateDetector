package com.example.sergey.pulsdetector2;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by sergey on 07.05.18.
 */

public class FourierTransformer {

    private int n, m;

    // Lookup tables.  Only need to recompute when size of FFT changes.
    private double[] cos;
    private double[] sin;

    private double[] window;

    FourierTransformer(int n) {
        this.n = n;
        this.m = (int) (Math.log(n) / Math.log(2));

        // Make sure n is a power of 2
        if (n != (1 << m))
            throw new RuntimeException("FFT length must be power of 2");

        // precompute tables
        cos = new double[n / 2];
        sin = new double[n / 2];

        //     for(int i=0; i<n/4; i++) {
        //       cos[i] = Math.cos(-2*Math.PI*i/n);
        //       sin[n/4-i] = cos[i];
        //       cos[n/2-i] = -cos[i];
        //       sin[n/4+i] = cos[i];
        //       cos[n/2+i] = -cos[i];
        //       sin[n*3/4-i] = -cos[i];
        //       cos[n-i]   = cos[i];
        //       sin[n*3/4+i] = -cos[i];
        //     }

        for (int i = 0; i < n / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / n);
            sin[i] = Math.sin(-2 * Math.PI * i / n);
        }

        makeWindow();
    }

    private void makeWindow() {
        // Make a blackman window:
        // w(n)=0.42-0.5cos{(2*PI*n)/(N-1)}+0.08cos{(4*PI*n)/(N-1)};
        window = new double[n];
        for (int i = 0; i < window.length; i++)
            window[i] = 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1))
                    + 0.08 * Math.cos(4 * Math.PI * i / (n - 1));
    }

    public double[] getWindow() {
        return window;
    }

    void fft(double[] x, double[] y) {
        int i, j, k, n1, n2, a;
        double c, s, e, t1, t2;


        // Bit-reverse
        j = 0;
        n2 = n / 2;
        for (i = 1; i < n - 1; i++) {
            n1 = n2;
            while (j >= n1) {
                j = j - n1;
                n1 = n1 / 2;
            }
            j = j + n1;

            if (i < j) {
                t1 = x[i];
                x[i] = x[j];
                x[j] = t1;
                t1 = y[i];
                y[i] = y[j];
                y[j] = t1;
            }
        }

        // FFT
        n1 = 0;
        n2 = 1;

        for (i = 0; i < m; i++) {
            n1 = n2;
            n2 = n2 + n2;
            a = 0;

            for (j = 0; j < n1; j++) {
                c = cos[a];
                s = sin[a];
                a += 1 << (m - i - 1);

                for (k = j; k < n; k = k + n2) {
                    t1 = c * x[k + n1] - s * y[k + n1];
                    t2 = s * x[k + n1] + c * y[k + n1];
                    x[k + n1] = x[k] - t1;
                    y[k + n1] = y[k] - t2;
                    x[k] = x[k] + t1;
                    y[k] = y[k] + t2;
                }
            }
        }
    }

    void interpretFourier(double xs[], double ys[], double[] freqs, double amplitudes[], double Fs, int N){
        for (int i=0; i < xs.length / 2; i++){
            freqs[i] = i*Fs/N;
            amplitudes[i] = Math.sqrt(xs[i]*xs[i] + ys[i]*ys[i]);
        }
    }

    void cleanResults(double[] freqs, double[] amplitudes, ArrayList<Long> bpm, ArrayList<Double> amps){
        int minPuls = 40;
        int maxPuls = 200;
        for (int i = 0; i < freqs.length; i++){
            if (freqs[i]*60 >= minPuls && freqs[i]*60 <= maxPuls){
                bpm.add(Math.round(freqs[i]*60));
                amps.add(amplitudes[i]);
            }
        }
    }

    long[] getMostProbablePuls(ArrayList<Long> freqs, ArrayList<Double> amplitudes){
        Long max1 = 0L;
        Double maxamp1 = .0;
        Long max2 = 0L;
        Double maxamp2 = .0;
        for (int i = 0; i < freqs.size(); i++){
            Double curamp = amplitudes.get(i);
            if (curamp > maxamp1){
                maxamp2 = maxamp1;
                maxamp1 = curamp;
                max2 = max1;
                max1 = freqs.get(i);
            }
            else if (curamp > maxamp2){
                maxamp2 = curamp;
                max2 = freqs.get(i);
            }
        }
        return new long[]{max1, max2};
    }
}