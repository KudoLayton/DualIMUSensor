package com.example.dualimusensor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.opengl.Visibility
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.get
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.experimental.xor
import kotlin.math.pow
import kotlin.math.sqrt

private const val ACTION_USB_PERMISSION = "com.example.dualimusensor.USB_PERMISSION"

class MainActivity : AppCompatActivity() {
    private var executor : ScheduledExecutorService? = null

    var ports : Array<com.hoho.android.usbserial.driver.UsbSerialPort?> = arrayOfNulls(4)
    var usbIoManager : Array<SerialInputOutputManager?> = arrayOfNulls(4)
    var files : Array<OutputStreamWriter?> = arrayOfNulls(4)
    var availableDrivers : List<UsbSerialDriver> = emptyList()
    val portPartList : Array<Spinner?> = arrayOfNulls(4)
    val portAccList : Array<TextView?> = arrayOfNulls(4)
    val portListener = arrayOf(PortListener(files, portAccList, 0),
        PortListener(files, portAccList, 1),
        PortListener(files, portAccList, 2),
        PortListener(files, portAccList, 3)
    )
    var isConnected = false
    var isRecorded = false


    class PortListener(var files: Array<OutputStreamWriter?>, var portAccList: Array<TextView?>, val portNum: Int) : SerialInputOutputManager.Listener{
        val e2boxChecker =
            """\*-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*\r\n""".toRegex()
        var cnt = 0
        override fun onRunError(e: Exception){

        }
        override fun onNewData(data: ByteArray){
            val strData = String(data)
            var lastTime = System.currentTimeMillis()
            Log.i("UART", "P${portNum + 1}: ${strData}\n")
            if(e2boxChecker.matchEntire(strData) != null) {
                try {
                    files[portNum]?.write("${strData.split('*').last().trim()}\n")
                } catch (e: IOException) { }
                //Log.i("UART", "delta Time: ${System.currentTimeMillis() - lastTime }")
                lastTime = System.currentTimeMillis()

                cnt = (++cnt).rem(50)
                if (cnt == 0) {
                    val out = strData.split('*').last().trim().split(',')
                    var sum = 0f
                    for (i in 4..6){
                        sum += out[i].toFloat().pow(2)
                    }
                    portAccList[portNum]?.text = "${sqrt(sum)}"
                }

            }else{
                val wrapper = ByteBuffer.wrap(data)
                var startIdx = 0
                while(startIdx < data.size) {
                    if (data[startIdx+0] == 0xff.toByte() && data[startIdx+1] == 0xff.toByte()) {
                        val testChecksum =
                            data.slice((startIdx+2)..(startIdx+44)).fold(0, { acc: Byte, other: Byte -> acc.xor(other) })
                        if (testChecksum == data[startIdx+45]) {
                            Log.i("UART", "Correct!")
                            val counter = data[startIdx + 2].toUShort() + data[startIdx + 3].toUShort() * 256u
                            val dataValue = (5..41 step 4).map{i -> wrapper.getFloat(i)}
                            try {
                                files[portNum]?.write("$counter ${dataValue[6]} ${dataValue[7]} ${dataValue[8]} ${dataValue[9]} ${dataValue[0]} ${dataValue[1]} ${dataValue[2]} ${dataValue[3]} ${dataValue[4]} ${dataValue[5]}\n")
                            } catch (e: IOException) { }
                        }
                    }
                    startIdx += 46
                }
            }
        }
    }

    fun checkSerialPort(){
        //Find available driver
        val usbManager: UsbManager =  getSystemService(Context.USB_SERVICE) as UsbManager
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()){
            Log.i("UART", "Cannot find any serial devices")
        }
        val layoutList = arrayOf(port1Layout, port2Layout, port3Layout, port4Layout)
        for (i in 0..3){
            if (i < availableDrivers.size)
                layoutList[i].visibility = View.VISIBLE
            else
                layoutList[i].visibility = View.GONE
        }
        swipe.isRefreshing = false

        for (i in 0..3.coerceAtMost(availableDrivers.size - 1)) {
            if(!usbManager.hasPermission(availableDrivers[i].device))
                usbManager.requestPermission(availableDrivers[i].device, PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0))
        }
    }

    fun connectSerialPort(){
        val usbManager: UsbManager =  getSystemService(Context.USB_SERVICE) as UsbManager

        if(availableDrivers.isEmpty())
            return

        swipe.isEnabled = false

        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        executor = Executors.newScheduledThreadPool(availableDrivers.size)
        for (i in 0..3.coerceAtMost(availableDrivers.size - 1)){
            var connection : UsbDeviceConnection? = usbManager.openDevice(availableDrivers[i].device)
            ports[i] = availableDrivers[i].ports[0]
            ports[i]?.open(connection)
            ports[i]?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbIoManager[i] = SerialInputOutputManager(ports[i], portListener[i])
            Log.i("UART", "PORT${i+1} connected: ${availableDrivers[i].device.productName}")
            executor?.scheduleAtFixedRate(usbIoManager[i], 0, 10, TimeUnit.MILLISECONDS)
        }
        connectButton.isEnabled = false

        if (availableDrivers.size > 0)
            recordButton.isEnabled = true
    }

    fun startRecordFile(){
        val name = nameText.text
        val date = Date()
        val calendar = GregorianCalendar()
        calendar.time = date
        val commonFileName = "${name}_${calendar.get(Calendar.YEAR.rem(100))}" +
                "${calendar.get(Calendar.MONTH)}" +
                "${calendar.get(Calendar.DAY_OF_MONTH)}"

        val time = "${calendar.get(Calendar.HOUR_OF_DAY)}" +
                "${calendar.get(Calendar.MINUTE)}" +
                "${calendar.get(Calendar.SECOND)}"

        val partArray = resources.getStringArray(R.array.body_part)

        for (i in availableDrivers.indices) {
            if (portPartList[i]?.visibility == View.GONE)
                continue
            files[i] = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "${commonFileName}_Port${i}_${portPartList[i]?.selectedItem}_$time.txt")
                .writer()
        }
    }

    fun stopRecordFile() {
        for (i in portPartList.indices) {
            files[i]?.close()
            files[i] = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        portPartList[0] = port1Part
        portPartList[1] = port2Part
        portPartList[2] = port3Part
        portPartList[3] = port4Part

        portAccList[0] = port1Value
        portAccList[1] = port2Value
        portAccList[2] = port3Value
        portAccList[3] = port4Value

        checkSerialPort()
        swipe.setOnRefreshListener { checkSerialPort() }

        port1Part.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.body_part))
        port2Part.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.body_part))
        port3Part.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.body_part))
        port4Part.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, resources.getStringArray(R.array.body_part))

        connectButton.setOnClickListener{connectSerialPort()}
        recordButton.setOnClickListener{
            isRecorded = !isRecorded
            runOnUiThread {
                if (isRecorded) {
                    startRecordFile()
                    recordButton.text = "Stop"
                } else {
                    stopRecordFile()
                    recordButton.text = "Rec"
                }
            }
        }
        
    }

    override fun onDestroy() {
        super.onDestroy()

        //close all serial ports
        for (i in ports.indices){
            if(ports[i] != null) {
                ports[i]?.close()
                files[i]?.close()
                Log.i("UART", "close serial $i")
            }
        }
        executor?.shutdown()
    }
}
