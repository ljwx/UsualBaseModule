package com.jdcr.baseble.core.scan

import com.jdcr.baseble.core.state.BleAdapterState
import kotlinx.coroutines.flow.SharedFlow

interface BleScanner {

    fun startScan(
        containName: Array<String?>?,
        timeoutMills: Long? = null
    ): Result<SharedFlow<BleAdapterState>>

    fun startScan(timeoutMills: Long? = null): Result<SharedFlow<BleAdapterState>>

    fun stopScan()

    fun release()

}