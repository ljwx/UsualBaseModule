package com.jdcr.baseble.core.state

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import com.jdcr.baseble.util.safeName

sealed class BleAdapterState {
    abstract val desc: String

    data object Disable : BleAdapterState() {
        override val desc = "蓝牙未开启"
    }

    data object Idle : BleAdapterState() {
        override val desc = "闲置状态"
    }

    data class ScanDine(val versionName: String) : BleAdapterState() {
        override val desc = "没有扫描权限,ovVersion:$versionName"
    }

    data class ScanningList(
        val results: List<ScanResult>,
        val timestamp: Long = System.currentTimeMillis()
    ) : BleAdapterState() {
        override val desc = "扫描中,数据集"
    }

    data class ScanningSingle(
        val result: ScanResult?,
        val timestamp: Long = System.currentTimeMillis()
    ) : BleAdapterState() {
        override val desc = "扫描中,单个数据"
    }

    data class Finish(val success: Boolean, val reason: Int, val errorCode: Int? = null) :
        BleAdapterState() {
        companion object {
            const val REASON_MANUAL = 0
            const val REASON_TIMEOUT = 1
            const val REASON_ON_FAIL = 2
        }

        override val desc = "扫描结束,成功:$success,$reason"
    }
}

sealed class BleDeviceState() {

    abstract val desc: String

    data object Idle : BleDeviceState() {
        override val desc = "闲置未使用"
    }

    data class Connecting(
        val device: BluetoothDevice,
        val address: String = device.address,
        val timestamp: Long = System.currentTimeMillis(),
    ) : BleDeviceState() {
        override val desc = "连接中:" + device.safeName() + "," + address
    }

    data class Connected(
        val device: BluetoothDevice,
        val address: String = device.address,
        val timestamp: Long = System.currentTimeMillis()
    ) : BleDeviceState() {
        override val desc = "硬件连接成功:" + device.safeName() + "," + address
    }

    data class DiscoveringServices(
        val device: BluetoothDevice,
        val address: String = device.address,
        val timestamp: Long = System.currentTimeMillis()
    ) : BleDeviceState() {
        override val desc = "服务启动中:" + device.safeName() + "," + address
    }

    data class ModifyMtu(
        val device: BluetoothDevice,
        val requestMut: Int,
        val address: String = device.address,
        val timestamp: Long = System.currentTimeMillis()
    ) : BleDeviceState() {
        override val desc = "修改mtu中:" + device.safeName() + "," + address + ",mtu≈" + requestMut
    }

    data class Ready(
        val device: BluetoothDevice,
        val services: List<BluetoothGattService>,
        val address: String = device.address,
        val mtu: Int = 23,
        val timestamp: Long = System.currentTimeMillis()
    ) : BleDeviceState() {
        override val desc = "通信服务已可用:" + device.safeName() + "," + address + ",mtu=" + mtu
    }

    data class Disconnecting(
        val device: BluetoothDevice,
        val address: String = device.address,
        val timestamp: Long = System.currentTimeMillis()
    ) : BleDeviceState() {
        override val desc = "断开连接中:" + device.safeName() + "," + address
    }

    data class Disconnected(
        val device: BluetoothDevice,
        val fromState: BleDeviceState = Idle,
        val status: Int,
        val address: String = device.address,
        val timestamp: Long = System.currentTimeMillis(),
    ) : BleDeviceState() {
        override val desc =
            "设备已断开:" + device.safeName() + "," + address + ",status=" + status + ",fromState=" + fromState.javaClass.simpleName

        fun isExceptionDisconnect(): Boolean {
            return status != BluetoothGatt.GATT_SUCCESS
        }

    }

}

sealed class CurrentState() {
    data object LocationPermissionDine : CurrentState()
    data object LocationDisable : CurrentState()
    data object BlePermissionDine : CurrentState()
    data object BleDisable : CurrentState()
    data object NoSupport : CurrentState()
//    data object UnKnow : CurrentState()
    data object Ready : CurrentState()
}