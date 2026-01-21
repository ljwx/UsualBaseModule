package com.jdcr.baseble.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BluetoothDevicePermission {

    enum class Type { SCAN, CONNECT, ADVERTISE, ALL }

    private var permissionsCallback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)? = null

    fun getPermissions(type: Type): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (type) {
                Type.SCAN -> arrayOf(Manifest.permission.BLUETOOTH_SCAN)
                Type.CONNECT -> arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
                Type.ADVERTISE -> arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE)
                Type.ALL -> arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            }
        } else {
            when (type) {
                Type.SCAN -> arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )

                Type.CONNECT, Type.ADVERTISE -> emptyArray() // 普通权限无需动态申请
                Type.ALL -> arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }

    fun check(context: Context, type: Type): Boolean {
        return getPermissions(type).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }.also { if (getPermissions(type).isEmpty()) return true }
    }

    fun getDenied(context: Context, type: Type): Array<String> {
        return getPermissions(type).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    fun request(
        activity: FragmentActivity,
        type: Type,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) {
        val denied = getDenied(activity, type)
        if (denied.isNotEmpty()) {
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
        type: Type,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ): Boolean {
        return if (check(activity, type)) {
            true
        } else {
            request(activity, type, callback)
            false
        }
    }

    fun checkScan(context: Context) = check(context, Type.SCAN)
    fun checkConnect(context: Context) = check(context, Type.CONNECT)
    fun checkAll(context: Context) = check(context, Type.ALL)

    fun requestScan(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = request(activity, Type.SCAN, callback)

    fun requestConnect(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = request(activity, Type.CONNECT, callback)

    fun requestAll(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = request(activity, Type.ALL, callback)

    fun checkAndRequestScan(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = checkAndRequest(activity, Type.SCAN, callback)

    fun checkAndRequestConnect(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = checkAndRequest(activity, Type.CONNECT, callback)

    fun checkAndRequestAll(
        activity: FragmentActivity,
        callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?
    ) = checkAndRequest(activity, Type.ALL, callback)

    fun setPermissionsCallback(callback: ((allGranted: Boolean, Map<String, Boolean>) -> Unit)?) {
        this.permissionsCallback = callback
    }

    fun release() {
        this.permissionsCallback = null
    }

}