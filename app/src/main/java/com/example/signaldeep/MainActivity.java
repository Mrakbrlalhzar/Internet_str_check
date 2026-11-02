package com.example.signaldeep;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.util.List;

public class MainActivity extends AppCompatActivity {
    static {
        System.loadLibrary("native-lib");
    }
    public native String stringFromNative();
    public static native int nativeLteScore(int rsrp, int rsrq, int sinr);
    public static native int nativeWcdmaScore(int dbm, int ecno);

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

        findViewById(R.id.btn_perms).setOnClickListener(v ->
            ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            }, 100));

        findViewById(R.id.btn_usage).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        findViewById(R.id.btn_start).setOnClickListener(v -> {
            try {
                ContextCompat.startForegroundService(this,
                    new Intent(this, SignalSamplingService.class));
                Toast.makeText(this, "Sampling started", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Err: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btn_stop).setOnClickListener(v -> {
            stopService(new Intent(this, SignalSamplingService.class));
            Toast.makeText(this, "Stopped", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_history).setOnClickListener(v ->
            startActivity(new Intent(this, CorrelationActivity.class)));
    }

    @Override protected void onResume() { super.onResume(); handler.post(poller); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(poller); }

    private void refresh() {
        StringBuilder sb = new StringBuilder();
        sb.append("Native: ").append(stringFromNative()).append("\n\n");

        int dataType = 0;
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) {
                dataType = tm.getDataNetworkType();
            } else {
                sb.append("Grant READ_PHONE_STATE to detect network type.\n");
            }
        } catch (Exception e) { sb.append("Type error: ").append(e.getMessage()).append("\n"); }

        sb.append("Active data: ").append(SignalInterpreter.networkName(dataType)).append("\n");

        boolean hasLoc = ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasLoc) { sb.append("Grant LOCATION permission first.\n"); tvSignal.setText(sb.toString()); return; }

        SignalStrength ss = null;
        try { ss = tm.getSignalStrength(); } catch (Exception ignored) {}
        if (ss != null) {
            int lvl = ss.getLevel();
            sb.append("System level: ").append(lvl).append("/4 ").append(SignalInterpreter.bars(lvl)).append("\n");
        }

        List<CellInfo> infos = null;
        try { infos = tm.getAllCellInfo(); } catch (Exception ignored) {}
        if (infos == null || infos.isEmpty()) {
            sb.append("No cell info available (Samsung often hides this).\n");
            tvSignal.setText(sb.toString()); return;
        }

        for (CellInfo ci : infos) {
            if (ci instanceof CellInfoLte && SignalInterpreter.isLte(dataType)) {
                CellInfoLte c = (CellInfoLte) ci;
                CellSignalStrengthLte sig = c.getCellSignalStrength();
                CellIdentityLte id = c.getCellIdentity();
                int rsrp = sig.getRsrp(), rsrq = sig.getRsrq(), sinr = sig.getRssnr();
                int score = nativeLteScore(rsrp, rsrq, sinr);
                sb.append("=== 4G LTE (serving) ===\n");
                sb.append("RSRP: ").append(rsrp).append(" dBm → ").append(SignalInterpreter.lteRsrpGrade(rsrp)).append("\n");
                sb.append("RSRQ: ").append(rsrq).append(" dB  → ").append(SignalInterpreter.lteRsrqGrade(rsrq)).append("\n");
                sb.append("SINR: ").append(sinr).append("     → ").append(SignalInterpreter.lteSinrGrade(sinr)).append("\n");
                sb.append("RSSI: ").append(sig.getRssi()).append(" dBm\n");
                sb.append("CQI:  ").append(sig.getCqi()).append("\n");
                sb.append("Level: ").append(sig.getLevel()).append("/4 ").append(SignalInterpreter.bars(sig.getLevel())).append("\n");
                sb.append("--- Identity ---\n");
                if (id.getCi() != Integer.MAX_VALUE) sb.append("CellID: ").append(id.getCi()).append("\n");
                if (id.getPci() != Integer.MAX_VALUE) sb.append("PCI: ").append(id.getPci()).append("\n");
                if (id.getTac() != Integer.MAX_VALUE) sb.append("TAC: ").append(id.getTac()).append("\n");
                if (id.getEarfcn() != Integer.MAX_VALUE) sb.append("EARFCN: ").append(id.getEarfcn()).append("\n");
                if (id.getMcc() != Integer.MAX_VALUE) sb.append("MCC/MNC: ").append(id.getMcc()).append("/").append(id.getMnc()).append("\n");
                sb.append("--- Score ---\n");
                sb.append("Quality: ").append(score).append("/100 → ").append(SignalInterpreter.scoreToGrade(score)).append("\n\n");
            } else if (ci instanceof CellInfoWcdma && SignalInterpreter.isWcdma(dataType)) {
                CellInfoWcdma c = (CellInfoWcdma) ci;
                CellSignalStrengthWcdma sig = c.getCellSignalStrength();
                CellIdentityWcdma id = c.getCellIdentity();
                int dbm = sig.getDbm(), ecno = sig.getEcNo();
                int score = nativeWcdmaScore(dbm, ecno);
                sb.append("=== 3G WCDMA (serving) ===\n");
                sb.append("RSCP/DBM: ").append(dbm).append(" dBm → ").append(SignalInterpreter.wcdmaDbmGrade(dbm)).append("\n");
                sb.append("Ec/Io:    ").append(ecno).append(" dB  → ").append(SignalInterpreter.wcdmaEcnoGrade(ecno)).append("\n");
                sb.append("Level: ").append(sig.getLevel()).append("/4 ").append(SignalInterpreter.bars(sig.getLevel())).append("\n");
                sb.append("--- Identity ---\n");
                if (id.getCid() != Integer.MAX_VALUE) sb.append("CellID: ").append(id.getCid()).append("\n");
                if (id.getPsc() != Integer.MAX_VALUE) sb.append("PSC: ").append(id.getPsc()).append("\n");
                if (id.getLac() != Integer.MAX_VALUE) sb.append("LAC: ").append(id.getLac()).append("\n");
                if (id.getUarfcn() != Integer.MAX_VALUE) sb.append("UARFCN: ").append(id.getUarfcn()).append("\n");
                if (id.getMcc() != Integer.MAX_VALUE) sb.append("MCC/MNC: ").append(id.getMcc()).append("/").append(id.getMnc()).append("\n");
                sb.append("--- Score ---\n");
                sb.append("Quality: ").append(score).append("/100 → ").append(SignalInterpreter.scoreToGrade(score)).append("\n\n");
            } else if (ci instanceof CellInfoGsm && SignalInterpreter.isGsm(dataType)) {
                CellInfoGsm c = (CellInfoGsm) ci;
                CellSignalStrengthGsm sig = c.getCellSignalStrength();
                CellIdentityGsm id = c.getCellIdentity();
                int dbm = sig.getDbm();
                sb.append("=== 2G GSM (serving) ===\n");
                sb.append("RxLev: ").append(dbm).append(" dBm → ").append(SignalInterpreter.gsmDbmGrade(dbm)).append("\n");
                sb.append("BER:   ").append(sig.getBitErrorRate()).append("\n");
                sb.append("Level: ").append(sig.getLevel()).append("/4 ").append(SignalInterpreter.bars(sig.getLevel())).append("\n");
                if (id.getCid() != Integer.MAX_VALUE) sb.append("CellID: ").append(id.getCid()).append("\n");
                if (id.getLac() != Integer.MAX_VALUE) sb.append("LAC: ").append(id.getLac()).append("\n\n");
            }
        }
        tvSignal.setText(sb.toString());
    }
}
