package com.jdcr.baseble.core.communication.write

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import androidx.annotation.IntDef
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.flow.SharedFlow
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
        @WriteType open val writeType: Int? = null
    ) {

        data class StringData(
            override val address: String?,
            override val serviceUuid: String,
            override val characterUuid: String,
            val data: String,
            @WriteType override val writeType: Int? = null
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
            @WriteType override val writeType: Int? = null
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
            if (!writeSinglePacket(operation, it)) {
                BleLog.w("写入失败,直接返回:" + it.address + "," + it.characterUuid)
                return false
            }
        }
        return true
    }

    private suspend fun writeSinglePacket(
        operation: BleCommunicateOperation.Write,
        byteData: WriteData.ByteData
    ): Boolean {
        return getTimeoutCancelableCoroutine(
            operation.address,
            operation.characterUuid.uppercase()
        ) { continuation ->
            registerOneShotCallback(operation.writeData.characterUuid.uppercase(), continuation)
            val success = executeWritePacket(byteData)
            if (!success) {
                unregisterOneShotCallback(operation.writeData.characterUuid.uppercase())
                if (continuation.isActive) continuation.resume(false, null)
            }
        }
    }

    override fun getWriteResultFlow(): SharedFlow<BleOperationResult.Write> {
        return getDataFlow()
    }

    @SuppressLint("MissingPermission")
    override fun writeData(data: WriteData) {
        sendOperation(BleCommunicateOperation.Write(data))
    }

    private fun executeWritePacket(data: WriteData.ByteData): Boolean {
        val gatt = core.getGatt(data.address)
        if (gatt == null) {
            BleLog.e("GATT为空，无法写入数据:${data.characterUuid}")
            return false
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
                return success
            } else {
                BleLog.e("写入时没有权限")
            }
        } else {
            BleLog.e("写入时未找到特征值:${data.characterUuid}")
        }
        return false
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