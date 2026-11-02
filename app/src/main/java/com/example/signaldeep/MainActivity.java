package com.example.signaldeep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    public static String NATIVE_STATUS = "not loaded";
    static {
        try { System.loadLibrary("native-lib"); NATIVE_STATUS = "OK"; }
        catch (Throwable t) { NATIVE_STATUS = "FAILED: " + t; }
    }
    public native String stringFromNative();
    public static native int nativeLteScore(int rsrp, int rsrq, int sinr);
    public static native int nativeWcdmaScore(int dbm, int ecno, boolean hasEcno);

    private TextView tvSignal;
    private TelephonyManager tm;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable poller = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 3000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        tvSignal = findViewById(R.id.tv_signal);
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        SignalLogger.log("Main","onCreate");

        findViewById(R.id.btn_perms).setOnClickListener(v -> {
            String[] perms;
            if (Build.VERSION.SDK_INT <= 28) {
                perms = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                };
            } else {
                perms = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                };
            }
            ActivityCompat.requestPermissions(this, perms, 100);
        });
        findViewById(R.id.btn_usage).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            SignalLogger.log("Main","opened Usage Access settings");
        });
        findViewById(R.id.btn_start).setOnClickListener(v -> {
            try {
                ContextCompat.startForegroundService(this, new Intent(this, SignalSamplingService.class));
                Toast.makeText(this, "Sampling started", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) { SignalLogger.crash(t); }
        });
        findViewById(R.id.btn_stop).setOnClickListener(v -> {
            stopService(new Intent(this, SignalSamplingService.class));
            Toast.makeText(this, "Stopped", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btn_history).setOnClickListener(v ->
            startActivity(new Intent(this, CorrelationActivity.class)));
        findViewById(R.id.btn_loginfo).setOnClickListener(v -> {
            java.io.File f = SignalLogger.getFile();
            String path = (f == null) ? "unavailable" : f.getAbsolutePath();
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Log file")
                .setMessage(path)
                .setPositiveButton("OK", null).show();
            SignalLogger.log("Main","log path: " + path);
        });
    }

    @Override protected void onResume() { super.onResume(); handler.post(poller); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(poller); }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append("Native: ").append(NATIVE_STATUS).append("\n");
        try {
            sb.append("C++: ").append(stringFromNative()).append("\n\n");
            doRefresh(sb);
        } catch (Throwable t) {
            SignalLogger.crash(t);
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append("\n=== ERROR in refresh ===\n").append(sw.toString());
        }
        tvSignal.setText(sb.toString());
    }

    private void doRefresh(StringBuilder sb) {
        int dataType = 0;
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) dataType = tm.getDataNetworkType();
        } catch (Throwable t) { sb.append("type err: ").append(t).append("\n"); }
        sb.append("Active data: ").append(SignalInterpreter.networkName(dataType)).append("\n");

        boolean hasLoc = ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasLoc) { sb.append("Grant LOCATION permission first.\n"); return; }

        SignalStrength ss = null;
        try { ss = tm.getSignalStrength(); } catch (Throwable ignored) {}
        if (ss != null) sb.append("System level: ").append(ss.getLevel()).append("/4 ")
            .append(SignalInterpreter.bars(ss.getLevel())).append("\n");

        List<CellInfo> infos = null;
        try { infos = tm.getAllCellInfo(); }
        catch (Throwable t) { sb.append("getAllCellInfo err: ").append(t).append("\n"); }
        if (infos == null || infos.isEmpty()) {
            sb.append("No cell info (Samsung often hides).\n"); return;
        }

        for (CellInfo ci : infos) {
            try {
                if (ci instanceof CellInfoLte && SignalInterpreter.isLte(dataType)) {
                    CellInfoLte c = (CellInfoLte) ci;
                    CellSignalStrengthLte sig = c.getCellSignalStrength();
                    CellIdentityLte id = c.getCellIdentity();
                    int rsrp = sig.getRsrp(), rsrq = sig.getRsrq(), sinr = sig.getRssnr();
                    int score = nativeLteScore(rsrp, rsrq, sinr);
                    sb.append("=== 4G LTE ===\n");
                    sb.append("RSRP: ").append(rsrp).append(" dBm → ")
                      .append(SignalInterpreter.lteRsrpGrade(rsrp)).append("\n");
                    sb.append("RSRQ: ").append(rsrq).append(" dB → ")
                      .append(SignalInterpreter.lteRsrqGrade(rsrq)).append("\n");
                    sb.append("SINR: ").append(sinr).append(" → ")
                      .append(SignalInterpreter.lteSinrGrade(sinr)).append("\n");
                    // RSSI is API 30+
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            int rssi = sig.getRssi();
                            if (rssi > -128) sb.append("RSSI: ").append(rssi).append(" dBm\n");
                        } catch (Throwable ignored) {}
                    }
                    // CQI is API 26+
                    try {
                        int cqi = sig.getCqi();
                        if (cqi >= 0) sb.append("CQI:  ").append(cqi).append("\n");
                    } catch (Throwable ignored) {}
                    sb.append("Level: ").append(sig.getLevel()).append("/4 ")
                      .append(SignalInterpreter.bars(sig.getLevel())).append("\n");
                    if (id.getCi() != Integer.MAX_VALUE) sb.append("CellID: ").append(id.getCi()).append("\n");
                    if (id.getPci() != Integer.MAX_VALUE) sb.append("PCI: ").append(id.getPci()).append("\n");
                    if (id.getTac() != Integer.MAX_VALUE) sb.append("TAC: ").append(id.getTac()).append("\n");
                    if (id.getEarfcn() != Integer.MAX_VALUE) sb.append("EARFCN: ").append(id.getEarfcn()).append("\n");
                    if (id.getMcc() != Integer.MAX_VALUE)
                        sb.append("MCC/MNC: ").append(id.getMcc()).append("/").append(id.getMnc()).append("\n");
                    sb.append("SCORE: ").append(score).append("/100 → ")
                      .append(SignalInterpreter.scoreToGrade(score)).append("\n\n");
                } else if (ci instanceof CellInfoWcdma && SignalInterpreter.isWcdma(dataType)) {
                    CellInfoWcdma c = (CellInfoWcdma) ci;
                    CellSignalStrengthWcdma sig = c.getCellSignalStrength();
                    CellIdentityWcdma id = c.getCellIdentity();
                    int dbm = sig.getDbm();
                    boolean hasEcno = Build.VERSION.SDK_INT >= 30;
                    int ecno = 0;
                    if (hasEcno) { try { ecno = sig.getEcNo(); } catch (Throwable t) { hasEcno = false; } }
                    int score = nativeWcdmaScore(dbm, ecno, hasEcno);
                    sb.append("=== 3G WCDMA ===\n");
                    sb.append("RSCP/Dbm: ").append(dbm).append(" dBm → ")
                      .append(SignalInterpreter.wcdmaDbmGrade(dbm)).append("\n");
                    if (hasEcno)
                        sb.append("Ec/Io: ").append(ecno).append(" dB → ")
                          .append(SignalInterpreter.wcdmaEcnoGrade(ecno)).append("\n");
                    else
                        sb.append("Ec/Io: N/A (Android 8 — needs API 30+)\n");
                    sb.append("Level: ").append(sig.getLevel()).append("/4 ")
                      .append(SignalInterpreter.bars(sig.getLevel())).append("\n");
                    if (id.getCid() != Integer.MAX_VALUE) sb.append("CellID: ").append(id.getCid()).append("\n");
                    if (id.getPsc() != Integer.MAX_VALUE) sb.append("PSC: ").append(id.getPsc()).append("\n");
                    if (id.getLac() != Integer.MAX_VALUE) sb.append("LAC: ").append(id.getLac()).append("\n");
                    if (id.getUarfcn() != Integer.MAX_VALUE) sb.append("UARFCN: ").append(id.getUarfcn()).append("\n");
                    if (id.getMcc() != Integer.MAX_VALUE)
                        sb.append("MCC/MNC: ").append(id.getMcc()).append("/").append(id.getMnc()).append("\n");
                    sb.append("SCORE: ").append(score).append("/100 → ")
                      .append(SignalInterpreter.scoreToGrade(score)).append("\n\n");
                } else if (ci instanceof CellInfoGsm && SignalInterpreter.isGsm(dataType)) {
                    CellInfoGsm c = (CellInfoGsm) ci;
                    CellSignalStrengthGsm sig = c.getCellSignalStrength();
                    CellIdentityGsm id = c.getCellIdentity();
                    int dbm = sig.getDbm();
                    sb.append("=== 2G GSM ===\n");
                    sb.append("RxLev: ").append(dbm).append(" dBm → ")
                      .append(SignalInterpreter.gsmDbmGrade(dbm)).append("\n");
                    sb.append("BER: ").append(sig.getBitErrorRate()).append("\n");
                    sb.append("Level: ").append(sig.getLevel()).append("/4 ")
                      .append(SignalInterpreter.bars(sig.getLevel())).append("\n");
                    if (id.getCid() != Integer.MAX_VALUE) sb.append("CellID: ").append(id.getCid()).append("\n");
                    if (id.getLac() != Integer.MAX_VALUE) sb.append("LAC: ").append(id.getLac()).append("\n\n");
                }
            } catch (Throwable t) {
                SignalLogger.crash(t);
                sb.append("Cell parse err: ").append(t).append("\n\n");
            }
        }
    }
}
