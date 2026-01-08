# Android BluetoothGattCallback 核心回调精讲

`BluetoothGattCallback` 是 Android BLE 通讯的**核心**，所有的异步操作结果（连接、读写、通知）都在这里返回。

以下是开发中**最常用**的 7 个回调及其核心作用。

---

### 1. onConnectionStateChange (连接状态改变)
**作用**：监听设备是**连上了**还是**断开了**。
**怎么用**：
*   **判断状态**：首先检查 `status == BluetoothGatt.GATT_SUCCESS`。
    *   如果 `newState == BluetoothProfile.STATE_CONNECTED`：**连接成功**。
        *   **关键一步**：必须立即调用 `gatt.discoverServices()` 开始发现服务，否则无法读写。
    *   如果 `newState == BluetoothProfile.STATE_DISCONNECTED`：**连接断开**。
        *   通常在这里处理重连逻辑或释放资源 (`gatt.close()`)。

### 2. onServicesDiscovered (发现服务)
**作用**：`gatt.discoverServices()` 执行完成后的回调。
**怎么用**：
*   **获取特征值**：在这里遍历 `gatt.getServices()`，找到你需要的 `Service` (服务) 和 `Characteristic` (特征值) 对象，保存下来供后续读写使用。
*   **开启通知**：通常在这里开启数据的 Notify 通知。

### 3. onCharacteristicChanged (收到通知数据)
**作用**：设备**主动**给你发数据（Notify/Indicate 方式）时回调这里。
**怎么用**：
*   **核心数据入口**：这是 BLE 设备给手机发数据的最主要渠道。
*   **区分数据源**：通过 `characteristic.getUuid()` 来区分数据是哪一个特征值传来的（如电量、心率）。
*   `characteristic.getValue()` 取出字节数组，进行解析。

### 4. onCharacteristicWrite (写入结果)
**作用**：你调用 `gatt.writeCharacteristic()` 后的**结果反馈**。
**怎么用**：
*   **确认发送成功**：检查 `status == BluetoothGatt.GATT_SUCCESS`。
*   **流控**：如果你需要分包连续发送大量数据，必须**等收到这个回调**确认上一包发送成功后，再发下一包，否则容易丢包或断连。

### 5. onDescriptorWrite (描述符写入结果 / 开启通知回调)
**作用**：你调用 `gatt.writeDescriptor()` 后的结果。
**怎么用**：
*   **通知开启确认**：Android 开启 Notify 需要两步：1. `setCharacteristicNotification(true)` (本地) -> 2. `writeDescriptor` (告诉设备)。
*   当收到这个回调且 `status == SUCCESS` 时，代表**通知真正开启成功**，设备才会开始发数据。

### 6. onCharacteristicRead (主动读取结果)
**作用**：你调用 `gatt.readCharacteristic()` 后，回来的数据。
**怎么用**：
*   **一次性获取**：适用于获取设备电量、设备名、版本号等不经常变动的静态数据。
*   *注意：不要和 onCharacteristicChanged 混淆，那个是设备推过来的，这个是你去拉取的。*

### 7. onMtuChanged (MTU 改变)
**作用**：你调用 `gatt.requestMtu(int)` 协商数据包大小后的回调。
**怎么用**：
*   **修改包大小**：默认 BLE 包只有 20 字节有效载荷。如果你申请了更大的 MTU (如 512)，等收到这个回调且 `status == SUCCESS` 后，你发送和接收的数据包长度限制才会生效。

---

### 核心操作流程拆解 (Step-by-Step)

#### 1. 初始化流程 (连接与发现)
这是与设备建立通讯的基础，**必须**按顺序完成。

1.  **开始连接**: 手机调用 `connectGatt()`
2.  **回调 ✅**: `onConnectionStateChange` (status=SUCCESS, newState=CONNECTED) -> **连接成功**
    *   *动作*: 立即调用 `gatt.discoverServices()`
3.  **回调 ✅**: `onServicesDiscovered`
    *   *动作*: 遍历 `gatt.getServices()`，找到需要的 Characteristic。
    *   *动作*: 如果需要监听数据，进入下面的“订阅通知”流程。

#### 2. 订阅通知流程 (接收设备数据)
想要设备主动发数据给你（如心率、按键、传感器），必须走这一步。

1.  **设置本地**: `gatt.setCharacteristicNotification(char, true)`
2.  **设置远程**: 获取该特征值的 Descriptor (通常 UUID 为 `00002902-0000-1000-8000-00805f9b34fb`)，写入启用指令（ENABLE_NOTIFICATION_VALUE）。
    *   *动作*: `gatt.writeDescriptor(descriptor)`
3.  **回调 ✅**: `onDescriptorWrite`
    *   *含义*: **订阅成功！** 此时通道已打通。
4.  **回调 🔄**: `onCharacteristicChanged`
    *   *含义*: **收到数据**。
    *   *注意*: `characteristic.getUuid()` 是**特征值 UUID**（数据通道），不是 Descriptor UUID（开关）。

#### 3. 写入数据流程 (发送指令)
给设备发指令（如开灯、设置参数）。

1.  **发送**: `char.setValue(bytes)` -> `gatt.writeCharacteristic(char)`
2.  **回调 ✅**: `onCharacteristicWrite`
    *   *含义*: **发送完成**。
    *   *注意*: 只有收到这个回调，才算“这一包”真正发出去了。如果要发下一包，**必须等待**这个回调回来后再发，不要用 `for` 循环狂发。

#### 4. 读取数据流程 (主动查询)
偶尔获取一下状态（如电量、版本）。

1.  **读取**: `gatt.readCharacteristic(char)`
2.  **回调 ✅**: `onCharacteristicRead`
    *   *含义*: **读取成功**。数据在 `characteristic.getValue()` 中。

---

### ⚠️ UUID 关键辨析 (常考题)

很多初学者容易在这里混淆，请务必分清：

| 概念 | Characteristic UUID (特征值) | Descriptor UUID (描述符) |
| :--- | :--- | :--- |
| **典型示例** | `0000ffe1-0000-1000...` (不固定，看厂商) | `00002902-0000-1000...` (固定，CCCD) |
| **形象比喻** | **水管** (数据从这里流出来) | **水龙头开关** (控制让不让水流) |
| **你的代码** | `onCharacteristicChanged` 里获取到的就是它 | 只有在 `writeDescriptor` 开启通知时才会用到 |
| **回答你的问题** | **是它！** 收数据时只看这个 UUID。 | **不是它！** 平时收数据时完全不用管它。 |
