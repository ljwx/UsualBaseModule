package com.jdcr.baseble.core.communication.read

import android.annotation.SuppressLint
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

open class BluetoothDeviceRead(private val core: BluetoothDeviceCore) :
    BleCommunicationBase<BleOperationResult.Read>(core),
    BleReader {

    data class RequestReadData(
        val address: String?,
        val serviceUuid: String,
        val characterUuid: String
    )

    suspend fun performReadSuspend(operation: BleCommunicateOperation.Read): Boolean {
        return getTimeoutCancelableCoroutine(
            operation.address,
            operation.characterUuid.uppercase()
        ) { continuation ->
            registerOneShotCallback(operation.readData.characterUuid.uppercase(), continuation)
            val success = executeReadData(operation.readData)
            if (!success) {
                unregisterOneShotCallback(operation.readData.characterUuid.uppercase())
                if (continuation.isActive) continuation.resume(false, null)
            }
        }
    }


    override fun getReadResultFlow(): SharedFlow<BleOperationResult.Read> {
        return getDataFlow()
    }

    override fun onReadResult(result: BleOperationResult.Read) {
        onOperationResult(result)
        onReceiveData(result)
        if (result.success) {
            BleLog.d("读取数据成功:${result.characterUuid}")
        } else {
            BleLog.d("读取数据失败:${result.characterUuid}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestReadData(
        data: RequestReadData
    ) {
        sendOperation(BleCommunicateOperation.Read(data))
    }

    private fun executeReadData(data: RequestReadData): Boolean {
        val gatt = core.getGatt(data.address)
        if (gatt == null) {
            BleLog.e("GATT为空，无法读取数据:${data.characterUuid}")
            return false
        }
        val service = gatt.getService(UUID.fromString(data.serviceUuid))
        val character = service?.getCharacteristic(UUID.fromString(data.characterUuid))
        if (character != null) {
            if (BluetoothDeviceUtils.checkConnectPermission(core.getApplicationContext())) {
                val success = gatt.readCharacteristic(character)
                BleLog.d("4发起读取数据请求:$success,${data.characterUuid}")
                return success
            }
        } else {
            BleLog.d("读取数据特征值为空:${data.characterUuid}")
        }
        return false
    }

}