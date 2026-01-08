# Android BLE Library - Core Design Principles & Guidelines
**"The Philosophy of Robust Bluetooth Engineering"**

这份文档总结了打造商业级 Android BLE 库必须遵循的架构原则与设计规范。在开发任何新功能或进行代码重构时，请以此为准绳。

---

## Ⅰ. State Management: Truth in Types (类型即真理)

### P1. 使用有限状态机 (FSM) 代替散乱变量
*   **原则**: 永远不要使用多个 `Boolean` (如 `isConnected`, `isScanning`, `isReady`) 来组合管理状态。
*   **规范**: 必须使用 **Sealed Class/Interface** 定义互斥的状态集合。
*   **警醒案例**: `isConnected = true` 但 `Service` 还没发现，此时调用 write 导致崩溃。
*   **Right Way**:
    ```kotlin
    sealed interface DeviceState {
        object Connecting : DeviceState
        // 只有在 Ready 状态下，才持有可操作的 Handle
        data class Ready(val services: ServiceList) : DeviceState 
    }
    ```

### P2. 消除非法状态 (Make Illegal States Unrepresentable)
*   **原则**: API 的设计应该让“错误用法”无法通过编译，而不是运行时报错。
*   **规范**: 不要让用户在 `Disconnected` 的状态下有机会调用 `write()` 方法。将操作方法绑定在 `Connected/Ready` 状态对象上，而不是 DeviceManager 全局单例上。

---

## Ⅱ. Robustness: Trust No One (零信任原则)

### P3. 不要信任底层回调 (Callback Hell)
*   **原则**: Android 蓝牙栈极其不稳定（特别是在某些国产定制 ROM 上）。
*   **规范**: **永远假设 callback 可能不回来**。
*   **强制执行**: 任何异步操作（Connect, Write, Read）必须包裹在 `withTimeout` 中。如果规定时间内底层没给回调，上层必须主动抛出 Timeout 异常并通过 `close()` 强制复位，防止队列永久卡死。

### P4. 防御性并发 (Defensive Concurrency)
*   **原则**: BLE 是单线程模型（GATT是串行的），但 APP 是多线程的。
*   **规范**:
    1.  **数据流出必拷贝**: 从 Scanner/Flow 发射出的集合数据，必须是 Deep Copy，防止外部 UI 线程遍历时引发 `ConcurrentModificationException`。
    2.  **串行化队列**: 所有 GATT 操作必须入队（FIFO），严禁并行调用 `writeCharacteristic`。

### P5. 资源生命周期 (Resource Ownership)
*   **原则**: 谁创建，谁销毁。防止连接句柄 (Connection Handle) 泄漏。
*   **规范**: `BluetoothGatt` 对象一旦断开 (`STATE_DISCONNECTED`)，必须立即 `close()`。绝不允许复用旧的 GATT 实例进行 `reconnect`，必须发起新的连接。

---

## Ⅲ. Error Handling: Fail Fast (快速失败)

### P6. 异常不要“吃掉” (Fail Loudly)
*   **原则**: 掩盖错误比错误本身更可怕。
*   **规范**:
    *   ❌ 能够恢复的错误 -> 返回 `Result.Failure`。
    *   ❌ 逻辑/配置错误 (如没权限) -> **直接抛出 RuntimeException**。不要打个 Log 然后返回 false，这会让开发者在上线后才发现问题。
*   **警醒案例**: 忘记申请 `BLUETOOTH_CONNECT` 权限，结果 `scan()` 默默返回空列表，排查一整天。应该直接 Crash 提醒开发者。

### P7. 结果明确化 (Explicit Results)
*   **原则**: `Boolean` 是信息量最低的类型。
*   **规范**: 业务方法的返回值禁止使用 `Boolean`。必须使用 `Result<T>` 或自定义 Sealed Class。
    *   `Success(data)`
    *   `Failure.Timeout`
    *   `Failure.GattError(status=133)`
    *   `Failure.PermissionDenied`

---

## Ⅳ. API Design: Minimal & Intuitive (极简且直观)

### P8. 隐藏复杂性 (Facade Pattern)
*   **原则**: 用户只应该关心“我要发什么数据”，而不是“Characteristic UUID 是多少”或“要不要分包”。
*   **规范**:复杂的底层逻辑（分包、重试、队列调度）必须封装在内部。对外暴露的 API 越傻瓜越好：`device.write("Hello")`。

### P9. 响应式编程 (Reactive First)
*   **原则**: 蓝牙是天生的异步事件流。
*   **规范**: 优先暴露 `Flow/StateFlow` 而不是传统的 Interface Callback。让 UI 层能够利用协程的生命周期自动管理订阅和取消。

---

> **Self-Check Questions (开发前自问):**
> 1. 如果这个手机蓝牙突然断电，我的函数会死锁吗？(检测超时)
> 2. 如果用户在连接中途狂点几十次写入，我的队列会爆吗？(检测背压/队列容量)
> 3. 如果这个操作失败了，调用者能知道确切原因吗？(检测错误类型)
