package com.jason.experiment.bletest;

import android.app.Application;
import android.util.Log;

import io.reactivex.plugins.RxJavaPlugins;

/**
 * MainApp
 * Created by jason on 18/6/18.
 */
public class MainApp extends Application {

    public static final String RX_ERROR_TAG = "RX_ERROR";

    @Override
    public void onCreate() {
        super.onCreate();
        RxJavaPlugins.setErrorHandler(error -> {
            Log.d(RX_ERROR_TAG, error.getMessage());
        });
    }
}
