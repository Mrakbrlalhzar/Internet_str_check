package com.example.signaldeep;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellSignalStrengthLte;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TelephonyManager tm;
    private PhoneStateListener phoneStateListener;
    private ScrollView rootView;

    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvRsrpCur, tvRsrpMinMax, tvRsrpQual;
    private TextView tvRsrqCur, tvRsrqMinMax, tvRsrqQual;
    private TextView tvSinrCur, tvSinrMinMax, tvSinrQual;
    private TextView tvCqiCur, tvCqiMinMax, tvCqiQual;

    private BarChartView chartRsrp, chartRsrq, chartSinr, chartCqi;

    private int rawRsrp = -140, rawRsrq = -20, rawSinr = 0, rawCqi = -1;
    
    // Buffers for 3-second Min/Max (30 samples * 100ms = 3s)
    private static class TimeSample {
        long time;
        int value;
        TimeSample(long t, int v) { time = t; value = v; }
    }
    private Deque<TimeSample> bufRsrp = new ArrayDeque<>();
    private Deque<TimeSample> bufRsrq = new ArrayDeque<>();
    private Deque<TimeSample> bufSinr = new ArrayDeque<>();
    private Deque<TimeSample> bufCqi = new ArrayDeque<>();

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            fetchSignalData();
            handler.postDelayed(this, 100);
        }
    };

    private final Runnable textTicker = new Runnable() {
        @Override public void run() {
            updateStats();
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable bgColorTicker = new Runnable() {
        @Override public void run() {
            updateBackgroundColor();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        rootView = findViewById(R.id.root_view);

        tvRsrpCur = findViewById(R.id.tv_rsrp_current);
        tvRsrpMinMax = findViewById(R.id.tv_rsrp_minmax);
        tvRsrpQual = findViewById(R.id.tv_rsrp_quality);
        tvRsrqCur = findViewById(R.id.tv_rsrq_current);
        tvRsrqMinMax = findViewById(R.id.tv_rsrq_minmax);
        tvRsrqQual = findViewById(R.id.tv_rsrq_quality);
        tvSinrCur = findViewById(R.id.tv_sinr_current);
        tvSinrMinMax = findViewById(R.id.tv_sinr_minmax);
        tvSinrQual = findViewById(R.id.tv_sinr_quality);
        tvCqiCur = findViewById(R.id.tv_cqi_current);
        tvCqiMinMax = findViewById(R.id.tv_cqi_minmax);
        tvCqiQual = findViewById(R.id.tv_cqi_quality);

        chartRsrp = findViewById(R.id.chart_rsrp);
        chartRsrq = findViewById(R.id.chart_rsrq);
        chartSinr = findViewById(R.id.chart_sinr);
        chartCqi = findViewById(R.id.chart_cqi);

        chartRsrp.setYRange(-140, -43);
        chartRsrp.setThresholds(-90, -105);
        chartRsrq.setYRange(-20, -3);
        chartRsrq.setThresholds(-15, -20);
        chartSinr.setYRange(-20, 30);
        chartSinr.setThresholds(13, 0);
        chartCqi.setYRange(0, 15);
        chartCqi.setThresholds(10, 4);

        findViewById(R.id.btn_loginfo).setOnClickListener(v -> {
            java.io.File f = SignalLogger.getFile();
            String path = (f == null) ? "unavailable" : f.getAbsolutePath();
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log file").setMessage(path)
                .setPositiveButton("OK", null).show();
        });

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCellInfoChanged(List<CellInfo> cellInfo) {
                super.onCellInfoChanged(cellInfo);
                fetchSignalData();
            }
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);
                fetchSignalData();
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            tm.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        }
        handler.post(ticker);
        handler.post(textTicker);
        handler.post(bgColorTicker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(textTicker);
        handler.removeCallbacks(bgColorTicker);
        SignalLogger.log("Main", "App went to background. Sampling stopped.");
    }

    private void fetchSignalData() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            List<CellInfo> infos = tm.getAllCellInfo();
            if (infos != null) {
                for (CellInfo ci : infos) {
                    if (ci instanceof CellInfoLte) {
                        CellInfoLte c = (CellInfoLte) ci;
                        CellSignalStrengthLte sig = c.getCellSignalStrength();
                        rawRsrp = sig.getRsrp();
                        rawRsrq = sig.getRsrq();
                        rawSinr = sig.getRssnr();
                        
                        int tempCqi = (Build.VERSION.SDK_INT >= 26) ? sig.getCqi() : 0;
                        rawCqi = (tempCqi >= 0 && tempCqi <= 15) ? tempCqi : -1;

                        long now = System.currentTimeMillis();
                        addToBuffer(bufRsrp, now, rawRsrp);
                        addToBuffer(bufRsrq, now, rawRsrq);
                        addToBuffer(bufSinr, now, rawSinr);
                        if (rawCqi != -1) addToBuffer(bufCqi, now, rawCqi);

                        chartRsrp.addValue(rawRsrp, SignalInterpreter.colorForRsrp(rawRsrp));
                        chartRsrq.addValue(rawRsrq, SignalInterpreter.colorForRsrq(rawRsrq));
                        chartSinr.addValue(rawSinr, SignalInterpreter.colorForSinr(rawSinr));
                        chartCqi.addValue(rawCqi == -1 ? 0 : rawCqi, rawCqi == -1 ? 0xFF444444 : SignalInterpreter.colorForCqi(rawCqi));

                        SignalLogger.debug("Sample", "RSRP=" + rawRsrp + " RSRQ=" + rawRsrq + " SINR=" + rawSinr + " CQI=" + rawCqi);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            SignalLogger.debug("Sample", "Error: " + t);
        }
    }

    private void addToBuffer(Deque<TimeSample> buf, long now, int val) {
        buf.addLast(new TimeSample(now, val));
        while (!buf.isEmpty() && now - buf.peekFirst().time > 3000) {
            buf.removeFirst();
        }
    }

    private int getMin(Deque<TimeSample> buf, int defaultVal) {
        if (buf.isEmpty()) return defaultVal;
        int min = Integer.MAX_VALUE;
        for (TimeSample s : buf) if (s.value < min) min = s.value;
        return min;
    }

    private int getMax(Deque<TimeSample> buf, int defaultVal) {
        if (buf.isEmpty()) return defaultVal;
        int max = Integer.MIN_VALUE;
        for (TimeSample s : buf) if (s.value > max) max = s.value;
        return max;
    }

    private void updateStats() {
        int minR = getMin(bufRsrp, rawRsrp);
        int maxR = getMax(bufRsrp, rawRsrp);
        tvRsrpCur.setText("Current: " + rawRsrp + " dBm");
        tvRsrpMinMax.setText("Min-Max (3s): " + minR + " / " + maxR);
        tvRsrpQual.setText("Quality: " + SignalInterpreter.lteRsrpGrade(rawRsrp));

        int minQ = getMin(bufRsrq, rawRsrq);
        int maxQ = getMax(bufRsrq, rawRsrq);
        tvRsrqCur.setText("Current: " + rawRsrq + " dB");
        tvRsrqMinMax.setText("Min-Max (3s): " + minQ + " / " + maxQ);
        tvRsrqQual.setText("Quality: " + SignalInterpreter.lteRsrqGrade(rawRsrq));

        int minS = getMin(bufSinr, rawSinr);
        int maxS = getMax(bufSinr, rawSinr);
        tvSinrCur.setText("Current: " + rawSinr);
        tvSinrMinMax.setText("Min-Max (3s): " + minS + " / " + maxS);
        tvSinrQual.setText("Quality: " + SignalInterpreter.lteSinrGrade(rawSinr));

        if (rawCqi == -1) {
            tvCqiCur.setText("Current: N/A");
            tvCqiMinMax.setText("Min-Max (3s): N/A");
            tvCqiQual.setText("Quality: NO DATA");
        } else {
            int minC = getMin(bufCqi, rawCqi);
            int maxC = getMax(bufCqi, rawCqi);
            tvCqiCur.setText("Current: " + rawCqi);
            tvCqiMinMax.setText("Min-Max (3s): " + minC + " / " + maxC);
            tvCqiQual.setText("Quality: " + SignalInterpreter.lteCqiGrade(rawCqi));
        }
    }

    private void updateBackgroundColor() {
        if (bufRsrp.isEmpty() || bufRsrq.isEmpty() || bufSinr.isEmpty()) return;

        // Calculate averages over the last 3 seconds
        double avgRsrp = 0, avgRsrq = 0, avgSinr = 0, avgCqi = 0;
        int cqiCount = 0;
        for (TimeSample s : bufRsrp) avgRsrp += s.value;
        for (TimeSample s : bufRsrq) avgRsrq += s.value;
        for (TimeSample s : bufSinr) avgSinr += s.value;
        for (TimeSample s : bufCqi) { avgCqi += s.value; cqiCount++; }
        
        avgRsrp /= bufRsrp.size();
        avgRsrq /= bufRsrq.size();
        avgSinr /= bufSinr.size();
        if (cqiCount > 0) avgCqi /= cqiCount;

        // Normalize (0 to 1)
        double nSinr = Math.max(0, Math.min(1, (avgSinr + 10) / 35.0)); // -10..25 -> 0..1
        double nRsrq = Math.max(0, Math.min(1, (avgRsrq + 20) / 12.0)); // -20..-8 -> 0..1
        double nRsrp = Math.max(0, Math.min(1, (avgRsrp + 120) / 50.0)); // -120..-70 -> 0..1
        
        // Weights: SINR 40%, RSRQ 30%, RSRP 20%, CQI 10%
        double score;
        if (cqiCount > 0) {
            double nCqi = Math.max(0, Math.min(1, (avgCqi - 1) / 14.0)); // 1..15 -> 0..1
            score = (nSinr * 0.40 + nRsrq * 0.30 + nRsrp * 0.20 + nCqi * 0.10) * 100;
        } else {
            // If no CQI, redistribute weight to SINR and RSRQ
            score = (nSinr * 0.45 + nRsrq * 0.35 + nRsrp * 0.20) * 100;
        }

        // Color mapping
        int color;
        if (score >= 80) color = Color.parseColor("#1B5E20"); // Excellent
        else if (score >= 60) color = Color.parseColor("#388E3C"); // Good
        else if (score >= 40) color = Color.parseColor("#F57C00"); // Fair
        else if (score >= 20) color = Color.parseColor("#E64A19"); // Poor
        else color = Color.parseColor("#B71C1C"); // Critical

        rootView.setBackgroundColor(color);
        SignalLogger.debug("BG", "Score: " + (int)score + " -> Color: " + String.format("#%06X", (0xFFFFFF & color)));
    }
}
