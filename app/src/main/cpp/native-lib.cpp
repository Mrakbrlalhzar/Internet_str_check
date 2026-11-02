#include <jni.h>
#include <string>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "SignalNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_signaldeep_MainActivity_stringFromNative(
        JNIEnv* env, jobject) {
    return env->NewStringUTF("Native layer active (C++17)");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_signaldeep_MainActivity_nativeLteScore(
        JNIEnv*, jobject, jint rsrp, jint rsrq, jint sinr) {
    double qR = clamp01((rsrp + 130.0) / 50.0);
    double qQ = clamp01((rsrq + 20.0)  / 10.0);
    double qS = clamp01( sinr        / 20.0);
    return (jint)std::lround(100.0 * (0.4*qR + 0.3*qQ + 0.3*qS));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_signaldeep_MainActivity_nativeWcdmaScore(
        JNIEnv*, jobject, jint dbm, jint ecno) {
    double qP = clamp01((dbm  + 110.0) / 40.0);
    double qE = clamp01((ecno + 12.0)  / 8.0);
    return (jint)std::lround(100.0 * (0.6*qP + 0.4*qE));
}
