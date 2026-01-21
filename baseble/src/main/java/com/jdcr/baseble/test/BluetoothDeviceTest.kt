package com.jdcr.baseble.test

import android.app.Activity
import android.content.Context
import com.jdcr.baseble.BluetoothDeviceManager
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification
import com.jdcr.baseble.core.communication.read.BluetoothDeviceRead
import com.jdcr.baseble.core.communication.write.BluetoothDeviceWrite
import com.jdcr.baseble.core.state.BleAdapterState
import com.jdcr.baseble.core.state.BleDeviceState
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

object BluetoothDeviceTest {

    private lateinit var manager: BluetoothDeviceManager
    private val scop = GlobalScope

    private var connectJob: Job? = null

    fun init(context: Context) {
        manager = BluetoothDeviceManager.init(context)
        scop.launch {
            manager.getNotifyDataFlow()/*.sample(100)*/.collect { data ->
                data.value?.let { value ->
                    when (data.characterUUID.uppercase()) {
                        MicrobitConstants.BUTTON_A_STATE_UUID -> {
                            val state = value[0].toInt()
                            val stateStr =
                                if (state == 0) "Release (松开)" else if (state == 1) "Press (按下)" else "Long Press (长按)"
                            BleLog.d("[通知] 按钮 A 状态变更: $stateStr")
                        }

                        MicrobitConstants.BUTTON_B_STATE_UUID -> {
                            val state = value[0].toInt()
                            val stateStr =
                                if (state == 0) "Release (松开)" else if (state == 1) "Press (按下)" else "Long Press (长按)"
                            BleLog.d("[通知] 按钮 B 状态变更: $stateStr")
                        }

                        MicrobitConstants.UART_RX_UUID -> {
                            val text = String(value, StandardCharsets.UTF_8)
                            BleLog.d("[通知] 收到串口消息: $text")
                        }

                        MicrobitConstants.IO_PIN_DATA_UUID -> {
                            // 格式: [pin, value]
                            if (value.size >= 2) {
                                val pin = value[0].toInt()
                                val pinValue = value[1].toInt() and 0xFF // 0-255
                                BleLog.d("[通知] IO引脚数据: Pin=$pin, Value=$pinValue")
                            }
                        }

                        MicrobitConstants.TEMPERATURE_DATA_UUID -> {
                            // 格式: [temp] (摄氏度)
                            if (value.isNotEmpty()) {
                                val temp = value[0].toInt()
                                BleLog.d("[通知] 温度数据: $temp°C")
                            }
                        }

                    }
                }
            }
        }
        scop.launch {
            manager.getNotifyDataFlow()
                .filter { it.characterUUID.uppercase() == MicrobitConstants.MAGNETOMETER_DATA_UUID }
                .sample(20000).collect { data ->
                    data.value?.let { value ->
                        when (data.characterUUID.uppercase()) {
                            MicrobitConstants.MAGNETOMETER_DATA_UUID -> {
                                // X, Y, Z (Little Endian, Short)
                                if (value.size >= 6) {
                                    val x = (value[0].toInt() and 0xFF) or (value[1].toInt() shl 8)
                                    val y = (value[2].toInt() and 0xFF) or (value[3].toInt() shl 8)
                                    val z = (value[4].toInt() and 0xFF) or (value[5].toInt() shl 8)
                                    BleLog.d("[通知] 磁力计数据: X=${x.toShort()}, Y=${y.toShort()}, Z=${z.toShort()}")
                                }
                            }
                        }
                    }
                }
        }
        scop.launch {
            manager.getNotifyDataFlow()
                .filter { it.characterUUID.uppercase() == MicrobitConstants.ACCELEROMETER_DATA_UUID }
                .sample(19000).collect { data ->
                    data.value?.let { value ->
                        when (data.characterUUID.uppercase()) {
                            MicrobitConstants.ACCELEROMETER_DATA_UUID -> {
                                // X, Y, Z (Little Endian, Short)
                                // 数据格式: [xlow, xhigh, ylow, yhigh, zlow, zhigh]
                                if (value.size >= 6) {
                                    val x = (value[0].toInt() and 0xFF) or (value[1].toInt() shl 8)
                                    val y = (value[2].toInt() and 0xFF) or (value[3].toInt() shl 8)
                                    val z = (value[4].toInt() and 0xFF) or (value[5].toInt() shl 8)
                                    // 转换为有符号 short (因为 byte 转 int 是无符号扩展或直接补码，这里需要转回 short 再打印方便看)
                                    BleLog.d("[通知] 加速度数据: X=${x.toShort()}, Y=${y.toShort()}, Z=${z.toShort()}")
                                }
                            }
                        }
                    }
                }
        }

        scop.launch {
            manager.getReadResultFlow().collect { result ->
                when (result.characterUuid.uppercase()) {
                    MicrobitConstants.TEMPERATURE_DATA_UUID -> {
                        BleLog.i("读取的温度值:" + result.value?.get(0)?.toInt())
                    }
                }
            }
        }
    }

    fun startScan(containName: Array<String?>?) = manager.startScan(containName)

    fun stopScan() = manager.stopScan()

    fun connect(activity: Activity, address: String) {
        manager.connect(address).onSuccess {
            val flow = it
            connectJob?.cancel()
            connectJob = scop.launch {
                BleLog.d("状态flow:$address,$flow")
                flow.collect {
                    if (it is BleDeviceState.Ready) {
                        registerTemperature(address)
                        registerAccelerometer(address)
                        registerMagnetometer(address)
                        registerIO(address)
                        registerButton(address)
                    }
                }
            }
        }.onFailure {
            BleLog.e("连接失败:$it")
            BluetoothDeviceUtils.requestConnectPermission(activity)
        }

    }

    fun disconnect(address: String?) = manager.disconnect(address)

    fun registerButton(address: String?) {
        manager.enableNotification(
            BluetoothDeviceNotification.EnableNotificationData(
                address,
                MicrobitConstants.BUTTON_SERVICE_UUID,
                MicrobitConstants.BUTTON_A_STATE_UUID,
                MicrobitConstants.CCCD_UUID
            )
        ) {
            BleLog.i("开启按钮a通知结果:" + it.characterUuid + "," + it.success)
        }
        manager.enableNotification(
            BluetoothDeviceNotification.EnableNotificationData(
                address,
                MicrobitConstants.BUTTON_SERVICE_UUID,
                MicrobitConstants.BUTTON_B_STATE_UUID,
                MicrobitConstants.CCCD_UUID
            )
        ) {
            BleLog.i("开启按钮b通知结果:" + it.characterUuid + "," + it.success)
        }
    }

    fun registerIO(address: String?) {
        // 1. 注册 IO 引脚数据通知
        manager.enableNotification(
            BluetoothDeviceNotification.EnableNotificationData(
                address,
                MicrobitConstants.IO_PIN_SERVICE_UUID,
                MicrobitConstants.IO_PIN_DATA_UUID,
                MicrobitConstants.CCCD_UUID
            )
        ) {
            BleLog.i("开启IO引脚通知结果:" + it.characterUuid + "," + it.success)
        }
    }

    fun registerTemperature(address: String?) {
        // 2. 注册 温度数据通知 (Temperature Service)
        manager.writeData(
            BluetoothDeviceWrite.WriteData.ByteData(
                address,
                MicrobitConstants.TEMPERATURE_SERVICE_UUID,
                MicrobitConstants.TEMPERATURE_PERIOD_UUID,
                byteArrayOf(0x0e, 0x27)
            )
        ) {
            BleLog.i("开启温度通知间隔结果:" + it.characterUuid + "," + it.success)
        }
        manager.enableNotification(
            BluetoothDeviceNotification.EnableNotificationData(
                address,
                MicrobitConstants.TEMPERATURE_SERVICE_UUID,
                MicrobitConstants.TEMPERATURE_DATA_UUID,
                MicrobitConstants.CCCD_UUID
            )
        ) {
            BleLog.i("开启温度通知结果:" + it.characterUuid + "," + it.success)
        }
    }

    fun registerAccelerometer(address: String?) {
        // 3. 注册 加速度计数据通知 (Accelerometer Service)
        manager.writeData(
            BluetoothDeviceWrite.WriteData.ByteData(
                address,
                MicrobitConstants.ACCELEROMETER_SERVICE_UUID,
                MicrobitConstants.ACCELEROMETER_PERIOD_UUID,
                byteArrayOf(0xD0.toByte(), 0x07.toByte())
            )
        ) {
            BleLog.i("开启加速度通知间隔结果:" + it.characterUuid + "," + it.success)
        }
        manager.enableNotification(
            BluetoothDeviceNotification.EnableNotificationData(
                address,
                MicrobitConstants.ACCELEROMETER_SERVICE_UUID,
                MicrobitConstants.ACCELEROMETER_DATA_UUID,
                MicrobitConstants.CCCD_UUID
            )
        ) {
            BleLog.i("开启加速度通知结果:" + it.characterUuid + "," + it.success)
        }
    }

    fun registerMagnetometer(address: String?) {
        // 4. 注册 磁力计数据通知 (Magnetometer Service)
        manager.writeData(
            BluetoothDeviceWrite.WriteData.ByteData(
                address,
                MicrobitConstants.MAGNETOMETER_SERVICE_UUID,
                MicrobitConstants.MAGNETOMETER_PERIOD_UUID,
                byteArrayOf(0x80.toByte(), 0x02.toByte())
            )
        ) {
            BleLog.i("开启磁力计通知间隔结果:" + it.characterUuid + "," + it.success)
        }
        manager.enableNotification(
            BluetoothDeviceNotification.EnableNotificationData(
                address,
                MicrobitConstants.MAGNETOMETER_SERVICE_UUID,
                MicrobitConstants.MAGNETOMETER_DATA_UUID,
                MicrobitConstants.CCCD_UUID
            )
        ) {
            BleLog.i("开启磁力计通知结果:" + it.characterUuid + "," + it.success)
        }
    }

    fun readTemperature(address: String?) {
        manager.requestReadData(
            BluetoothDeviceRead.RequestReadData(
                address,
                MicrobitConstants.TEMPERATURE_SERVICE_UUID,
                MicrobitConstants.TEMPERATURE_DATA_UUID
            )
        ) {
            BleLog.i("读取温度结果:" + it.characterUuid + "," + it.success + "," + it.value)
        }
    }

    fun writeTextToLed(address: String?) {
        manager.writeData(
            BluetoothDeviceWrite.WriteData.StringData(
                address,
                MicrobitConstants.LED_SERVICE_UUID,
                MicrobitConstants.LED_TEXT_UUID,
                "a---b---c---d---e---0123"
            )
        ) {
            BleLog.i("写入LED结果:" + it.characterUuid + "," + it.success)
        }
    }

}