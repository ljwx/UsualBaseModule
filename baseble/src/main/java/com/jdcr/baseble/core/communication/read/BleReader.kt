package com.jdcr.baseble.core.communication.read

import com.jdcr.baseble.core.communication.BleCommunicationBase
import kotlinx.coroutines.flow.SharedFlow

interface BleReader {

    fun requestReadData(data: BluetoothDeviceRead.RequestReadData)

    fun onReadResult(result: BleCommunicationBase.BleOperationResult.Read)

    fun getReadResultFlow(): SharedFlow<BleCommunicationBase.BleOperationResult.Read>

}