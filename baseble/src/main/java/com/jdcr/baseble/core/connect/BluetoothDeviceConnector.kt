package com.jdcr.baseble.core.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.res.Resources.NotFoundException
import android.os.Build
import android.os.LimitExceededException
import androidx.annotation.RequiresPermission
import com.jdcr.baseble.config.MTU_DEFAULT_SIZE
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.core.communication.BluetoothDeviceCommunicationHandler
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification
import com.jdcr.baseble.core.exception.DeviceNotFoundException
import com.jdcr.baseble.core.exception.PermissionDineException
import com.jdcr.baseble.core.state.BleDeviceState
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import com.jdcr.baseble.util.printTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

open class BluetoothDeviceConnector(private val core: BluetoothDeviceCore) : BleConnector {

    private var retryTimes = HashMap<String, Int>()
    private var communicationHandler: BluetoothDeviceCommunicationHandler? = null
    fun setCommunicationHandler(handler: BluetoothDeviceCommunicationHandler) {
        this.communicationHandler = handler
    }

    private fun getGattCallback(): BluetoothGattCallback {
        return object : BluetoothGattCallback() {

            fun isExceptionDisconnect(
                address: String,
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ): Boolean {
                if (newState == BluetoothProfile.STATE_DISCONNECTED && status != BluetoothGatt.GATT_SUCCESS) {
                    exceptionDisconnect(gatt.device, status)
                    retryConnect(address)
                    return true
                }
                return false
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
                super.onReadRemoteRssi(gatt, rssi, status)

            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                gatt?.device ?: return
                val device = gatt.device
                val address = device.address
                if (isExceptionDisconnect(address, gatt, status, newState)) {
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    exceptionDisconnect(gatt.device, status)
                    return
                }
                when (newState) {

                    BluetoothProfile.STATE_CONNECTING -> {
                        core.changeDeviceState(address, BleDeviceState.Connecting(device))
                    }

                    BluetoothProfile.STATE_CONNECTED -> {
                        core.changeDeviceState(address, BleDeviceState.Connected(device))
                        gatt.discoverServices()
                        clearRetryTimes(address)
                    }

                    BluetoothProfile.STATE_DISCONNECTING -> {
                        core.changeDeviceState(address, BleDeviceState.Disconnecting(device))
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        core.closeGatt(address)
                        val state = core.getDeviceStatus(address)
                        core.changeDeviceState(
                            address,
                            BleDeviceState.Disconnected(device, state, status)
                        )
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                gatt?.device ?: return
                val device = gatt.device
                val address = device.address
                BleLog.d("onServicesDiscovered:$address")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val mtu = core.getConfig().communicate.mtu
                    if (mtu > MTU_DEFAULT_SIZE) {
                        core.changeDeviceState(address, BleDeviceState.ModifyMtu(device, mtu))
                        val result = gatt.requestMtu(mtu) ?: false
                        if (!result) {
                            core.changeDeviceState(
                                address,
                                BleDeviceState.Ready(device, gatt.services)
                            )
                        }
                        BleLog.d("请求修改mtu大小:$result,$mtu")
                    } else {
                        core.changeDeviceState(address, BleDeviceState.Ready(device, gatt.services))
                    }
                } else {
                    BleLog.d("onServicesDiscovered失败")
                    exceptionDisconnect(gatt.device, status)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt?,
                descriptor: BluetoothGattDescriptor?,
                status: Int
            ) {
                super.onDescriptorWrite(gatt, descriptor, status)
                val success = status == BluetoothGatt.GATT_SUCCESS
                val characterUuid = descriptor?.characteristic?.uuid?.toString() ?: ""
                val notificationUuid = descriptor?.uuid?.toString() ?: ""
                communicationHandler?.notify?.onEnableNotificationResult(
                    BleOperationResult.EnableNotification(
                        gatt?.device?.address,
                        characterUuid,
                        notificationUuid,
                        success
                    )
                )
                BleLog.d("通知是否生效: $success, ${descriptor?.characteristic?.uuid}")
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
//                BleLog.i("通知: ${characteristic?.uuid}")
                val serviceUuid = characteristic?.service?.uuid?.toString()
                val uuid = characteristic?.uuid?.toString()
                val value = characteristic?.value
                if (uuid != null) {
                    communicationHandler?.notify?.onNotification(
                        BluetoothDeviceNotification.NotificationData(
                            gatt?.device?.address,
                            serviceUuid,
                            uuid,
                            value
                        )
                    )
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
//                BleLog.i("通知: ${characteristic.uuid}")
                communicationHandler?.notify?.onNotification(
                    BluetoothDeviceNotification.NotificationData(
                        gatt.device.address,
                        characteristic.service.uuid.toString(),
                        characteristic.uuid.toString(),
                        value
                    )
                )
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                val success = status == BluetoothGatt.GATT_SUCCESS
                communicationHandler?.write?.onWriteResult(
                    BleOperationResult.Write(
                        gatt?.device?.address,
                        success,
                        characteristic?.uuid?.toString() ?: "",
                        status
                    )
                )
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                BleLog.i("数据读取: ${characteristic?.uuid}")
                val success = status == BluetoothGatt.GATT_SUCCESS
                val characterUuid = characteristic?.uuid?.toString() ?: ""
                val value = characteristic?.value
                communicationHandler?.read?.onReadResult(
                    BleOperationResult.Read(
                        gatt?.device?.address,
                        success,
                        characterUuid,
                        value
                    )
                )
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                BleLog.i("数据读取: ${characteristic.uuid}")
                val success = status == BluetoothGatt.GATT_SUCCESS
                val characterUuid = characteristic.uuid.toString()
                communicationHandler?.read?.onReadResult(
                    BleOperationResult.Read(
                        gatt.device?.address,
                        success,
                        characterUuid,
                        value
                    )
                )
            }

            override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                gatt?.device ?: return
                val device = gatt.device
                val address = gatt.device.address
                core.setCurrentMtu(mtu)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    BleLog.w("修改mtu失败:$status")
                }
                BleLog.i("实际数据包可用大小:${core.maxPacketSize},$mtu")
                core.changeDeviceState(address, BleDeviceState.Ready(device, gatt.services))
            }

        }
    }

    override fun connect(address: String): Result<StateFlow<BleDeviceState>> {
        if (!BluetoothDeviceUtils.checkConnectPermission(core.getApplicationContext())) {
            return Result.failure(
                PermissionDineException(BluetoothDeviceUtils.getConnectPermission())
            )
        }
        val device = core.getBluetoothAdapter()?.getRemoteDevice(address)
        if (device != null) {
            return connect(device)
        } else {
            BleLog.w("未发现该设备:$address")
            return Result.failure(DeviceNotFoundException(address))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connect(device: BluetoothDevice): Result<StateFlow<BleDeviceState>> {
        if (!BluetoothDeviceUtils.checkConnectPermission(core.getApplicationContext())) {
            return Result.failure(
                PermissionDineException(BluetoothDeviceUtils.getConnectPermission())
            )
        }
        val address = device.address
        BleLog.i("发起连接请求:" + device.printTag())
        if (core.isConnected(address)) {
            return Result.success(core.getDevicesStatusFlow(address))
        }
        if (core.isConnectLimit(address)) {
            BleLog.w("超出连接上限:" + device.printTag())
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Result.failure(LimitExceededException("超出连接上限"))
            } else {
                Result.failure(UnsupportedOperationException("超出连接上限"))
            }
        }
        core.changeDeviceState(address, BleDeviceState.Connecting(device))
        val gatt = device.connectGatt(core.getApplicationContext(), false, getGattCallback())
        if (gatt == null) {
            val state = core.getDeviceStatus(address)
            core.changeDeviceState(address, BleDeviceState.Disconnected(device, state, -1))
            BleLog.w("gatt is null")
            return Result.failure(RuntimeException("gatt is null"))
        } else {
            core.addGatt(address, gatt)
            return Result.success(core.getDevicesStatusFlow(address))
        }
    }

    override fun disconnect() {
        disconnect(null)
    }

    @SuppressLint("MissingPermission")
    override fun disconnect(address: String?) {
        BleLog.d("触发手动断开连接:$address")
        core.closeGatt(address)
        core.getFinalAddress(address)?.let { clearRetryTimes(it) }
    }

    private fun exceptionDisconnect(device: BluetoothDevice, status: Int) {
        val address = device.address
        val state = core.getDeviceStatus(address)
        BleLog.d("服务异常,触发执行断开连接")
        core.closeGatt(address)
        BleLog.d("已执行断连" + state.desc)
        core.changeDeviceState(address, BleDeviceState.Disconnected(device, state, status))
    }

    private fun addRetryTimes(address: String) {
        val times = retryTimes[address]
        if (times == null) {
            clearRetryTimes(address)
        }
        retryTimes[address] = (retryTimes[address] ?: 0) + 1
    }

    private fun retryConnect(address: String) {
        core.getConnectWithLock {
            val reconnectConfig = core.getConfig().reconnect
            if (!reconnectConfig.enableReconnect) return@getConnectWithLock
            var times = retryTimes[address] ?: 0
            if (times < reconnectConfig.retryTimes) {
                addRetryTimes(address)
                delay(reconnectConfig.delayMill)
                val deviceState = core.getDeviceStatus(address)
                if (deviceState is BleDeviceState.Disconnected) {
                    connect(deviceState.device)
                    BleLog.i("尝试重连:$retryTimes")
                }
            }
        }
    }

    private fun clearRetryTimes(address: String) {
        BleLog.i("清除重连记录:$address")
        retryTimes[address] = 0
    }

    fun release() {
        disconnect()
    }

}