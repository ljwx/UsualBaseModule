package com.jdcr.baseble.core.communication.read

import com.jdcr.baseble.core.communication.BleCommunicationBase
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import kotlinx.coroutines.flow.SharedFlow

interface BleReader {

    fun requestReadData(
        data: BluetoothDeviceRead.RequestReadData,
        callback: ((result: BleOperationResult.Read) -> Unit)?
    )

    suspend fun requestReadData(data: BluetoothDeviceRead.RequestReadData): BleOperationResult.Read

    fun onReadResult(result: BleCommunicationBase.BleOperationResult.Read)

    fun getReadResultFlow(): SharedFlow<BleCommunicationBase.BleOperationResult.Read>

}