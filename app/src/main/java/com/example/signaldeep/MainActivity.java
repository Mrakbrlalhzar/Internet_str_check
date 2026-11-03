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

    private Handler dataHandler = new Handler(Looper.getMainLooper());
    private Handler animHandler = new Handler(Looper.getMainLooper());

    private TextView tvRsrp, tvRsrq, tvSinr, tvCqi;
    private BarChartView chartRsrp, chartRsrq, chartSinr, chartCqi;

    private float targetRsrp = 0f, targetRsrq = 0f, targetSinr = 0f, targetCqi = 0f;
    private int colorRsrp = 0xFF4CAF50, colorRsrq = 0xFF4CAF50, colorSinr = 0xFF4CAF50, colorCqi = 0xFF4CAF50;
    private int rawRsrp = -140, rawRsrq = -20, rawSinr = 0, rawCqi = 0;

    private float currentRsrp = 0f, currentRsrq = 0f, currentSinr = 0f, currentCqi = 0f;

    private final Runnable animTicker = new Runnable() {
        @Override public void run() {
            currentRsrp += (targetRsrp - currentRsrp) * 0.1f;
            currentRsrq += (targetRsrq - currentRsrq) * 0.1f;
            currentSinr += (targetSinr - currentSinr) * 0.1f;
            currentCqi += (targetCqi - currentCqi) * 0.1f;

            chartRsrp.addValue(currentRsrp, colorRsrp);
            chartRsrq.addValue(currentRsrq, colorRsrq);
            chartSinr.addValue(currentSinr, colorSinr);
            chartCqi.addValue(currentCqi, colorCqi);

            animHandler.postDelayed(this, 100);
        }
    };

    private final Runnable textUpdater = new Runnable() {
        @Override public void run() {
            updateTextViews();
            dataHandler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        tvRsrp = findViewById(R.id.tv_rsrp_val);
        tvRsrq = findViewById(R.id.tv_rsrq_val);
        tvSinr = findViewById(R.id.tv_sinr_val);
        tvCqi = findViewById(R.id.tv_cqi_val);

        chartRsrp = findViewById(R.id.chart_rsrp);
        chartRsrq = findViewById(R.id.chart_rsrq);
        chartSinr = findViewById(R.id.chart_sinr);
        chartCqi = findViewById(R.id.chart_cqi);

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
        animHandler.post(animTicker);
        dataHandler.post(textUpdater);
        fetchSignalData();
    }

    @Override
    protected void onStop() {
        super.onStop();
        tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        animHandler.removeCallbacks(animTicker);
        dataHandler.removeCallbacks(textUpdater);
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

                        targetRsrp = Math.max(0, Math.min(1, (rawRsrp + 140f) / 60f));
                        targetRsrq = Math.max(0, Math.min(1, (rawRsrq + 20f) / 15f));
                        targetSinr = Math.max(0, Math.min(1, (rawSinr + 10f) / 35f));
                        targetCqi = Math.max(0, Math.min(1, rawCqi / 15f));

                        colorRsrp = SignalInterpreter.colorForRsrp(rawRsrp);
                        colorRsrq = SignalInterpreter.colorForRsrq(rawRsrq);
                        colorSinr = SignalInterpreter.colorForSinr(rawSinr);
                        colorCqi = SignalInterpreter.colorForCqi(rawCqi);

                        SignalLogger.debug("Sample", "RSRP=" + rawRsrp + " RSRQ=" + rawRsrq + " SINR=" + rawSinr + " CQI=" + rawCqi);
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            SignalLogger.debug("Sample", "Error: " + t);
        }
    }

    private void updateTextViews() {
        tvRsrp.setText("RSRP: " + rawRsrp + " dBm\n" + SignalInterpreter.lteRsrpGrade(rawRsrp));
        tvRsrq.setText("RSRQ: " + rawRsrq + " dB\n" + SignalInterpreter.lteRsrqGrade(rawRsrq));
        tvSinr.setText("SINR: " + rawSinr + "\n" + SignalInterpreter.lteSinrGrade(rawSinr));
        tvCqi.setText("CQI: " + rawCqi + "\n" + SignalInterpreter.lteCqiGrade(rawCqi));
    }
}
