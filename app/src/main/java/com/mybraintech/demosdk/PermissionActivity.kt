package com.mybraintech.demosdk

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.mybraintech.sdk.core.model.EnumMBTDevice
import timber.log.Timber


class PermissionActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PERMISSION_CODE: Int = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        initView()
    }

    private fun initView() {
        findViewById<Button>(R.id.button_storage_permission).setOnClickListener {
            requestPermissions()
        }

        findViewById<Button>(R.id.button_go_q_plus).setOnClickListener {
            goAcquisition(EnumMBTDevice.Q_PLUS)
        }

        findViewById<Button>(R.id.button_go_melomind).setOnClickListener {
            goAcquisition(EnumMBTDevice.MELOMIND)
        }

        findViewById<Button>(R.id.button_go_hyperion).setOnClickListener {
            goAcquisition(EnumMBTDevice.HYPERION)
        }
    }

    private fun goAcquisition(mbtDevice: EnumMBTDevice) {
        val bluetoothManager =
            applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val isBluetoothEnabled = bluetoothAdapter?.isEnabled

        if (isBluetoothEnabled == true) {
            val intent = Intent(applicationContext, AcquisitionActivity::class.java)
            intent.putExtra(AcquisitionActivity.KEY_DEVICE_TYPE, mbtDevice.toString())
            startActivity(intent)
        } else {
            alertDialog("Please enable Bluetooth !")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Timber.i("Requested Permissions = $permissions")
        Timber.i("Grant Results = $grantResults")
        var allGranted = true
        for (i in permissions.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                Timber.i("Permission ${permissions[i]} is granted.")
            } else {
                Timber.w("Permission ${permissions[i]} is NOT granted.")
                allGranted = false
            }
        }
        if (!allGranted) {
            alertDialog("Please grant all permissions !")
        }
    }

    private fun alertDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNeutralButton("OK", null)
            .create()
            .show()
    }

    private fun requestPermissions() {
        if (isAllPermissionsGranted(this, getRequiredPermissions())) {
            val text = "Permissions are granted"
            Timber.i(text)
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT)
                .show()
        } else {
            Timber.i("Request permissions...")
            ActivityCompat.requestPermissions(
                this,
                getRequiredPermissions(),
                REQUEST_PERMISSION_CODE
            )
        }
    }

    private fun isAllPermissionsGranted(
        context: Context,
        requiredPermissions: Array<String>
    ): Boolean {
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getBluetoothPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH,
                )
            }
        }
    }

    private fun getLocationPermission(): String {
        val locationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            Manifest.permission.ACCESS_COARSE_LOCATION
        }
        return locationPermission
    }

    private fun getRequiredPermissions(): Array<String> {
        return getBluetoothPermissions().plus(getLocationPermission())
    }
}