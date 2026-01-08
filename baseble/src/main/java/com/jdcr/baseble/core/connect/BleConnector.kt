package com.jdcr.baseble.core.connect

import android.bluetooth.BluetoothDevice
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.state.BleDeviceState
import kotlinx.coroutines.flow.StateFlow

interface BleConnector {

    fun connect(address: String): Result<StateFlow<BleDeviceState>>
    fun connect(device: BluetoothDevice): Result<StateFlow<BleDeviceState>>

    fun disconnect(address: String?)
    fun disconnect()
}