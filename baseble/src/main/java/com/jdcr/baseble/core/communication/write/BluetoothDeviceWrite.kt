package com.jdcr.baseble.core.communication.write

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import androidx.annotation.IntDef
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.core.exception.PermissionDineException
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeout
import java.nio.charset.StandardCharsets
import java.util.UUID

const val WRITE_TYPE_DEFAULT = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
const val WRITE_TYPE_NO_RESPONSE = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
const val WRITE_TYPE_SIGNED = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
@IntDef(WRITE_TYPE_DEFAULT, WRITE_TYPE_NO_RESPONSE, WRITE_TYPE_SIGNED)
@Retention(AnnotationRetention.SOURCE)
annotation class WriteType


class BluetoothDeviceWrite(private val core: BluetoothDeviceCore) :
    BleCommunicationBase<BleOperationResult.Write>(core),
    BleWriter {

    sealed class WriteData private constructor(
        open val address: String?,
        open val serviceUuid: String,
        open val characterUuid: String,
        @WriteType open val writeType: Int? = null,
    ) {

        data class StringData(
            override val address: String?,
            override val serviceUuid: String,
            override val characterUuid: String,
            val data: String,
            @WriteType override val writeType: Int? = null,
        ) : WriteData(address, serviceUuid, characterUuid, writeType) {
            override fun getPacketArray(): ByteArray {
                return data.toByteArray(StandardCharsets.UTF_8)
            }
        }

        data class ByteData(
            override val address: String?,
            override val serviceUuid: String,
            override val characterUuid: String,
            val byteArray: ByteArray,
            @WriteType override val writeType: Int? = null,
        ) : WriteData(address, serviceUuid, characterUuid, writeType) {
            override fun getPacketArray(): ByteArray {
                return byteArray
            }
        }

        open fun getPacketArray(): ByteArray {
            return byteArrayOf()
        }

        fun toByteData(): ByteData {
            return if (this is ByteData) {
                this
            } else {
                ByteData(address, serviceUuid, characterUuid, getPacketArray(), writeType)
            }
        }

    }

    suspend fun performWriteSuspend(operation: BleCommunicateOperation.Write): Boolean {
        val packets = getSplitPacketArray(operation.writeData)
        packets.forEach {
            if (!writeSinglePacket(operation, it).isSuccess) {
                BleLog.w("写入失败,直接返回:" + it.address + "," + it.characterUuid)
                return false
            }
        }
        return true
    }

    private suspend fun writeSinglePacket(
        operation: BleCommunicateOperation.Write,
        byteData: WriteData.ByteData
    ): Result<BleOperationResult> {
        if (byteData.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
            val success = executeWritePacket(byteData).isSuccess
            if (success) delay(10)
            return Result.success(
                BleOperationResult.Write(
                    operation.address,
                    success,
                    operation.characterUuid.uppercase(),
                    0
                )
            )
        }
        return getTimeoutCancelableCoroutine(
            operation.address,
            operation.characterUuid.uppercase()
        ) { continuation ->
            registerOneShotCallback(operation.address, operation.writeData.characterUuid, continuation)
            executeWritePacket(byteData).onFailure {
                unregisterOneShotCallback(operation.address, operation.writeData.characterUuid)
                if (continuation.isActive) continuation.resume(Result.failure(it), null)
            }
        }
    }

    override fun getWriteResultFlow(): SharedFlow<BleOperationResult.Write> {
        return getDataFlow()
    }

    @SuppressLint("MissingPermission")
    override fun writeData(
        data: WriteData,
        callback: ((result: BleOperationResult.Write) -> Unit)?
    ) {
        sendOperation(BleCommunicateOperation.Write(data, callback = callback))
    }

    override suspend fun writeData(data: WriteData): BleOperationResult.Write {
        val deferred = CompletableDeferred<BleOperationResult.Write>()
        sendOperation(BleCommunicateOperation.Write(data, deferred = deferred))
        return try {
            withTimeout(core.getConfig().communicate.timeoutMills + 3000) {
                deferred.await()
            }
        } catch (e: Exception) {
            deferred.cancel()
            BleOperationResult.Write(data.address, false, data.characterUuid, -1)
        }
    }

    private fun executeWritePacket(data: WriteData.ByteData) : Result<Boolean> {
        val gatt = core.getGatt(data.address)
            ?: "GATT为空，无法写入数据:${data.characterUuid}".let {
                BleLog.e(it)
                return Result.failure(IllegalStateException(it))
            }
        val service = gatt.getService(UUID.fromString(data.serviceUuid))
        val character = service?.getCharacteristic(UUID.fromString(data.characterUuid))
        val packet = data.getPacketArray()
        if (character != null) {
            character.properties
            val writeType = data.writeType
                ?: when {
                    character.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    }

                    character.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }

                    else -> {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                }
            if (BluetoothDeviceUtils.checkConnectPermission(core.getApplicationContext())) {
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = gatt.writeCharacteristic(character, packet, writeType)
                    val success = status == BluetoothStatusCodes.SUCCESS
                    BleLog.i("4写入结果:$success,$status")
                    success
                } else {
                    character.value = packet
                    character.writeType = writeType
                    val success = gatt.writeCharacteristic(character)
                    BleLog.i("4写入结果:$success")
                    success
                }
                return if (success) Result.success(true) else Result.failure(Exception("操作失败"))
            } else {
                "写入时没有权限".let {
                    BleLog.e(it)
                    return Result.failure(PermissionDineException(it))
                }
            }
        } else {
            "写入时未找到特征值:${data.characterUuid}".let {
                BleLog.d(it)
                return Result.failure(IllegalStateException(it))
            }
        }
    }


    override fun onWriteResult(result: BleOperationResult.Write) {
        onOperationResult(result)
        if (result.success) {
            BleLog.d("数据写入成功: ${result.characterUuid}")
        } else {
            BleLog.d("数据写入失败: ${result.status}")
        }
    }

    private fun getSplitPacketArray(data: WriteData): Array<WriteData.ByteData> {
        val packets = data.getPacketArray()
        if (packets.size > core.maxPacketSize) {
            BleLog.d("需要分包:${packets.size},${core.maxPacketSize}")
            val list = mutableListOf<WriteData.ByteData>()
            var index = 0
            while (index < packets.size) {
                val end = (index + core.maxPacketSize).coerceAtMost(packets.size)
                val packet = packets.copyOfRange(index, end)
                val data = WriteData.ByteData(
                    data.address,
                    data.serviceUuid,
                    data.characterUuid,
                    packet,
                    data.writeType
                )
                list.add(data)
                index = end
            }
            BleLog.d("分包大小:" + list.size)
            return list.toTypedArray()
        } else {
            return arrayOf(data.toByteData())
        }
    }

}