package com.mybraintech.demosdk

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.IntentFilter
import timber.log.Timber

class DemoSDKApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
