package com.mybraintech.demosdk

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.ResearchStudy
import com.mybraintech.sdk.core.listener.*
import com.mybraintech.sdk.core.model.*
import com.mybraintech.sdk.util.toJson
import timber.log.Timber
import java.io.File


class AcquisitionViewModel : ViewModel() {

    // mbt device objets
    private lateinit var mbtClient: MbtClient
    var mbtDevice: MbtDevice? = null

    private val batteryLevelListener: BatteryLevelListener by lazy { createBatteryLevelListener() }
    private val deviceInformationListener: DeviceInformationListener by lazy { createDeviceInformationListener() }
    private val mUpdateLiveData: MutableLiveData<String> = MutableLiveData<String>()
    private val mDeviceInfoLiveData: MutableLiveData<String> = MutableLiveData<String>()
    private val eegListener: EEGListener by lazy { createEEGListener() }
    private val scanResultListener: ScanResultListener by lazy { createScanResultListener() }

    private val connectionListener: ConnectionListener by lazy { createConnectionListener() }
    private val mConnectionStateLiveData: MutableLiveData<String> = MutableLiveData<String>()

    private var filterMode = EnumEEGFilterConfig.NO_FILTER
    private val mFilterLiveData: MutableLiveData<String> = MutableLiveData<String>(filterMode.name)

    private var mDeviceInformation: DeviceInformation? = null
    private val mEEGStatusLiveData: MutableLiveData<String> = MutableLiveData<String>()
    private val mChart1LiveData: MutableLiveData<ArrayList<ArrayList<Float>>> =
        MutableLiveData<ArrayList<ArrayList<Float>>>()
    private val mChart2LiveData: MutableLiveData<ArrayList<ArrayList<Float>>> =
        MutableLiveData<ArrayList<ArrayList<Float>>>()
    private val mQualityLiveData: MutableLiveData<ArrayList<Float>> =
        MutableLiveData<ArrayList<Float>>()

    private var isScanning: Boolean = false
    private val mScanStateLiveData: MutableLiveData<String> = MutableLiveData<String>()

    fun getUpdateLiveData(): LiveData<String> {
        return mUpdateLiveData
    }

    fun getDeviceInfoLiveData(): LiveData<String> {
        return mDeviceInfoLiveData
    }

    fun getQualityLiveData(): LiveData<ArrayList<Float>> {
        return mQualityLiveData
    }

    fun getScanStateLiveData(): LiveData<String> {
        return mScanStateLiveData
    }

    fun getConnectionStateLiveDate(): LiveData<String> {
        return mConnectionStateLiveData
    }

    fun getFilterLiveData(): LiveData<String> {
        return mFilterLiveData
    }

    fun getEEGStatusLiveData(): LiveData<String> {
        return mEEGStatusLiveData
    }

    fun getChart1LiveData(): LiveData<ArrayList<ArrayList<Float>>> {
        return mChart1LiveData
    }

    fun getChart2LiveData(): LiveData<ArrayList<ArrayList<Float>>> {
        return mChart2LiveData
    }

    @SuppressLint("MissingPermission")
    fun setupMbtSdk(context: Context, deviceType: EnumMBTDevice) {
        mbtClient = MbtClientManager.getMbtClient(context, deviceType)
        val connectionStatus = mbtClient.getBleConnectionStatus()
        Timber.i("isConnectionEstablished = ${connectionStatus.isConnectionEstablished}")
        if (connectionStatus.isConnectionEstablished) {
            Timber.i("bluetoothDevice name = ${connectionStatus.mbtDevice?.bluetoothDevice?.name}")

            mbtClient.getBatteryLevel(batteryLevelListener)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun actionScanButton() {
        if (!isScanning) {
            mbtClient.startScan(scanResultListener)
            isScanning = true
            mScanStateLiveData.postValue("Scanning... Click again to stop")
        } else {
            stopScan()
        }
    }

    private fun stopScan() {
        mbtClient.stopScan()
        isScanning = false
        mScanStateLiveData.postValue("Scan")
    }

    fun getRecordingSize(): Int {
        return mbtClient.getRecordingBufferSize()
    }

    fun startRecord(outputFile: File, recordingListener: RecordingListener) {
        if (mbtClient.isEEGEnabled()) {
            if (!mbtClient.isRecordingEnabled()) {
                if (mDeviceInformation != null) {
                    mbtClient.startRecording(
                        RecordingOption(
                            outputFile,
                            KwakContext().apply { ownerId = "1234" },
                            mDeviceInformation!!,
                            "record-${System.currentTimeMillis()}"
                        ),
                        recordingListener
                    )
                } else {
                    mUpdateLiveData.postValue("Click again to record")
                    mbtClient.getDeviceInformation(deviceInformationListener)
                }
            }
        } else {
            mUpdateLiveData.postValue("please start EEG first")
        }
    }

    fun stopRecord() {
        mbtClient.stopRecording()
    }

    fun actionConnectButton() {
        if (mbtDevice != null) {
            if (!mbtClient.getBleConnectionStatus().isConnectionEstablished) {
                mbtClient.connect(mbtDevice!!, connectionListener)
                mConnectionStateLiveData.postValue("Connection in progress...")
            } else {
                mbtClient.disconnect()
                mConnectionStateLiveData.postValue("Connect")
            }
        } else {
            mConnectionStateLiveData.postValue("Please scan first")
        }
    }

    fun getBatteryLevel() {
        Timber.d("getBatteryLevel")
        if (mbtClient.getBleConnectionStatus().isConnectionEstablished) {
            mbtClient.getBatteryLevel(batteryLevelListener)
        } else {
            mUpdateLiveData.postValue("no device")
        }
    }

    fun actionFilterButton() {
        filterMode = when (filterMode) {
            EnumEEGFilterConfig.NO_FILTER -> EnumEEGFilterConfig.BANDPASS_BANDSTOP
            EnumEEGFilterConfig.BANDPASS_BANDSTOP -> EnumEEGFilterConfig.BANDSTOP
            EnumEEGFilterConfig.BANDSTOP -> EnumEEGFilterConfig.NO_FILTER
            else -> EnumEEGFilterConfig.NO_FILTER
        }
        mFilterLiveData.postValue(filterMode.name)
    }

    @OptIn(ResearchStudy::class)
    fun startStopEEG() {
        Timber.d("startStopEEG")
        if (mbtClient.getBleConnectionStatus().isConnectionEstablished) {
            if (!mbtClient.isEEGEnabled()) {
                Timber.d("startStreaming...")
                mbtClient.setEEGListener(eegListener)
                mbtClient.startStreaming(
                    StreamingParams.Builder()
                        .setEEG(true)
                        .setTriggerStatus(false)
                        .setAccelerometer(false)
                        .setQualityChecker(true)
                        .setEEGFilterConfig(filterMode)
                        .build()
                )
                mEEGStatusLiveData.postValue("Start EEG...")
            } else {
                Timber.d("stopStreaming...")
                mbtClient.stopStreaming()
                mEEGStatusLiveData.postValue("Stop EEG...")
            }
        } else {
            mUpdateLiveData.postValue("Please connect first")
        }
    }

    private fun createBatteryLevelListener(): BatteryLevelListener {
        return object : BatteryLevelListener {
            override fun onBatteryLevel(float: Float) {
                Timber.d("battery level = $float")
                val level: Int = float.toInt()
                val info = "Battery level: $level %"
                mUpdateLiveData.postValue(info)
            }

            override fun onBatteryLevelError(error: Throwable) {
                Timber.e("onBatteryLevelError : ${error.message}")
                mUpdateLiveData.postValue("Battery level : unknown")
            }
        }
    }

    private fun createScanResultListener(): ScanResultListener {
        return object : ScanResultListener {
            @SuppressLint("MissingPermission")
            override fun onMbtDevices(mbtDevices: List<MbtDevice>) {
                if (mbtDevices.isNotEmpty()) {
                    mbtDevice = mbtDevices[0]
                    mUpdateLiveData.postValue("found ${mbtDevices.size} devices and target ${mbtDevice?.bluetoothDevice?.name}")
                    mDeviceInfoLiveData.postValue(mbtDevice?.bluetoothDevice?.name)
                    stopScan()
                } else {
                    mbtDevice = null
                }
            }

            override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {
                Timber.d("found ${otherDevices.size} other devices")
            }

            override fun onScanError(error: Throwable) {
                Timber.e(error)
                mUpdateLiveData.postValue("encountered a scan error")
            }

        }
    }

    @SuppressLint("MissingPermission")
    private fun createConnectionListener(): ConnectionListener {
        return object : ConnectionListener {
            override fun onBonded(device: BluetoothDevice) {
                Timber.i("onBonded : ${device.name}")
            }

            override fun onBondingFailed(device: BluetoothDevice) {
                Timber.i("onBondingFailed : ${device.name}")
            }

            override fun onBondingRequired(device: BluetoothDevice) {
                Timber.i("onBondingRequired : ${device.name}")
            }

            override fun onConnectionError(error: Throwable) {
                Timber.i("onConnectionError")
                Timber.e(error)
                mConnectionStateLiveData.postValue("Connect")
                mUpdateLiveData.postValue("encountered a connection error")
            }

            override fun onDeviceDisconnected() {
                Timber.i("onDeviceDisconnected")
                mConnectionStateLiveData.postValue("Connect")
                mUpdateLiveData.postValue("Disconnected")
            }

            override fun onDeviceReady() {
                Timber.i("onDeviceReady")
                mConnectionStateLiveData.postValue("Disconnect")
                mbtClient.getDeviceInformation(deviceInformationListener)
            }

            override fun onServiceDiscovered() {
                Timber.i("onServiceDiscovered")
                mConnectionStateLiveData.postValue("Connecting...")
            }

        }
    }

    private fun createDeviceInformationListener(): DeviceInformationListener {
        return object : DeviceInformationListener {
            override fun onDeviceInformation(deviceInformation: DeviceInformation) {
                mDeviceInformation = deviceInformation
                Timber.i("onDeviceInformation : ${deviceInformation.toJson()}")
                mDeviceInfoLiveData.postValue("name : ${deviceInformation.bleName} | fw ${deviceInformation.firmwareVersion}")
            }

            override fun onDeviceInformationError(error: Throwable) {
                Timber.e(error)
                mUpdateLiveData.postValue("fail to retrieve device information")
            }

        }
    }

    private fun createEEGListener(): EEGListener {
        return object : EEGListener {
            override fun onEEGStatusChange(isEnabled: Boolean) {
                if (isEnabled) {
                    mEEGStatusLiveData.postValue("Stop EEG")
                } else {
                    mEEGStatusLiveData.postValue("Start EEG")
                }
            }

            override fun onEegError(error: Throwable) {
                Timber.e(error)
            }

            override fun onEegPacket(mbtEEGPacket: MbtEEGPacket) {
                if (mbtEEGPacket.channelsData.size >= 2) {
                    val ch12 = ArrayList<ArrayList<Float>>()
                    ch12.add(secureNaN(mbtEEGPacket.channelsData[0]))
                    ch12.add(secureNaN(mbtEEGPacket.channelsData[1]))
                    ch12.add(secureNaN(mbtEEGPacket.statusData))
                    mChart1LiveData.postValue(ch12)
                }
                if (mbtEEGPacket.channelsData.size >= 4) {
                    val ch34 = ArrayList<ArrayList<Float>>()
                    ch34.add(secureNaN(mbtEEGPacket.channelsData[2]))
                    ch34.add(secureNaN(mbtEEGPacket.channelsData[3]))
                    ch34.add(secureNaN(mbtEEGPacket.statusData))
                    mChart2LiveData.postValue(ch34)
                }
                mQualityLiveData.postValue(mbtEEGPacket.qualities)
            }
        }
    }

    fun secureNaN(list: ArrayList<Float>): ArrayList<Float> {
        for (i in list.indices) {
            if (list[i].isNaN()) {
                list[i] = 0f
            }
        }
        return list
    }

}