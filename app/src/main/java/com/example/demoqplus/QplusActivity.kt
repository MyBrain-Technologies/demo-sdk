package com.example.demoqplus

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
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
import com.mybraintech.sdk.MbtClient
import com.mybraintech.sdk.MbtClientManager
import com.mybraintech.sdk.core.model.EnumMBTDevice
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList


class QplusActivity : AppCompatActivity() {

    lateinit var viewModel : QPlusViewModel

    // Declare bluetooth permissions
    var PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION)

    val REQUEST_PERMISSION_CODE:Int = 1
    val PERMISSION_ALL: Int = 1
    var PERMISSION_GRANTED: Boolean = false

    // bluetooth
    val REQUEST_ENABLE_BT: Int = 1
    lateinit var mbtClient: MbtClient

    // quality check
    private lateinit var binding: ActivityQplusBinding
    var lastQualities = ArrayList<LinkedList<Float>>()
    var qualityWindowLength = 6
    var channelNb = 4
    var qualityButtons = ArrayList<Button>()
    var channelFlags = ArrayList<Boolean>().apply {
        for (i in 1..channelNb) {
            this.add(false)
        }
    }
    var recordingSavedListener: RecordingSavedListener = object : RecordingSavedListener {
        override fun onRecordingSaved(recordConfig: RecordConfig) {
            Timber.d("onRecordingSaved")
        }
    }

    // line  chart
    private val TWO_SECONDS = 500f
    private var counter: Long = 0
    private var bufferedChartData = ArrayList<ArrayList<Float>>()
    private var bufferedChartData2 = ArrayList<ArrayList<Float>>()

    // test case only
    var isClientExisted: Boolean = false
    var IS_STREAMING: Boolean = false
    var IS_DEVICE_CONNECTED:Boolean = false
    val KEY_P300: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qplus)

        binding = ActivityQplusBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        viewModel = ViewModelProvider(this).get(QPlusViewModel::class.java)

        initActivity()

        checkPermission()

        //var streamStateChange = findViewById<Button>(R.id.button_start_stop_stream)
    }

    private fun initActivity(){
        viewModel.init(this, EnumMBTDevice.Q_PLUS)
        //TODO: start here
        viewModel.mbtClient?.getBleConnectionStatus() //....

        // connect device
        binding.connectDevice.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                if (PERMISSION_GRANTED) {
                    connectMBTdevice()
                    binding.buttonStream.isEnabled = true
                } else {
                    binding.connectDevice.isChecked = false
                    checkPermission()
                    Toast.makeText(this, "Permissions are denied", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (IS_DEVICE_CONNECTED){
                    mbtClient.disconnectBluetooth()
                    binding.buttonStream.text = "Start Stream"
                }
            }
        }

        // button stream
        binding.buttonStream.setOnClickListener{
            if (!IS_STREAMING){
                // start stream if it isn't streamin
                if (IS_DEVICE_CONNECTED) {
                    binding.buttonStream.text = "Stop Stream"
                    onStartStreamButtonClicked()
                    IS_STREAMING = true
                } else {
                    Toast.makeText(this, "device not connected", Toast.LENGTH_SHORT).show()
                }
            } else {
                // stop
                mbtClient.stopStream()
                binding.buttonStream.text = "Start Stream"
                IS_STREAMING = false
            }

        }

        initializeGraph()

        // add buttons to list
        qualityButtons.add(binding.P3)
        qualityButtons.add(binding.P4)
        qualityButtons.add(binding.AF3)
        qualityButtons.add(binding.AF4)
        for (i in 1..channelNb) {
            lastQualities.add(LinkedList())
        }

        // button finish
        binding.buttonFinish.setOnClickListener{
            if (isClientExisted && IS_DEVICE_CONNECTED) {
                mbtClient.disconnectBluetooth()
            }
            finish()
        }
    }

    private fun isAllPermissionsGranted(context: Context, vararg permissions: String): Boolean = permissions.all{
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // functions for graph
    private fun initializeGraph() {
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
        val lineData1 = getLineData(statusDataSet1, dataSetChan2, dataSetChan3)
        binding.chart1.data = lineData1
        setupLineChart(binding.chart1)

        val lineData2 = getLineData(statusDataSet2, dataSetChan4, dataSetChan5)
        binding.chart2.data = lineData2
        setupLineChart(binding.chart2)
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

        lineChart.onChartGestureListener = object : OnChartGestureListener {
            override fun onChartGestureStart(
                me: MotionEvent?,
                lastPerformedGesture: ChartTouchListener.ChartGesture?
            ) {
            }

            override fun onChartGestureEnd(
                me: MotionEvent?,
                lastPerformedGesture: ChartTouchListener.ChartGesture?
            ) {
            }

            override fun onChartLongPressed(me: MotionEvent?) {
            }

            override fun onChartDoubleTapped(me: MotionEvent?) {
            }

            override fun onChartSingleTapped(me: MotionEvent?) {
            }

            override fun onChartFling(
                me1: MotionEvent?,
                me2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
            ) {
            }

            override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
            }

            override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
            }
        }
        lineChart.invalidate()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            REQUEST_PERMISSION_CODE ->{
                if(grantResults.isNotEmpty()){
                    var accessCoarse: Boolean = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    var accessFine: Boolean = grantResults[1] == PackageManager.PERMISSION_GRANTED
                    PERMISSION_GRANTED = accessCoarse&&accessFine
                    if (PERMISSION_GRANTED){
                        Timber.i("All permissions are granted by user")
                        // Do something here...
                    } else {
                        // Permission denied, do something here...
                        PERMISSION_GRANTED = false
                    }
                } else {
                    // permissions denied
                    PERMISSION_GRANTED = false
                }
            }
            else ->{
                Timber.d("requestCode problem, please check its value")
            }
        }
    }

    private fun checkPermission(){
        PERMISSION_GRANTED = isAllPermissionsGranted(this, *PERMISSIONS)
        if (!PERMISSION_GRANTED){
            // Not granted
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
    }

    private fun connectMBTdevice(){
        // init sdk
        if (!isClientExisted){
            mbtClient = MbtClient.init(this)
            isClientExisted = true
        } else {
            mbtClient = MbtClient.getClientInstance()
        }

        val connConfig = ConnectionConfig.Builder(this)
            .createForDevice(KEY_DEVICE_TYPE)

        // enable bluetooth
        val bluetoothAdp = BluetoothAdapter.getDefaultAdapter()
        val isEnable = bluetoothAdp.isEnabled
        if (!isEnable){
            Timber.i("Bluetooth isn't enable")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        Timber.d("execute connectBluetooth...")
        mbtClient.connectBluetooth(connConfig)

    }

    private fun readBattery() {
        val batteryListener = object : DeviceBatteryListener<BaseError> {
            override fun onError(error: BaseError?, additionalInfo: String?) {
                Timber.e("batteryListener onError ${error?.message}")
            }

            override fun onBatteryLevelReceived(level: String) {
                Timber.i("onBatteryLevelReceived: $level")
            }
        }
        mbtClient.readBattery(batteryListener)
    }

    override fun onError(error: BaseError?, additionalInfo: String?) {
    }

    override fun onDeviceConnected(p0: MbtDevice?) {
        // device is connected
        Timber.d("Device is connected")
        IS_DEVICE_CONNECTED = true
        readBattery()
    }

    override fun onDeviceDisconnected(p0: MbtDevice?) {
        //device is disconnected
        Timber.d("Device is disconnected")
        mbtClient.stopStream()
        IS_STREAMING = false
        IS_DEVICE_CONNECTED = false

    }


    private fun onStartStreamButtonClicked(){
        Timber.d("onStartStreamButtonClicked")
        startStreamWithTrigger(KEY_P300, false)

    }

    private fun startStreamWithTrigger(isTriggerEnabled: Boolean, ppgEnabled: Boolean) {

        val streamConfigBuilder = StreamConfig.Builder(this@QplusActivity)
            .useQualities()
            .setDeviceStatusListener(this@QplusActivity)
        val streamConfig = streamConfigBuilder.createForDevice(MbtDeviceType.MELOMIND_Q_PLUS)
        streamConfig.recordingSavedListener = recordingSavedListener
        streamConfig.isImsEnabled = false
        Timber.d("ppgEnabled = $ppgEnabled")
        streamConfig.isPpgEnabled = ppgEnabled
        streamConfig.isTriggerEnabled = isTriggerEnabled
        mbtClient.startStream(streamConfig)
        Timber.d("startStreamWithTrigger")
    }



    override fun onNewPackets(mbteegPackets: MbtEEGPacket) {
        counter++
        Timber.d("onNewPackets")
        updateQualityButtons(mbteegPackets.qualities)

        mbteegPackets.channelsData = MatrixUtils.invertFloatMatrix(mbteegPackets.channelsData)

        val p3p4Data = getP3P4(mbteegPackets.channelsData)
        val af3af4Data = getAF3AF4(mbteegPackets.channelsData)

        //Updating chart
        binding.chart1.post {
            if (counter <= 2) {
                addEntry(binding.chart1, p3p4Data, mbteegPackets.statusData)
            } else {
                updateEntry(binding.chart1, p3p4Data, bufferedChartData, mbteegPackets.statusData)
            }
            bufferedChartData = p3p4Data
            bufferedChartData.add(0, mbteegPackets.statusData)
        }

        binding.chart2.post {
            if (counter <= 2) {
                addEntry(binding.chart2, af3af4Data, mbteegPackets.statusData)
            } else {
                updateEntry(
                    binding.chart2,
                    af3af4Data,
                    bufferedChartData2,
                    mbteegPackets.statusData
                )
            }
            bufferedChartData2 = af3af4Data
            bufferedChartData2.add(0, mbteegPackets.statusData)
        }


    }

    override fun onNewStreamState(streamState: StreamState) {}

    override fun onSaturationStateChanged(p0: SaturationEvent?) {
        //TODO("Not yet implemented")
    }

    override fun onNewDCOffsetMeasured(p0: DCOffsetEvent?) {
        //
    }

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
                    qualityButtons[i].background =
                        AppCompatResources.getDrawable(this, R.color.green_signal)
                } else {
                    qualityButtons[i].background =
                        AppCompatResources.getDrawable(this, R.color.gray)
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

    private fun isSignalGoodEnough(): Boolean {
//        return haProgress == qualityWindowLength //rule 1
        return channelFlags.count { it } == channelNb //rule 2 : all channels are passed to good at least one (separately)
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

    // Update graph data
    private fun getP3P4(data: ArrayList<ArrayList<Float>>): ArrayList<ArrayList<Float>> {
        val result = ArrayList<ArrayList<Float>>()
        result.add(data[0])
        result.add(data[1])
        return result
    }

    private fun getAF3AF4(data: ArrayList<ArrayList<Float>>): ArrayList<java.util.ArrayList<Float>> {
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
                        if (statusData != null) data.addEntry(
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
                        if (statusData != null) data.addEntry(
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


}