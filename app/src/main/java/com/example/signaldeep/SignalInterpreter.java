package com.example.signaldeep;

public class SignalInterpreter {
    public static String lteRsrpGrade(int v) {
        if (v >= -80) return "EXCELLENT (4/4)";
        if (v >= -90) return "GOOD (3/4)";
        if (v >= -100) return "FAIR (2/4)";
        if (v >= -110) return "POOR (1/4)";
        if (v >= -120) return "VERY POOR (0/4)";
        return "NO SIGNAL";
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
        if (v >= 0)  return "FAIR";
        return "POOR";
    }
    public static String wcdmaDbmGrade(int v) {
        if (v >= -70)  return "EXCELLENT (4/4)";
        if (v >= -85)  return "GOOD (3/4)";
        if (v >= -100) return "FAIR (2/4)";
        if (v >= -110) return "POOR (1/4)";
        return "NO SIGNAL";
    }
    public static String wcdmaEcnoGrade(int v) {
        if (v >= -4)  return "EXCELLENT";
        if (v >= -8)  return "GOOD";
        if (v >= -12) return "FAIR";
        return "POOR";
    }
    public static String gsmDbmGrade(int v) {
        if (v >= -75)  return "EXCELLENT (4/4)";
        if (v >= -85)  return "GOOD (3/4)";
        if (v >= -95)  return "FAIR (2/4)";
        if (v >= -105) return "POOR (1/4)";
        return "NO SIGNAL";
    }
    public static String scoreToGrade(int s) {
        if (s >= 85) return "EXCELLENT";
        if (s >= 65) return "GOOD";
        if (s >= 40) return "FAIR";
        if (s >= 20) return "POOR";
        return "VERY POOR";
    }
    public static String bars(int level) {
        switch (level) {
            case 0: return "[    ]";
            case 1: return "[|   ]";
            case 2: return "[||  ]";
            case 3: return "[||| ]";
            case 4: return "[||||]";
            default: return "[????]";
        }
    }
    public static String networkName(int t) {
        switch (t) {
            case 1: return "GPRS (2G)"; case 2: return "EDGE (2G)";
            case 3: return "UMTS (3G)"; case 4: return "CDMA (2G)";
            case 5: return "EVDO_0 (3G)"; case 6: return "EVDO_A (3G)";
            case 7: return "1xRTT (2G)"; case 8: return "HSDPA (3G)";
            case 9: return "HSUPA (3G)"; case 10: return "HSPA (3G)";
            case 11: return "iDEN (2G)"; case 12: return "EVDO_B (3G)";
            case 13: return "LTE (4G)"; case 14: return "eHRPD (3G)";
            case 15: return "HSPAP (3G)"; case 16: return "GSM (2G)";
            case 17: return "TD-SCDMA (3G)"; case 18: return "IWLAN";
            case 19: return "LTE-CA (4G+)";
            default: return "UNKNOWN (" + t + ")";
        }
    }
    public static boolean isLte(int t)   { return t == 13 || t == 19; }
    public static boolean isWcdma(int t) {
        return t == 3 || t == 5 || t == 6 || t == 8 || t == 9 ||
               t == 10 || t == 12 || t == 14 || t == 15 || t == 17;
    }
    public static boolean isGsm(int t) {
        return t == 1 || t == 2 || t == 4 || t == 7 || t == 11 || t == 16;
    }
}
