package com.jdcr.baseble.permission

import android.Manifest

abstract class BluetoothDevicePermission {

    private val android11Base =
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    private val android11Scan = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val android12 = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )


    fun checkPermission(): Boolean {
        return true
    }

    fun requestPermission() {

    }

}