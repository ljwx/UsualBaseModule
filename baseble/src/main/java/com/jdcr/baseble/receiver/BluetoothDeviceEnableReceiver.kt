package com.jdcr.baseble.receiver

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.jdcr.baseble.util.BleLog

class BluetoothDeviceEnableReceiver(private var listener: ((enable: Boolean) -> Unit)?) :
    BroadcastReceiver() {

    companion object {

        private var receiver: BluetoothDeviceEnableReceiver? = null

        fun registerEnable(
            context: Context,
            listener: ((enable: Boolean) -> Unit)?
        ): BluetoothDeviceEnableReceiver {
            unregisterEnable(context)
            BleLog.i("注册蓝牙开关变化广播")
            val receiver = BluetoothDeviceEnableReceiver(listener)
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
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            BleLog.i("蓝牙开关变化:$state(12开,10关)")
            when (state) {
                BluetoothAdapter.STATE_ON -> listener?.invoke(true)
                BluetoothAdapter.STATE_OFF -> listener?.invoke(false)
            }
        }
    }

    fun release(context: Context) {
        unregisterEnable(context)
        listener = null
        receiver = null
        BleLog.i("注销蓝牙开关监听")
    }

}