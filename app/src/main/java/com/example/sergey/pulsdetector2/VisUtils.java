package com.example.sergey.pulsdetector2;

import com.jjoe64.graphview.series.DataPoint;

import java.util.ArrayList;

/**
 * Created by sergey on 14.07.18.
 */

abstract class VisUtils {
    public static DataPoint[] getDataPoints(ArrayList<Integer> sums){
        DataPoint[] points = new DataPoint[sums.size()];
        for (int i = 0; i < sums.size(); i++){
            points[i] = new DataPoint(i, sums.get(i));
        }
        return points;
    }

    public static DataPoint[] getSignalSpecterPoints(ArrayList<Integer> bpms, ArrayList<Double> amps){
        DataPoint[] points = new DataPoint[bpms.size()];
        for (int i = 0; i < bpms.size(); i++){
            points[i] = new DataPoint(bpms.get(i), amps.get(i));
        }
        return points;
    }
}
