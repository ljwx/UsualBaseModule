package com.jdcr.baseble.core.scan

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.os.SystemClock
import com.jdcr.baseble.core.BluetoothDeviceCore
import com.jdcr.baseble.core.exception.PermissionDineException
import com.jdcr.baseble.core.state.BleAdapterState
import com.jdcr.baseble.util.BleLog
import com.jdcr.baseble.util.BluetoothDeviceUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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

    private var filterDeviceName: Array<String?>? = null
    private val filterMapLazy: Lazy<HashMap<String, ScanFilter>> = lazy { HashMap() }
    private val filterMap by filterMapLazy
    private var settings: ScanSettings? = null
    private var scanCallback: ScanCallback? = null

    private var isScanning = false
    private var scanResultSendJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private val listMutex = Mutex()
    private val scanResultList by lazy { mutableListOf<ScanResult>() }
    private var scanResultChannelJob: Job? = null

    @Volatile
    private var scanResultChannel = Channel<ScanResult>(Channel.BUFFERED)
    private val scanResultFlow = MutableSharedFlow<BleAdapterState>(
        replay = 1,
        extraBufferCapacity = 30,
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

    private fun launchScanResultChannel() {
        if (scanResultChannelJob?.isActive == true) return
        if (!scanResultChannel.isClosedForSend) {
            scanResultChannel.close()
        }
        scanResultChannel = Channel(Channel.BUFFERED)
        scanResultChannelJob = core.getScope().launch {
            try {
                for (result in scanResultChannel) {
                    processScanResult(result)
                }
            } catch (e: Exception) {
                BleLog.i("处理扫描结果协程异常退出:$e")
            } finally {
                scanResultChannelJob = null
            }
        }
    }

    private suspend fun clearExpiredDevice(singleMode: Boolean): List<ScanResult> {
        return listMutex.withLock {
            val currentBootMillis = SystemClock.elapsedRealtime()
            val iterator = scanResultList.iterator()
            val scanConfig = core.getConfig().scan
            while (iterator.hasNext()) {
                val result = iterator.next()
                val deviceBootMillis = result.timestampNanos / 1000_000
                val interval = currentBootMillis - deviceBootMillis
                if (scanConfig.filterNullName && result.device.name.isNullOrEmpty()) {
                    iterator.remove()
                } else if (interval > scanConfig.expiredTimeMills) {
                    iterator.remove()
                }
            }
            if (scanConfig.rssiSort) {
                scanResultList.sortByDescending { it.rssi }
            }
            if (singleMode) {
                if (scanResultList.isEmpty()) {
                    emptyList()
                } else {
                    listOf(scanResultList.first())
                }
            } else {
                ArrayList(scanResultList)
            }
        }
    }

    private suspend fun processScanResult(result: ScanResult) {
        listMutex.withLock {
            val device = result.device
            val existsIndex = scanResultList.indexOfFirst { it.device.address == device.address }
            if (result.rssi < core.getConfig().scan.minRssi) {
                if (existsIndex >= 0) {
                    scanResultList.removeAt(existsIndex)
                } else {
                }
            } else {
                if (existsIndex >= 0) {
                    scanResultList[existsIndex] = result
                } else {
                    scanResultList.add(result)
                }
            }
//            BleLog.i("扫描结果:${scanResultList.size}")
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun startScan(
        containName: Array<String?>?,
        timeoutMills: Long?
    ): Result<SharedFlow<BleAdapterState>> {
        filterDeviceName = containName
        return startScan(timeoutMills)
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun startScan(timeoutMills: Long?): Result<SharedFlow<BleAdapterState>> {
        BleLog.i("触发蓝牙扫描")
        if (!BluetoothDeviceUtils.checkScanPermission(core.getApplicationContext())) {
            return Result.failure(
                PermissionDineException(BluetoothDeviceUtils.getScanPermission())
            )
        }
        if (isScanning) {
            return Result.success(scanResultFlow.asSharedFlow())
        }
        val scanner = core.getScanner()
        if (scanner != null) {
            BleLog.i("开始扫描")
            val filters = if (filterMapLazy.isInitialized()) {
                filterMap.values.toList()
            } else null
            startScanResultSendJob()
            launchScanResultChannel()
            scanCallback = scanCallback ?: getScanCallback()
            isScanning = true
            scanner.startScan(filters, settings ?: getDefaultScanSettings(), scanCallback)
            startScanTimeoutJob(timeoutMills)
            return Result.success(scanResultFlow.asSharedFlow())
        } else {
            return Result.failure(NullPointerException("bluetooth scanner is null"))
        }
    }

    private fun startScanResultSendJob() {
        scanResultSendJob?.cancel()
        scanResultSendJob = core.getScope().launch {
            supervisorScope {
                launch {
                    val scanConfig = core.getConfig().scan
                    while (isActive) {
                        delay(scanConfig.resultIntervalMills)
                        try {
                            val listCopy = clearExpiredDevice(scanConfig.singleResultMode)
                            BleLog.i("发送扫描结果,单个模式:" + scanConfig.singleResultMode + "," + listCopy.size)
                            if (scanConfig.singleResultMode) {
                                scanResultFlow.tryEmit(BleAdapterState.ScanningSingle(listCopy.firstOrNull()))
                            } else {
                                scanResultFlow.tryEmit(BleAdapterState.ScanningList(listCopy))
                            }
                        } catch (e: Exception) {
                            BleLog.e("处理扫描结果时异常:$e")
                        }
                    }
                }
            }
        }
    }

    private fun startScanTimeoutJob(timeoutMills: Long?) {
        scanTimeoutJob = core.getScope().launch {
            delay(timeoutMills ?: core.getConfig().scan.timeout)
            if (isScanning) {
                BleLog.d("扫描超时,停止扫描")
                core.getScanner()?.stopScan(scanCallback)
                scanFinish(BleAdapterState.Finish(true, BleAdapterState.Finish.REASON_TIMEOUT))
            }
        }
    }

    @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    override fun stopScan() {
        if (!isScanning) return
        core.getScanner()?.apply {
            BleLog.i("手动停止扫描")
            try {
                stopScan(scanCallback)
            } catch (e: Exception) {
                BleLog.e("停止扫描异常:$e")
            }
            scanFinish(BleAdapterState.Finish(true, BleAdapterState.Finish.REASON_MANUAL))
        }
    }

    private fun getScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val filterName = filterDeviceName
                if (filterName.isNullOrEmpty()) {
                    scanResultChannel.trySend(result)
                } else {
                    result.device.name?.let { deviceName ->
                        val contain =
                            filterName.filterNotNull().any { deviceName.contains(it, true) }
                        if (contain) {
                            scanResultChannel.trySend(result)
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                scanFinish(
                    BleAdapterState.Finish(
                        false,
                        BleAdapterState.Finish.REASON_ON_FAIL,
                        errorCode
                    )
                )
            }
        }
    }

    private fun scanFinish(state: BleAdapterState) {
        BleLog.i("扫描结束,清理资源")
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        filterDeviceName = null
        isScanning = false
        scanResultSendJob?.cancel()
        scanResultSendJob = null
        scanResultChannel.close()
        scanResultChannelJob?.cancel()
        scanResultChannelJob = null
        core.getScope().launch { listMutex.withLock { scanResultList.clear() } }
        scanResultFlow.tryEmit(state)
    }

    override fun release() {
        stopScan()
    }

}