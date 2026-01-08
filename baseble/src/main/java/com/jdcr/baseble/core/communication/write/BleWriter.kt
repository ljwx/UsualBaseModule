package com.jdcr.baseble.core.communication.write

import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.core.communication.write.BluetoothDeviceWrite.WriteData
import kotlinx.coroutines.flow.SharedFlow

interface BleWriter {

    fun writeData(data: WriteData)

    fun onWriteResult(result: BleOperationResult.Write)

    fun getWriteResultFlow(): SharedFlow<BleOperationResult.Write>

}