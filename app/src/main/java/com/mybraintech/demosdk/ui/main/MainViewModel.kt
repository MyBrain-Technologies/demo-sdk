package com.mybraintech.demosdk.ui.main

import androidx.lifecycle.ViewModel
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.core.model.DeviceInformation
import com.mybraintech.sdk.core.model.MbtDevice

class MainViewModel : ViewModel() {

    lateinit var mbtClient: MbtClient
    var targetDevice: MbtDevice? = null
    lateinit var deviceInformation: DeviceInformation
}