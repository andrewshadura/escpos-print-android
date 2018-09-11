package me.shadura.escposprint;

import android.app.Application;
import android.content.Context;

public class EscPosPrintApp extends Application {
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
    }
}
