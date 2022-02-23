package com.example.demoqplus.simpleVersion

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.example.demoqplus.R
import com.example.demoqplus.databinding.ActivityQplusSimpleBinding
import timber.log.Timber
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.widget.Button
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.listener.*
import com.mybraintech.sdk.core.model.*
import com.mybraintech.sdk.util.toJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class QplusSimpleActivity : AppCompatActivity(), ConnectionListener {

    private lateinit var binding: ActivityQplusSimpleBinding

    // Declare bluetooth permissions
    private var isPermissionsGranted: Boolean = false
    var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION)

    // bluetooth state
    private var isScanning: Boolean = false
    private lateinit var bluetoothStateReceiver: BluetoothStateReceiver

    // mbt device objets
    private lateinit var mbtClient: MbtClient
    var mbtDevice: MbtDevice? = null
    var deviceInformation: DeviceInformation? = null
    private var isMbtConnected: Boolean = false

    //EEG signals
    var eegCount = 0

    // quality check
    var lastQualities = ArrayList<LinkedList<Float>>()
    var qualityWindowLength = 6
    var channelNb = 4
    var qualityButtons = ArrayList<Button>()
    var channelFlags = ArrayList<Boolean>().apply {
        for (i in 1..channelNb) {
            this.add(false)
        }
    }

    // line  chart
    private val TWO_SECONDS = 500f
    private var counter: Int = 0
    private var bufferedChartData = ArrayList<Float>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus_simple)

        // Initialize viewbinding
        binding = ActivityQplusSimpleBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // initialize some parameters
        isPermissionsGranted = isAllPermissionsGranted(this, *PERMISSIONS)
        bluetoothStateReceiver = BluetoothStateReceiver(BluetoothAdapter.getDefaultAdapter().isEnabled)
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        // create mbt client
        mbtClient = MbtClientManager.getMbtClient(applicationContext, EnumMBTDevice.Q_PLUS)

        checkConnection()
        setBatteryLevel()
        initUI()
        initChart()
    }

    private fun initUI(){
        // functions for scanning devices
        binding.simpleScanDevices.setOnClickListener {
            when {
                isScanning -> {
                    mbtClient.stopScan()
                    isScanning = false
                }
                bluetoothStateReceiver.isBluetoothOn -> {
                    Timber.i("is ready for scanning devices...")
                    isScanning = true

                    mbtClient.startScan(object : ScanResultListener {
                        override fun onMbtDevices(mbtDevices: List<MbtDevice>) {
                            Timber.i("onMbtDevices size = ${mbtDevices.size}")
                            for (device in mbtDevices) {
                                Timber.i("device ${device.bluetoothDevice.name}")
                            }
                            mbtClient.stopScan()
                            mbtDevice = mbtDevices[0]

                            isScanning = false
                            //addResultText("found devices ${mbtDevice?.bluetoothDevice?.name}")
                            //addResultText("stop scan")
                        }

                        override fun onOtherDevices(otherDevices: List<BluetoothDevice>) {
                            Timber.i("onOtherDevices size = ${otherDevices.size}")
                            for (device in otherDevices) {
                                if (device.name != null) {
                                    Timber.d("onOtherDevices name = ${device.name}")
                                }
                            }
                        }

                        override fun onScanError(error: Throwable) {
                            binding.simpleScanDevices.background = AppCompatResources.getDrawable(applicationContext, R.color.bluetooth_disable)
                            Timber.e(error)
                            //addResultText("onScanError")
                        }
                    })
                }
                else -> {
                    Toast.makeText(this, "bluetooth is off, please turn it on.", Toast.LENGTH_SHORT).show()
                    activateBluetooth()
                }
            }
        }

        // functions for connecting device
        binding.simpleConnectDevice.setOnClickListener {
            if (!isMbtConnected && mbtDevice != null){
                // device is not connected...
                mbtClient.connect(mbtDevice!!, this)
            }
            if (isMbtConnected)
                mbtClient.disconnect()
        }

        // get power level
        binding.simpleGetBattery.setOnClickListener {
            setBatteryLevel()
        }

        // start receive EEG
        binding.simpleStartReceive.setOnClickListener {
            if (isMbtConnected && !mbtClient.isEEGEnabled())
                onBtnStartEEGClicked(false)
        }

        binding.simpleStopReceive.setOnClickListener {
            if (isMbtConnected && mbtClient.isEEGEnabled()){
                mbtClient.stopEEG()
                eegCount = 0
            }
        }

        // record EEG
        binding.simpleStartRecord.setOnClickListener {
            if (mbtClient.isEEGEnabled() && !mbtClient.isRecordingEnabled()){
                onBtnStartRecordingClicked()
            }
        }

        binding.simpleStopRecord.setOnClickListener {
            if(isMbtConnected && mbtClient.isRecordingEnabled()){
                mbtClient.stopEEGRecording()
            }

        }

        binding.simpleFinish.setOnClickListener {
            finish()
        }

        // quality checkers
        // add buttons to list
        qualityButtons.add(binding.P3)
        qualityButtons.add(binding.P4)
        qualityButtons.add(binding.AF3)
        qualityButtons.add(binding.AF4)
        for (i in 1..channelNb) {
            lastQualities.add(LinkedList())
        }

    }

    private fun onBtnStartEEGClicked(isStatusEnabled: Boolean) {
        mbtClient.startEEG(
            EEGParams(
                sampleRate = 250,
                isTriggerStatusEnabled = isStatusEnabled,
                isQualityCheckerEnabled = true
            ),
            object : EEGListener {
                override fun onEegPacket(mbtEEGPacket2: MbtEEGPacket2) {
                    if (mbtClient.isRecordingEnabled()){
                        Timber.d("is recording")
                    }

                    // get data from channel 1
                    val p3Data = getP3(mbtEEGPacket2.channelsData)

                    //Updating chart
                    binding.chart1.post {
                        if (counter < 2) {
                            counter++
                            addEntry(binding.chart1, p3Data)
                        } else {
                            updateEntry(
                                binding.chart1,
                                p3Data,
                                bufferedChartData,
                            )
                            bufferedChartData = p3Data
                        }
                    }
                    updateQualityButtons(mbtEEGPacket2.qualities)
                }
                override fun onEegError(error: Throwable) {
                    Timber.e(error)
                }
            },
        )
    }

    private fun onBtnStartRecordingClicked() {
        if (deviceInformation == null) {
            Timber.e("device info is empty!")
            return
        }

        Timber.d("onBtnStartRecordingClicked")

        val name = "${deviceInformation?.productName}-${getTimeNow()}.json"
        var folder = File(Environment.getExternalStorageDirectory().toString() + "/MBT_DEMO")
        folder.mkdirs()
        if (!folder.isDirectory || !folder.canWrite()) {
            folder = cacheDir
        }
        val outputFile = File(folder, name)

        mbtClient.startEEGRecording(
            RecordingOption(
                outputFile,
                KwakContext().apply { ownerId = "1" },
                deviceInformation!!,
                "record-" + UUID.randomUUID().toString()
            ),
            object : RecordingListener {
                override fun onRecordingSaved(outputFile: File) {
                    val path = outputFile.path
                    Timber.i("output file path = $path")

                    if (path.isPrivateMemory()) {
                        val contentUri: Uri = FileProvider.getUriForFile(
                            this@QplusSimpleActivity,
                            "com.example.demoqplus",
                            outputFile
                        )
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/json"
                            data = contentUri
                            putExtra(Intent.EXTRA_STREAM, contentUri);
                            clipData = ClipData.newRawUri("", contentUri)
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }.also {
                            startActivity(it)
                        }
                    }
                }

                override fun onRecordingError(error: Throwable) {
                    Timber.e(error)
                }

            }
        )
    }

    @SuppressLint("SimpleDateFormat")
    fun getTimeNow(): String {
        try {
            val date = Date()
            val tf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return tf.format(date)
        } catch (e: Exception) {
            Timber.e(e)
            return System.currentTimeMillis().toString()
        }
    }

    private fun String.isPrivateMemory(): Boolean {
        return this.contains(this@QplusSimpleActivity.packageName)
    }

    /**
     * quality checkers
     * **/

    private fun updateQualityButtons(qualities: ArrayList<Float>?){
        if (qualities != null && qualities.size == channelNb) {
            var greenCount = 0
            for (i in 0 until channelNb) {
                updateQualityBuffer(
                    qualities[i],
                    lastQualities[i]
                ) //add quality value at the end of channel quality buffer
                val isGreen = areGoodSignals(lastQualities[i])

                if (isGreen) {
                    channelFlags[i] = true //rule 2: set flag to true
                    greenCount++
                    qualityButtons[i].background = AppCompatResources.getDrawable(applicationContext, R.color.green_signal)
                } else {
                    qualityButtons[i].background = AppCompatResources.getDrawable(applicationContext, R.color.red)
                }
            }

            if (isSignalGoodEnough()) {
                Timber.d("quality is good")
            } else {
                Timber.d("quality is bad")
            }
        } else {
            Timber.e("qualities size is not equal $channelNb!")
        }
    }

    private fun updateQualityBuffer(newValue: Float, list: LinkedList<Float>) {
        if (!newValue.isNaN() && !newValue.isInfinite()) {
            list.addLast(newValue)
            if (list.size > qualityWindowLength) list.pollFirst()
        }
    }

    private fun areGoodSignals(qualities: LinkedList<Float>): Boolean {
        var count = 0
        for (value in qualities) {
            if (value < 0.25) {
                return false
            }
            if (value == 1.0f) {
                count++
            }
        }
        return (count == qualities.size || count >= 3)
    }

    private fun isSignalGoodEnough(): Boolean {
        //return haProgress == qualityWindowLength //rule 1
        return channelFlags.count { it } == channelNb //rule 2 : all channels are passed to good at least one (separately)
    }

    /**
     * line chart
     * Draw data of P3 only
     * */

    private fun initChart(){
        // arraylist size depends on the sample frequency, how many data in a second
        val channelDataSet1 = LineDataSet(ArrayList(250), "Channel1")
        configureEegLineDataSet(channelDataSet1, "P3", Color.CYAN)

        // setting chart
        val lineData = LineData()
        lineData.removeDataSet(0)
        lineData.addDataSet(channelDataSet1)
        binding.chart1.data = lineData
        setupLineChart(binding.chart1)
    }

    private fun setupLineChart(lineChart: LineChart) {
        // configuration of chart
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.TOP_INSIDE
        xAxis.textSize = 10f
        xAxis.textColor = Color.WHITE
        xAxis.setDrawAxisLine(false)
        xAxis.setDrawGridLines(true)
        xAxis.textColor = Color.rgb(255, 192, 56)
        xAxis.setCenterAxisLabels(true)
        xAxis.granularity = 1f // one hour
        lineChart.isDoubleTapToZoomEnabled = false
        lineChart.isAutoScaleMinMaxEnabled = true
        lineChart.axisLeft.setDrawGridLines(false)
        lineChart.axisLeft.setDrawLabels(true)
        lineChart.axisRight.setDrawLabels(true)
        lineChart.axisRight.setDrawGridLines(false)
        lineChart.axisRight.axisMinimum = -0.05f
        lineChart.axisRight.axisMaximum = 1f
        lineChart.xAxis.setDrawGridLines(false)
        lineChart.description = Description().apply {
            text = "sample"
            textSize = 8f //dp unit
        }
    }

    private fun configureEegLineDataSet(
        lineDataSet: LineDataSet,
        label: String,
        @ColorInt color: Int) {
        lineDataSet.label = label
        lineDataSet.setDrawValues(false)
        lineDataSet.disableDashedLine()
        lineDataSet.setDrawCircleHole(false)
        lineDataSet.setDrawCircles(false)
        lineDataSet.color = color
        lineDataSet.axisDependency = YAxis.AxisDependency.LEFT
    }

    // Update graph data
    private fun getP3(data: ArrayList<ArrayList<Float>>): ArrayList<Float> {
        return data[0]
    }

    private fun addEntry(
        chart: LineChart,
        channelData: ArrayList<Float>) {
        val data = chart.data
        if (data != null) {
            for (i in channelData.indices) {
                data.addEntry(
                    Entry(
                        data.dataSets[0].entryCount.toFloat(),
                        secureFloat(channelData[i] * 1000000)), 0)
            }
            data.notifyDataChanged()
            // let the chart know it's data has changed
            chart.notifyDataSetChanged()
            chart.setVisibleXRangeMaximum(TWO_SECONDS)
            chart.invalidate()
        } else {
            throw IllegalStateException("Graph not correctly initialized")
        }
    }

    private fun updateEntry(
        chart: LineChart,
        channelData: ArrayList<Float>,
        bufferedData: ArrayList<Float>) {
        try {
            val data = chart.data
            if ((data != null) && (data.dataSets?.size == 1)) {
                Timber.d("here is me")
                data.dataSets[0].clear()
                for (i in bufferedData.indices) {
                    data.addEntry(Entry(data.dataSets[0].entryCount.toFloat(),
                        secureFloat(bufferedData[i]) * 1000000), 0)
                }

                for (i in channelData.indices) {
                    data.addEntry(
                        Entry(data.dataSets[0].entryCount.toFloat(),
                            secureFloat(channelData[i]) * 1000000), 0)
                }
                data.notifyDataChanged()
                // let the chart know it's data has changed
                chart.notifyDataSetChanged()
                chart.invalidate()
            } else {
                Timber.e("data is null / data is not completed")
                Timber.e("Graph not correctly initialized")
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun secureFloat(value: Float): Float {
        return if (value.isNaN()) {
            0f
        } else {
            value
        }
    }

    /**
     * these functions are implemented for BatteryLevelListener
     * onBatteryLevel() / onBatteryLevelError()
     * **/
    private fun setBatteryLevel(){
        if (isMbtConnected){
            mbtClient.getBatteryLevel(object: BatteryLevelListener{
                override fun onBatteryLevel(float: Float) {
                    Timber.d("level = $float")
                    // set process and text
                    var level: Int = float.toInt()
                    binding.simpleBattery.progress = level
                    binding.simpleBatteryLevel.text = level.toString()+" %"
                }

                override fun onBatteryLevelError(error: Throwable) {
                    TODO("Not yet implemented")
                }
            })
        } else {
            binding.simpleBattery.progress = 0
            binding.simpleBatteryLevel.text = "0 %"
        }
    }

    // is device connected?
    private fun checkConnection(){
        // this variable contains 2 useful things: isConnectionEstablished and mbtDevice
        // mbtdevice = android.bluetooth.BluetoothDevice
        val bleConnectionStatus = mbtClient.getBleConnectionStatus()
        Timber.d("getBleConnectionStatus = ${bleConnectionStatus.mbtDevice.toJson()} | ${bleConnectionStatus.isConnectionEstablished}")
    }

    // activate bluetooth
    private fun activateBluetooth(){
        if (isPermissionsGranted){
            // ask for activation of bluetooth
            if (!bluetoothStateReceiver.isBluetoothOn){
                Timber.i("Bluetooth isn't enable, ask user for turning it on...")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, 1)
            }
        } else {
            // request permissions
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
        }
    }

    // check all permissions
    private fun isAllPermissionsGranted(context: Context, vararg permissions: String): Boolean = permissions.all{
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // fetch request permissions results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            1 ->{
                if(grantResults.isNotEmpty()){
                    var accessCoarse: Boolean = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    var accessFine: Boolean = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    isPermissionsGranted = accessCoarse&&accessFine
                    if (isPermissionsGranted){
                        Timber.i("All permissions are granted by user")
                        // Do something here...
                        activateBluetooth()
                    } else {
                        // Permission denied, do something here...
                        isPermissionsGranted = false
                    }
                } else {
                    // permissions denied
                    isPermissionsGranted = false
                }
            }
            else ->{
                Timber.d("requestCode problem, please check its value")
            }
        }
    }


    /**
     * these functions are implemented for ConnectionListener
     * onBonded() / onBondingFailed() / onBondingRequired()
     * onConnectionError() / onDeviceDisconnected() / onDeviceReady() / onServiceDiscovered()
     * **/
    override fun onBonded(device: BluetoothDevice) {

    }

    override fun onBondingFailed(device: BluetoothDevice) {

    }

    override fun onBondingRequired(device: BluetoothDevice) {

    }

    // connections
    override fun onServiceDiscovered() {
        Timber.d("onServiceDiscovered")
    }

    override fun onDeviceReady() {
        // = onConnectionSuccess
        isMbtConnected = true
        Timber.d("onDeviceReady")
        binding.simpleConnectDevice.text = "Disconnect"
        setBatteryLevel()

        // get device info
        mbtClient.getDeviceInformation(object : DeviceInformationListener {
            override fun onDeviceInformation(deviceInformation: DeviceInformation) {
                this@QplusSimpleActivity.deviceInformation = deviceInformation
                Timber.i(deviceInformation.toString())
            }

            override fun onDeviceInformationError(error: Throwable) {
                Timber.e(error)
            }

        })
    }

    override fun onDeviceDisconnected() {
        isMbtConnected = false
        Timber.d("onDeviceDisconnected")
        binding.simpleConnectDevice.text = "Connect"
        //binding.simpleIsRecording.background = AppCompatResources.getDrawable(this, R.color.red)
        setBatteryLevel()
        // disconnect reset quality checkers

    }

    override fun onConnectionError(error: Throwable) {
        Timber.e("device connection error!")
    }

    // activity
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
        mbtClient.disconnect()
    }

}