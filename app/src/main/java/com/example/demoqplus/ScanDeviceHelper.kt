package com.example.demoqplus

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.core.listener.ScanResultListener
import com.mybraintech.sdk.core.model.MbtDevice
import timber.log.Timber

class ScanDeviceHelper: ScanResultListener {

    private lateinit var mbtClient: MbtClient
    private val mDevices: MutableLiveData<List<MbtDevice>> = MutableLiveData()
    private var isScanning: Boolean = false

    fun setClient(mbtClient: MbtClient){
        this.mbtClient = mbtClient
    }

    fun getDevices(): LiveData<List<MbtDevice>> {
        return mDevices
    }

    fun getStatus(): Boolean{
        return this.isScanning
    }

    fun setStatus(status: Boolean){
        this.isScanning = status
    }

    fun scanMbtDevice(){
        Timber.d("scanning start")
        this.mbtClient.startScan(this)
    }

    fun stopScanDevice(){
        Timber.d("scanning stop")
        this.mbtClient.stopScan()
    }

    override fun onMbtDevices(mbtDevices: List<MbtDevice>) {
        Timber.d("new list found")
        if (!isScanning) {
            this.isScanning = true
        }
        mDevices.value = mbtDevices
    }

    override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {
        if (!isScanning) {
            this.isScanning = true
        }
        // other devices, do sth here...
    }

    override fun onScanError(error: Throwable) {
        this.isScanning = false
        Timber.e(error)
    }


}