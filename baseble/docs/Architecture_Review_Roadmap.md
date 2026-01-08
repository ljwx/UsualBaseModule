# Android BLE Library Architectural Review & Roadmap

**Date:** 2026-01-13
**Reviewer:** Antigravity (Assistant)
**Target:** `com.jdcr.baseble`

## 1. 总体评价 (Executive Summary)

**当前评级: B+ (Production Ready with Caution)**

你的库在并发控制和核心流程上打下了非常坚实的基础。使用 Kotlin Coroutines Channel 实现串行化队列是正确的选择。但是，代码目前处于“功能实现”阶段，距离“商业级通用库”还缺乏一些现代化的架构特征。主要体现在**状态表达不够丰富**、**错误处理不够精确**以及**部分防御性编程缺失**。

---

## 2. 核心架构缺陷 (Architectural Gaps)

### 2.1 状态管理的 "Primitive Obsession" (原始类型偏执)
*   **现状**: 使用 `Int` 常量 (`BLE_STATE_CONNECTED` 等) 并在回调中传递。
*   **问题**:
    1.  **非完备性**: `Int` 可以是任意值，编译器无法强制覆盖所有状态。
    2.  **信息丢失**: 状态往往需要携带数据（例如：`DISCONNECTED` 状态应该携带 `reason`，`READY` 状态应该携带 `Service` 列表）。现在的设计把状态和数据分离了。
*   **架构建议**: 迁移至 **Sealed Interface State Machine**。

### 2.2 结果反馈的 "Boolean Trap" (布尔值陷阱)
*   **现状**: `write/read` 操作返回 `Boolean`。
*   **问题**: 调用者不知道 `false` 到底是因为“没连上”、“超时了”、“权限被拒”还是“特征值属性不支持”。这对于排查线上问题是灾难性的。
*   **架构建议**: 所有业务操作返回 `Result<T>` 或自定义 `BleResult`。

### 2.3 缺乏 "Ready" 状态的明确界定
*   **现状**: 连接成功 (`CONNECTED`) 就通知上层。
*   **问题**: BLE 的物理连接成功 != 具体业务可用。在服务发现 (`Service Discovery`) 完成前，任何读写都会失败。
*   **架构建议**: 引入 `ServiceReady` 状态，对上层屏蔽中间的 `Connected -> Discovering` 过程。

---

## 3. 具体代码隐患 (Code Smells)

1.  **并发修改风险 (Concurrent Modification Exception)**:
    *   `BluetoothDeviceScanner` 中直接发射 List 引用。必须使用防御性拷贝（Defensive Copy）。
2.  **队列 "死锁" 风险**:
    *   `BleCommunicationBase` 的消费者循环依赖 `suspend` 函数返回。如果底层蓝牙栈卡死（未回调），且没有 Timeout 机制，整个队列将永久冻结。
3.  **MTU 依赖**:
    *   写入长数据（>20字节）时直接透传。如果 MTU 未协商或协商失败，操作将静默失败。需要应用层分包（Chunking/Split Write）。

---

## 4. 演进路线图 (Evolution Roadmap)

请按照 P0 -> P1 -> P2 的顺序执行。

### 🚀 P0: 稳定性与防崩 (Critical Stability)
确保库在极端情况下不会导致 APP 崩溃或功能瘫痪。

- [ ] **Fix: 扫描结果并发拷贝**
    在 `BluetoothDeviceScanner` 发射数据前，使用 `mutex` 锁住并 `new ArrayList(list)`。
- [ ] **Feat: 任务队列超时机制**
    恢复 `BleCommunicationBase` 中的 `withTimeout` 逻辑。每个指令（Connect/Read/Write）都必须有最大执行时间（如 5000ms），超时强制抛出异常并清理队列。
- [ ] **Fix: 队列异常捕获**
    确保 `Channel` 消费者循环 (`for(op in channel)`) 内部包裹在 `try-catch` 中，防止单个任务的 Crashing 杀掉整个协程。(已完成)
- [ ] **Fix: 断连后的资源清理**
    确保 `close()` 逻辑在所有断开场景下都能被正确执行（你目前的逻辑是对的，但需保持警惕）。

### 🛠 P1: 核心功能补全 (Core Features)
让库变得真正“好用”，处理脏活累活。

- [ ] **Feat: 数据分片写入 (Split Write)**
    在 `BluetoothDeviceWrite` 中实现分片逻辑。
    *   根据当前 `core.maxPacketSize` 自动切割大 ByteArray。
    *   循环发送，等待每一包的 callback，或使用 `Write_No_Response` 的流控策略。
- [ ] **Feat: 写类型自动推断**
    (已完成) 根据特征值 `Properties` 自动选择 `Write_Type`。

### 🏛 P2: 架构重构 (Architectural Refactoring)
提升代码档次，符合 2025 年 Kotlin 标准。

- [ ] **Refactor: 状态机改造 (Sealed Class)**
    ```kotlin
    sealed interface DeviceState {
        object Disconnected : DeviceState
        object Connecting : DeviceState
        // 只有这个状态下才能通过 device 拿到 gatt 进行操作
        data class Ready(val device: BluetoothDevice) : DeviceState
        data class Error(val cause: Throwable) : DeviceState
    }
    ```
- [ ] **Refactor: 结果类型改造**
    将 `suspend fun write(...) : Boolean` 改为 `suspend fun write(...) : Result<Unit>`。
    定义明确的错误类型：`TimeoutException`, `GattException(status)`, `DeviceNotReadyException`。
- [ ] **Feat: 生命周期感知的连接**
    允许将连接绑定到 `CoroutineScope`。Scope 取消时自动断开连接。
    `fun connect(scope: CoroutineScope, address: String)`

---

## 5. 架构师寄语 (Design Philosophy)

1.  **Fail Fast, Fail Loud**: 不要吞掉异常。如果用户没给权限，直接抛出异常，而不是打印一行 Log 返回 false。让开发者在开发阶段就发现问题。
2.  **Truth in Types (类型即真理)**: 不要让用户处于一个“连接了但是不能写”的状态。利用 Kotlin 的类型系统，如果在 `Error` 状态，用户根本就不应该能访问到 `write` 方法。
3.  **Trust No One (不要信任底层)**: Android 的蓝牙栈极其不稳定（不同厂商魔改严重）。假设 `onConnectionStateChange` 永远不会回调、假设 `onCharacteristicWrite` 会超时。你的库必须由自己的定时器和状态检查来兜底。
