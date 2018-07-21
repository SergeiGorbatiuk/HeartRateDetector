package com.example.sergey.pulsdetector2;

import android.os.CountDownTimer;

import com.jjoe64.graphview.series.DataPoint;

import java.util.ArrayList;

/**
 * Created by sergey on 14.07.18.
 */

public class PeaceMeasurer {

    private ArrayList<Integer> BPMs = null;
    private ArrayList<Double> amps = null;

    public Integer getPulse(ArrayList<Integer> redsums, Double sampleFrequency){

        while (redsums.size() > 512){ //ineffective!
            redsums.remove(0);
        }
        // process intervals
        PulseCalculator PulseCalculator = new PulseCalculator(3, sampleFrequency);
        ArrayList<Integer> intervalResults = PulseCalculator.CalculatePulseOverIntervals(redsums);


        // processing main interval
        ArrayList<Integer> BPMs = new ArrayList<>();
        ArrayList<Double> amps = new ArrayList<>();
        Integer result = PulseCalculator.CalculatePulseNoIntervals(redsums, BPMs ,amps);
        this.BPMs = BPMs;
        this.amps = amps;

        return GetPulseIfPossible(result, intervalResults);
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

    public DataPoint[] getBPMampPoints(){
        return VisUtils.getSignalSpecterPoints(this.BPMs, this.amps);
    }
}
