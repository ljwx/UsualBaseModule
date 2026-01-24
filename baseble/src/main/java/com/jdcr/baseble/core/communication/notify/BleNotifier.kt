package com.jdcr.baseble.core.communication.notify

import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification.EnableNotificationData
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification.NotificationData
import kotlinx.coroutines.flow.SharedFlow

interface BleNotifier {

    fun enableNotification(
        data: EnableNotificationData,
        callback: ((result: BleOperationResult.EnableNotification) -> Unit)?,
    )

    suspend fun enableNotification(data: EnableNotificationData): BleOperationResult.EnableNotification

    fun onEnableNotificationResult(result: BleOperationResult.EnableNotification)

    fun onNotification(data: NotificationData)

    fun getNotifyDataFlow(): SharedFlow<NotificationData>

    fun release()

}