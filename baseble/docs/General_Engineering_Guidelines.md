# General Software Engineering & Android Guidelines
**"Breaking the Intermediate Plateau: From Coder to Engineer"**

这份文档总结了从初级/中级迈向高级工程师过程中，最容易犯的错误和必须坚持的原则。它超越了具体的语言特性，关注代码的生命周期、健壮性和可维护性。

---

## Ⅰ. Concurrency: The Root of All Evil (并发是万恶之源)

### 1. Mutable Shared State is the Enemy (可变共享状态是敌人)
*   **现象**: 在多线程/协程之间直接传递一个 `ArrayList`、`HashMap` 或可变对象。
*   **后果**: 偶发性崩溃 (`ConcurrentModificationException`)、数据竞态 (Race Condition)、难以复现的 Bug。
*   **警醒**:
    *   **原则**: **Immutability by Default (默认不可变)**。集合尽量用 `List` 而非 `MutableList` 对外暴露。
    *   **规范**: 如果必须共享，必须加锁 (`Mutex`) 或者使用 **Copy-on-Write (写时拷贝)** 策略。
    *   *Self-Check*: "如果有两个线程同时调这个方法，我的数据会坏掉吗？"

### 2. Scope Lifecycle mismatch (生命周期错配)
*   **现象**: 在 `Activity/Fragment` 中使用 `GlobalScope` 或自定义 Scope 却不处理 `cancel`。
*   **后果**: 内存泄漏 (Memory Leak)、野指针崩溃（页面关了还在回调更新 UI）。
*   **警醒**:
    *   **原则**: **Structured Concurrency (结构化并发)**。协程必须绑定到特定的生命周期组件 (ViewModelScope, LifecycleScope)。
    *   *Self-Check*: "如果用户打开这个页面 1 秒就切走了，我后台的任务还在跑吗？如果在跑，它回来更新 UI 会 Crash 吗？"

---

## Ⅱ. Error Handling: The Silence is Deadly (沉默是致命的)

### 3. Swallowing Exceptions (吞噬异常)
*   **现象**:
    ```kotlin
    try {
        doSomething()
    } catch (e: Exception) {
        e.printStackTrace() // 或者 Log.d
    }
    ```
*   **后果**: 程序状态已经错误（比如文件没写完、对象是空的），但代码继续往下跑，导致后面出现莫名其妙的 NullPointer，且此时已经丢失了案发现场。
*   **警醒**:
    *   **原则**: **Fail Fast (快速失败)**。如果你不知道如何处理这个异常（比如恢复数据），那就**不要捕获它**，让它崩溃。崩溃能让你立刻发现 Bug，而掩盖错误会让 Bug 存活数年。
    *   **规范**: 在库开发中，要么抛出异常，要么返回 `Result.Failure`，绝不仅仅打印 Log。

### 4. Boolean Return Values (布尔值返回)
*   **现象**: 函数返回 `true/false` 表示成功/失败。
*   **后果**: 上层调用者无法知道失败的具体原因（网络断了？密码错了？服务器崩了？），导致无法向用户展示正确的提示。
*   **警醒**:
    *   **原则**: **Rich Result Types**。使用 `Result<T>` 或 Sealed Class 携带错误信息。

---

## Ⅲ. Architecture: Separation of Concerns (关注点分离)

### 5. God Classes / God Activities (上帝类)
*   **现象**: 一个 `Activity` 或 `Manager` 类有 2000+ 行代码，包揽了 UI 逻辑、网络请求、数据解析、蓝牙连接。
*   **后果**: 改一个 bug 引入三个新 bug，无人敢动核心代码。
*   **警醒**:
    *   **原则**: **Single Responsibility Principle (单一职责原则)**。
    *   **技巧**: 如果一个类里的方法一部分在操作 UI，一部分在操作 File，那它就该拆分了。
    *   *Self-Check*: "我能用一句话描述这个类是干嘛的吗？如果用了'和'字（如：负责显示xx**和**下载xx），那就该拆了。"

### 6. Leaking Implementation Details (泄漏实现细节)
*   **现象**: `BluetoothDeviceManager` 对外暴露了 `BluetoothGatt` 对象；或者 Repository 对外暴露了 SQL 查询 Cursor。
*   **后果**: 上层业务层对底层产生了强依赖，导致底层无法重构（比如想换数据库、想换蓝牙库）。
*   **警醒**:
    *   **原则**: **Dependency Inversion (依赖倒置)**。高层模块不应依赖低层模块，二者都应依赖抽象。
    *   **规范**: 对外暴露的数据应该是纯粹的 Domain Model (Pojos/Data Classes)，不包含任何框架特定的对象。

---

## Ⅳ. General Coding Habits (通用编码习惯)

### 7. Magic Numbers & Strings (硬编码)
*   **现象**: `if (type == 1) ...` 或 `write(..., 2000)`。
*   **后果**: 没人知道 `1` 代表什么，`2000` 是毫秒还是秒。代码可读性为零。
*   **警醒**:
    *   **原则**: **Named Constants**。提取为常量 `const val TYPE_HEADSET = 1`，`const val TIMEOUT_MS = 2000L`。

### 8. Premature Optimization (过早优化)
*   **现象**: 为了"性能"，手写复杂的对象池，或者把清晰的逻辑写得晦涩难懂。
*   **后果**: 99% 的情况下，性能没提升多少，但代码维护成本增加了 10 倍。
*   **警醒**:
    *   **原则**: **Make it work, make it right, make it fast**。先写出清晰、正确的代码。只有在分析器证明这里是瓶颈时，才去优化。现代 JVM/ART 非常聪明。

---

> **The Senior Engineer's Mindset:**
> *   **Code is Liability, not Asset**: 代码是负债，不是资产。写得越少，Bug 越少。
> *   **Think about Day 2**: 不要只想着今天能跑通，要想三个月后，你自己（或者接手的人）还能看懂并修改它吗？
