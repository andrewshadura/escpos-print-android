package me.shadura.escposprint

import com.hypertrack.hyperlog.HyperLog

/**
 * Logging/crash reporting functions
 */
object L {
    /**
     * Verbose log
     *
     * @param msg Log message
     */
    fun v(msg: String) {
        HyperLog.v(EscPosPrintApp.LOG_TAG, msg)
    }

    /**
     * Info log
     *
     * @param msg Log message
     */
    fun i(msg: String) {
        HyperLog.i(EscPosPrintApp.LOG_TAG, msg)
    }

    /**
     * Warning log
     *
     * @param msg Log message
     */
    fun w(msg: String) {
        HyperLog.w(EscPosPrintApp.LOG_TAG, msg)
    }

    /**
     * Debug log
     *
     * @param msg Log message
     */
    fun d(msg: String) {
        HyperLog.d(EscPosPrintApp.LOG_TAG, msg)
    }

    /**
     * Error log
     *
     * @param msg Log message
     */
    fun e(msg: String) {
        HyperLog.e(EscPosPrintApp.LOG_TAG, msg)
    }

    /**
     * Error reporting
     *
     * @param msg Log message
     * @param t   Throwable to print, if not null
     */
    fun e(msg: String, t: Throwable?) {
        e(msg)
        if (t != null) {
            e(t.localizedMessage ?: t.message ?: "<no message>")
        }
    }


}
