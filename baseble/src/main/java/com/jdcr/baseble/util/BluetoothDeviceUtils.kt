package com.jdcr.baseble.util

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

fun BluetoothDevice.safeName(): String {
    return name ?: "name_null"
}

object BluetoothDeviceUtils {

    fun checkInfoPermission(context: Context): Boolean {
        BleLog.i("检查设备信息权限")
        return checkConnectPermission(context).apply { BleLog.i("是否有设备信息权限:$this") }
    }

    fun getConnectPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //Android 12+：检查BLUETOOTH_CONNECT权限
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            //Android 12以下：BLUETOOTH/BLUETOOTH_ADMIN是普通权限，无需动态检查
            null
        }
    }

    fun checkConnectPermission(context: Context): Boolean {
        getConnectPermission()?.let {
            return (ActivityCompat.checkSelfPermission(
                context,
                it
            ) == PackageManager.PERMISSION_GRANTED).apply { if (!this) BleLog.w("没有蓝牙连接权限") }
        }
        return true
    }

    fun requestConnectPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(getConnectPermission()),
                1
            )
        }
    }

    fun getScanPermission(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return Manifest.permission.BLUETOOTH_SCAN
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            null
        }
    }

    fun checkScanPermission(context: Context): Boolean {
        getScanPermission()?.let {
            return (ActivityCompat.checkSelfPermission(
                context,
                it
            ) == PackageManager.PERMISSION_GRANTED).apply { if (!this) BleLog.w("没有蓝牙扫描权限") }
        }
        return true
    }

    fun requestScanPermissions(context: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(getScanPermission()),
                1
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(getScanPermission()),
                1
            )
        }
    }

    fun turnOnSwitch() {

    }

    fun openSwitchPage(context: Context) {
        val switchIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//        context.startActivity(switchIntent)
    }

}