package com.example.demoqplus;

import android.app.Application;

import timber.log.Timber;

public class SubApplication extends Application {

    @Override
    public void onCreate(){
        super.onCreate();
        if (BuildConfig.DEBUG){
            Timber.plant(new Timber.DebugTree());
        }
    }
}
