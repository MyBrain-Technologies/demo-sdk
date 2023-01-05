package com.mybraintech.demosdk.ui.main.frags

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import com.mybraintech.demosdk.R
import com.mybraintech.demosdk.databinding.FragmentAccelerometerBinding
import com.mybraintech.demosdk.ui.main.MainViewModel
import com.mybraintech.sdk.core.LabStreamingLayer
import com.mybraintech.sdk.core.TestBench
import com.mybraintech.sdk.core.listener.*
import com.mybraintech.sdk.core.model.*
import com.zhuinden.fragmentviewbindingdelegatekt.viewBinding
import timber.log.Timber
import java.io.File

@OptIn(TestBench::class, LabStreamingLayer::class)
class AccelerometerFragment : Fragment() {

    private val binding by viewBinding(FragmentAccelerometerBinding::bind)
    private lateinit var mainViewModel: MainViewModel
    private val antiDoubleClickHandler = Handler(Looper.getMainLooper())
    private val logBuilder = StringBuilder()

    private val recordingListener by lazy {
        object : RecordingListener {
            override fun onRecordingSaved(outputFile: File) {
                val msg = "onRecordingSaved : file = ${outputFile.canonicalPath}"
                Timber.i(msg)
                addLog(msg)
            }

            override fun onRecordingError(error: Throwable) {
                Timber.e(error)
                addLog(error.message)
            }

        }
    }

    private val eegListener = object : EEGListener {
        override fun onEEGStatusChange(isEnabled: Boolean) {
            val msg = "onEEGStatusChange : isEnabled = $isEnabled"
            Timber.i(msg)
            addLog(msg)
        }

        override fun onEegPacket(mbtEEGPacket: MbtEEGPacket) {
            Timber.i("onEegPacket : ${System.currentTimeMillis() / 1000 % 1000}")
        }

        override fun onEegError(error: Throwable) {
            Timber.e(error)
            addLog(error.message)
        }

    }

    private val realtimeListener = object : EEGRealtimeListener {
        override fun onEEGFrame(pack: EEGSignalPack) {
//            Timber.i("onEEGFrame : index = ${pack.index} | size = [${pack.eegSignals.size}x${pack.eegSignals[0].size}]")
        }

    }

    private val accelerometerListener = object : AccelerometerListener {
        override fun onAccelerometerStatusChange(isEnabled: Boolean) {
            Timber.i("onAccelerometerStatusChange : isEnabled = $isEnabled")
            addLog("onAccelerometerStatusChange : isEnabled = $isEnabled")
        }

        override fun onAccelerometerPacket(accelerometerPacket: AccelerometerPacket) {
            Timber.d("onAccelerometerPacket : ${System.currentTimeMillis() / 1000 % 1000}")
        }

        override fun onAccelerometerError(error: Throwable) {
            Timber.e(error)
        }
    }

    private val sensorStatusListener = object : SensorStatusListener {
        override fun onSensorStatusFetched(sensorStatus: Indus5SensorStatus) {
            val msg =
                "onSensorStatusFetched : isIMSStarted = ${sensorStatus.isIMSStarted} | isEEGStarted = ${sensorStatus.isEEGStarted}"
            Timber.i(msg)
            addLog(msg)
        }

        override fun onSensorStatusError(error: String) {
            Timber.e(error)
            addLog(error)
        }
    }

    private val accelerometerConfigListener = object : AccelerometerConfigListener {
        override fun onAccelerometerConfigFetched(config: AccelerometerConfig) {
            with("onAccelerometerConfigFetched : sampleRate = ${config.sampleRate.sampleRate}") {
                Timber.i(this)
                addLog(this)
            }
        }

        override fun onAccelerometerConfigError(error: String) {
            Timber.e(error)
            addLog(error)
        }
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            AccelerometerFragment().apply {
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

    private fun addLog(line: String?) {
        requireActivity().runOnUiThread {
            logBuilder.appendLine(line)
            binding.txtLog.text = logBuilder.toString()
        }
    }

    private fun clearLog() {
        requireActivity().runOnUiThread {
            logBuilder.clear()
            binding.txtLog.text = null
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
        return inflater.inflate(R.layout.fragment_accelerometer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mainViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        mainViewModel.getMbtClient().setEEGListener(eegListener)
        mainViewModel.getMbtClient().setEEGRealtimeListener(realtimeListener)
        mainViewModel.getMbtClient().setAccelerometerListener(accelerometerListener)

        initView()

        mainViewModel.getMbtClient().getDeviceInformation(object : DeviceInformationListener {
            override fun onDeviceInformation(deviceInformation: DeviceInformation) {
                mainViewModel.deviceInformation = deviceInformation
            }

            override fun onDeviceInformationError(error: Throwable) {
                Timber.e(error)
                addLog(error.message)
            }

        })
    }

    private fun initView() {
        binding.btnIsImsStreaming.setOnClickListener {
            it.antiDoubleClick()
            mainViewModel.getMbtClient().getStreamingState(sensorStatusListener)
            addLog("getStreamingState...")
        }

        binding.btnStopStreaming.setOnClickListener {
            it.antiDoubleClick()
            addLog("btnStopStreaming...")
            mainViewModel.getMbtClient().stopStreaming()
        }

        binding.btnGetImsConfig.setOnClickListener {
            it.antiDoubleClick()
            addLog("btnGetImsConfig...")
            mainViewModel.getMbtClient().getAccelerometerConfig(accelerometerConfigListener)
        }

        binding.btn100Hz.setOnClickListener {
            it.antiDoubleClick()
            addLog("btn100Hz...")
            val params = StreamingParams.Builder()
                .setEEG(false)
                .setAccelerometer(true)
                .setAccelerometerSampleRate(EnumAccelerometerSampleRate.F_100_HZ)
                .build()
            mainViewModel.getMbtClient().startStreaming(params)
        }

        binding.btn50Hz.setOnClickListener {
            it.antiDoubleClick()
            addLog("btn50Hz...")
            val params = StreamingParams.Builder()
                .setEEG(false)
                .setAccelerometer(true)
                .setAccelerometerSampleRate(EnumAccelerometerSampleRate.F_50_HZ)
                .build()
            mainViewModel.getMbtClient().startStreaming(params)
        }

        binding.btnEegIms100Hz.setOnClickListener {
            it.antiDoubleClick()
            addLog("btnEegIms100Hz...")
            val params = StreamingParams.Builder()
                .setEEG(true)
                .setAccelerometer(true)
                .setAccelerometerSampleRate(EnumAccelerometerSampleRate.F_100_HZ)
                .build()
            mainViewModel.getMbtClient().startStreaming(params)
        }

        binding.btnEegIms50Hz.setOnClickListener {
            it.antiDoubleClick()
            addLog("btnEegIms50Hz...")
            val params = StreamingParams.Builder()
                .setEEG(true)
                .setAccelerometer(true)
                .setAccelerometerSampleRate(EnumAccelerometerSampleRate.F_50_HZ)
                .build()
            mainViewModel.getMbtClient().startStreaming(params)
        }

        binding.btnStartRecording.setOnClickListener {
            onStartRecClicked(it)
        }

        binding.btnStopRecording.setOnClickListener {
            it.antiDoubleClick()
            addLog("btnStopRecording...")
            mainViewModel.getMbtClient().stopRecording()
        }

        binding.btnClearLog.setOnClickListener {
            it.antiDoubleClick()
            clearLog()
        }
    }

    private fun onStartRecClicked(btn: View) {
        btn.antiDoubleClick()
        addLog("btnStartRecording...")
        val name = "recording-" + (System.currentTimeMillis() / 1000).toString()
        val recordingOption = RecordingOption(
            File(requireActivity().cacheDir, "$name.json"),
            KwakContext(),
            mainViewModel.deviceInformation,
            name
        )
        mainViewModel.getMbtClient().startRecording(
            recordingOption,
            recordingListener
        )
        var duration = try {
            binding.edtRecDuration.text.toString().toLong()
        } catch (e: Exception) {
            Timber.e(e)
            30000
        }
        if (duration < 0) {
            duration = 30000
        }
        Handler(Looper.getMainLooper()).postDelayed(
            {
                Timber.i("duration = $duration")
                binding.btnStopRecording.callOnClick()
            },
            duration
        )
    }

}