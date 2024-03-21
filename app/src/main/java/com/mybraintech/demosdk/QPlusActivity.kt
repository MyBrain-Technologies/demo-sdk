package com.mybraintech.demosdk

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.mybraintech.demosdk.databinding.ActivityQPlusBinding
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.listener.*
import com.mybraintech.sdk.core.model.*
import com.mybraintech.sdk.util.toJson
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("MissingPermission", "SetTextI18n")
class QPlusActivity : AppCompatActivity(), ConnectionListener {

    private lateinit var binding: ActivityQPlusBinding

    // Declare bluetooth permissions
    private var isPermissionsGranted: Boolean = false
    var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

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

    // line  chart
    private val TWO_SECONDS = 500f // frequency is 250 samples per sec
    private var counter: Int = 0
    private var isP3P4: Boolean = true
    private var bufferedChartData = ArrayList<ArrayList<Float>>()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize viewbinding
        binding = ActivityQPlusBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        supportActionBar?.title = "Q Plus"

        // initialize some parameters
        isPermissionsGranted = isAllPermissionsGranted(this, *PERMISSIONS)
        bluetoothStateReceiver =
            BluetoothStateReceiver(BluetoothAdapter.getDefaultAdapter().isEnabled)
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        mbtClient = MbtClientManager.getMbtClient(applicationContext, EnumMBTDevice.Q_PLUS)

        checkConnection()
        setBatteryLevel()
        initUI()
        initializeGraph()
    }

    private fun initUI() {
        // functions for scanning devices
        binding.simpleScanDevices.setOnClickListener {
            when {
                isScanning -> {
                    binding.simpleScanDevices.text = "Scan"
                    mbtClient.stopScan()
                    isScanning = false
                }
                bluetoothStateReceiver.isBluetoothOn -> {
                    Timber.i("is ready for scanning devices...")
                    binding.simpleScanDevices.text = "Stop Scan"
                    isScanning = true
                    mbtClient.startScan(object : ScanResultListener {
                        override fun onMbtDevices(mbtDevices: List<MbtDevice>) {
                            Timber.i("onMbtDevices size = ${mbtDevices.size}")
                            for (device in mbtDevices) {
                                Timber.i("device ${device.bluetoothDevice.name}")
                            }
                            mbtClient.stopScan()
                            binding.simpleScanDevices.text = "Scan"
                            mbtDevice = mbtDevices[0]
                            binding.txtDeviceName.text = mbtDevice?.bluetoothDevice?.name

                            Toast.makeText(
                                applicationContext,
                                "device: ${mbtDevice!!.bluetoothDevice.name} has been found! ",
                                Toast.LENGTH_LONG
                            ).show()

                            isScanning = false
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
                            binding.simpleScanDevices.text = "Scan"
                            Timber.e(error)
                            //addResultText("onScanError")
                        }
                    })
                }
                else -> {
                    Toast.makeText(this, "bluetooth is off, please turn it on.", Toast.LENGTH_SHORT)
                        .show()
                    activateBluetooth()
                }
            }
        }

        // functions for connecting device
        binding.simpleConnectDevice.setOnClickListener {
            if (!isMbtConnected && mbtDevice != null) {
                // device is not connected...
                mbtClient.connect(mbtDevice!!, this)
                binding.simpleConnectDevice.text = "Disconnect"
            }
            if (isMbtConnected) {
                mbtClient.disconnect()
                binding.simpleConnectDevice.text = "Connect"
            }
        }

        // get power level
        binding.simpleGetBattery.setOnClickListener {
            setBatteryLevel()
        }

        // start receive EEG
        binding.simpleStartReceive.setOnClickListener {
            if (isMbtConnected && !mbtClient.isEEGEnabled()) {
                onBtnStartEEGClicked()
                binding.simpleStartReceive.text = "Stop EEG"
            } else {
                mbtClient.stopStreaming()
                binding.simpleStartReceive.text = "Start EEG"
                eegCount = 0
            }
        }

        // record EEG
        binding.simpleStartRecord.setOnClickListener {
            if (mbtClient.isEEGEnabled() && !mbtClient.isRecordingEnabled()) {
                onBtnStartRecordingClicked()
                binding.simpleStartRecord.text = "Stop Record"
            } else {
                mbtClient.stopRecording()
                binding.simpleStartRecord.text = "Start Record"
            }
        }

        binding.simpleSwitchEEG.setOnClickListener {
            if (isP3P4) {
                binding.simpleSwitchEEG.text = "AF3/AF4"

            } else {
                binding.simpleSwitchEEG.text = "P3/P4"

            }
            isP3P4 = !isP3P4
            initializeGraph()
        }

        binding.simpleFinish.setOnClickListener {
            finish()
        }
    }

    private fun onBtnStartEEGClicked() {
        val listener = object : EEGListener {
            override fun onEegPacket(mbtEEGPacket2: MbtEEGPacket) {
                val getData = if (isP3P4) {
                    getP3P4(mbtEEGPacket2.channelsData)
                } else {
                    getAF3AF4(mbtEEGPacket2.channelsData)
                }
                //Updating chart
                binding.chart1.post {
                    if (counter < 2) {
                        counter++
                        addEntry(binding.chart1, getData, mbtEEGPacket2.statusData)
                    } else {
                        updateEntry(
                            binding.chart1,
                            getData,
                            bufferedChartData,
                            mbtEEGPacket2.statusData
                        )
                    }
                    bufferedChartData = getData
                    bufferedChartData.add(0, mbtEEGPacket2.statusData)
                }
                // bufferedChartData2 = af3af4Data
                // bufferedChartData2.add(0, mbtEEGPacket2.statusData)

                updateQualityButtons(mbtEEGPacket2.qualities)
            }

            override fun onEEGStatusChange(isEnabled: Boolean) {
                Timber.i("onEEGStatusChange = $isEnabled")
            }

            override fun onEegError(error: Throwable) {
                Timber.e(error)
            }
        }

        mbtClient.setEEGListener(listener)

        val params = StreamingParams.Builder()
            .setEEG(true)
            .setQualityChecker(true)
            .build()

        mbtClient.startStreaming(params)
    }

    private fun onBtnStartRecordingClicked() {
        if (deviceInformation == null) {
            Timber.e("device info is empty!")
            return
        }

        Timber.d("onBtnStartRecordingClicked")

        val name = "${deviceInformation?.bleName}-${getTimeNow()}.json"
        var folder = File(Environment.getExternalStorageDirectory().toString() + "/MBT_DEMO")
        folder.mkdirs()
        if (!folder.isDirectory || !folder.canWrite()) {
            folder = cacheDir
        }
        val outputFile = File(folder, name)

        mbtClient.startRecording(
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

                    val contentUri: Uri = FileProvider.getUriForFile(
                        this@QPlusActivity,
                        "authority.com.mybraintech.demosdk",
                        outputFile
                    )
                    Intent(Intent.ACTION_SEND).apply {
                        setDataAndType(contentUri, "text/json")
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        clipData = ClipData.newRawUri("", contentUri)
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }.also {
                        startActivity(it)
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
            val tf = SimpleDateFormat("yyyy-MM-dd'T'HH'h'mm'm'ss")
            return tf.format(date)
        } catch (e: Exception) {
            Timber.e(e)
            return System.currentTimeMillis().toString()
        }
    }

    @Suppress("unused")
    private fun String.isPrivateMemory(): Boolean {
        return this.contains(this@QPlusActivity.packageName)
    }

    /**
     * quality checkers
     * **/

    private fun updateQualityButtons(qualities: ArrayList<Float>?) {
        runOnUiThread {
            if (qualities != null && qualities.size == 4) {
                binding.P3.text = String.format("P3: %.1f", qualities[0])
                binding.P4.text = String.format("P4: %.1f", qualities[1])
                binding.AF3.text = String.format("AF3: %.1f", qualities[2])
                binding.AF4.text = String.format("AF4: %.1f", qualities[3])
            } else {
                Timber.e("qualities size is not equal 4!")
            }
        }
    }

    /**
     * line chart
     * Draw data of P3 only
     * */
    // functions for graph
    private fun initializeGraph() {
        counter = 0
        //status is common
        val statusDataSet1 = LineDataSet(ArrayList(250), "Status")
        configureStatusLineDataSet(statusDataSet1) //use for chart 1, do not reuse for chart 2
        val statusDataSet2 = LineDataSet(ArrayList(250), "Status")
        configureStatusLineDataSet(statusDataSet2) //use for chart 2
        val dataSetChan2 = LineDataSet(ArrayList(250), "Channel 1")
        configureEegLineDataSet(dataSetChan2, "P3", Color.RED)
        val dataSetChan3 = LineDataSet(ArrayList(250), "Channel 2")
        configureEegLineDataSet(dataSetChan3, "P4", Color.BLUE)
        //for indus5
        val dataSetChan4 = LineDataSet(ArrayList(250), "Channel 3")
        configureEegLineDataSet(dataSetChan4, "AF3", Color.MAGENTA)
        val dataSetChan5 = LineDataSet(ArrayList(250), "Channel 4")
        configureEegLineDataSet(dataSetChan5, "AF4", Color.CYAN)
        // setting chart
        if (isP3P4) {
            binding.chart1.data = getLineData(statusDataSet1, dataSetChan2, dataSetChan3)
        } else {
            binding.chart1.data = getLineData(statusDataSet2, dataSetChan4, dataSetChan5)
        }
        // val lineData1 = getLineData(statusDataSet1, dataSetChan2, dataSetChan3)
        // val lineData2 = getLineData(statusDataSet2, dataSetChan4, dataSetChan5)

        setupLineChart(binding.chart1)
    }

    private fun configureStatusLineDataSet(lineDataSet: LineDataSet) {
        lineDataSet.label = "STYM"
        lineDataSet.setDrawValues(false)
        lineDataSet.disableDashedLine()
        lineDataSet.setDrawCircleHole(false)
        lineDataSet.setDrawCircles(false)
        lineDataSet.color = Color.GREEN
        lineDataSet.setDrawFilled(true)
        lineDataSet.fillColor = Color.GREEN
        lineDataSet.fillAlpha = 40
        lineDataSet.axisDependency = YAxis.AxisDependency.RIGHT
    }

    private fun configureEegLineDataSet(
        lineDataSet: LineDataSet,
        label: String,
        @ColorInt color: Int
    ) {
        lineDataSet.label = label
        lineDataSet.setDrawValues(false)
        lineDataSet.disableDashedLine()
        lineDataSet.setDrawCircleHole(false)
        lineDataSet.setDrawCircles(false)
        lineDataSet.color = color
        lineDataSet.axisDependency = YAxis.AxisDependency.LEFT
    }

    private fun getLineData(
        dataSet1: LineDataSet,
        dataSet2: LineDataSet,
        dataSet3: LineDataSet
    ): LineData {
        val lineData = LineData()
        lineData.removeDataSet(2)
        lineData.removeDataSet(1)
        lineData.removeDataSet(0)
        lineData.addDataSet(dataSet1)
        lineData.addDataSet(dataSet2)
        lineData.addDataSet(dataSet3)
        return lineData
    }

    private fun setupLineChart(lineChart: LineChart) {
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
        lineChart.invalidate()
    }

    // Update graph data
    private fun getP3P4(data: ArrayList<ArrayList<Float>>): ArrayList<ArrayList<Float>> {
        val result = ArrayList<ArrayList<Float>>()
        result.add(data[0])
        result.add(data[1])
        return result
    }

    private fun getAF3AF4(data: ArrayList<ArrayList<Float>>): ArrayList<ArrayList<Float>> {
        val result = ArrayList<ArrayList<Float>>()
        result.add(data[2])
        result.add(data[3])
        return result
    }


    private fun addEntry(
        chart: LineChart,
        channelData: ArrayList<ArrayList<Float>>,
        statusData: ArrayList<Float>?
    ) {
        val data = chart.data
//        data.dataSets[0].clear()
//        data.dataSets[1].clear()
//        data.dataSets[2].clear()
        if (data != null) {
            check(channelData.size >= 2) { "Incorrect matrix size, one or more channel are missing" }
            if (channelData[0].size == channelData[1].size) {
                for (i in channelData[0].indices) {
                    if (statusData != null) data.addEntry(
                        Entry(
                            data.dataSets[0].entryCount.toFloat(),
                            secureFloat(statusData[i])
                        ), 0
                    )
                    data.addEntry(
                        Entry(
                            data.dataSets[1].entryCount.toFloat(),
                            secureFloat(channelData[0][i] * 1000000)
                        ), 1
                    )
                    data.addEntry(
                        Entry(
                            data.dataSets[2].entryCount.toFloat(),
                            secureFloat(channelData[1][i] * 1000000)
                        ), 2
                    )
                }
            } else {
                throw IllegalStateException("Channels do not have the same amount of data")
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
        channelData: ArrayList<ArrayList<Float>>,
        bufferedData: ArrayList<ArrayList<Float>>,
        statusData: ArrayList<Float>
    ) {
        try {
            val data = chart.data
            if ((data != null) && (data.dataSets?.size == 3)) {
                data.dataSets[0].clear()
                data.dataSets[1].clear()
                data.dataSets[2].clear()
                check(channelData.size >= 2) { "Incorrect matrix size, one or more channel are missing" }
                if (bufferedData[1].size == bufferedData[2].size) {
                    for (i in bufferedData[1].indices) {
                        data.addEntry(
                            Entry(
                                data.dataSets[0].entryCount.toFloat(),
                                secureFloat(bufferedData[0][i])
                            ), 0
                        )
                        data.addEntry(
                            Entry(
                                data.dataSets[1].entryCount.toFloat(),
                                secureFloat(bufferedData[1][i]) * 1000000
                            ), 1
                        )
                        data.addEntry(
                            Entry(
                                data.dataSets[2].entryCount.toFloat(),
                                secureFloat(bufferedData[2][i]) * 1000000
                            ), 2
                        )
                    }
                } else {
                    throw IllegalStateException("Channels do not have the same amount of data")
                }
                if (channelData[0].size == channelData[1].size) {
                    for (i in channelData[0].indices) {
                        data.addEntry(
                            Entry(
                                data.dataSets[0].entryCount.toFloat(),
                                secureFloat(statusData[i])
                            ), 0
                        )
                        data.addEntry(
                            Entry(
                                data.dataSets[1].entryCount.toFloat(),
                                secureFloat(channelData[0][i]) * 1000000
                            ), 1
                        )
                        data.addEntry(
                            Entry(
                                data.dataSets[2].entryCount.toFloat(),
                                secureFloat(channelData[1][i]) * 1000000
                            ), 2
                        )
                    }
                } else {
                    throw IllegalStateException("Channels do not have the same amount of data")
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
    private fun setBatteryLevel() {
        if (isMbtConnected) {
            mbtClient.getBatteryLevel(object : BatteryLevelListener {
                override fun onBatteryLevel(float: Float) {
                    Timber.d("level = $float")
                    // set process and text
                    val level: Int = float.toInt()
                    val info = "Device info: battery level: $level %"
                    Toast.makeText(applicationContext, info, Toast.LENGTH_SHORT).show()
                }

                override fun onBatteryLevelError(error: Throwable) {
                    Timber.e("onBatteryLevelError : ${error.message}")
                    Toast.makeText(
                        applicationContext,
                        "onBatteryLevelError : ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    // is device connected?
    private fun checkConnection() {
        // this variable contains 2 useful things: isConnectionEstablished and mbtDevice
        // mbtdevice = android.bluetooth.BluetoothDevice
        val bleConnectionStatus = mbtClient.getBleConnectionStatus()
        Timber.d("getBleConnectionStatus = ${bleConnectionStatus.mbtDevice.toJson()} | ${bleConnectionStatus.isConnectionEstablished}")
    }

    // activate bluetooth
    private fun activateBluetooth() {
        if (isPermissionsGranted) {
            // ask for activation of bluetooth
            if (!bluetoothStateReceiver.isBluetoothOn) {
                Timber.i("Bluetooth isn't enable, ask user for turning it on...")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBtIntent)
            }
        } else {
            // request permissions
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
        }
    }

    // check all permissions
    private fun isAllPermissionsGranted(context: Context, vararg permissions: String): Boolean =
        permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    // fetch request permissions results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()) {
                    val accessCoarse: Boolean = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    val accessFine: Boolean = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    isPermissionsGranted = accessCoarse && accessFine
                    if (isPermissionsGranted) {
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
            else -> {
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
        Timber.d("onBonded : ${device.name}")
    }

    override fun onBondingFailed(device: BluetoothDevice) {
        Timber.d("onBondingFailed : ${device.name}")
    }

    override fun onBondingRequired(device: BluetoothDevice) {
        Timber.d("onBondingRequired : ${device.name}")
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
                this@QPlusActivity.deviceInformation = deviceInformation
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
        binding.simpleStartReceive.text = "Start EEG"
        binding.simpleStartRecord.text = "Stop Record"
        //binding.simpleIsRecording.background = AppCompatResources.getDrawable(this, R.color.red)
        setBatteryLevel()
        // disconnect reset quality checkers

    }

    override fun onConnectionError(error: Throwable) {
        Timber.e("device connection error!")
        binding.simpleConnectDevice.text = "Connect"
    }

    // activity
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
        mbtClient.disconnect()
    }

}