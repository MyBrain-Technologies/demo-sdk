package com.mybraintech.demosdk.ui.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.mybraintech.demosdk.R
import com.mybraintech.demosdk.ui.main.frags.ConnectionFragment
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.model.EnumMBTDevice

class MainActivity : AppCompatActivity() {

    lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // init view model
        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        mainViewModel.setMbtClient(MbtClientManager.getMbtClient(this, EnumMBTDevice.Q_PLUS))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ConnectionFragment.newInstance())
                .commitNow()
        }
    }
}