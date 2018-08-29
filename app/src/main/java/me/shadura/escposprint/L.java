package me.shadura.escposprint;

import android.support.annotation.Nullable;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

/**
 * Logging/crash reporting functions
 */
public class L {
    /**
     * Verbose log + crashlytics log
     *
     * @param msg Log message
     */
    public static void v(String msg) {
        Log.v(EscPosPrintApp.LOG_TAG, msg);
        Crashlytics.log("V: " + msg);
    }

    /**
     * Info log + crashlytics log
     *
     * @param msg Log message
     */
    public static void i(String msg) {
        Log.i(EscPosPrintApp.LOG_TAG, msg);
        Crashlytics.log("I: " + msg);
    }

    /**
     * Warning log + crashlytics log
     *
     * @param msg Log message
     */
    public static void w(String msg) {
        Log.w(EscPosPrintApp.LOG_TAG, msg);
        Crashlytics.log("W: " + msg);
    }

    /**
     * Debug log + crashlytics log
     *
     * @param msg Log message
     */
    public static void d(String msg) {
        Log.d(EscPosPrintApp.LOG_TAG, msg);
        Crashlytics.log("D: " + msg);
    }

    /**
     * Error log + crashlytics log
     *
     * @param msg Log message
     */
    public static void e(String msg) {
        Log.e(EscPosPrintApp.LOG_TAG, msg);
        Crashlytics.log("E: " + msg);
    }

    /**
     * Error reporting + send exception to crashlytics
     *
     * @param msg Log message
     * @param t   Throwable to send to crashlytics, if not null
     */
    public static void e(String msg, @Nullable Throwable t) {
        e(msg);
        if (t != null) {
            e(t.getLocalizedMessage());
            Crashlytics.logException(t);
        }
    }
}
