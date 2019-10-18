package me.shadura.escposprint

import android.content.Context
import androidx.multidex.MultiDexApplication

import com.hypertrack.hyperlog.HyperLog

class EscPosPrintApp : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        HyperLog.initialize(this)
    }

    companion object {
        val LOG_TAG = "ESCPOS"

        lateinit var instance: EscPosPrintApp

        val context: Context
            get() = instance.applicationContext
    }
}
