package com.mybraintech.demosdk

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.mybraintech.demosdk.databinding.ActivityAcquisitionBinding
import com.mybraintech.sdk.core.ResearchStudy
import com.mybraintech.sdk.core.listener.*
import com.mybraintech.sdk.core.model.*
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


@OptIn(ResearchStudy::class)
@SuppressLint("MissingPermission", "SetTextI18n")
class AcquisitionActivity : AppCompatActivity() {

    companion object {
        const val KEY_DEVICE_TYPE = "device_type"
    }

    private val viewModel: AcquisitionViewModel by viewModels()
    private lateinit var binding: ActivityAcquisitionBinding

    // line  chart
    private val TWO_SECONDS = 500f // frequency is 250 samples per sec

    /**
     * We display only last two data packets : this buffer store N-1 packet.
     * Size = [3x250]. Two first rows are EEG data, third row is status data
     */
    private var bufferedDataChart1 = ArrayList<ArrayList<Float>>()

    /**
     * We display only last two data packets : this buffer store N-1 packet.
     * Size = [3x250]. Two first rows are EEG data, third row is status data
     */
    private var bufferedDataChart2 = ArrayList<ArrayList<Float>>()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAcquisitionBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        val deviceType = getDeviceType(intent.getStringExtra(KEY_DEVICE_TYPE))
        if (deviceType == EnumMBTDevice.MELOMIND || deviceType == EnumMBTDevice.Q_PLUS || deviceType == EnumMBTDevice.HYPERION) {
            viewModel.setupMbtSdk(applicationContext, deviceType)
            initView()
        } else {
            showNotSupportedDialog(deviceType)
        }
    }

    private fun getDeviceType(requestedType: String?): EnumMBTDevice? {
        if (requestedType == null) {
            return null
        } else {
            for (deviceType in EnumMBTDevice.values()) {
                if (requestedType.uppercase() == deviceType.name.uppercase()) {
                    return deviceType
                }
            }
            return null
        }
    }

    private fun showNotSupportedDialog(type: EnumMBTDevice?) {
        AlertDialog.Builder(this).setMessage("Device type $type is not supported !")
            .setCancelable(false).setNeutralButton("OK") { _, _ ->
                closeActivity()
            }.create().show()
    }

    private fun closeActivity() {
        finish()
    }

    private fun initView() {
        initListeners()
        initializeGraph()
    }

    private fun initListeners() {
        viewModel.getUpdateLiveData().observe(this) { infos ->
            Toast.makeText(applicationContext, infos, Toast.LENGTH_SHORT).show()
        }

        viewModel.getDeviceInfoLiveData().observe(this) { text ->
            binding.txtDeviceInfo.text = text
        }

        viewModel.getScanStateLiveData().observe(this) { scanState ->
            binding.btnScan.text = scanState
        }
        binding.btnScan.setOnClickListener {
            viewModel.actionScanButton()
        }

        viewModel.getConnectionStateLiveDate().observe(this) { connectionState ->
            binding.btnConnect.text = connectionState
        }
        binding.btnConnect.setOnClickListener {
            viewModel.actionConnectButton()
        }

        viewModel.getFilterLiveData().observe(this) { filterMode ->
            binding.btnFilter.text = filterMode
        }
        binding.btnFilter.setOnClickListener { viewModel.actionFilterButton() }

        viewModel.getEEGStatusLiveData().observe(this) { streamingStatus ->
            binding.btnEeg.text = streamingStatus
        }
        binding.btnEeg.setOnClickListener { viewModel.startStopEEG() }

        binding.btnBattery.setOnClickListener { viewModel.getBatteryLevel() }

        binding.btnStartRecord.setOnClickListener {
            viewModel.startRecord(
                createOutputFile(binding.edtPrefix.text.toString()), createRecordingListener()
            )
        }

        binding.btnStopRecord.setOnClickListener {
            viewModel.stopRecord()
        }

        binding.btnFinish.setOnClickListener { closeActivity() }
    }

    private fun createOutputFile(prefix: String): File {
        return File(applicationContext.cacheDir, "$prefix-${getTimeNow()}-eeg-recording.json")
    }

    @SuppressLint("SimpleDateFormat")
    fun getTimeNow(): String {
        return try {
            val date = Date()
            val tf = SimpleDateFormat("yyyy-MM-dd'T'HH'h'mm'm'ss")
            tf.format(date)
        } catch (e: Exception) {
            Timber.e(e)
            System.currentTimeMillis().toString()
        }
    }

    @Suppress("unused")
    private fun String.isPrivateMemory(): Boolean {
        return this.contains(this@AcquisitionActivity.packageName)
    }

    private fun initializeGraph() {
        // status is common
        val statusDataSet1 = LineDataSet(ArrayList(250), "Status")
        configureStatusLineDataSet(statusDataSet1) //use for chart 1, do not reuse for chart 2
        val statusDataSet2 = LineDataSet(ArrayList(250), "Status")
        configureStatusLineDataSet(statusDataSet2) //use for chart 2
        val dataSetCh1 = LineDataSet(ArrayList(250), "Channel 1")
        configureEegLineDataSet(dataSetCh1, "P3", Color.RED)
        val dataSetCh2 = LineDataSet(ArrayList(250), "Channel 2")
        configureEegLineDataSet(dataSetCh2, "P4", Color.BLUE)
        // for indus5
        val dataSetCh3 = LineDataSet(ArrayList(250), "Channel 3")
        configureEegLineDataSet(dataSetCh3, "AF3", Color.MAGENTA)
        val dataSetCh4 = LineDataSet(ArrayList(250), "Channel 4")
        configureEegLineDataSet(dataSetCh4, "AF4", Color.CYAN)
        // setting chart
        binding.chart1.data = getLineData(dataSetCh1, dataSetCh2, statusDataSet1)
        binding.chart2.data = getLineData(dataSetCh3, dataSetCh4, statusDataSet2)
        setupLineChart(binding.chart1)
        setupLineChart(binding.chart2)

        viewModel.getChart1LiveData().observe(this) { ch12 ->
            binding.txtRecordingSize.text = viewModel.getRecordingSize().toString()
            updateEntry(binding.chart1, bufferedDataChart1, ch12)
            bufferedDataChart1 = ch12
        }

        viewModel.getChart2LiveData().observe(this) { ch34 ->
            updateEntry(binding.chart2, bufferedDataChart2, ch34)
            bufferedDataChart2 = ch34
        }

        viewModel.getQualityLiveData().observe(this) {
            if (it.size >= 2) {
                binding.txtP3.text = String.format("%.2f", it[0])
                binding.txtP4.text = String.format("%.2f", it[1])
            }
            if (it.size >= 4) {
                binding.txtAf3.text = String.format("%.2f", it[2])
                binding.txtAf4.text = String.format("%.2f", it[3])
            }
        }
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
        lineDataSet: LineDataSet, label: String, @ColorInt color: Int
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
        dataSet1: LineDataSet, dataSet2: LineDataSet, dataSet3: LineDataSet
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

    private fun updateEntry(
        chart: LineChart,
        bufferedData: ArrayList<ArrayList<Float>>,
        channelData: ArrayList<ArrayList<Float>>
    ) {
        try {
            val data = chart.data
            if ((data != null) && (data.dataSets?.size == 3)) {
                data.dataSets[0].clear()
                data.dataSets[1].clear()
                data.dataSets[2].clear()
                check(channelData.size >= 2) { "Incorrect matrix size, one or more channel are missing" }
                if (bufferedData.isNotEmpty()) { //initially, buffer is empty
                    for (i in bufferedData[0].indices) {
                        data.addEntry(
                            Entry(
                                data.dataSets[0].entryCount.toFloat(), bufferedData[0][i] * 1000000
                            ), 0
                        )
                        data.addEntry(
                            Entry(
                                data.dataSets[1].entryCount.toFloat(), bufferedData[1][i] * 1000000
                            ), 1
                        )
                        data.addEntry(
                            Entry(
                                data.dataSets[2].entryCount.toFloat(), bufferedData[2][i]
                            ), 2
                        )
                    }
                }
                for (i in channelData[0].indices) {
                    data.addEntry(
                        Entry(
                            data.dataSets[0].entryCount.toFloat(), channelData[0][i] * 1000000
                        ), 0
                    )
                    data.addEntry(
                        Entry(
                            data.dataSets[1].entryCount.toFloat(), channelData[1][i] * 1000000
                        ), 1
                    )
                    data.addEntry(
                        Entry(
                            data.dataSets[2].entryCount.toFloat(), channelData[2][i]
                        ), 2
                    )
                }
                data.notifyDataChanged()
                // let the chart know it's data has changed
                chart.notifyDataSetChanged()
                chart.setVisibleXRangeMaximum(TWO_SECONDS)
                chart.moveViewToX(chart.xChartMax)
                chart.invalidate()
            } else {
                Timber.e("data is null / data is not completed")
                Timber.e("Graph not correctly initialized")
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun createRecordingListener(): RecordingListener {
        return object : RecordingListener {
            override fun onRecordingError(error: Throwable) {
                Timber.e(error)
                Toast.makeText(
                    applicationContext, "encountered recording error", Toast.LENGTH_SHORT
                ).show()
            }

            override fun onRecordingSaved(outputFile: File) {
                EEGFileProvider.shareFile(outputFile, this@AcquisitionActivity)
            }
        }
    }
}