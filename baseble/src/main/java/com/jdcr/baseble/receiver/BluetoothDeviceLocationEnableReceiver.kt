package com.jdcr.baseble.receiver

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils

class BluetoothDeviceLocationEnableReceiver(private var listener: ((enable: Boolean) -> Unit)?) :
    BroadcastReceiver() {

    companion object {

        private var receiver: BluetoothDeviceLocationEnableReceiver? = null

        fun registerEnable(
            context: Context,
            listener: ((enable: Boolean) -> Unit)?
        ): BluetoothDeviceLocationEnableReceiver {
            unregisterEnable(context)
            BleLog.i("注册定位开关变化广播")
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
            context ?: return
            val isLocationEnable = BluetoothDeviceUtils.isLocationEnable(context)
            BleLog.i("定位开关变化,是否开启:$isLocationEnable")
            listener?.invoke(isLocationEnable)
        }
    }

    fun release(context: Context) {
        unregisterEnable(context)
        listener = null
        receiver = null
        BleLog.i("注销定位开关监听")
    }

}