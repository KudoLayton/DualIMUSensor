package com.example.dualimusensor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.file.Paths
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.experimental.xor

private const val ACTION_USB_PERMISSION = "com.example.dualimusensor.USB_PERMISSION"

class MainActivity : AppCompatActivity() {
    private val executorList: Array<ExecutorService> = arrayOf(
        Executors.newSingleThreadExecutor(),
        Executors.newSingleThreadExecutor()
    )

    var ports : Array<UsbSerialPort?> = arrayOfNulls(2)
    var usbIoManager : Array<SerialInputOutputManager?> = arrayOfNulls(2)
    var files : Array<OutputStreamWriter?> = arrayOfNulls(2)


    class PortListener(var files: Array<OutputStreamWriter?>, val portNum: Int) : SerialInputOutputManager.Listener{
        val e2boxChecker =
            """\*-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*,-?\d+.?\d*\r\n""".toRegex()
        override fun onRunError(e: Exception){

        }
        override fun onNewData(data: ByteArray){
            val strData = String(data)
            if(e2boxChecker.matchEntire(strData) != null) {
                Log.i("UART", "${String(data)}\n")
                try {
                    files[portNum]?.write("${strData.split('*').last().trim()}\n")
                } catch (e: IOException) { }
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

    val port1Listener = PortListener(files, 0)

    fun checkSerialPort(){
        //Find available driver
        val usbManager: UsbManager =  getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers : List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkSerialPort()
        swipe.setOnRefreshListener { checkSerialPort() }

        files[0] = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "port1Out.txt")
            .writer()

//        val usbManager: UsbManager =  getSystemService(Context.USB_SERVICE) as UsbManager
//        val availableDrivers : List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
//        if (availableDrivers.isEmpty()){
//            Log.i("UART", "Cannot find any serial devices")
//            return
//        }
//
//        for (i in 0..1.coerceAtMost(availableDrivers.size - 1)){
//            //Get a device connection, or get a permission of device
//            var connection : UsbDeviceConnection? = usbManager.openDevice(availableDrivers[i].device)
//            if(connection == null){
//                usbManager.requestPermission(availableDrivers[i].device, PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0))
//                connection = usbManager.openDevice(availableDrivers[i].device)
//            }
//
//            //open ports
//            ports[i] = availableDrivers[i].ports[0]
//            ports[i]?.open(connection)
//            ports[i]?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
//            usbIoManager[i] = SerialInputOutputManager(ports[i])
//            Executors.newSingleThreadExecutor()
//            Log.i("UART", "serial $i open Driver-${availableDrivers[i].device.productName}")
//        }
//        if(ports[0] != null) {
//            usbIoManager[0] = SerialInputOutputManager(ports[0], port1Listener)
//            executorList[0].submit(usbIoManager[0])
//        }
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
    }
}
