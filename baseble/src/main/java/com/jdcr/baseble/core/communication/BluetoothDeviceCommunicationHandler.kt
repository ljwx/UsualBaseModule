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

        fun removeExceptionOperation(address: String?, characterUuid: String) {
            val finalAddress = core.getFinalAddress(address) ?: "null"
            val key = "${finalAddress}_${characterUuid.uppercase()}"
            core.pendingOperations[key]?.apply {
                if (isActive) {
                    resume(Result.failure(Exception("执行异常")), null)
                }
            }
            core.pendingOperations.remove(key)
        }

        core.getScope().launch {
            for (op in core.operationChannel) {
                var writeResult: BleOperationResult.Write? = null
                var readResult: BleOperationResult.Read? = null
                var enableNotifyResult: BleOperationResult.EnableNotification? = null
                try {
                    BleLog.i("2执行队列:" + op.getDisplayTag())
                    withTimeout(core.getConfig().communicate.timeoutMills + 500) {
                        when (op) {
                            is BleCommunicateOperation.Write -> {
                                val success = bleWrite.performWriteSuspend(op)
                                BleLog.i("写入的最终结果," + success + "," + op.characterUuid)
                                writeResult = BleOperationResult.Write(
                                    op.address,
                                    success,
                                    op.characterUuid,
                                    0
                                )
                            }

                            is BleCommunicateOperation.Read -> {
                                bleRead.performReadSuspend(op).onSuccess {
                                    if (it is BleOperationResult.Read) {
                                        readResult = it
                                    }
                                }
                            }

                            is BleCommunicateOperation.Notify -> {
                                bleNotify.performEnableNotifySuspend(op).onSuccess {
                                    if (it is BleOperationResult.EnableNotification) {
                                        enableNotifyResult = it
                                    }
                                }
                            }
                        }
                    }
                } catch (timeout: TimeoutCancellationException) {
                    BleLog.e("2.5执行队列任务时超时:${op.characterUuid}")
                    if (op is BleCommunicateOperation.Write) {
                        writeResult =
                            BleOperationResult.Write(op.address, false, op.characterUuid, -2)
                    }
                    if (op is BleCommunicateOperation.Read) {
                        readResult =
                            BleOperationResult.Read(op.address, false, op.characterUuid, null, -2)
                    }
                    if (op is BleCommunicateOperation.Notify) {
                        enableNotifyResult =
                            BleOperationResult.EnableNotification(
                                op.address,
                                op.characterUuid,
                                op.notifyData.notificationUuid,
                                false
                            )
                    }
                } catch (e: Exception) {
                    BleLog.e("2.5执行队列任务时异常:${op.characterUuid},$e")
                    if (op is BleCommunicateOperation.Write) {
                        writeResult =
                            BleOperationResult.Write(op.address, false, op.characterUuid, -1)
                    }
                    if (op is BleCommunicateOperation.Read) {
                        readResult =
                            BleOperationResult.Read(op.address, false, op.characterUuid, null, -1)
                    }
                    if (op is BleCommunicateOperation.Notify) {
                        enableNotifyResult =
                            BleOperationResult.EnableNotification(
                                op.address,
                                op.characterUuid,
                                op.notifyData.notificationUuid,
                                false
                            )
                    }
                } finally {
                    if (op is BleCommunicateOperation.Write) {
                        val finalResult = writeResult ?: BleOperationResult.Write(
                            op.address,
                            false,
                            op.characterUuid,
                            -3
                        )
                        op.deferred?.complete(finalResult)
                        op.callback?.invoke(finalResult)
                        bleWrite.emmitData(finalResult)
                    }
                    if (op is BleCommunicateOperation.Read) {
                        val finalResult = readResult ?: BleOperationResult.Read(
                            op.address,
                            false,
                            op.characterUuid,
                            null,
                            -3
                        )
                        op.deferred?.complete(finalResult)
                        op.callback?.invoke(finalResult)
                    }
                    if (op is BleCommunicateOperation.Notify) {
                        val finalResult =
                            enableNotifyResult ?: BleOperationResult.EnableNotification(
                                op.address,
                                op.characterUuid,
                                op.notifyData.notificationUuid,
                                false
                            )
                        op.deferred?.complete(finalResult)
                        op.callback?.invoke(finalResult)
                    }
                    removeExceptionOperation(op.address, op.characterUuid)
                }
            }
        }
    }

    val notify: BleNotifier = bleNotify

    val write: BleWriter = bleWrite

    val read: BleReader = bleRead

}