package com.example.demoqplus

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import com.example.demoqplus.databinding.ActivityQplusBinding
import com.github.mikephil.charting.charts.LineChart
import config.ConnectionConfig
import config.RecordConfig
import config.StreamConfig
import core.bluetooth.StreamState
import core.device.event.DCOffsetEvent
import core.device.event.SaturationEvent
import core.device.event.indus5.RecordingSavedListener
import core.device.model.MbtDevice
import core.eeg.storage.MbtEEGPacket
import engine.MbtClient
import engine.clientevents.*
import features.MbtDeviceType
import timber.log.Timber
import utils.MatrixUtils
import java.util.*
import kotlin.collections.ArrayList


class QplusActivity : AppCompatActivity(), ConnectionStateListener<BaseError>,
    EegListener<BaseError>, DeviceStatusListener<BaseError> {

    // Declare bluetooth permissions
    var PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)

    val REQUEST_PERMISSION_CODE:Int = 1
    val PERMISSION_ALL: Int = 1
    var PERMISSION_GRANTED: Boolean = false

    // bluetooth
    val REQUEST_ENABLE_BT: Int = 1
    lateinit var mbtClient: MbtClient

    // quality check
    private lateinit var binding: ActivityQplusBinding
    var lastQualities = ArrayList<LinkedList<Float>>()
    var qualityWindowLength = 6
    var channelNb = 4
    var qualityButtons = ArrayList<Button>()
    var channelFlags = ArrayList<Boolean>().apply {
        for (i in 1..channelNb) {
            this.add(false)
        }
    }
    var recordingSavedListener: RecordingSavedListener = object : RecordingSavedListener {
        override fun onRecordingSaved(recordConfig: RecordConfig) {
            Timber.d("onRecordingSaved")
        }
    }

    // test case only
    var isClientExisted: Boolean = false
    var IS_STREAMING: Boolean = false
    var IS_DEVICE_CONNECTED:Boolean = false
    val KEY_DEVICE_TYPE = MbtDeviceType.MELOMIND_Q_PLUS
    val KEY_P300: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus)

        binding = ActivityQplusBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //var mLineChart = findViewById<LineChart>(R.id.chart)
        // add buttons to list
        qualityButtons.add(binding.P3)
        qualityButtons.add(binding.P4)
        qualityButtons.add(binding.AF3)
        qualityButtons.add(binding.AF4)
        for (i in 1..channelNb) {
            lastQualities.add(LinkedList())
        }

        var streamStateChange = findViewById<Button>(R.id.button_start_stop_stream)
        streamStateChange.setOnClickListener{
            if (!IS_STREAMING){
                // start stream if it isn't streamin
                if (IS_DEVICE_CONNECTED) {
                    streamStateChange.setBackgroundColor(getResources().getColor(R.color.red))
                    streamStateChange.setText("Stop Stream")
                    onStartStreamButtonClicked()
                    IS_STREAMING = true
                } else {
                    Toast.makeText(this, "device not connected", Toast.LENGTH_SHORT).show()
                }
            } else {
                // stop
                mbtClient.stopStream()
                streamStateChange.setBackgroundColor(getResources().getColor(R.color.white))
                streamStateChange.setText("Start Stream")
                IS_STREAMING = false
            }

        }


        checkPermission()

        val btnFinish = findViewById<Button>(R.id.button_finish)
        btnFinish.setOnClickListener{
            if (isClientExisted && IS_DEVICE_CONNECTED) {
                mbtClient.disconnectBluetooth()
            }
            finish()
        }

        val switchDevice = findViewById<SwitchCompat>(R.id.switch_connect_device)
        switchDevice.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (PERMISSION_GRANTED) {
                    connectMBTdevice()
                    streamStateChange.isEnabled = true
                } else {
                    switchDevice.isChecked = false
                    checkPermission()
                    Toast.makeText(this, "Permissions are denied", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (IS_DEVICE_CONNECTED){
                    mbtClient.disconnectBluetooth()
                    streamStateChange.setText("Start Stream")
                }
            }
        }
    }

    private fun isAllPermissionsGranted(context: Context, vararg permissions: String): Boolean = permissions.all{
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            REQUEST_PERMISSION_CODE ->{
                if(grantResults.isNotEmpty()){
                    var accessCoarse: Boolean = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    var accessFine: Boolean = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    PERMISSION_GRANTED = accessCoarse&&accessFine
                    if (PERMISSION_GRANTED){
                        Timber.i("All permissions are granted by user")
                        // Do something here...
                    } else {
                        // Permission denied, do something here...
                        PERMISSION_GRANTED = false
                    }
                } else {
                    // permissions denied
                    PERMISSION_GRANTED = false
                }
            }
            else ->{
                Timber.d("requestCode problem, please check its value")
            }
        }
    }

    private fun checkPermission(){
        PERMISSION_GRANTED = isAllPermissionsGranted(this, *PERMISSIONS)
        if (!PERMISSION_GRANTED){
            // Not granted
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
    }

    private fun connectMBTdevice(){
        // init sdk
        if (!isClientExisted){
            mbtClient = MbtClient.init(this)
            isClientExisted = true
        } else {
            mbtClient = MbtClient.getClientInstance()
        }

        val connConfig = ConnectionConfig.Builder(this)
            .createForDevice(KEY_DEVICE_TYPE)

        // enable bluetooth
        val bluetoothAdp = BluetoothAdapter.getDefaultAdapter()
        val isEnable = bluetoothAdp.isEnabled
        if (!isEnable){
            Timber.i("Bluetooth isn't enable")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        Timber.d("execute connectBluetooth...")
        mbtClient.connectBluetooth(connConfig)

    }

    private fun readBattery() {
        val batteryListener = object : DeviceBatteryListener<BaseError> {
            override fun onError(error: BaseError?, additionalInfo: String?) {
                Timber.e("batteryListener onError ${error?.message}")
            }

            override fun onBatteryLevelReceived(level: String) {
                Timber.i("onBatteryLevelReceived: $level")
            }
        }
        mbtClient.readBattery(batteryListener)
    }

    override fun onError(error: BaseError?, additionalInfo: String?) {
    }

    override fun onDeviceConnected(p0: MbtDevice?) {
        // device is connected
        Timber.d("Device is connected")
        IS_DEVICE_CONNECTED = true
        readBattery()
    }

    override fun onDeviceDisconnected(p0: MbtDevice?) {
        //device is disconnected
        Timber.d("Device is disconnected")
        mbtClient.stopStream()
        IS_STREAMING = false
        IS_DEVICE_CONNECTED = false

    }


    private fun onStartStreamButtonClicked(){
        Timber.d("onStartStreamButtonClicked")
        startStreamWithTrigger(KEY_P300, false)

    }

    private fun startStreamWithTrigger(isTriggerEnabled: Boolean, ppgEnabled: Boolean) {

        val streamConfigBuilder = StreamConfig.Builder(this@QplusActivity)
            .useQualities()
            .setDeviceStatusListener(this@QplusActivity)
        val streamConfig = streamConfigBuilder.createForDevice(MbtDeviceType.MELOMIND_Q_PLUS)
        streamConfig.recordingSavedListener = recordingSavedListener
        streamConfig.isImsEnabled = false
        Timber.d("ppgEnabled = $ppgEnabled")
        streamConfig.isPpgEnabled = ppgEnabled
        streamConfig.isTriggerEnabled = isTriggerEnabled
        mbtClient.startStream(streamConfig)
        Timber.d("startStreamWithTrigger")
    }



    override fun onNewPackets(mbteegPackets: MbtEEGPacket) {
        Timber.d("onNewPackets")
    }

    override fun onNewStreamState(streamState: StreamState) {}

    override fun onSaturationStateChanged(p0: SaturationEvent?) {
        //TODO("Not yet implemented")
    }

    override fun onNewDCOffsetMeasured(p0: DCOffsetEvent?) {
        //
    }

}