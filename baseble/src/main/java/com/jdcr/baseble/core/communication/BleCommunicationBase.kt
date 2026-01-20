package com.jdcr.baseble.core.communication

import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification
import com.jdcr.baseble.core.communication.read.BluetoothDeviceRead
import com.jdcr.baseble.core.communication.write.BluetoothDeviceWrite
import com.jdcr.baseble.util.BleLog
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

open class BleCommunicationBase<Data>(private val core: BluetoothDeviceCore) {

    sealed class BleCommunicateOperation(val address: String?, val characterUuid: String) {
        data class Write(
            val writeData: BluetoothDeviceWrite.WriteData,
            val deferred: CompletableDeferred<BleOperationResult.Write>? = null,
            val callback: ((result: BleOperationResult.Write) -> Unit)? = null
        ) : BleCommunicateOperation(writeData.address, writeData.characterUuid)

        data class Read(
            val readData: BluetoothDeviceRead.RequestReadData,
            val deferred: CompletableDeferred<BleOperationResult.Read>? = null,
            val callback: ((result: BleOperationResult.Read) -> Unit)? = null
        ) : BleCommunicateOperation(readData.address, readData.characterUuid)

        data class Notify(
            val notifyData: BluetoothDeviceNotification.EnableNotificationData
        ) : BleCommunicateOperation(notifyData.address, notifyData.characterUuid)

        fun getDisplayTag(): String {
            return javaClass.simpleName + ",address:" + (address
                ?: "") + ",characterUuid:" + characterUuid
        }

    }

    sealed class BleOperationResult(
        open val address: String?,
        open val characterUuid: String,
        open val success: Boolean
    ) {
        data class EnableNotification(
            override val address: String?,
            override val characterUuid: String,
            val notificationUuid: String,
            override val success: Boolean
        ) : BleOperationResult(address, characterUuid, success)

        data class Read(
            override val address: String?,
            override val success: Boolean,
            override val characterUuid: String,
            val value: ByteArray?,
            val status: Int? = null
        ) : BleOperationResult(address, characterUuid, success)

        data class Write(
            override val address: String?,
            override val success: Boolean,
            override val characterUuid: String,
            val status: Int
        ) : BleOperationResult(address, characterUuid, success)

        fun getDisplayTag(): String {
            return javaClass.simpleName + "," + success + ",address:" + (address
                ?: "") + ",characterUuid:" + characterUuid
        }

    }

    private val _communicateFlow = MutableSharedFlow<Data>(
        replay = 1,
        extraBufferCapacity = 30,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val communicateFlow: SharedFlow<Data> = _communicateFlow.asSharedFlow()

    suspend fun getTimeoutCancelableCoroutine(
        address: String?,
        characterUuid: String,
        block: (CancellableContinuation<Result<BleOperationResult>>) -> Unit
    ): Result<BleOperationResult> {
        return try {
            withTimeout(core.getConfig().communicate.timeoutMills) {
                suspendCancellableCoroutine(block)
            }
        } catch (e: TimeoutCancellationException) {
            BleLog.e("操作超时:$address,$characterUuid")
            unregisterOneShotCallback(characterUuid.uppercase())
            Result.failure(e)
        } catch (e: Exception) {
            BleLog.e("操作异常::$address,$characterUuid,$e")
            unregisterOneShotCallback(characterUuid.uppercase())
            Result.failure(e)
        }
    }

    protected fun sendOperation(operation: BleCommunicateOperation) {
        BleLog.i("1加入队列," + operation.getDisplayTag())
        core.operationChannel.trySend(operation)
    }

    protected fun registerOneShotCallback(
        characterUuid: String,
        continuation: CancellableContinuation<Result<BleOperationResult>>
    ) {
        val old = core.pendingOperations.remove(characterUuid.uppercase())
        if (old != null) {
            BleLog.d("取消上一步的结果等待:$characterUuid")
            old.cancel()
        }
        BleLog.d("3添加结果等待:$characterUuid")
        core.pendingOperations[characterUuid.uppercase()] = continuation
        continuation.invokeOnCancellation {
            BleLog.d("结果等待被取消¬:$characterUuid")
            core.pendingOperations.remove(characterUuid.uppercase())
        }
    }

    protected fun unregisterOneShotCallback(characterUuid: String) {
        core.pendingOperations.remove(characterUuid.uppercase())
    }

    protected fun onOperationResult(result: BleOperationResult) {
        val continuation = core.pendingOperations.remove(result.characterUuid.uppercase())
        if (continuation != null && continuation.isActive) {
            BleLog.d("5收到等待结果:" + result.getDisplayTag())
            continuation.resume(Result.success(result), null)
        } else {
            BleLog.d("5收到结果回调，但没有挂起的任务在等待: ${result.characterUuid}")
        }
    }

    fun emmitData(result: Data) {
        _communicateFlow.tryEmit(result)
    }

    protected fun onReceiveData(data: Data) {
        _communicateFlow.tryEmit(data)
    }

    protected fun getDataFlow(): SharedFlow<Data> {
        return communicateFlow
    }

}