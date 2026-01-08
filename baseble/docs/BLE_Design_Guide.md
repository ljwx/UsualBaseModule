# 蓝牙 (BLE) 库设计核心指南

本文档旨在为你即将开发的蓝牙通信库提供核心概念解析、标准流程蓝图以及关键的代码实现的最小示例。

## 一、 核心概念：三个 ID 的作用

在 BLE 开发中，这三个 ID 构成了寻址的基础。

### 1. Peripheral ID (设备地址)
*   **作用**：唯一标识一个物理蓝牙设备。
*   **形式**：Android 上是 MAC 地址 (如 `AA:BB:CC:DD:EE:FF`)。
*   **代码体现**：`BluetoothDevice` 对象。

### 2. Service ID (服务 UUID)
*   **作用**：设备功能的逻辑分组。一个设备可以有多个 Service（例如：设备信息服务、心率服务、OTA 服务）。
*   **形式**：UUID (如 `0000180d-0000-1000-8000-00805f9b34fb`)。
*   **关键点**：连接后通过 `discoverServices()` 获取。

### 3. Characteristic ID (特征值 UUID)
*   **作用**：实际的数据读写通道。每个 Service 下包含多个 Characteristic。
*   **形式**：UUID。
*   **权限**：
    *   **Write**: 发送指令。
    *   **Read**: 读取状态。
    *   **Notify/Indicate**: 订阅设备主动上报的数据。

---

## 补充：经典蓝牙 vs 低功耗蓝牙 (BLE)

你可能会问：**“为什么只提 BLE？不需要扫经典蓝牙吗？”**

这是一个非常关键的区别，混淆两者会导致扫描不到设备或连接失败。

### 1. 区别对比
| 特性 | 经典蓝牙 (Classic / BR / EDR) | 低功耗蓝牙 (BLE) |
| :--- | :--- | :--- |
| **主要用途** | 传输音频 (耳机/音箱)、大数据文件传输 | 传感器数据 (手环/心率计)、指令控制、IoT 设备 |
| **功耗** | 高 | 极低 (纽扣电池可用数月) |
| **连接方式** | Socket (SPP, A2DP) | GATT 协议 (Service / Characteristic) |
| **扫描 API** | `BluetoothAdapter.startDiscovery()` | `BluetoothLeScanner.startScan()` |
| **发现速度** | 较慢 (10秒左右) | 极快 (毫秒级) |

### 2. 为什么本库只关注 BLE？
modern Android 开发中，90% 的 **“外设通信”** 需求（如智能硬件、医疗设备、打印机指令）都是基于 BLE 的。
*   **经典蓝牙** (Classic / BR / EDR) 通常由系统直接接管（如系统设置里连蓝牙耳机），开发者很少需要自己写代码去配对连接 A2DP。
*   如果你要连的是基于 **SPP (RFCOMM)** 的老式扫码枪或打印机，那就是经典蓝牙。

### 3. 如何判断我的设备是哪一种？(关键知识点)
如果不确定手中的硬件是 BLE 还是经典蓝牙，请检查**通信协议文档**或询问硬件工程师：

*   **是 BLE 的铁证**：
    *   文档中出现了大量的 **UUID** (如 `0000ffe0-0000-1000-8000-00805f9b34fb`)。
    *   提到了关键词：**GATT**, **Service**, **Characteristic**, **Notify**, **Indicate**。
    *   不需要在系统设置里输入 PIN 码（如 0000/1234）配对即可连接。
    *   **绝大多数现代智能硬件（手环、医疗仪器、物联网控制）都是 BLE。**

*   **是经典蓝牙的证据**：
    *   文档提到了 **SPP** (Serial Port Profile) 或 **RFCOMM**。
    *   必须在系统蓝牙设置里先配对，否则 App 搜不到。

*   **特殊情况：蓝牙键盘/鼠标 (HID 设备)**
    *   蓝牙键盘即使是 BLE 技术，一旦连接也会被 **Android 系统独占接管** 作为输入设备。
    *   App **无法**通过标准蓝牙 API 直接获取键盘按键数据（只能通过 `onKeyDown` 等事件）。
    *   **本库适用于 App 与外设的数据通信，不适用于开发“连接蓝牙键盘”的功能。**

**结论：如果你的协议文档里有 Service UUID 和 Characteristic UUID，那么请放心使用本指南及后续的 BLE 代码。**

---

## 二、 蓝牙通信基本流程 (严谨版)

一个工业级的 BLE 流程必须是严格 **串行** 且 **异步** 的。

### 1. 扫描 (Scan)
使用 `ScanFilter` 提高效率，只关注目标设备。

```kotlin
// 获取 BluetoothManager (Android 4.3+)
val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
val adapter = bluetoothManager.adapter

// 检查蓝牙是否开启
if (adapter == null || !adapter.isEnabled) {
    // 提示用户开启蓝牙
    return
}

val scanner = adapter.bluetoothLeScanner

// 过滤特定 Service UUID 的设备（推荐）
val filter = ScanFilter.Builder()
    .setServiceUuid(ParcelUuid.fromString("YOUR_SERVICE_UUID"))
    .build()

val settings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .build()

val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        // 获取到设备 -> 停止扫描 -> 发起连接
        scanner.stopScan(this)
        connectToDevice(device)
    }
}

scanner.startScan(listOf(filter), settings, scanCallback)
```

### 2. 连接 (Connect) & 状态监控
**注意**: 连接是一个耗时操作，必须在回调中确认状态。

```kotlin
fun connectToDevice(device: BluetoothDevice) {
    // autoConnect = false 表示直接连接，速度快；true 表示系统在后台慢速尝试
    device.connectGatt(context, false, gattCallback)
}

val gattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            // 连接成功 -> 必须立即发现服务
            // 建议：此处发送一个 "OnConnected" 事件给上层
            gatt.discoverServices()
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // 处理断开连接，触发重连逻辑或释放资源
            gatt.close()
        }
    }
}
```

### 3. 发现服务 (Discover Services)
连接成功后，必须发现服务才能操作特征值。

```kotlin
override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
    if (status == BluetoothGatt.GATT_SUCCESS) {
        // 服务表准备就绪 -> 此时才能查找特征值
        val service = gatt.getService(UUID.fromString("YOUR_SERVICE_UUID"))
        val characteristic = service?.getCharacteristic(UUID.fromString("YOUR_CHAR_UUID"))

        // 下一步：比如开启通知
        enableNotification(gatt, characteristic)
    }
}
```

### 4. 开启通知 (Enable Notification)
这是最容易出错的一步。除了在 App 本地开启，还必须往设备的 `Descriptor` (描述符) 里写数据通知设备。

```kotlin
fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
    // 1. 本地开启
    gatt.setCharacteristicNotification(characteristic, true)

    // 2. 获取 CCCD Descriptor (通常是 0x2902)
    val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    val descriptor = characteristic.getDescriptor(cccdUuid)

    // 3. 写入 ENABLE_NOTIFICATION_VALUE
    // 注意：旧版 Android API 写法，Android 13+ 有新 API
    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
    gatt.writeDescriptor(descriptor)
    // -> 等待 onDescriptorWrite 回调才算成功
}
```

### 5. 数据读写 (Read / Write)

**写入指令**:
```kotlin
fun writeCommand(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, data: ByteArray) {
    char.value = data
    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT // 或 NO_RESPONSE
    gatt.writeCharacteristic(char)
    // -> 必须等待 onCharacteristicWrite 回调
}
```

**接收通知数据**:
```kotlin
override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
    val data = characteristic.value
    // 处理设备发来的数据
}
```

---

## 三、 关键注意事项 (实现难点)

### 1. 任务队列 (Command Queue) —— **最核心部分**
Android 的 `BluetoothGatt` 方法大多返回 `boolean`，但这只代表“请求开始”，不代表“执行结束”。如果在前一个操作的 Callback 回来之前执行下一个操作，系统会直接丢弃。

**你需要实现一个 FIFO 队列：**

```kotlin
// 伪代码示例
class BleRequestQueue {
    private val queue = ConcurrentLinkedQueue<Runnable>()
    private var isBusy = false

    fun add(request: Runnable) {
        queue.add(request)
        next()
    }

    private fun next() {
        if (isBusy || queue.isEmpty()) return

        val request = queue.poll()
        isBusy = true
        request.run()
    }

    // 在每一个 BluetoothGattCallback (如 onCharacteristicWrite, onDescriptorWrite) 中调用
    fun onCompleted() {
        isBusy = false
        next()
    }
}
```

### 2. 线程安全
`BluetoothGattCallback` 的方法通常运行在 Binder 线程（非主线程）。
*   不要在回调里直接更新 UI。
*   **严谨做法**：使用 Kotlin Flow 或 Handler 将数据抛回主线程或指定的业务线程。

### 3. MTU (最大传输单元)
默认 BLE 包体很小 (20-23 字节)。
*   如果需要发长文本/图片，必须**分包**。
*   或者申请更大的 MTU: `gatt.requestMtu(512)` (需要设备支持)，并在 `onMtuChanged` 回调后生效。

### 4. 资源释放
*   断开连接时，务必调用 `gatt.close()` 以释放系统的 BluetoothGatt 资源，否则达到连接上限（通常 7 个）后将无法连接新设备。

---

## 四、 建议的库结构

```text
/baseble
  ├── manager
  │     └── BleManager.kt       // 单例入口，负责扫描、初始化
  ├── device
  │     ├── BleDevice.kt        // 代表一个连接中的设备，持有 Gatt 实例
  │     └── BleConnection.kt    // 负责具体的连接状态机维护
  ├── queue
  │     └── BleRequestQueue.kt  // 任务调度队列，保证串行执行
  ├── callback
  │     └── BleCallback.kt      // 统一的对外接口
  └── utils
        └── BleUtils.kt         // UUID 转换、日志等工具
```
