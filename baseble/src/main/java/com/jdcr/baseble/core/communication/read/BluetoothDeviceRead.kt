package com.jdcr.baseble.core.communication.read

import android.annotation.SuppressLint
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.core.exception.PermissionDineException
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID

open class BluetoothDeviceRead(private val core: BluetoothDeviceCore) :
    BleCommunicationBase<BleOperationResult.Read>(core),
    BleReader {

    data class RequestReadData(
        val address: String?,
        val serviceUuid: String,
        val characterUuid: String
    )

    suspend fun performReadSuspend(operation: BleCommunicateOperation.Read): Result<BleOperationResult> {
        return getTimeoutCancelableCoroutine(
            operation.address,
            operation.characterUuid.uppercase()
        ) { continuation ->
            registerOneShotCallback(operation.address, operation.readData.characterUuid, continuation)
            executeReadData(operation.readData).onFailure {
                unregisterOneShotCallback(operation.address, operation.readData.characterUuid)
                if (continuation.isActive) continuation.resume(Result.failure(it), null)
            }
        }
    }


    override fun getReadResultFlow(): SharedFlow<BleOperationResult.Read> {
        return getDataFlow()
    }

    override fun onReadResult(result: BleOperationResult.Read) {
        onOperationResult(result)
        if (result.success) {
            BleLog.d("读取数据成功:${result.characterUuid}")
        } else {
            BleLog.d("读取数据失败:${result.characterUuid}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun requestReadData(
        data: RequestReadData,
        callback: ((result: BleOperationResult.Read) -> Unit)?
    ) {
        sendOperation(BleCommunicateOperation.Read(data, callback = callback))
    }

    override suspend fun requestReadData(data: RequestReadData): BleOperationResult.Read {
        val deferred = CompletableDeferred<BleOperationResult.Read>()
        sendOperation(BleCommunicateOperation.Read(data, deferred = deferred))
        return try {
            withTimeout(core.getConfig().communicate.timeoutMills + 3000) {
                deferred.await()
            }
        } catch (e: Exception) {
            deferred.cancel()
            BleOperationResult.Read(data.address, false, data.characterUuid, null, -1)
        }
    }

    private fun executeReadData(data: RequestReadData): Result<Boolean> {
        val gatt = core.getGatt(data.address)
            ?: "GATT为空，无法读取数据:${data.characterUuid}".let {
                BleLog.e(it)
                return Result.failure(IllegalStateException(it))
            }
        val service = gatt.getService(UUID.fromString(data.serviceUuid))
        val character = service?.getCharacteristic(UUID.fromString(data.characterUuid))
        if (character != null) {
            if (BluetoothDeviceUtils.checkConnectPermission(core.getApplicationContext())) {
                val success = gatt.readCharacteristic(character)
                BleLog.d("4发起读取数据请求:$success,${data.characterUuid}")
                return if (success) Result.success(true) else Result.failure(Exception("操作失败"))
            } else {
                "读取时没有权限".let {
                    BleLog.e(it)
                    return Result.failure(PermissionDineException(it))
                }
            }
        } else {
            "读取数据特征值为空:${data.characterUuid}".let {
                BleLog.d(it)
                return Result.failure(IllegalStateException(it))
            }
        }
    }

}