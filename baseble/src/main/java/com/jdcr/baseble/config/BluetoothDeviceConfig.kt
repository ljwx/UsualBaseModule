package com.jdcr.baseble.config

const val MTU_DEFAULT_SIZE = 23

data class BleScanConfig(
    val timeout: Long = 60000,
    val minRssi: Int = -100,
    val filterNullName: Boolean = true,
    val expiredTimeMills: Int = 2000,
    val resultIntervalMills: Long = 200,
)

data class BleReconnectConfig(
    val enableReconnect: Boolean = true,
    val delayMill: Long = 4000,
    val retryTimes: Int = 30
)

data class BleConnectConfig(val maxConnectDevice: Int = 3)

data class BleCommunicateConfig(val mtu: Int = 240, val timeoutMills: Long = 5000)

data class BluetoothDeviceConfig(
    val scan: BleScanConfig = BleScanConfig(),
    val connect: BleConnectConfig = BleConnectConfig(),
    val reconnect: BleReconnectConfig = BleReconnectConfig(),
    val communicate: BleCommunicateConfig = BleCommunicateConfig()
)