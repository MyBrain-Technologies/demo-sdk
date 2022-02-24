package com.example.demoqplus

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.demoqplus.simpleVersion.BluetoothStateReceiver
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.model.EnumMBTDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mybraintech.sdk.core.model.MbtDevice


class QPlusViewModel : ViewModel() {

    // declare some state
    private var isBluetoothOn: Boolean = false

    // Parameters of MbtClient
    var mbtClient : MbtClient? = null


    // scan and get devices
    private var scanDeviceHelper: ScanDeviceHelper = ScanDeviceHelper()
    //private var mbtDevices: MutableLiveData<List<MbtDevice>> = MutableLiveData()

    /**
     * Initialise the  bluetooth state
     * */
    fun setBluetoothState(state: Boolean){
        this.isBluetoothOn = state
    }

    fun getBluetoothState(): Boolean{
        return this.isBluetoothOn
    }

    /**
     * Initialise the ScanHelper
     * Functions for ScanHelper
     * */
    fun iniScanHelper(): Boolean{
        return if (this.mbtClient == null) {
            false
        } else {
            this.scanDeviceHelper.setClient(this.mbtClient!!)
            true
        }
    }

    fun scanDevices(){
        this.scanDeviceHelper.setStatus(true)
        this.scanDeviceHelper.scanMbtDevice()

    }

    fun stopScan(){
        this.scanDeviceHelper.setStatus(false)
        this.scanDeviceHelper.stopScanDevice()
    }

    fun isScanning(): Boolean{
        return this.scanDeviceHelper.getStatus()
    }

    fun getDevicesFromHelper():LiveData<List<MbtDevice>>{
        return this.scanDeviceHelper.getDevices()
    }

    fun getDevices(): LiveData<List<MbtDevice>> {
        // for simplicity return data directly to view
        return Repository.instance().devices
    }

    /**
     * Initialise the MbtClient
     * Functions for MbtClient
     * */

    // MbtClient
    fun init(context: Context, deviceType: EnumMBTDevice) {
        mbtClient = MbtClientManager.getMbtClient(context, deviceType)
    }

    fun getClient(): MbtClient{
        return mbtClient!!
    }
}