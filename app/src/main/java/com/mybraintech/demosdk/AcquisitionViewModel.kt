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
import com.mybraintech.sdk.core.bluetooth.devices.EnumBluetoothConnection
import com.mybraintech.sdk.core.listener.BatteryLevelListener
import com.mybraintech.sdk.core.listener.ConnectionListener
import com.mybraintech.sdk.core.listener.DeviceInformationListener
import com.mybraintech.sdk.core.listener.EEGListener
import com.mybraintech.sdk.core.listener.RecordingListener
import com.mybraintech.sdk.core.listener.ScanResultListener
import com.mybraintech.sdk.core.model.DeviceInformation
import com.mybraintech.sdk.core.model.EnumEEGFilterConfig
import com.mybraintech.sdk.core.model.EnumMBTDevice
import com.mybraintech.sdk.core.model.KwakContext
import com.mybraintech.sdk.core.model.MBTErrorCode
import com.mybraintech.sdk.core.model.MbtDevice
import com.mybraintech.sdk.core.model.MbtEEGPacket
import com.mybraintech.sdk.core.model.RecordingOption
import com.mybraintech.sdk.core.model.StreamingParams
import com.mybraintech.sdk.util.toJson
import com.sirvar.bluetoothkit.BluetoothKit
import com.twilio.audioswitch.AudioSwitch
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors


class AcquisitionViewModel : ViewModel() {

    // mbt device objets
    private lateinit var mbtClient: MbtClient
    var mbtDevice: MbtDevice? = null
    var audioBlueToothDevice: MbtDevice? = null

    private val batteryLevelListener: BatteryLevelListener by lazy { createBatteryLevelListener() }
    private val deviceInformationListener: DeviceInformationListener by lazy { createDeviceInformationListener() }
    private val mUpdateLiveData: MutableLiveData<String> = MutableLiveData<String>()
    private val mDeviceInfoLiveData: MutableLiveData<String> = MutableLiveData<String>()
    private val mDeviceAudioInfoLiveData: MutableLiveData<String> = MutableLiveData<String>()
    private val connectedBleDevice: MutableLiveData<BluetoothDevice?> = MutableLiveData<BluetoothDevice?>(null)
    private val connectedAudioDevice: MutableLiveData<BluetoothDevice?> = MutableLiveData<BluetoothDevice?>(null)
    val mDeviceAudioNameLiveData: MutableLiveData<String?> = MutableLiveData<String?>()
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
    private val mScanAudioStateLiveData: MutableLiveData<String> = MutableLiveData<String>()
    fun getAudioDeviceInfoLiveData(): LiveData<String> {
        return mDeviceAudioInfoLiveData
    }


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


    fun getScanAudioStateLiveData(): LiveData<String> {
        return mScanAudioStateLiveData
    }

    fun getBlueToothDevice(): LiveData<BluetoothDevice?> {
        return connectedBleDevice
    }

    fun getConnectionStateLiveDate(): LiveData<String> {
        return mConnectionStateLiveData
    }

    fun getFilterLiveData(): LiveData<String> {
        return mFilterLiveData
    }
    fun onStopScan() {
        mbtClient.stopScan()
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

    fun testCompputeStatistic() {
        mbtClient.computeStatistics(0.8f, arrayOf(0.7f,0.8f))
        isScanning = true
        mScanStateLiveData.postValue("Scanning... Click again to stop")
    }
    fun actionStopScanAudioButton() {

        stopAudioScan()

        mScanAudioStateLiveData.postValue("Scan Audio")
    }


    fun actionScanAudioButton() {
        if (!isScanning) {
            mbtClient.startScanAudio("",object:ScanResultListener{
                @SuppressLint("MissingPermission")
                override fun onMbtDevices(mbtDevices: List<MbtDevice>) {

                    if (mbtDevices.isNotEmpty()) {
                        audioBlueToothDevice = mbtDevices[0]

                        mUpdateLiveData.postValue("found audio device ${mbtDevices.size} devices and target ${audioBlueToothDevice?.bluetoothDevice?.name}")
                        mDeviceAudioInfoLiveData.postValue(audioBlueToothDevice?.bluetoothDevice?.name)
                        connectedAudioDevice.postValue(audioBlueToothDevice?.bluetoothDevice)
                        stopAudioScan()
                    } else {
                        audioBlueToothDevice = null
                    }
                }

                override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {


                    TNLog.d("AcquisitionViewModel","ScanResultListener onOtherDevices:$otherDevices")
                }

                override fun onScanError(error: Throwable) {
                    Timber.e(error)
                    TNLog.d("AcquisitionViewModel","ScanResultListener onScanError:$error")
                    mUpdateLiveData.postValue("encountered a scan error")
                }
            })
            isScanning = true
            mScanAudioStateLiveData.postValue("Scanning... Click again to stop")

        } else {
            stopAudioScan()
        }
    }


    fun actionScanButton() {
        if (!isScanning) {
            mbtClient.startScan("",scanResultListener)
            isScanning = true
            mScanStateLiveData.postValue("Scanning... Click again to stop")

        } else {
            stopScan()
        }
    }


    private fun stopAudioScan() {
        mbtClient.stopScanAudio()
        isScanning = false
        mScanAudioStateLiveData.postValue("Scan Audio")
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

            mbtClient?.initA2DP()
            if (!mbtClient.getBleConnectionStatus().isConnectionEstablished) {

                val currentThread = Thread.currentThread().name
                TNLog.d("AcquisitionActivity", "actionConnectButton currentThreasd:${currentThread}")
                mbtClient.connect(mbtDevice!!, connectionListener, EnumBluetoothConnection.BLE)
                mConnectionStateLiveData.postValue("Connection in progress...")
            } else {
                mbtClient.disconnect()
                mConnectionStateLiveData.postValue("Connect")
            }
        } else {
            mConnectionStateLiveData.postValue("Please scan first")
        }
    }

    fun actionDisConnectAudioButton() {
        audioBlueToothDevice?.let { mbtClient?.disconnectAudio(it) }
    }
    fun actionConnectAudioButton() {
        if (audioBlueToothDevice != null) {

            try {

//                val audioSwitch = AudioSwitch(applicationContext)
//
//                audioSwitch.start { audioDevices, selectedDevice ->
//                    audioSwitch.selectDevice(audioDevices.get(0))
//                    TNLog.d("AcquisitionActivity", "btnScanClassic audioDevices:${audioDevices.get(0).name} selectedDevices:$audioDevices")
//                }
//                viewModel.testCompputeStatistic()
//                startBleScan()

               mbtClient?.connectAudio(audioBlueToothDevice!!,object :ConnectionListener{
                   override fun onServiceDiscovered(message: String) {
                   }

                   override fun onBondingRequired(device: BluetoothDevice) {
                   }

                   override fun onBonded(device: BluetoothDevice) {
                   }

                   override fun onBondingFailed(device: BluetoothDevice) {
                   }

                   override fun onDeviceReady(message: String) {
                   }

                   override fun onDeviceDisconnected() {
                   }

                   override fun onConnectionError(error: Throwable, errorCode: MBTErrorCode) {
                   }
               })
            } catch (e: Exception) {
                e.printStackTrace()
            }
//            if (!mbtClient.getBleConnectionStatus().isConnectionEstablished) {
//
//                val currentThread = Thread.currentThread().name
//                TNLog.d("AcquisitionActivity", "actionConnectButton currentThreasd:${currentThread}")
//                mbtClient.connect(mbtDevice!!, connectionListener, EnumBluetoothConnection.BLE)
//                mConnectionStateLiveData.postValue("Connection in progress...")
//            } else {
//                mbtClient.disconnect()
//                mConnectionStateLiveData.postValue("Connect")
//            }
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
                        .setTriggerStatus(true)
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
                    connectedBleDevice.postValue(mbtDevice?.bluetoothDevice)
                    try{

                        stopScan()
                    } catch (ex:Exception) {
                        stopAudioScan()
                    }
                } else {
                    mbtDevice = null
                }
            }

            override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {


                TNLog.d("AcquisitionViewModel","ScanResultListener onOtherDevices:$otherDevices")
            }

            override fun onScanError(error: Throwable) {
                Timber.e(error)
                TNLog.d("AcquisitionViewModel","ScanResultListener onScanError:$error")
                mUpdateLiveData.postValue("encountered a scan error")
            }

        }
    }

    @SuppressLint("MissingPermission")
    private fun createConnectionListener(): ConnectionListener {
        return object : ConnectionListener {
            override fun onBonded(device: BluetoothDevice) {
                Timber.i("onBonded : ${device.name}")

                mbtClient.getDeviceInformation(deviceInformationListener)
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onBonded : ${device.name}")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onBonded mac : ${device.address}")
            }

            override fun onBondingFailed(device: BluetoothDevice) {
                Timber.i("onBondingFailed : ${device.name}")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onBondingFailed : ${device.name}")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onBondingFailed mac: ${device.address}")
            }

            override fun onBondingRequired(device: BluetoothDevice) {
                Timber.i("onBondingRequired : ${device.name}")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onBondingRequired : ${device.name}")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onBondingRequired mac: ${device.address}")
            }

            override fun onConnectionError(error: Throwable, errorCode:MBTErrorCode) {
                Timber.i("onConnectionError")
                Timber.e(error)
                mConnectionStateLiveData.postValue("Connect")
                mUpdateLiveData.postValue("encountered a connection error")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onConnectionError : $error")
            }

            override fun onDeviceDisconnected() {
                Timber.i("onDeviceDisconnected")
                mConnectionStateLiveData.postValue("Connect")
                mUpdateLiveData.postValue("Disconnected")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onDeviceDisconnected ")
            }

            override fun onDeviceReady(message: String) {
                Timber.i("onDeviceReady")
                mConnectionStateLiveData.postValue("Disconnect")
                if (!message.contains("audio")) {
                    mbtClient.getDeviceInformation(deviceInformationListener)
                }
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onDeviceReady :$message")
            }

            override fun onServiceDiscovered(message:String) {
                Timber.i("onServiceDiscovered")
                mConnectionStateLiveData.postValue("Connecting...")
                TNLog.d("AcquisitionViewModel", "createConnectionListener: onServiceDiscovered :$message")
            }

        }
    }

    private fun createDeviceInformationListener(): DeviceInformationListener {
        return object : DeviceInformationListener {
            override fun onDeviceInformation(deviceInformation: DeviceInformation) {
                mDeviceInformation = deviceInformation
                Timber.i("Dev_debug onDeviceInformation : ${deviceInformation.toJson()}")
                mDeviceInfoLiveData.postValue("name : ${deviceInformation.bleName} | fw ${deviceInformation.firmwareVersion}")

                mDeviceAudioNameLiveData.postValue(mDeviceInformation?.audioName)
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

                TNLog.d("AcquisitionViewModel", "onEegPacket: mbtEEGPacket :$mbtEEGPacket")
                if (mbtEEGPacket.channelsData.size >= 2) {
                    TNLog.d("AcquisitionViewModel", "onEegPacket:mbtEEGPacket.channelsData.size >= 2)")
                    val ch12 = ArrayList<ArrayList<Float>>()
                    ch12.add(secureNaN(mbtEEGPacket.channelsData[0]))
                    ch12.add(secureNaN(mbtEEGPacket.channelsData[1]))
                    ch12.add(secureNaN(mbtEEGPacket.statusData))
                    TNLog.d("AcquisitionViewModel", "ch12:$ch12")
                    mChart1LiveData.postValue(ch12)
                }
                if (mbtEEGPacket.channelsData.size >= 4) {
                    TNLog.d("AcquisitionViewModel", "onEegPacket:mbtEEGPacket.channelsData.size >= 4)")
                    val ch34 = ArrayList<ArrayList<Float>>()
                    ch34.add(secureNaN(mbtEEGPacket.channelsData[2]))
                    ch34.add(secureNaN(mbtEEGPacket.channelsData[3]))
                    ch34.add(secureNaN(mbtEEGPacket.statusData))
                    TNLog.d("AcquisitionViewModel", "ch34:$ch34")
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