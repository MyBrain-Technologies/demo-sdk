package com.example.demoqplus

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.widget.ImageViewCompat
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
                    streamStateChange.setText("Stop Stream")
                    onStartStreamButtonClicked()
                    IS_STREAMING = true
                } else {
                    Toast.makeText(this, "device not connected", Toast.LENGTH_SHORT).show()
                }
            } else {
                // stop
                mbtClient.stopStream()
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
        updateQualityButtons(mbteegPackets.qualities)
    }

    override fun onNewStreamState(streamState: StreamState) {}

    override fun onSaturationStateChanged(p0: SaturationEvent?) {
        //TODO("Not yet implemented")
    }

    override fun onNewDCOffsetMeasured(p0: DCOffsetEvent?) {
        //
    }

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
                    qualityButtons[i].background =
                        AppCompatResources.getDrawable(this, R.color.green_signal)
                } else {
                    qualityButtons[i].background =
                        AppCompatResources.getDrawable(this, R.color.gray)
                }
            }
            /*
            if (greenCount == 4) {
                if (haProgress < qualityWindowLength) {
                    haProgress++
                }
            } else {
                haProgress = 0
            }*/
            //renderHeadsetAdjustmentProgress(haProgress)

            if (isSignalGoodEnough()) {
                Timber.d("quality is good")
            } else {
                Timber.d("quality is bad")
            }
        } else {
            Timber.e("qualities size is not equal $channelNb!")
        }
    }

    private fun isSignalGoodEnough(): Boolean {
//        return haProgress == qualityWindowLength //rule 1
        return channelFlags.count { it } == channelNb //rule 2 : all channels are passed to good at least one (separately)
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

    /*
    private fun renderHeadsetAdjustmentProgress(level: Int) {
        if (level > 6 || level < 0) {
            Timber.e("invalid HA level")
            return
        }
        for (i in 1..6) {
            //1 to 6 is progress view positions, 0 is text "Low" position, 7 is text "High" position
            val view = binding.layoutQualityProgress.getChildAt(i)
            view?.safeCast<ImageView> {
                val colorFilter = if (level >= i) {
                    resources.getColor(R.color.colorAccent)
                } else {
                    resources.getColor(R.color.colorAccentInactive)
                }
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(colorFilter))
            }
        }
    }*/

}