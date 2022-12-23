package com.mybraintech.demosdk.ui.main.frags

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.mybraintech.demosdk.R
import com.mybraintech.demosdk.databinding.FragmentConnectionBinding
import com.mybraintech.demosdk.ui.main.MainViewModel
import com.mybraintech.sdk.core.listener.ScanResultListener
import com.mybraintech.sdk.core.model.MbtDevice
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import timber.log.Timber

@SuppressLint("MissingPermission")
class ConnectionFragment : Fragment() {

    private val binding by viewBinding(FragmentConnectionBinding::bind)
    private lateinit var mainViewModel: MainViewModel
    private val antiDoubleClickHandler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()

    private val scanResultListener = object : ScanResultListener {
        override fun onMbtDevices(mbtDevices: List<MbtDevice>) {
            if (mbtDevices.isNotEmpty()) {
                addLog("onMbtDevices : size = ${mbtDevices.size} | first device name = ${mbtDevices[0].bluetoothDevice.name}")
                mainViewModel.targetDevice = mbtDevices[0]
                addLog("stopScan")
                mainViewModel.mbtClient.stopScan()
            } else {
                addLog("error : mbtDevices is empty ")
            }
        }

        override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {
            Timber.i("onOtherDevices : ${otherDevices.size}")
        }

        override fun onScanError(error: Throwable) {
            Timber.e(error)
        }

    }

    private fun addLog(line: String) {
        logBuilder.appendLine(line)
        binding.txtLog.text = logBuilder.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_connection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        initView()
    }

    private fun initView() {
        binding.btnStartScan.setOnClickListener {
            it.antiDoubleClick()
            addLog("startScan")
            mainViewModel.mbtClient.startScan(scanResultListener)
        }

        binding.btnStopScan.setOnClickListener {
            it.antiDoubleClick()
            addLog("stopScan")
            mainViewModel.mbtClient.stopScan()
        }

        TODO("btn connect")
    }

    private fun View.antiDoubleClick(delay: Long = 200) {
        this.isEnabled = false
        antiDoubleClickHandler.postDelayed(
            {
                this.isEnabled = true
            },
            delay
        )
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            ConnectionFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}