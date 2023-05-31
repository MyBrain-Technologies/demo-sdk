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
import com.mybraintech.sdk.core.ResearchStudy
import com.mybraintech.sdk.core.TestBench
import com.mybraintech.sdk.core.listener.*
import com.mybraintech.sdk.core.model.DeviceSystemStatus
import com.mybraintech.sdk.core.model.EnumEEGFilterConfig
import com.mybraintech.sdk.core.model.Indus5SensorStatus
import com.mybraintech.sdk.core.model.MbtDevice
import com.mybraintech.sdk.util.toJson
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import timber.log.Timber

@OptIn(TestBench::class, ResearchStudy::class)
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
                mainViewModel.getMbtClient().stopScan()
            } else {
                addLog("error : mbtDevices is empty ")
            }
        }

        override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {
            Timber.i("onOtherDevices : ${otherDevices.size}")
        }

        override fun onScanError(error: Throwable) {
            Timber.e(error)
            addLog("onScanError" + error.message)
        }

    }

    private val connectionListener = object : ConnectionListener {
        override fun onServiceDiscovered() {
            Timber.i("onServiceDiscovered")
        }

        override fun onBondingRequired(device: BluetoothDevice) {
            Timber.i("onBondingRequired : ${device.name}")
        }

        override fun onBonded(device: BluetoothDevice) {
            Timber.i("onBonded : ${device.name}")
        }

        override fun onBondingFailed(device: BluetoothDevice) {
            Timber.i("onBondingFailed : ${device.name}")
        }

        override fun onDeviceReady() {
            Timber.i("onDeviceReady")
            addLog("onDeviceReady")
            mainViewModel.getMbtClient().getBatteryLevel(batteryLevelListener)
        }

        override fun onDeviceDisconnected() {
            Timber.i("onDeviceDisconnected")
            addLog("onDeviceDisconnected")
        }

        override fun onConnectionError(error: Throwable) {
            Timber.e(error)
            addLog("onConnectionError : ${error.message}")
        }
    }

    private val batteryLevelListener = object : BatteryLevelListener {
        override fun onBatteryLevel(float: Float) {
            Timber.i("onBatteryLevel : $float")
            addLog("onBatteryLevel : $float")
        }

        override fun onBatteryLevelError(error: Throwable) {
            Timber.e(error)
        }

    }

    private val sensorStatusListener = object : SensorStatusListener {
        override fun onSensorStatusFetched(sensorStatus: Indus5SensorStatus) {
            Timber.i("onSensorStatusFetched : ${sensorStatus.toJson()}")
            addLog("onSensorStatusFetched : ${sensorStatus.toJson()}")
        }

        override fun onSensorStatusError(error: String) {
            Timber.e(error)
        }
    }

    private val deviceSystemStatusListener = object : DeviceSystemStatusListener {
        override fun onDeviceSystemStatusFetched(deviceSystemStatus: DeviceSystemStatus) {
            Timber.i("onDeviceSystemStatusFetched : ${deviceSystemStatus.toJson()}")
            addLog("onDeviceSystemStatusFetched : ${deviceSystemStatus.toJson()}")
        }

        override fun onDeviceSystemStatusError(error: String) {
            Timber.e(error)
            addLog(error)
        }

    }

    companion object {
        @JvmStatic
        fun newInstance() =
            ConnectionFragment().apply {
                arguments = Bundle().apply {
                }
            }
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

    private fun addLog(line: String) {
        logBuilder.appendLine(line)
        if (!isRemoving && !isDetached) {
            binding.txtLog.text = logBuilder.toString()
        }
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
        binding.btnIsConnected.setOnClickListener {
            it.antiDoubleClick()
            val connectionStatus = mainViewModel.getMbtClient().getBleConnectionStatus()
            addLog("connectionStatus = ${connectionStatus.isConnectionEstablished}")
        }

        binding.btnStartScan.setOnClickListener {
            it.antiDoubleClick()
            addLog("startScan")
            mainViewModel.getMbtClient().startScan(scanResultListener)
        }

        binding.btnStopScan.setOnClickListener {
            it.antiDoubleClick()
            addLog("stopScan")
            mainViewModel.getMbtClient().stopScan()
        }

        binding.btnConnect.setOnClickListener {
            it.antiDoubleClick()
            if (mainViewModel.targetDevice == null) {
                addLog("please scan first")
            } else {
                addLog("connect...")
                mainViewModel.getMbtClient()
                    .connect(mainViewModel.targetDevice!!, connectionListener)
            }
        }

        binding.btnDisconnect.setOnClickListener {
            it.antiDoubleClick()
            mainViewModel.getMbtClient().disconnect()
        }

        binding.btnHeadsetStatus.setOnClickListener {
            it.antiDoubleClick()
            if (mainViewModel.targetDevice == null) {
                addLog("please connect first")
            } else {
                addLog("get headset status...")
                mainViewModel.getMbtClient().getDeviceSystemStatus(deviceSystemStatusListener)
            }
        }

        binding.btnStreamingStatus.setOnClickListener {
            it.antiDoubleClick()
            if (mainViewModel.targetDevice == null) {
                addLog("please connect first")
            } else {
                addLog("get streaming state...")
                mainViewModel.getMbtClient().getStreamingState(sensorStatusListener)
            }
        }

        initFilterModeListener()

        binding.btnGoIms.setOnClickListener {
            goIMS()
        }

    }

    private fun initFilterModeListener() {
        binding.btnFilterMode.setOnClickListener {
            it.antiDoubleClick()
            if (mainViewModel.targetDevice == null) {
                addLog("please connect first")
            } else {
                addLog("get filter mode...")
                mainViewModel.getMbtClient().getEEGFilterConfig(object : EEGFilterConfigListener {
                    override fun onEEGFilterConfig(config: EnumEEGFilterConfig) {
                        Timber.i("onEEGFilterConfig : ${config.name}")
                        addLog("onEEGFilterConfig : ${config.name}")
                    }

                    override fun onEEGFilterConfigError(errorMsg: String) {
                        Timber.e(errorMsg)
                    }

                })
            }
        }
    }

    private fun goIMS() {
        if (mainViewModel.getMbtClient().getBleConnectionStatus().isConnectionEstablished) {
            addLog("switching to IMS...")
            requireActivity().supportFragmentManager
                .beginTransaction()
                .replace(R.id.container, AccelerometerFragment.newInstance(), "tag_ims")
                .addToBackStack("name_ims")
                .commit()
        } else {
            addLog("please connect first")
        }
    }

}