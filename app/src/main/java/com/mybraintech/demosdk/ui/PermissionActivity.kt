package com.mybraintech.demosdk.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.mybraintech.demosdk.R
import com.mybraintech.demosdk.ui.main.MainActivity
import timber.log.Timber


class PermissionActivity : AppCompatActivity() {

    // Declare the permissions you want to grant
    // Don't forget to add permissions into your AndroidManifest
    val REQUEST_PERMISSION_CODE: Int = 1
    var PERMISSION_ALL: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        initView()
    }

    private fun initView() {
        val btnRequestPermission = findViewById<Button>(R.id.button_storage_permission)
        btnRequestPermission.setOnClickListener {
            // Request permissions for read and write external storage
            checkPermissions()
        }

        findViewById<Button>(R.id.button_go_q_plus).setOnClickListener {
            // Go to next activity
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.button_go_melomind).setOnClickListener {
            Toast.makeText(applicationContext, "under construction", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty()) {
                    val writePermission: Boolean =
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                    val readPermission: Boolean =
                        grantResults[1] == PackageManager.PERMISSION_GRANTED

                    if (writePermission && readPermission) {
                        // Granted, do something here...
                        Timber.d("get permissions!")
                    } else {
                        // Denied, do something here...
                        Timber.d("permissions denied!")

                    }
                } else {
                    // Denied, do something here...
                    Timber.d("permissions denied!")
                }
            }
            else -> {
                Timber.d("requestCode problem, please check its value")
            }
        }
    }

    private fun checkPermissions() {
        // Check if the permission has been granted
        if (isAllPermissionsGranted(this, getRequiredPermissions())) {
            // All permissions are granted, do something...
            Timber.i("W/R external permissions have been granted")
            Toast.makeText(
                this@PermissionActivity,
                "permissions have been already granted",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Ask user for authorization
            Timber.i("W/R external permissions are not granted, ask for user")
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_ALL)
        }
    }

    // Helper function to check is all of permissions granted
    private fun isAllPermissionsGranted(
        context: Context,
        requiredPermissions: Array<String>
    ): Boolean =
        requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH,
                )
            }
            else -> {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH,
                )
            }
        }
    }

}