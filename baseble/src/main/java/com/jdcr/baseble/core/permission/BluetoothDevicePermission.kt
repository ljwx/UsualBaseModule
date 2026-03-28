package com.jdcr.baseble.core.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothPermissionUtils

class BluetoothDevicePermission {

    private var permissionsCallback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)? = null

    fun request(
        activity: FragmentActivity,
        type: BluetoothPermissionUtils.Type,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) {
        val denied = BluetoothPermissionUtils.getDenied(activity, type)
        if (denied.isNotEmpty()) {
            BleLog.i("发起权限请求:$denied")
            if (permissionsCallback == null) {
                BluetoothDevicePermissionFragment.requestPermission(activity, denied, callback)
            } else {
                BluetoothDevicePermissionFragment.requestPermission(activity, denied) { all, map ->
                    callback?.invoke(all, map)
                    permissionsCallback?.invoke(all, map)
                }
            }
        } else {
            callback?.invoke(true, emptyMap())
            permissionsCallback?.invoke(true, emptyMap())
        }
    }

    fun checkAndRequest(
        activity: FragmentActivity,
        type: BluetoothPermissionUtils.Type,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ): Boolean {
        return if (BluetoothPermissionUtils.check(activity, type)) {
            BleLog.i("所有权限检查通过")
            true
        } else {
            request(activity, type, callback)
            false
        }
    }

    fun checkScan(context: Context) =
        BluetoothPermissionUtils.check(context, BluetoothPermissionUtils.Type.SCAN)

    fun checkConnect(context: Context) =
        BluetoothPermissionUtils.check(context, BluetoothPermissionUtils.Type.CONNECT)

    fun checkAll(context: Context) =
        BluetoothPermissionUtils.check(context, BluetoothPermissionUtils.Type.ALL)

    fun requestScan(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = request(activity, BluetoothPermissionUtils.Type.SCAN, callback)

    fun requestConnect(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = request(activity, BluetoothPermissionUtils.Type.CONNECT, callback)

    fun requestAll(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = request(activity, BluetoothPermissionUtils.Type.ALL, callback)

    fun checkAndRequestScan(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = checkAndRequest(activity, BluetoothPermissionUtils.Type.SCAN, callback)

    fun checkAndRequestConnect(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = checkAndRequest(activity, BluetoothPermissionUtils.Type.CONNECT, callback)

    fun checkAndRequestAll(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = checkAndRequest(activity, BluetoothPermissionUtils.Type.ALL, callback)

    fun checkLocationPermissions(context: Context): Boolean {
        return BluetoothPermissionUtils.getLocationPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }.also { if (BluetoothPermissionUtils.getLocationPermissions().isEmpty()) return true }
    }

    fun checkBluetoothPermissions(context: Context): Boolean {
        return BluetoothPermissionUtils.getBluetoothPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }.also { if (BluetoothPermissionUtils.getBluetoothPermissions().isEmpty()) return true }
    }

    fun setPermissionsCallback(callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?) {
        this.permissionsCallback = callback
    }

    fun release() {
        this.permissionsCallback = null
    }

}