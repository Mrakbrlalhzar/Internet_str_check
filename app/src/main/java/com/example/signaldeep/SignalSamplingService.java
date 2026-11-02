package com.example.signaldeep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import androidx.core.app.NotificationCompat;
import java.util.List;

public class SignalSamplingService extends Service {
    private static final String CH = "signal_ch";
    private Handler h;
    private Runnable r;
    private TelephonyManager tm;
    private WifiManager wm;
    private DatabaseHelper db;
    private UsageStatsManager usm;

    @Override public void onCreate() {
        super.onCreate();
        tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        db = new DatabaseHelper(this);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH, "Sampling", NotificationManager.IMPORTANCE_LOW));
        Notification n = new NotificationCompat.Builder(this, CH)
            .setContentTitle("SignalDeep Sampling")
            .setContentText("Collecting every 5s...")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build();
        startForeground(1, n);
        SignalLogger.log("Service", "onCreate — sampling started");

        h = new Handler(Looper.getMainLooper());
        r = new Runnable() {
            @Override public void run() {
                try { sample(); } catch (Throwable t) {
                    SignalLogger.crash(t);
                }
                h.postDelayed(this, 5000);
            }
        };
        h.post(r);
    }

    private void sample() {
        SignalSample s = new SignalSample();
        s.timestamp = System.currentTimeMillis();
        int dataType = 0;
        try {
            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                    == PackageManager.PERMISSION_GRANTED) dataType = tm.getDataNetworkType();
        } catch (Throwable t) { SignalLogger.debug("Service","type:"+t); }
        s.networkType = SignalInterpreter.networkName(dataType);

        try { SignalStrength ss = tm.getSignalStrength(); if (ss != null) s.level = ss.getLevel(); }
        catch (Throwable ignored) {}

        try {
            List<CellInfo> infos = tm.getAllCellInfo();
            if (infos != null) for (CellInfo ci : infos) {
                if (ci instanceof CellInfoLte && SignalInterpreter.isLte(dataType)) {
                    CellInfoLte c = (CellInfoLte) ci;
                    CellSignalStrengthLte sig = c.getCellSignalStrength();
                    CellIdentityLte id = c.getCellIdentity();
                    s.power = sig.getRsrp(); s.quality = sig.getRsrq(); s.sinr = sig.getRssnr();
                    if (s.level == 0) s.level = sig.getLevel();
                    if (id.getCi() != Integer.MAX_VALUE) s.cellId = id.getCi();
                    if (id.getPci() != Integer.MAX_VALUE) s.pci = id.getPci();
                    if (id.getTac() != Integer.MAX_VALUE) s.areaCode = id.getTac();
                    if (id.getEarfcn() != Integer.MAX_VALUE) s.freqCode = id.getEarfcn();
                    if (id.getMcc() != Integer.MAX_VALUE) s.mcc = id.getMcc();
                    if (id.getMnc() != Integer.MAX_VALUE) s.mnc = id.getMnc();
                    s.score = MainActivity.nativeLteScore(s.power, s.quality, s.sinr);
                    s.grade = SignalInterpreter.lteRsrpGrade(s.power) + " / " + SignalInterpreter.lteRsrqGrade(s.quality);
                    break;
                } else if (ci instanceof CellInfoWcdma && SignalInterpreter.isWcdma(dataType)) {
                    CellInfoWcdma c = (CellInfoWcdma) ci;
                    CellSignalStrengthWcdma sig = c.getCellSignalStrength();
                    CellIdentityWcdma id = c.getCellIdentity();
                    s.power = sig.getDbm();
                    boolean hasEcno = Build.VERSION.SDK_INT >= 30;
                    int ecno = 0;
                    if (hasEcno) { try { ecno = sig.getEcNo(); } catch (Throwable t) { hasEcno = false; } }
                    s.quality = hasEcno ? ecno : -99;
                    if (s.level == 0) s.level = sig.getLevel();
                    if (id.getCid() != Integer.MAX_VALUE) s.cellId = id.getCid();
                    if (id.getPsc() != Integer.MAX_VALUE) s.pci = id.getPsc();
                    if (id.getLac() != Integer.MAX_VALUE) s.areaCode = id.getLac();
                    if (id.getUarfcn() != Integer.MAX_VALUE) s.freqCode = id.getUarfcn();
                    if (id.getMcc() != Integer.MAX_VALUE) s.mcc = id.getMcc();
                    if (id.getMnc() != Integer.MAX_VALUE) s.mnc = id.getMnc();
                    s.score = MainActivity.nativeWcdmaScore(s.power, ecno, hasEcno);
                    s.grade = SignalInterpreter.wcdmaDbmGrade(s.power) +
                              (hasEcno ? " / " + SignalInterpreter.wcdmaEcnoGrade(ecno) : " (EcNo N/A)");
                    break;
                } else if (ci instanceof CellInfoGsm && SignalInterpreter.isGsm(dataType)) {
                    CellInfoGsm c = (CellInfoGsm) ci;
                    CellSignalStrengthGsm sig = c.getCellSignalStrength();
                    CellIdentityGsm id = c.getCellIdentity();
                    s.power = sig.getDbm(); s.quality = sig.getBitErrorRate();
                    if (s.level == 0) s.level = sig.getLevel();
                    if (id.getCid() != Integer.MAX_VALUE) s.cellId = id.getCid();
                    if (id.getLac() != Integer.MAX_VALUE) s.areaCode = id.getLac();
                    if (id.getArfcn() != Integer.MAX_VALUE) s.freqCode = id.getArfcn();
                    if (id.getMcc() != Integer.MAX_VALUE) s.mcc = id.getMcc();
                    if (id.getMnc() != Integer.MAX_VALUE) s.mnc = id.getMnc();
                    s.score = 50;
                    s.grade = SignalInterpreter.gsmDbmGrade(s.power);
                    break;
                }
            }
        } catch (Throwable t) { SignalLogger.debug("Service","cells:"+t); }

        try {
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now);
            long latest = 0; String fg = "";
            if (stats != null) for (UsageStats u : stats)
                if (u.getLastTimeUsed() > latest) { latest = u.getLastTimeUsed(); fg = u.getPackageName(); }
            s.fgApp = fg;
        } catch (Throwable ignored) {}

        try {
            if (wm != null && wm.isWifiEnabled()) {
                WifiInfo wi = wm.getConnectionInfo();
                if (wi != null && wi.getRssi() < 0) {
                    s.wifiRssi = wi.getRssi(); s.wifiSsid = wi.getSSID();
                }
            }
        } catch (Throwable ignored) {}

        db.insert(s);
        SignalLogger.debug("Sample",
            "net=" + s.networkType + " lvl=" + s.level +
            " power=" + s.power + " q=" + s.quality + " sinr=" + s.sinr +
            " cid=" + s.cellId + " pci=" + s.pci + " area=" + s.areaCode +
            " freq=" + s.freqCode + " score=" + s.score +
            " fg=" + s.fgApp + " wifi=" + s.wifiRssi);
    }

    @Override public int onStartCommand(Intent i, int f, int st) { return START_STICKY; }
    @Override public void onDestroy() {
        SignalLogger.log("Service","onDestroy — sampling stopped");
        if (h != null && r != null) h.removeCallbacks(r);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
