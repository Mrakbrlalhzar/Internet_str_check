package com.example.signaldeep;

import android.app.Application;

public class SignalDeepApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        SignalLogger.init(this);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { SignalLogger.crash(e); } catch (Throwable ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
        SignalLogger.log("App", "onCreate — SignalDeep started");
    }
}
