package com.jdcr.baseble.core.communication

import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleCommunicateOperation
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.core.communication.notify.BleNotifier
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification
import com.jdcr.baseble.core.communication.read.BleReader
import com.jdcr.baseble.core.communication.read.BluetoothDeviceRead
import com.jdcr.baseble.core.communication.write.BleWriter
import com.jdcr.baseble.core.communication.write.BluetoothDeviceWrite
import com.jdcr.baseble.util.BleLog
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class BluetoothDeviceCommunicationHandler(
    private val core: BluetoothDeviceCore,
    bleNotify: BluetoothDeviceNotification,
    bleWrite: BluetoothDeviceWrite,
    bleRead: BluetoothDeviceRead
) {

    init {

        fun removeExceptionOperation(characterUuid: String) {
            core.pendingOperations[characterUuid]?.apply {
                if (isActive) {
                    resume(false, null)
                }
            }
            core.pendingOperations.remove(characterUuid)
        }

        core.getScope().launch {
            for (op in core.operationChannel) {
                try {
                    BleLog.i("2执行队列:" + op.getDisplayTag())
                    withTimeout(core.getConfig().communicate.timeoutMills + 500) {
                        when (op) {
                            is BleCommunicateOperation.Write -> {
                                val success = bleWrite.performWriteSuspend(op)
                                BleOperationResult.Write(op.address, success, op.characterUuid, 0)
                                    .apply {
                                        bleWrite.emmitData(this)
                                    }
                            }

                            is BleCommunicateOperation.Read -> {
                                bleRead.performReadSuspend(op)
                            }

                            is BleCommunicateOperation.Notify -> {
                                bleNotify.performEnableNotifySuspend(op)
                            }
                        }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    BleLog.e("2.5执行队列任务时超时:${op.characterUuid}")
                } catch (e: Exception) {
                    BleLog.e("2.5执行队列任务时异常:${op.characterUuid},$e")
                } finally {
                    removeExceptionOperation(op.characterUuid.uppercase())
                }
            }
        }
    }

    val notify: BleNotifier = bleNotify

    val write: BleWriter = bleWrite

    val read: BleReader = bleRead

}