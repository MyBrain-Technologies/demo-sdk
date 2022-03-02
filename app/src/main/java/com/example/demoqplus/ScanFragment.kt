package com.example.demoqplus

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.example.demoqplus.databinding.ScanFragmentLayoutBinding
import com.example.demoqplus.simpleVersion.BluetoothStateReceiver
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.listener.ScanResultListener
import com.mybraintech.sdk.core.model.EnumMBTDevice
import com.mybraintech.sdk.core.model.MbtDevice
import timber.log.Timber


class ScanFragment: Fragment(R.layout.scan_fragment_layout) {

    // bluetooth state
    // mbt device objets
    private val model: QPlusViewModel by activityViewModels()

    private lateinit var mbtClient: MbtClient
    var mbtDevice: MbtDevice? = null

    private var recyclerViewAdapter: DeviceRecyclerViewAdapter = DeviceRecyclerViewAdapter()
    private var recyclerView: RecyclerView? = null

    private lateinit var scan: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?): View {
        val scanLayout: View = inflater.inflate(R.layout.scan_fragment_layout, container, false)

        // create mbt client
        mbtClient = model.getClient()

        initUI(scanLayout)
        return scanLayout
    }


    private fun initUI(view: View){
        recyclerView = view.findViewById(R.id.fragment_devices)
        recyclerView!!.adapter = recyclerViewAdapter


        scan = view.findViewById(R.id.scan_fragment)
        scan.setOnClickListener {
            if (model.isScanning()){
                model.stopScan()
                Repository.instance().removeDeviceSource(model.getDevicesFromHelper())
            } else {
                model.scanDevices()
                Repository.instance().addDeviceSource(model.getDevicesFromHelper())
                model.getDevices().observe(viewLifecycleOwner, {
                    recyclerViewAdapter.updateDeviceList(it)
                })
            }
        }
    }

}