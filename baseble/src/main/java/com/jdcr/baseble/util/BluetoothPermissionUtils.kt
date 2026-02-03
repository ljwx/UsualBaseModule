package com.jdcr.baseble.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BluetoothPermissionUtils {

    enum class Type { SCAN, CONNECT, ADVERTISE, ALL }

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

    fun getLocationPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            emptyArray()
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            emptyArray()
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

    fun isOSVersionForLocationPermissions(): Boolean {
        return getLocationPermissions().isNotEmpty()
    }

    fun isOSVersionBluetoothPermissions(): Boolean {
        return getBluetoothPermissions().isNotEmpty()
    }

}