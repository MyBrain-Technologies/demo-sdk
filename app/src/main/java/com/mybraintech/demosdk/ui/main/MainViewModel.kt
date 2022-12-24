package com.mybraintech.demosdk.ui.main

import androidx.lifecycle.ViewModel
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.core.model.DeviceInformation
import com.mybraintech.sdk.core.model.MbtDevice

class MainViewModel : ViewModel() {

    private lateinit var _mbtClient: MbtClient
    var targetDevice: MbtDevice? = null
    lateinit var deviceInformation: DeviceInformation

    fun setMbtClient(mbtClient: MbtClient) {
        _mbtClient = mbtClient
        targetDevice = mbtClient.getBleConnectionStatus().mbtDevice
    }

    fun getMbtClient(): MbtClient = _mbtClient
}