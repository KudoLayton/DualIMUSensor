package com.example.dualimusensor

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Spinner
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.lang.Math.min
import java.util.concurrent.Executors

private const val ACTION_USB_PERMISSION = "com.example.dualimusensor.USB_PERMISSION"
class MainActivity : AppCompatActivity() {
    var ports : Array<UsbSerialPort?> = arrayOfNulls(2);
    var usbIoManager : Array<SerialInputOutputManager?> = arrayOfNulls(2)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
