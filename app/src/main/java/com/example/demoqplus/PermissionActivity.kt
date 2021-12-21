package com.example.demoqplus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat


class PermissionActivity : AppCompatActivity() {

    val ACTIVITY_TAG:String = "PermissionActivity"

    // Declare the permissions you want to grant
    // Don't forget to add permissions into your AndroidManifest
    val REQUEST_PERMISSION_CODE:Int = 1
    var PERMISSION_ALL: Int = 1
    var PERMISSIONS = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        val btnRequestPermission = findViewById<Button>(R.id.button_storage_permission)
        btnRequestPermission.setOnClickListener{
            // Request permissions for read and write external storage
            checkPermissions()
        }

        val btnNextActivity = findViewById<Button>(R.id.button_go_qplus)
        btnNextActivity.setOnClickListener{
            // Go to next activity
            val intent = Intent(this@PermissionActivity, QplusActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode){
            REQUEST_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty()){
                    var writePermission: Boolean = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    var readPermission: Boolean = grantResults[1] == PackageManager.PERMISSION_GRANTED

                    if (writePermission && readPermission){
                        // Granted, do something here...
                        Log.d(ACTIVITY_TAG, "get permissions!")
                    } else {
                        // Denied, do something here...
                        Log.d(ACTIVITY_TAG, "permissions denied!")

                    }
                } else {
                    // Denied, do something here...
                    Log.d(ACTIVITY_TAG, "permissions denied!")
                }
            }
            else -> {
                Log.d(ACTIVITY_TAG, "requestCode problem, please check its value")
            }
        }
    }

    private fun checkPermissions() {
        // Check if the permission has been granted
        if(isAllPermissionsGranted(this, *PERMISSIONS)){
            // All permissions are granted, do something...
            Log.i(ACTIVITY_TAG, "W/R external permissions have been granted")
            Toast.makeText(this@PermissionActivity, "permissions have been already granted", Toast.LENGTH_SHORT).show()
        } else {
            // Ask user for authorization
            Log.i(ACTIVITY_TAG, "W/R external permissions are not granted, ask for user")
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        }
    }
    // Helper function to check is all of permissions granted
    private fun isAllPermissionsGranted(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

}