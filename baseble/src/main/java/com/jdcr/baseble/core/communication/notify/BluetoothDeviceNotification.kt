package com.jdcr.baseble.core.communication.notify

import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase
import com.jdcr.baseble.core.exception.PermissionDineException
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothDeviceNotification(private val core: BluetoothDeviceCore) :
    BleCommunicationBase<BluetoothDeviceNotification.NotificationData>(core),
    BleNotifier {

    data class EnableNotificationData(
        val address: String?,
        val serviceUuid: String,
        val characterUuid: String,
        val notificationUuid: String = "00002902-0000-1000-8000-00805f9b34fb",
        val isIndicationValue: Boolean = false,
        val interval: Long = 0
    )

    data class NotificationData(
        val address: String?,
        val serviceUuid: String?,
        val characterUuid: String,
        val value: ByteArray?
    )

    private val throttleIntervalMap = ConcurrentHashMap<String, Long>()
    private val lastEmitTimeMap = ConcurrentHashMap<String, Long>()

    suspend fun performEnableNotifySuspend(operation: BleCommunicateOperation.Notify): Result<BleOperationResult> {
        return getTimeoutCancelableCoroutine(
            operation.address,
            operation.characterUuid.uppercase()
        ) { continuation ->
            registerOneShotCallback(operation.address, operation.notifyData.characterUuid, continuation)
            executeEnableNotification(operation.notifyData).onFailure {
                unregisterOneShotCallback(operation.address, operation.notifyData.characterUuid)
                if (continuation.isActive) continuation.resume(Result.failure(it), null)
            }
        }
    }

    override fun getNotifyDataFlow(): SharedFlow<NotificationData> {
        return getDataFlow()
    }

    override fun enableNotification(
        data: EnableNotificationData,
        callback: ((result: BleOperationResult.EnableNotification) -> Unit)?
    ) {
        throttleNotify(data)
        sendOperation(BleCommunicateOperation.Notify(data, callback = callback))
    }

    override suspend fun enableNotification(data: EnableNotificationData): BleOperationResult.EnableNotification {
        val deferred = CompletableDeferred<BleOperationResult.EnableNotification>()
        throttleNotify(data)
        sendOperation(BleCommunicateOperation.Notify(data, deferred = deferred))
        return try {
            withTimeout(core.getConfig().communicate.timeoutMills + 3000) {
                deferred.await()
            }
        } catch (e: Exception) {
            deferred.cancel()
            BleOperationResult.EnableNotification(
                data.address,
                data.characterUuid,
                data.notificationUuid,
                false
            )
        }
    }

    override fun onEnableNotificationResult(result: BleOperationResult.EnableNotification) {
        onOperationResult(result)
    }

    private fun executeEnableNotification(data: EnableNotificationData): Result<Boolean> {
        val gatt = core.getGatt(data.address)
            ?: "GATT为空，无法开启通知:${data.characterUuid}".let {
                BleLog.e(it)
                return Result.failure(IllegalStateException(it))
            }
        val service = gatt.getService(UUID.fromString(data.serviceUuid))
        val character = service?.getCharacteristic(UUID.fromString(data.characterUuid))
        if (character != null) {
            if (BluetoothDeviceUtils.checkConnectPermission(core.getApplicationContext())) {
                BleLog.d("4执行开启通知:${data.characterUuid}")
                gatt.setCharacteristicNotification(character, true)
                val value =
                    if (data.isIndicationValue) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                character.getDescriptor(UUID.fromString(data.notificationUuid))?.apply {
                    val writeResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val result = gatt.writeDescriptor(this, value)
                        BleLog.d("写入指令结果:$result (0=成功),${data.characterUuid}")
                        result == 0
                    } else {
                        this.value = value
                        gatt.writeDescriptor(this)
                    }
                    if (!writeResult) {
                        BleLog.e("写入指令失败:${data.characterUuid}")
                        return Result.failure(Exception("操作失败"))
                    }
                    return Result.success(true)
                }
                "订阅通知特征值为空:${data.characterUuid}".let {
                    BleLog.d(it)
                    return Result.failure(IllegalStateException(it))
                }
            } else {
                "订阅通知时没有权限".let {
                    BleLog.e(it)
                    return Result.failure(PermissionDineException(it))
                }
            }
        } else {
            "开启监听未找到特征值:${data.characterUuid}".let {
                BleLog.d(it)
                return Result.failure(IllegalStateException(it))
            }
        }
    }

    private fun throttleNotify(data: EnableNotificationData) {
        val key = getTempKey(data.address, data.characterUuid)
        if (data.interval > 0) {
            throttleIntervalMap[key] = data.interval
        } else {
            throttleIntervalMap.remove(key)
        }
    }

    private fun getTempKey(address: String?, characterUuid: String): String {
        return "${address ?: ""}_${characterUuid.uppercase()}"
    }

    override fun onNotification(
        data: NotificationData
    ) {
        val key = getTempKey(data.address, data.characterUuid)
        val interval = throttleIntervalMap[key] ?: 0
        if (interval > 0) {
            val lastEmitTime = lastEmitTimeMap[key] ?: 0
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastEmitTime < interval) {
                return
            }
            lastEmitTimeMap[key] = currentTime
        }
        onReceiveData(data)
    }

    override fun release() {
        throttleIntervalMap.clear()
        lastEmitTimeMap.clear()
    }

}