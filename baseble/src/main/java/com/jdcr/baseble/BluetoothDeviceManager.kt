package com.jdcr.baseble

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.jdcr.baseble.config.BluetoothDeviceConfig
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BluetoothDeviceCommunicationHandler
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification.NotificationData
import com.jdcr.baseble.core.communication.read.BluetoothDeviceRead
import com.jdcr.baseble.core.communication.read.BluetoothDeviceRead.RequestReadData
import com.jdcr.baseble.core.communication.write.BluetoothDeviceWrite
import com.jdcr.baseble.core.connect.BluetoothDeviceConnector
import com.jdcr.baseble.core.scan.BluetoothDeviceScanner
import kotlinx.coroutines.flow.SharedFlow


class BluetoothDeviceManager private constructor(context: Context, config: BluetoothDeviceConfig) {

    companion object {
        @Volatile
        private var instance: BluetoothDeviceManager? = null
        fun init(
            context: Context,
            config: BluetoothDeviceConfig = BluetoothDeviceConfig()
        ): BluetoothDeviceManager {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = BluetoothDeviceManager(context, config)
                    }
                }
            }
            return instance!!
        }

        fun getInstance(): BluetoothDeviceManager {
            return instance
                ?: throw IllegalStateException("BluetoothDeviceManager not initialized. Call init() first.")
        }

    }

    private val core = BluetoothDeviceCore(context).apply { setManagerConfig(config) }
    private val scanner = BluetoothDeviceScanner(core)
    private val notification = BluetoothDeviceNotification(core)
    private val read = BluetoothDeviceRead(core)
    private val write = BluetoothDeviceWrite(core)
    private val dataHandler = BluetoothDeviceCommunicationHandler(core, notification, write, read)
    private val connector =
        BluetoothDeviceConnector(core).apply { setCommunicationHandler(dataHandler) }

    fun startScan(containName: Array<String?>?, timeout: Long? = null) =
        scanner.startScan(containName, timeout)

    fun startScan(timeout: Long?) = scanner.startScan(timeout)
    fun stopScan() = scanner.stopScan()

    fun connect(device: BluetoothDevice) = connector.connect(device)
    fun connect(address: String) = connector.connect(address)
    fun disconnect(address: String?) = connector.disconnect(address)

    fun enableNotification(
        notificationData: BluetoothDeviceNotification.EnableNotificationData
    ) = dataHandler.notify.enableNotification(notificationData)

    fun getNotifyDataFlow(): SharedFlow<NotificationData> =
        dataHandler.notify.getNotifyDataFlow()

    fun getReadResultFlow() =
        dataHandler.read.getReadResultFlow()

    fun requestReadData(data: RequestReadData) =
        dataHandler.read.requestReadData(data)

    fun writeData(data: BluetoothDeviceWrite.WriteData) =
        dataHandler.write.writeData(data)

}

