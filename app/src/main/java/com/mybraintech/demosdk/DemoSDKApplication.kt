package com.mybraintech.demosdk

import android.app.Application
import timber.log.Timber

class DemoSDKApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
