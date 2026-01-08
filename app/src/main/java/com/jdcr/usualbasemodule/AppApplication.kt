package com.jdcr.usualbasemodule

import android.app.Application
import com.jdcr.baseble.test.BluetoothDeviceTest

class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        BluetoothDeviceTest.init(this)
    }

}