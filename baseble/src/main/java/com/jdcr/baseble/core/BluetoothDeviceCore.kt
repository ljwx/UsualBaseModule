package com.jdcr.baseble.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import com.jdcr.baseble.config.BluetoothDeviceConfig
import com.jdcr.baseble.constant.BLE_STATE_CONNECTED
import com.jdcr.baseble.constant.BLE_STATE_CONNECTING
import com.jdcr.baseble.constant.BLE_STATE_CONNECT_EXCEPTION
import com.jdcr.baseble.constant.BLE_STATE_CONNECT_LIMIT
import com.jdcr.baseble.constant.BLE_STATE_DISCONNECTED
import com.jdcr.baseble.constant.BLE_STATE_DISCONNECTING
import com.jdcr.baseble.constant.BLE_STATE_EXCEPTION_DISCONNECT
import com.jdcr.baseble.constant.BLE_STATE_GATT_EXCEPTION
import com.jdcr.baseble.constant.BLE_STATE_NORMAL_DISCONNECT
import com.jdcr.baseble.constant.BLE_STATE_REQUEST_CONNECT
import com.jdcr.baseble.constant.BLE_STATE_SCANNING_END
import com.jdcr.baseble.constant.BLE_STATE_SCANNING_FAIL
import com.jdcr.baseble.constant.BLE_STATE_SCANNING_START
import com.jdcr.baseble.constant.BLE_STATE_SERVER_READY
import com.jdcr.baseble.constant.BleServerState
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleCommunicateOperation
import com.jdcr.baseble.core.state.BleDeviceState
import com.jdcr.baseble.util.BleLog
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

class BluetoothDeviceCore(context: Context) {

//    data class DeviceStatusData(
//        val address: String,
//        val name: String,
//        var gatt: BluetoothGatt?,
//        private var status: Int,
//        val device: BluetoothDevice,
//        val connectedTimestamp: Long
//    ) {
//
//        companion object {
//            fun createData(
//                device: BluetoothDevice,
//                @BleServerState status: Int,
//                gatt: BluetoothGatt?
//            ): DeviceStatusData {
//                return DeviceStatusData(
//                    device.address,
//                    device.name,
//                    gatt,
//                    status,
//                    device,
//                    System.currentTimeMillis()
//                )
//            }
//        }
//
//        fun getStatus(): Int {
//            return status
//        }
//
//        fun isConnect(): Boolean {
//            when (status) {
//                BLE_STATE_CONNECTED, BLE_STATE_CONNECTING, BLE_STATE_DISCONNECTING -> {
//                    return true
//                }
//            }
//            return false
//        }
//
//        fun isWaitReady(): Boolean {
//            return status == BLE_STATE_CONNECTED
//        }
//
//        fun isServerReady(): Boolean {
//            return status == BLE_STATE_SERVER_READY
//        }
//
//        fun isDisconnected(): Boolean {
//            when (status) {
//                BLE_STATE_DISCONNECTED -> {
//                    return true
//                }
//            }
//            return false
//        }
//
//    }

    private var applicationContext: Context = context.applicationContext
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var deviceConfig = BluetoothDeviceConfig()
    private var currentMtu: Int = 23
    val maxPacketSize: Int
        get() = currentMtu - 3

    private val scopeLock = Any()
    private var _scope: CoroutineScope? = null

    private val connectMutex = Mutex()
    private val deviceStatusMap =
        LinkedHashMap<String, MutableStateFlow<BleDeviceState>>()
    private val deviceGattMap = ConcurrentHashMap<String, BluetoothGatt>()

    private var singleModeAddress: String? = null

    val operationChannel = Channel<BleCommunicateOperation>(Channel.UNLIMITED)
    val pendingOperations = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()

    init {
        initManager(applicationContext)
        initAdapter(applicationContext)
    }

    private fun initManager(context: Context?) {
        context?.apply {
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }
    }

    private fun initAdapter(context: Context?) {
        bluetoothAdapter = if (bluetoothManager != null) {
            bluetoothManager?.adapter
        } else {
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private fun getOrCreateCoroutine(): CoroutineScope {
        if (_scope == null || _scope?.isActive != true) {
            synchronized(scopeLock) {
                if (_scope == null || _scope?.isActive != true) {
                    val coroutineExceptionHandler = CoroutineExceptionHandler { _, e ->
                        e.printStackTrace()
                        BleLog.d("协程收到异常：${e}")
                    }
                    BleLog.d("创建新协程")
                    _scope?.cancel()
                    _scope =
                        CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)
                }
            }
        }
        return _scope!!
    }

    fun getBluetoothManager(): BluetoothManager {
        if (bluetoothManager == null) {
            throw NullPointerException("bluetooth manger is uninitialized")
        }
        return bluetoothManager!!
    }

    fun getBluetoothAdapter(): BluetoothAdapter? {
        return bluetoothAdapter
    }

    protected fun bluetoothIsEnable(context: Context? = null): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun getApplicationContext(): Context {
        if (applicationContext == null) {
            throw NullPointerException("bluetooth device manger context is uninitialized")
        }
        return applicationContext!!
    }

    fun getConfig(): BluetoothDeviceConfig {
        return deviceConfig
    }

    fun setManagerConfig(config: BluetoothDeviceConfig) {
        this.deviceConfig = config
    }

    private fun isSingleMode(): Boolean {
        return getConfig().connect.maxConnectDevice == 1
    }

    fun setCurrentMtu(mtu: Int) {
        this.currentMtu = mtu
    }

    fun getScanner(): BluetoothLeScanner? {
        return bluetoothAdapter?.bluetoothLeScanner
    }

    fun getScope(): CoroutineScope {
        return getOrCreateCoroutine()
    }

    fun <T> getConnectWithLock(action: suspend (coroutine: CoroutineScope) -> T) {
        getScope().launch {
            connectMutex.withLock {
                action(this)
            }
        }
    }

    private fun getFinalAddress(address: String?): String? {
        return address ?: if (isSingleMode()) singleModeAddress else address
    }

    private fun getOrCreateStateFlow(address: String?): MutableStateFlow<BleDeviceState> {
        return synchronized(deviceStatusMap) {
            val finalAddress = if (address == null && isSingleMode()) {
                singleModeAddress ?: deviceStatusMap.keys.firstOrNull()
            } else {
                address
            }
            if (finalAddress == null) {
                return MutableStateFlow(BleDeviceState.Idle)
            }
            deviceStatusMap.getOrPut(finalAddress) { MutableStateFlow(BleDeviceState.Idle) }
        }
    }

    fun getSingleStatusFlow(): StateFlow<BleDeviceState> {
        return getOrCreateStateFlow(null)
    }

    fun getDevicesStatusFlow(address: String): StateFlow<BleDeviceState> {
        return getOrCreateStateFlow(address)
    }

    fun getSingleStatus(): BleDeviceState {
        return getOrCreateStateFlow(null).value
    }

    fun getDeviceStatus(address: String): BleDeviceState {
        return getOrCreateStateFlow(address).value
    }

    private fun postDeviceStatus(address: String?, status: BleDeviceState) {
        val flow = getOrCreateStateFlow(address)
        BleLog.i("发送设备状态," + status.desc)
        flow.value = status
        if (status is BleDeviceState.Disconnected) {
            removeDevice(address)
        }
    }

    private fun removeDevice(address: String?) {
        BleLog.i("触发移除设备:$address")
        getFinalAddress(address)?.let { finalAddress ->
            synchronized(deviceStatusMap) {
                deviceStatusMap.remove(finalAddress)
                deviceGattMap.remove(finalAddress)
                if (finalAddress == singleModeAddress) {
                    singleModeAddress = null
                }
                BleLog.i("移除设备成功: $finalAddress")
            }
        }
    }

    fun getGatt(address: String?): BluetoothGatt? {
        getFinalAddress(address)?.let {
            return deviceGattMap[it]
        }
        return null
    }

    fun addGatt(address: String?, gatt: BluetoothGatt) {
        getFinalAddress(address)?.let {
            synchronized(deviceStatusMap) {
                deviceGattMap.remove(it)?.apply {
                    disconnect()
                    close()
                }
                deviceGattMap[it] = gatt
            }
        }
    }

    fun closeGatt(address: String?): Boolean {
        getFinalAddress(address)?.let { address ->
            deviceGattMap.remove(address)?.let {
                it.disconnect()
                it.close()
                return true
            }
        }
        return false
    }

    fun isConnected(address: String?): Boolean {
        getFinalAddress(address)?.let {
            getDeviceStatus(it).apply {
                when (this) {
                    is BleDeviceState.Ready, is BleDeviceState.Connected -> {
                        BleLog.i("设备已连接,且状态活跃,$desc")
                        return true
                    }

                    is BleDeviceState.Connecting, is BleDeviceState.DiscoveringServices, is BleDeviceState.ModifyMtu -> {
                        BleLog.i("设备已连接,正在初始化,$desc")
                        return true
                    }

                    else -> return false //Idle、Disconnected 或 Disconnecting
                }
            }
        }
        return false
//        return try {
//            val connectState =
//                core.getBluetoothManager().getConnectionState(device, BluetoothProfile.GATT)
//            return connectState == BluetoothProfile.STATE_CONNECTED
//        } catch (e: Exception) {
//            return false
//        }
    }

    fun isConnectLimit(address: String?): Boolean {
        getFinalAddress(address)?.let {
            if (isConnected(address)) {
                return false
            }
        }
        val activeCount = synchronized(deviceStatusMap) {
            deviceStatusMap.values.count {
                val status = it.value
                status !is BleDeviceState.Disconnected && status !is BleDeviceState.Idle && status !is BleDeviceState.Disconnecting
            }
        }
        return activeCount >= getConfig().connect.maxConnectDevice
    }

    fun changeDeviceState(address: String?, status: BleDeviceState) {
        BleLog.d("触发状态变更," + status.desc)
        postDeviceStatus(address, status)
        if (status is BleDeviceState.Connecting) {
            if (isSingleMode()) {
                val oldAddress = singleModeAddress
                if (oldAddress != null && oldAddress != status.address) {
                    BleLog.w("单连接模式,清除旧连接:$oldAddress")
                    closeGatt(oldAddress)
                    removeDevice(oldAddress)
                }
                singleModeAddress = status.address
            }
        }
    }

    fun release() {
        BleLog.d("释放资源")
        _scope?.cancel()
        _scope = null
        synchronized(deviceStatusMap) {
            deviceGattMap.forEach {
                BleLog.i("强制释放资源")
                try {
                    it.value.disconnect()
                    it.value.close()
                } catch (e: Exception) {
                    BleLog.w("释放异常:" + it.value.device.address + "," + e)
                }
            }
            deviceGattMap.clear()
            deviceStatusMap.clear()
            operationChannel.close()
            singleModeAddress = null
        }
        operationChannel.close()
    }

}