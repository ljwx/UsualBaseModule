package com.jdcr.baseble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.location.LocationManager
import androidx.fragment.app.FragmentActivity
import com.jdcr.baseble.config.BluetoothDeviceConfig
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.communication.BleCommunicationBase.BleOperationResult
import com.jdcr.baseble.core.communication.BluetoothDeviceCommunicationHandler
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification
import com.jdcr.baseble.core.communication.notify.BluetoothDeviceNotification.NotificationData
import com.jdcr.baseble.core.communication.read.BluetoothDeviceRead
import com.jdcr.baseble.core.communication.read.BluetoothDeviceRead.RequestReadData
import com.jdcr.baseble.core.communication.write.BluetoothDeviceWrite
import com.jdcr.baseble.core.communication.write.BluetoothDeviceWrite.WriteData
import com.jdcr.baseble.core.connect.BluetoothDeviceConnector
import com.jdcr.baseble.core.permission.BluetoothDevicePermission
import com.jdcr.baseble.core.permission.BluetoothEnableFragment
import com.jdcr.baseble.core.permission.BluetoothSettingsFragment
import com.jdcr.baseble.core.scan.BluetoothDeviceScanner
import com.jdcr.baseble.core.state.CurrentState
import com.jdcr.baseble.receiver.BluetoothDeviceEnableReceiver
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

    private var adapterEnableReceiver: BluetoothDeviceEnableReceiver? = null
    private val permission by lazy { BluetoothDevicePermission() }
    private val core = BluetoothDeviceCore(context).apply { setManagerConfig(config) }
    private val scanner = BluetoothDeviceScanner(core)
    private val notification = BluetoothDeviceNotification(core)
    private val read = BluetoothDeviceRead(core)
    private val write = BluetoothDeviceWrite(core)
    private val dataHandler = BluetoothDeviceCommunicationHandler(core, notification, write, read)
    private val connector =
        BluetoothDeviceConnector(core).apply { setCommunicationHandler(dataHandler) }

    fun checkAllPermission(context: Context): Boolean {
        return permission.checkAll(context)
    }

    fun checkAndRequestAllPermission(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) {
        permission.checkAndRequestAll(activity, callback)
    }

    fun setPermissionCallback(callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?) {
        permission.setPermissionsCallback(callback)
    }

    fun setAdapterEnableListener(context: Context, listener: ((enable: Boolean) -> Unit)?) {
        adapterEnableReceiver = BluetoothDeviceEnableReceiver.registerEnable(context, listener)
    }

    fun enableAdapter(activity: FragmentActivity, callback: (enabled: Boolean) -> Unit) {
        BluetoothEnableFragment.request(activity, callback)
    }

    fun openSettings(activity: FragmentActivity, callback: (enabled: Boolean) -> Unit) {
        BluetoothSettingsFragment.open(activity, callback)
    }

    fun isLocationEnable(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnable = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnable = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return isGpsEnable || isNetworkEnable
    }

    fun getCurrentState(): CurrentState {
        return when {
            core.getBluetoothAdapter() == null -> return CurrentState.NoSupport
            !permission.checkLocationPermissions(core.getApplicationContext()) -> return CurrentState.LocationPermissionDine
            !permission.checkBluetoothPermissions(core.getApplicationContext()) -> return CurrentState.BlePermissionDine
            core.getBluetoothAdapter()?.isEnabled != true -> CurrentState.BleDisable
            !isLocationEnable(core.getApplicationContext()) -> CurrentState.LocationDisable
            else -> CurrentState.Ready
        }
    }

    fun startScan(containName: Array<String?>?, timeout: Long? = null) =
        scanner.startScan(containName, timeout)

    fun startScan(timeout: Long?) = scanner.startScan(timeout)
    fun stopScan() = scanner.stopScan()

    fun connect(device: BluetoothDevice) = connector.connect(device)
    fun connect(address: String) = connector.connect(address)
    fun disconnect(address: String?) = connector.disconnect(address)

    fun enableNotification(
        notificationData: BluetoothDeviceNotification.EnableNotificationData,
        callback: ((result: BleOperationResult.EnableNotification) -> Unit)?
    ) = dataHandler.notify.enableNotification(notificationData, callback)

    suspend fun enableNotification(
        notificationData: BluetoothDeviceNotification.EnableNotificationData
    ) = dataHandler.notify.enableNotification(notificationData)

    fun getNotifyDataFlow(): SharedFlow<NotificationData> =
        dataHandler.notify.getNotifyDataFlow()

    fun getReadResultFlow() =
        dataHandler.read.getReadResultFlow()

    fun requestReadData(
        data: RequestReadData,
        callback: ((result: BleOperationResult.Read) -> Unit)?
    ) = dataHandler.read.requestReadData(data, callback)

    suspend fun requestReadData(data: RequestReadData) = dataHandler.read.requestReadData(data)

    fun writeData(
        data: BluetoothDeviceWrite.WriteData,
        callback: ((result: BleOperationResult.Write) -> Unit)?
    ) = dataHandler.write.writeData(data, callback)

    suspend fun writeData(data: WriteData) = dataHandler.write.writeData(data)

    fun release() {
        adapterEnableReceiver?.release(core.getApplicationContext())
        permission.release()
        core.release()
        scanner.release()
        connector.release()
    }

}

