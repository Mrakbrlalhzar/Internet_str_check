package com.example.signaldeep;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SignalLogger {
    private static final String TAG = "SignalLogger";
    private static final String FILE_NAME = "signaldeep_log.txt";
    private static final long MAX_SIZE = 2 * 1024 * 1024;
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TS =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static Context appCtx;
    public static void init(Context c) { appCtx = c.getApplicationContext(); }

    public static File getFile() {
        if (appCtx == null) return null;
        if (Build.VERSION.SDK_INT <= 28) {
            try {
                if (appCtx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                    if (dir != null && (dir.exists() || dir.mkdirs()))
                        return new File(dir, FILE_NAME);
                }
            } catch (Throwable t) { Log.w(TAG, "pub dir", t); }
        }
        File dir = appCtx.getExternalFilesDir(null);
        if (dir != null) return new File(dir, FILE_NAME);
        return new File(appCtx.getFilesDir(), FILE_NAME);
    }

    private static void write(String line) {
        if (appCtx == null) { Log.i(TAG, line); return; }
        synchronized (LOCK) {
            try {
                File f = getFile();
                if (f == null) return;
                if (f.length() > MAX_SIZE) {
                    File bak = new File(f.getParentFile(), FILE_NAME + ".old");
                    if (bak.exists()) bak.delete();
                    f.renameTo(bak);
                }
                FileWriter fw = new FileWriter(f, true);
                fw.write(line); fw.write("\n"); fw.close();
            } catch (Throwable t) { Log.e(TAG, "write fail", t); }
        }
    }

    public static void log(String tag, String msg) {
        String line = "[" + TS.format(new Date()) + "] [LOG] [" + tag + "] " + msg;
        Log.i(TAG, line); write(line);
    }
    public static void debug(String tag, String msg) {
        String line = "[" + TS.format(new Date()) + "] [DEBUG] [" + tag + "] " + msg;
        Log.d(TAG, line); write(line);
    }
    public static void crash(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String line = "[" + TS.format(new Date()) + "] [CRASH] " + t.getClass().getName()
            + "\n" + sw.toString() + "\n---END-CRASH---\n";
        Log.e(TAG, line); write(line);
    }
}
