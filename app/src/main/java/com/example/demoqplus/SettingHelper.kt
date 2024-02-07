package com.example.demoqplus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.location.LocationManagerCompat
import timber.log.Timber

class SettingHelper(private val context: Context) {

    fun checkPermission(permissionName: String): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            val granted =
                ContextCompat.checkSelfPermission(context, permissionName)
            granted == PackageManager.PERMISSION_GRANTED
        } else {
            val granted =
                PermissionChecker.checkSelfPermission(context, permissionName)
            granted == PermissionChecker.PERMISSION_GRANTED
        }
    }

    fun checkLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) { //Android 10+11 ~ API 29+30
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else { // up to Android 9 ~ API 28
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        return checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun isLocationEnabled(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            LocationManagerCompat.isLocationEnabled(manager)
        } catch (e: Exception) {
            Timber.e(e)
            false
        }
    }

    fun checkBluetoothPermission() : Boolean {
        return checkPermission(Manifest.permission.)
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 31) { //Android 12 ~ API 31
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else if (Build.VERSION.SDK_INT >= 29) { //Android 10+11 ~ API 29+30
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else { // up to Android 9 ~ API 28
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }
}