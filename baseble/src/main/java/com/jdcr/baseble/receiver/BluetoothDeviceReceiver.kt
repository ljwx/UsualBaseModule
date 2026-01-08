package com.jdcr.baseble.receiver

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class BluetoothDeviceReceiver {

    private var receiver: BroadcastReceiver? = null

    fun setReceiver(receiver: BroadcastReceiver?) {
        this.receiver = receiver
    }

    fun registerReceiver(context: Context, listener: ((device: BluetoothDevice) -> Unit)?) {
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        val receiverFinal =
            if (listener != null || this.receiver == null) object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    if (BluetoothDevice.ACTION_FOUND == action) {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (device != null) {
                            listener?.invoke(device)
                        }
                    }
                }

            } else this.receiver
        context.registerReceiver(receiverFinal, filter)
    }

}