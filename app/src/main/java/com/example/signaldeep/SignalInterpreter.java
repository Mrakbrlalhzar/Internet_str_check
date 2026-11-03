package com.example.signaldeep;

public class SignalInterpreter {
    public static String lteRsrpGrade(int v) {
        if (v >= -80) return "EXCELLENT";
        if (v >= -90) return "GOOD";
        if (v >= -100) return "FAIR";
        if (v >= -110) return "POOR";
        return "VERY POOR";
    }
    public static String lteRsrqGrade(int v) {
        if (v >= -10) return "EXCELLENT";
        if (v >= -15) return "GOOD";
        if (v >= -20) return "FAIR";
        return "POOR";
    }
    public static String lteSinrGrade(int v) {
        if (v >= 20) return "EXCELLENT";
        if (v >= 13) return "GOOD";
        if (v >= 5)  return "FAIR";
        if (v >= 0)  return "POOR";
        return "VERY POOR";
    }
    public static String lteCqiGrade(int v) {
        if (v >= 13) return "EXCELLENT";
        if (v >= 10) return "GOOD";
        if (v >= 7)  return "FAIR";
        if (v >= 1)  return "POOR";
        return "NO DATA";
    }
    public static int colorForRsrp(int v) {
        if (v >= -90) return 0xFF4CAF50;
        if (v >= -105) return 0xFFFF9800;
        return 0xFFF44336;
    }
    public static int colorForRsrq(int v) {
        if (v >= -15) return 0xFF4CAF50;
        if (v >= -20) return 0xFFFF9800;
        return 0xFFF44336;
    }
    public static int colorForSinr(int v) {
        if (v >= 13) return 0xFF4CAF50;
        if (v >= 0) return 0xFFFF9800;
        return 0xFFF44336;
    }
    public static int colorForCqi(int v) {
        if (v >= 10) return 0xFF4CAF50;
        if (v >= 4) return 0xFFFF9800;
        return 0xFFF44336;
    }
}
