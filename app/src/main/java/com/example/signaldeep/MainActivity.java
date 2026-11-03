package com.example.signaldeep;

import android.Manifest;
import android.content.pm.PackageManager;
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
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TelephonyManager tm;
    private PhoneStateListener phoneStateListener;

    private Handler handler = new Handler(Looper.getMainLooper());
    private TextView tvRsrpCur, tvRsrpMinMax, tvRsrpQual;
    private TextView tvRsrqCur, tvRsrqMinMax, tvRsrqQual;
    private TextView tvSinrCur, tvSinrMinMax, tvSinrQual;
    private TextView tvCqiCur, tvCqiMinMax, tvCqiQual;

    private BarChartView chartRsrp, chartRsrq, chartSinr, chartCqi;

    private int rawRsrp = -140, rawRsrq = -20, rawSinr = 0, rawCqi = 0;
    private int minRsrp = 0, maxRsrp = -140;
    private int minRsrq = 0, maxRsrq = -20;
    private int minSinr = 100, maxSinr = -100;
    private int minCqi = 15, maxCqi = 0;

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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

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

        // Set actual valid ranges for LTE parameters based on 3GPP / Android docs
        chartRsrp.setYRange(-140, -43);
        chartRsrp.setThresholds(-90, -105); // Green at -90, Orange at -105

        chartRsrq.setYRange(-20, -3);
        chartRsrq.setThresholds(-15, -20); // Green at -15, Orange at -20

        chartSinr.setYRange(-20, 30);
        chartSinr.setThresholds(13, 0); // Green at 13, Orange at 0

        chartCqi.setYRange(0, 15);
        chartCqi.setThresholds(10, 4); // Green at 10, Orange at 4

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
    }

    @Override
    protected void onStop() {
        super.onStop();
        tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(textTicker);
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
                        rawCqi = (Build.VERSION.SDK_INT >= 26) ? sig.getCqi() : 0;

                        if (rawRsrp < minRsrp) minRsrp = rawRsrp;
                        if (rawRsrp > maxRsrp) maxRsrp = rawRsrp;
                        if (rawRsrq < minRsrq) minRsrq = rawRsrq;
                        if (rawRsrq > maxRsrq) maxRsrq = rawRsrq;
                        if (rawSinr < minSinr) minSinr = rawSinr;
                        if (rawSinr > maxSinr) maxSinr = rawSinr;
                        if (rawCqi < minCqi) minCqi = rawCqi;
                        if (rawCqi > maxCqi) maxCqi = rawCqi;

                        chartRsrp.addValue(rawRsrp, SignalInterpreter.colorForRsrp(rawRsrp));
                        chartRsrq.addValue(rawRsrq, SignalInterpreter.colorForRsrq(rawRsrq));
                        chartSinr.addValue(rawSinr, SignalInterpreter.colorForSinr(rawSinr));
                        chartCqi.addValue(rawCqi, SignalInterpreter.colorForCqi(rawCqi));

                        SignalLogger.debug("Sample", "RSRP=" + rawRsrp + " RSRQ=" + rawRsrq + " SINR=" + rawSinr + " CQI=" + rawCqi);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            SignalLogger.debug("Sample", "Error: " + t);
        }
    }

    private void updateStats() {
        tvRsrpCur.setText("Current: " + rawRsrp + " dBm");
        tvRsrpMinMax.setText("Min-Max: " + minRsrp + " / " + maxRsrp);
        tvRsrpQual.setText("Quality: " + SignalInterpreter.lteRsrpGrade(rawRsrp));

        tvRsrqCur.setText("Current: " + rawRsrq + " dB");
        tvRsrqMinMax.setText("Min-Max: " + minRsrq + " / " + maxRsrq);
        tvRsrqQual.setText("Quality: " + SignalInterpreter.lteRsrqGrade(rawRsrq));

        tvSinrCur.setText("Current: " + rawSinr);
        tvSinrMinMax.setText("Min-Max: " + minSinr + " / " + maxSinr);
        tvSinrQual.setText("Quality: " + SignalInterpreter.lteSinrGrade(rawSinr));

        tvCqiCur.setText("Current: " + rawCqi);
        tvCqiMinMax.setText("Min-Max: " + minCqi + " / " + maxCqi);
        tvCqiQual.setText("Quality: " + SignalInterpreter.lteCqiGrade(rawCqi));
    }
}
