package com.example.demoqplus

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.demoqplus.databinding.ActivityQplusBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
//import com.mybraintech.sdk.MbtClient
//import com.mybraintech.sdk.MbtClientManager
//import com.mybraintech.sdk.core.model.EnumMBTDevice
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList
import androidx.fragment.app.FragmentManager
import com.example.demoqplus.simpleVersion.BluetoothStateReceiver
import com.mybraintech.sdk.core.model.EnumMBTDevice


class QplusActivity : FragmentActivity() {

    lateinit var binding: ActivityQplusBinding

    private val model: QPlusViewModel by viewModels()

    // Declare  permissions
    var PERMISSION_ALL: Int = 1
    var PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE)



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus)

        // initial viewModel
        model.init(this, EnumMBTDevice.Q_PLUS)
        if (model.iniScanHelper()){
            Timber.i("ScanHelper lunch successfully...")
        } else {
            Timber.i("You have to initialise your mbtClient, it's null now...")
        }
        model.setBluetoothState(BluetoothAdapter.getDefaultAdapter().isEnabled)

        requirePermission()

        if (savedInstanceState == null) {
            val fragmentManager: FragmentManager = supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(R.id.fragment_container_view, ScanFragment::class.java, null)
                .setReorderingAllowed(true)
                .addToBackStack("name") // name can be null
                .commit()
        }

    }

    // Require permissions
    private fun requirePermission(){
        if (!isAllPermissionsGranted(this, *PERMISSIONS)){
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
    }

    private fun isAllPermissionsGranted(context: Context, vararg permissions: String): Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}