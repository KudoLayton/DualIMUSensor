package com.example.dualimusensor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.lang.Exception
import java.nio.file.Paths
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


private const val ACTION_USB_PERMISSION = "com.example.dualimusensor.USB_PERMISSION"

class MainActivity : AppCompatActivity() {
    private val executorList: Array<ExecutorService> = arrayOf(
        Executors.newSingleThreadExecutor(),
        Executors.newSingleThreadExecutor()
    )

    var ports : Array<UsbSerialPort?> = arrayOfNulls(2)
    var usbIoManager : Array<SerialInputOutputManager?> = arrayOfNulls(2)
    var files : Array<FileWriter?> = arrayOfNulls(2)

    class PortListener(var fileList: Array<FileWriter?>,val portNum: Int) : SerialInputOutputManager.Listener{
        override fun onRunError(e: Exception){

        }
        override fun onNewData(data: ByteArray){
            //Log.i("UART", "${String(data)}\n")
        }
    }
    val port1Listener = PortListener(files, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "text.txt")
        var writer = file.writer()
        if(file.canWrite()) {
            writer.write("test")
            Log.i("UART", "Write!")
            writer.close()
        }
        getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        Log.i("UART", "${getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)}")

        //Find available driver
        val usbManager: UsbManager =  getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers : List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()){
            Log.i("UART", "Cannot find any serial devices")
            return
        }

        for (i in 0..1.coerceAtMost(availableDrivers.size - 1)){
            //Get a device connection, or get a permission of device
            var connection : UsbDeviceConnection? = usbManager.openDevice(availableDrivers[i].device)
            if(connection == null){
                usbManager.requestPermission(availableDrivers[i].device, PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0))
                connection = usbManager.openDevice(availableDrivers[i].device)
            }

            //open ports
            ports[i] = availableDrivers[i].ports[0]
            ports[i]?.open(connection)
            ports[i]?.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            usbIoManager[i] = SerialInputOutputManager(ports[i])
            Executors.newSingleThreadExecutor()
            Log.i("UART", "serial $i open Driver-${availableDrivers[i].device.productName}")
        }
        if(ports[0] != null) {
            usbIoManager[0] = SerialInputOutputManager(ports[0], port1Listener)
            executorList[0].submit(usbIoManager[0])
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        //close all serial ports
        for (i in ports.indices){
            if(ports[i] != null) {
                ports[i]?.close()
                Log.i("UART", "close serial $i")
            }
        }
    }
}
