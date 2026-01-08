package com.jdcr.baseble.core.communication.notify

import android.bluetooth.BluetoothGattDescriptor
import android.os.Build
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

class BluetoothDeviceNotification(private val core: BluetoothDeviceCore) :
    BleCommunicationBase<BluetoothDeviceNotification.NotificationData>(core),
    BleNotifier {

    data class EnableNotificationData(
        val address: String?,
        val serviceUuid: String,
        val characterUuid: String,
        val notificationUuid: String,
        val isIndicationValue: Boolean = false
    )

    data class NotificationData(
        val address: String?,
        val characterUUID: String,
        val value: ByteArray?
    )

    suspend fun performEnableNotifySuspend(operation: BleCommunicateOperation.Notify): Boolean {
        return getTimeoutCancelableCoroutine(
            operation.address,
            operation.characterUuid.uppercase()
        ) { continuation ->
            registerOneShotCallback(operation.notifyData.characterUuid.uppercase(), continuation)
            val success = executeEnableNotification(operation.notifyData)
            if (!success) {
                unregisterOneShotCallback(operation.notifyData.characterUuid.uppercase())
                if (continuation.isActive) continuation.resume(false, null)
            }
        }
    }

    override fun getNotifyDataFlow(): SharedFlow<NotificationData> {
        return getDataFlow()
    }

    override fun enableNotification(
        data: EnableNotificationData
    ) {
        sendOperation(BleCommunicateOperation.Notify(data))
    }

    override fun onEnableNotificationResult(result: BleOperationResult.EnableNotification) {
        onOperationResult(result)
    }

    private fun executeEnableNotification(data: EnableNotificationData): Boolean {
        val gatt = core.getGatt(data.address)
        if (gatt == null) {
            BleLog.e("GATT为空，无法开启通知:${data.characterUuid}")
            return false
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
                        return false
                    }
                    return true
                }
            }
        } else {
            BleLog.e("开启监听未找到特征值:${data.characterUuid}")
        }
        return false
    }

    override fun onNotification(
        data: NotificationData
    ) {
        onReceiveData(data)
    }

}