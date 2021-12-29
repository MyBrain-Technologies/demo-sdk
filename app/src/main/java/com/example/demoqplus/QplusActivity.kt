package com.example.demoqplus

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import config.ConnectionConfig
import core.device.model.MbtDevice
import engine.MbtClient
import engine.clientevents.BaseError
import engine.clientevents.ConnectionStateListener
import engine.clientevents.DeviceBatteryListener
import features.MbtDeviceType
import timber.log.Timber


class QplusActivity : AppCompatActivity(), ConnectionStateListener<BaseError> {

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
    // test case only
    var isClientExisted: Boolean = false
    val KEY_DEVICE_TYPE = MbtDeviceType.MELOMIND_Q_PLUS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus)

        if (BuildConfig.DEBUG){
            Timber.plant(Timber.DebugTree())
        }

        checkPermission()

        val btnFinish = findViewById<Button>(R.id.button_finish)
        btnFinish.setOnClickListener{
            mbtClient.disconnectBluetooth()
            finish()
        }

        val switchDevice = findViewById<SwitchCompat>(R.id.switch_connect_device)
        switchDevice.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (PERMISSION_GRANTED) {
                    connectMBTdevice()
                } else {
                    switchDevice.isChecked = false
                    checkPermission()
                    Toast.makeText(this, "Permissions are denied", Toast.LENGTH_SHORT).show()
                }
            } else {
                mbtClient.disconnectBluetooth()
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
        readBattery()
    }

    override fun onDeviceDisconnected(p0: MbtDevice?) {
        //device is disconnected
        Timber.d("Device is disconnected")
    }

}