package com.jdcr.baseble.core.exception

class DeviceNotFoundException(val address: String) : Exception("未发现该设备:$address")