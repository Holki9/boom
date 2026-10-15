package com.holki.boom;

import android.app.Application;

public class BoomApp extends Application {
    private static BoomApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static BoomApp getInstance() {
        return instance;
    }
}
