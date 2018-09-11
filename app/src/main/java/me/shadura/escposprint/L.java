package me.shadura.escposprint;

import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Logging/crash reporting functions
 */
public class L {
    /**
     * Verbose log
     *
     * @param msg Log message
     */
    public static void v(String msg) {
        Log.v(EscPosPrintApp.LOG_TAG, msg);
    }

    /**
     * Info log
     *
     * @param msg Log message
     */
    public static void i(String msg) {
        Log.i(EscPosPrintApp.LOG_TAG, msg);
    }

    /**
     * Warning log
     *
     * @param msg Log message
     */
    public static void w(String msg) {
        Log.w(EscPosPrintApp.LOG_TAG, msg);
    }

    /**
     * Debug log
     *
     * @param msg Log message
     */
    public static void d(String msg) {
        Log.d(EscPosPrintApp.LOG_TAG, msg);
    }

    /**
     * Error log
     *
     * @param msg Log message
     */
    public static void e(String msg) {
        Log.e(EscPosPrintApp.LOG_TAG, msg);
    }

    /**
     * Error reporting
     *
     * @param msg Log message
     * @param t   Throwable to print, if not null
     */
    public static void e(String msg, @Nullable Throwable t) {
        e(msg);
        if (t != null) {
            e(t.getLocalizedMessage());
        }
    }
}
