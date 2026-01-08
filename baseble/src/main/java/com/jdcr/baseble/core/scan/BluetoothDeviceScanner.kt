package com.jdcr.baseble.core.scan

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.exception.PermissionDineException
import com.jdcr.baseble.core.state.BleAdapterState
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

open class BluetoothDeviceScanner(private val core: BluetoothDeviceCore) : BleScanner {

    private var filterDeviceName: Array<String>? = null
    private val filterMapLazy: Lazy<HashMap<String, ScanFilter>> = lazy { HashMap() }
    private val filterMap by filterMapLazy
    private var settings: ScanSettings? = null
    private var scanCallback: ScanCallback? = null

    private var isScanning = false

    private var scanJob: Job? = null
    private val listMutex = Mutex()
    private val scanResultList by lazy { mutableListOf<ScanResult>() }
    private val scanResultFlow = MutableSharedFlow<BleAdapterState>(
        replay = 0,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )


    fun addScanFilter(name: String, filter: ScanFilter) {
        filterMap[name] = filter
    }

    fun addScanFilterUUID(name: String, uuid: String) {
        val uuidFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build()
        addScanFilter(name, uuidFilter)
    }

    protected fun getDefaultScanSettings(): ScanSettings {
        return ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    }

    fun setScanSettings(settings: ScanSettings) {
        this.settings = settings
    }

    fun setScanCallback(callback: ScanCallback) {
        this.scanCallback = callback
    }

    private suspend fun clearExpiredDevice(): List<ScanResult> {
        val currentBootMillis = android.os.SystemClock.elapsedRealtime()
        val resultList = scanResultList.toMutableList()
        val iterator = resultList.iterator()
        while (iterator.hasNext()) {
            val result = iterator.next()
            val deviceBootMillis = result.timestampNanos / 1000_000
            val interval = currentBootMillis - deviceBootMillis
//                BleLog.i("$currentBootMillis,$deviceBootMillis")
            if (interval > core.getConfig().scan.expiredTimeMills) {
                iterator.remove()
            }
            if (result.device.name.isNullOrEmpty()) {
                iterator.remove()
            }
        }
        return resultList
    }

    private suspend fun processScanResult(result: ScanResult) {
        listMutex.withLock {
            val device = result.device
            val existsIndex = scanResultList.indexOfFirst { it.device.address == device.address }
            if (result.rssi < core.getConfig().scan.minRssi) {
                if (existsIndex >= 0) {
                    scanResultList.removeAt(existsIndex)
                }
            } else {
                if (existsIndex >= 0) {
                    scanResultList[existsIndex] = result
                } else {
                    scanResultList.add(result)
                }
            }
            scanResultList.sortByDescending { it.rssi }
//            BleLog.i("扫描结果:${scanResultList.size}")
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun startScan(
        containName: Array<String>,
        timeoutMills: Long?
    ): Result<SharedFlow<BleAdapterState>> {
        filterDeviceName = containName
        return startScan(timeoutMills)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun startScan(timeoutMills: Long?): Result<SharedFlow<BleAdapterState>> {
        BleLog.i("触发蓝牙扫描")
        if (!BluetoothDeviceUtils.checkScanPermission(core.getApplicationContext())) {
            scanFinish()
            return Result.failure(
                PermissionDineException(BluetoothDeviceUtils.getScanPermission())
            )
        }
        if (isScanning) {
            return Result.success(scanResultFlow.asSharedFlow())
        }
        core.getScanner()?.apply {
            BleLog.i("开始扫描")
            val filters = if (filterMapLazy.isInitialized()) {
                filterMap.values.toList()
            } else null
            scanCallback = scanCallback ?: getScanCallback()
            scanJob = core.getScope().launch {
                supervisorScope {
                    launch {
                        while (isActive) {
                            delay(core.getConfig().scan.resultIntervalMills)
                            try {
                                val listCopy = clearExpiredDevice()
                                BleLog.i("发送扫描结果:" + listCopy.size)
                                scanResultFlow.tryEmit(BleAdapterState.Scanning(listCopy))
                            } catch (e: Exception) {
                                BleLog.e("处理扫描结果时异常:$e")
                            }
                        }
                    }
                }
            }
            isScanning = true
            startScan(filters, settings ?: getDefaultScanSettings(), scanCallback)
        }
        core.getScope().launch {
            delay(timeoutMills ?: core.getConfig().scan.timeout)
            if (isScanning) {
                stopScan()
            }
        }
        return Result.success(scanResultFlow.asSharedFlow())
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun stopScan() {
        core.getScanner()?.apply {
            BleLog.i("停止扫描")
            stopScan(scanCallback)
            scanFinish()
        }
    }

    private fun getScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                result ?: return
                core.getScope().launch {
                    val filterName = filterDeviceName
                    if (filterName.isNullOrEmpty()) {
                        processScanResult(result)
                    } else {
                        result.device.name?.let {
                            val contain = filterName.filter { it.contains(it, true) }
                            if (contain.isNotEmpty()) {
                                processScanResult(result)
                            }
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                scanFinish()
                scanResultFlow.tryEmit(
                    BleAdapterState.Finish(
                        false,
                        BleAdapterState.Finish.REASON_ON_FAIL
                    )
                )
            }
        }
    }

    private fun scanFinish() {
        filterDeviceName = null
        isScanning = false
        scanJob?.cancel()
        scanJob = null
    }

    override fun release() {
        stopScan()
    }

}