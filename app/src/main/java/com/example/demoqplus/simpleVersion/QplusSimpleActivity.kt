package com.example.demoqplus.simpleVersion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.example.demoqplus.R
import com.example.demoqplus.databinding.ActivityQplusSimpleBinding
import timber.log.Timber
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import com.example.demoqplus.databinding.ActivityQplusBinding
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.listener.*
import com.mybraintech.sdk.core.model.*
import com.mybraintech.sdk.util.toJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class QplusSimpleActivity : AppCompatActivity(), ConnectionListener {

    private lateinit var binding: ActivityQplusSimpleBinding

    // Declare bluetooth permissions
    private var isPermissionsGranted: Boolean = false
    var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION)

    // bluetooth state
    private var isScanning: Boolean = false
    private lateinit var bluetoothStateReceiver: BluetoothStateReceiver

    // mbt device objets
    private lateinit var mbtClient: MbtClient
    var mbtDevice: MbtDevice? = null
    var deviceInformation: DeviceInformation? = null
    private var isMbtConnected: Boolean = false

    //EEG signals
    var eegCount = 0

    // quality check
    var lastQualities = ArrayList<LinkedList<Float>>()
    var qualityWindowLength = 6
    var channelNb = 4
    var qualityButtons = ArrayList<Button>()
    var channelFlags = ArrayList<Boolean>().apply {
        for (i in 1..channelNb) {
            this.add(false)
        }
    }

    var test: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus_simple)

        binding = ActivityQplusSimpleBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        isPermissionsGranted = isAllPermissionsGranted(this, *PERMISSIONS)
        bluetoothStateReceiver = BluetoothStateReceiver(BluetoothAdapter.getDefaultAdapter().isEnabled)
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        // create mbt client
        mbtClient = MbtClientManager.getMbtClient(applicationContext, EnumMBTDevice.Q_PLUS)

        checkConnection()
        setBatteryLevel()
        initUI()
    }

    private fun initUI(){
        binding.simpleActivateBluetooth.setOnClickListener {
            activateBluetooth()
        }
        // functions for scanning devices
        binding.simpleScanDevices.setOnClickListener {
            when {
                isScanning -> {
                    Toast.makeText(this, "Is scanning now...", Toast.LENGTH_SHORT).show()
                }
                bluetoothStateReceiver.isBluetoothOn -> {
                    Timber.i("is ready for scanning devices...")
                    isScanning = true
                    mbtClient.startScan(object : ScanResultListener {
                        override fun onMbtDevices(mbtDevices: List<MbtDevice>) {
                            Timber.i("onMbtDevices size = ${mbtDevices.size}")
                            for (device in mbtDevices) {
                                Timber.i("device ${device.bluetoothDevice.name}")
                            }
                            mbtClient.stopScan()
                            mbtDevice = mbtDevices[0]

                            isScanning = false
                            //addResultText("found devices ${mbtDevice?.bluetoothDevice?.name}")
                            //addResultText("stop scan")
                        }

                        override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {
                            Timber.i("onOtherDevices size = ${otherDevices.size}")
                            for (device in otherDevices) {
                                if (device.name != null) {
                                    Timber.d("onOtherDevices name = ${device.name}")
                                }
                            }
                        }

                        override fun onScanError(error: Throwable) {
                            Timber.e(error)
                            //addResultText("onScanError")
                        }
                    })
                }
                else -> {
                    Toast.makeText(this, "bluetooth is off, please turn it on.", Toast.LENGTH_SHORT).show()
                    activateBluetooth()
                }
            }
        }

        binding.simpleStopscanDevices.setOnClickListener {
            if (isScanning) {
                mbtClient.stopScan()
                isScanning = false
            }
        }

        // functions for connecting device
        binding.simpleConnectDevice.setOnClickListener {
            if (!isMbtConnected && mbtDevice != null){
                // device is not connected...
                mbtClient.connect(mbtDevice!!, this)
            }
        }

        binding.simpleDisconnectDevice.setOnClickListener {
            if (isMbtConnected)
                mbtClient.disconnect()
        }

        // get power level
        binding.simpleGetBattery.setOnClickListener {
            setBatteryLevel()
        }

        // start receive EEG
        binding.simpleStartReceive.setOnClickListener {
            if (isMbtConnected && !mbtClient.isEEGEnabled())
                onBtnStartEEGClicked(false)
        }

        binding.simpleStopReceive.setOnClickListener {
            if (isMbtConnected && mbtClient.isEEGEnabled()){
                mbtClient.stopEEG()
                eegCount = 0
            }
        }

        // record EEG
        binding.simpleStartRecord.setOnClickListener {
            if (mbtClient.isEEGEnabled() && !mbtClient.isRecordingEnabled()){
                onBtnStartRecordingClicked()
            }
        }

        binding.simpleStopRecord.setOnClickListener {
            if(isMbtConnected && mbtClient.isRecordingEnabled()){
                mbtClient.stopEEGRecording()
            }

        }

        // quality checkers
        // add buttons to list
        qualityButtons.add(binding.P3)
        qualityButtons.add(binding.P4)
        qualityButtons.add(binding.AF3)
        qualityButtons.add(binding.AF4)
        for (i in 1..channelNb) {
            lastQualities.add(LinkedList())
        }

    }

    private fun onBtnStartEEGClicked(isStatusEnabled: Boolean) {
        mbtClient.startEEG(
            EEGParams(
                sampleRate = 250,
                isTriggerStatusEnabled = isStatusEnabled,
                isQualityCheckerEnabled = true
            ),
            object : EEGListener {
                override fun onEegPacket(mbtEEGPacket2: MbtEEGPacket2) {
                    if (mbtClient.isRecordingEnabled()){
                        Timber.d("is recording")
                    }
                    Timber.d("${mbtEEGPacket2.qualities}")
                    updateQualityButtons(mbtEEGPacket2.qualities)
                }
                override fun onEegError(error: Throwable) {
                    Timber.e(error)
                }
            },
        )
    }

    private fun onBtnStartRecordingClicked() {
        if (deviceInformation == null) {
            Timber.e("device info is empty!")
            return
        }

        Timber.d("onBtnStartRecordingClicked")

        val name = "${deviceInformation?.productName}-${getTimeNow()}.json"
        var folder = File(Environment.getExternalStorageDirectory().toString() + "/MBT_DEMO")
        folder.mkdirs()
        if (!folder.isDirectory || !folder.canWrite()) {
            folder = cacheDir
        }
        val outputFile = File(folder, name)

        mbtClient.startEEGRecording(
            RecordingOption(
                outputFile,
                KwakContext().apply { ownerId = "1" },
                deviceInformation!!,
                "record-" + UUID.randomUUID().toString()
            ),
            object : RecordingListener {
                override fun onRecordingSaved(outputFile: File) {
                    val path = outputFile.path
                    Timber.i("output file path = $path")

                    if (path.isPrivateMemory()) {
                        val contentUri: Uri = FileProvider.getUriForFile(
                            this@QplusSimpleActivity,
                            "com.example.demoqplus",
                            outputFile
                        )
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/json"
                            data = contentUri
                            putExtra(Intent.EXTRA_STREAM, contentUri);
                            clipData = ClipData.newRawUri("", contentUri)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }.also {
                            startActivity(it)
                        }
                    }
                }

                override fun onRecordingError(error: Throwable) {
                    Timber.e(error)
                }

            }
        )
    }

    @SuppressLint("SimpleDateFormat")
    fun getTimeNow(): String {
        try {
            val date = Date()
            val tf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return tf.format(date)
        } catch (e: Exception) {
            Timber.e(e)
            return System.currentTimeMillis().toString()
        }
    }

    private fun String.isPrivateMemory(): Boolean {
        return this.contains(this@QplusSimpleActivity.packageName)
    }

    /**
     * functions for quality checkers d
     * **/

    private fun updateQualityButtons(qualities: ArrayList<Float>?){
        if (qualities != null && qualities.size == channelNb) {
            var greenCount = 0
            for (i in 0 until channelNb) {
                updateQualityBuffer(
                    qualities[i],
                    lastQualities[i]
                ) //add quality value at the end of channel quality buffer
                val isGreen = areGoodSignals(lastQualities[i])

                if (isGreen) {
                    channelFlags[i] = true //rule 2: set flag to true
                    greenCount++
                    qualityButtons[i].background = AppCompatResources.getDrawable(applicationContext, R.color.green_signal)
                } else {
                    qualityButtons[i].background = AppCompatResources.getDrawable(applicationContext, R.color.red)
                }
            }

            if (isSignalGoodEnough()) {
                Timber.d("quality is good")
            } else {
                Timber.d("quality is bad")
            }
        } else {
            Timber.e("qualities size is not equal $channelNb!")
        }
    }

    private fun updateQualityBuffer(newValue: Float, list: LinkedList<Float>) {
        if (!newValue.isNaN() && !newValue.isInfinite()) {
            list.addLast(newValue)
            if (list.size > qualityWindowLength) list.pollFirst()
        }
    }

    private fun areGoodSignals(qualities: LinkedList<Float>): Boolean {
        var count = 0
        for (value in qualities) {
            if (value < 0.25) {
                return false
            }
            if (value == 1.0f) {
                count++
            }
        }
        return (count == qualities.size || count >= 3)
    }

    private fun isSignalGoodEnough(): Boolean {
        //return haProgress == qualityWindowLength //rule 1
        return channelFlags.count { it } == channelNb //rule 2 : all channels are passed to good at least one (separately)
    }

    /**
     * these functions are implemented for BatteryLevelListener
     * onBatteryLevel() / onBatteryLevelError()
     * **/
    private fun setBatteryLevel(){
        if (isMbtConnected){
            mbtClient.getBatteryLevel(object: BatteryLevelListener{
                override fun onBatteryLevel(float: Float) {
                    Timber.d("level = $float")
                    // set process and text
                    var level: Int = float.toInt()
                    binding.simpleBattery.progress = level
                    binding.simpleBatteryLevel.text = level.toString()+" %"
                }

                override fun onBatteryLevelError(error: Throwable) {
                    TODO("Not yet implemented")
                }
            })
        } else {
            binding.simpleBattery.progress = 0
            binding.simpleBatteryLevel.text = "0 %"
        }
    }

    // is device connected?
    private fun checkConnection(){
        // this variable contains 2 useful things: isConnectionEstablished and mbtDevice
        // mbtdevice = android.bluetooth.BluetoothDevice
        val bleConnectionStatus = mbtClient.getBleConnectionStatus()
        Timber.d("getBleConnectionStatus = ${bleConnectionStatus.mbtDevice.toJson()} | ${bleConnectionStatus.isConnectionEstablished}")
    }

    // activate bluetooth
    private fun activateBluetooth(){
        if (isPermissionsGranted){
            // ask for activation of bluetooth
            if (!bluetoothStateReceiver.isBluetoothOn){
                Timber.i("Bluetooth isn't enable, ask user for turning it on...")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, 1)
            }
        } else {
            // request permissions
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
        }
    }

    // check all permissions
    private fun isAllPermissionsGranted(context: Context, vararg permissions: String): Boolean = permissions.all{
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // fetch request permissions results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            1 ->{
                if(grantResults.isNotEmpty()){
                    var accessCoarse: Boolean = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    var accessFine: Boolean = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    isPermissionsGranted = accessCoarse&&accessFine
                    if (isPermissionsGranted){
                        Timber.i("All permissions are granted by user")
                        // Do something here...
                        activateBluetooth()
                    } else {
                        // Permission denied, do something here...
                        isPermissionsGranted = false
                    }
                } else {
                    // permissions denied
                    isPermissionsGranted = false
                }
            }
            else ->{
                Timber.d("requestCode problem, please check its value")
            }
        }
    }


    /**
     * these functions are implemented for ConnectionListener
     * onBonded() / onBondingFailed() / onBondingRequired()
     * onConnectionError() / onDeviceDisconnected() / onDeviceReady() / onServiceDiscovered()
     * **/
    override fun onBonded(device: BluetoothDevice) {
        TODO("Not yet implemented")
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        TODO("Not yet implemented")
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        TODO("Not yet implemented")
    }

    // connections
    override fun onServiceDiscovered() {
        Timber.d("onServiceDiscovered")
    }

    override fun onDeviceReady() {
        // = onConnectionSuccess
        isMbtConnected = true
        Timber.d("onDeviceReady")
        binding.simpleConnectstateDevice.background = AppCompatResources.getDrawable(this, R.color.green)
        setBatteryLevel()

        // get device info
        mbtClient.getDeviceInformation(object : DeviceInformationListener {
            override fun onDeviceInformation(deviceInformation: DeviceInformation) {
                this@QplusSimpleActivity.deviceInformation = deviceInformation
                Timber.i(deviceInformation.toString())
            }

            override fun onDeviceInformationError(error: Throwable) {
                Timber.e(error)
            }

        })
    }

    override fun onDeviceDisconnected() {
        isMbtConnected = false
        Timber.d("onDeviceDisconnected")
        binding.simpleConnectstateDevice.background = AppCompatResources.getDrawable(this, R.color.red)
        //binding.simpleIsRecording.background = AppCompatResources.getDrawable(this, R.color.red)
        setBatteryLevel()
        // disconnect reset quality checkers
        qualityButtons[0].background = AppCompatResources.getDrawable(applicationContext, R.color.black)

    }

    override fun onConnectionError(error: Throwable) {
        Timber.e("device connection error!")
    }

    // activity
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
    }

}