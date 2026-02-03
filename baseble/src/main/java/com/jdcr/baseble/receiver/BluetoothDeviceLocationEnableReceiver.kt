package com.jdcr.baseble.receiver

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager

class BluetoothDeviceLocationEnableReceiver(private var listener: ((enable: Boolean) -> Unit)?) :
    BroadcastReceiver() {

    companion object {

        private var receiver: BluetoothDeviceLocationEnableReceiver? = null

        fun registerEnable(
            context: Context,
            listener: ((enable: Boolean) -> Unit)?
        ): BluetoothDeviceLocationEnableReceiver {
            unregisterEnable(context)
            val receiver = BluetoothDeviceLocationEnableReceiver(listener)
            this.receiver = receiver
            context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            return receiver
        }

        fun unregisterEnable(context: Context) {
            try {
                receiver?.let { context.unregisterReceiver(receiver) }
            } catch (e: Exception) {

            }
            receiver = null
        }

    }

    override fun onReceive(context: Context?, intent: Intent) {
        if (intent.action == LocationManager.MODE_CHANGED_ACTION) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> listener?.invoke(true)
                BluetoothAdapter.STATE_OFF -> listener?.invoke(false)
            }
        }
    }

    fun release(context: Context) {
        unregisterEnable(context)
        listener = null
        receiver = null
    }

}