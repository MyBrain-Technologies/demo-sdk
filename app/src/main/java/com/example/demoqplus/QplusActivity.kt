package com.example.demoqplus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.widget.ButtonBarLayout
import androidx.core.app.ActivityCompat
import engine.MbtClient
import timber.log.Timber

class QplusActivity : AppCompatActivity() {




    // Declare bluetooth permissions
    var PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        // not sure whether a bluetooth connect permission is required below Android S
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val REQUEST_PERMISSION_CODE:Int = 1
    val PERMISSION_ALL: Int = 1


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus)

        if (BuildConfig.DEBUG){
            Timber.plant(Timber.DebugTree())
        }

        val btnConnectBluetooth = findViewById<Button>(R.id.button_connect_bluetooth)
        btnConnectBluetooth.setOnClickListener{
            checkBluetoothPermission()
        }

        //MbtClient.init(this)
        //TODO("implement sdk functions")
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
                    var bluetoothConnectPermission: Boolean = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    var accessCoarse: Boolean = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    var accessFine: Boolean = grantResults[2] == PackageManager.PERMISSION_GRANTED
                    if (bluetoothConnectPermission&&accessCoarse&&accessFine){
                        Timber.i("All permissions are granted by user")
                        // Do something here...

                    } else {
                        Timber.i("some permissions are denied by user")
                        // Permission denied, do something here...

                    }

                } else {
                    Timber.i("grantResults is empty")
                    // permissions denied

                }
            }
            else ->{
                Timber.d("requestCode problem, please check its value")
            }
        }



    }

    private fun checkBluetoothPermission(){
        if (isAllPermissionsGranted(this, *PERMISSIONS)){
            // all permissions are granted
            // do something here
            Timber.i("Bluetooth permissions are granted")
        } else {
            // Not granted
            Timber.i("Bluetooth permissions are not granted, request")
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
    }

}