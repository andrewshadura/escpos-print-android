package me.shadura.escposprint;

import android.content.Context;
import androidx.multidex.MultiDexApplication;

import com.hypertrack.hyperlog.HyperLog;

public class EscPosPrintApp extends MultiDexApplication {
    public static final String LOG_TAG = "ESCPOS";

    private static EscPosPrintApp instance;

    public static EscPosPrintApp getInstance() {
        return instance;
    }

    public static Context getContext() {
        return instance.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        HyperLog.initialize(this);
    }
}
