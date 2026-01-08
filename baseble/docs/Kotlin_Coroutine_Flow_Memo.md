协程收到异常：java.util.ConcurrentModificationException

# Kotlin 协程与 Flow 实践备忘录

## 协程创建与管理

### 协程Scope的创建
```kotlin
// 推荐：双重检查锁定模式（线程安全）
@Volatile
private var _scope: CoroutineScope? = null
private val scopeLock = Any()

val scope: CoroutineScope
    get() {
        val currentScope = _scope
        if (currentScope != null && currentScope.isActive) {
            return currentScope
        }
        
        return synchronized(scopeLock) {
            if (_scope != null && _scope!!.isActive) {
                _scope!!
            } else {
                _scope?.cancel()  // 取消旧的
                _scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
                _scope!!
            }
        }
    }
```

### Job vs SupervisorJob
- **Job**：子协程异常会传播到父协程和兄弟协程
- **SupervisorJob**：子协程异常只影响自己，不影响其他协程
- **应用场景**：蓝牙多任务场景推荐使用 SupervisorJob

### 协程取消
- `scope.cancel()` 等同于 `job.cancel()`
- 取消后无法再启动新任务，需重新创建
- 判断是否取消：`scope.isActive == false`

## 线程安全

### 锁的使用
- `synchronized(this)`：锁粒度较粗，可能影响其他方法
- `synchronized(Any())`：专用锁，推荐使用，避免外部锁定风险

### volatile关键字
- 用于确保变量的可见性
- 适用于双重检查锁定模式

## Flow 使用要点

### SharedFlow vs MutableSharedFlow
```kotlin
// 内部可变，外部只读
private val _flow = MutableSharedFlow<Type>()  // 可发送
val flow: SharedFlow<Type> = _flow.asSharedFlow()  // 只读
```

### Flow 发送
- `emit()`：挂起函数，需要协程上下文，可能阻塞
- `tryEmit()`：普通函数，立即返回成功/失败状态，无需协程

### Flow 配置
```kotlin
MutableSharedFlow(
    replay = 0,                    // 重播数量
    extraBufferCapacity = 10,      // 缓冲区大小
    onBufferOverflow = BufferOverflow.DROP_OLDEST  // 溢出策略
)
```

## 上下文切换

### withContext vs launch
- `withContext()`：切换当前协程上下文，不创建新协程
- `launch()`：创建新子协程
- `withContext(Dispatchers.Default)`：用于计算密集型任务

## 异常处理
- `CoroutineExceptionHandler`：处理未捕获的异常
- SupervisorJob：异常不会传播到其他子协程
- 在协程中应妥善处理异常，避免影响其他任务

## 协程结构与取消
- **collect异常影响**：当Flow的collect操作出现异常时，会取消整个父协程及其所有子协程
- **结构化并发**：使用launch启动的子协程会随父协程一同取消
- **supervisorScope vs 普通作用域**：
  - 普通作用域：任一子协程异常会取消所有子协程
  - supervisorScope：子协程异常只影响自身，其他子协程继续运行
- **unreachable code**：collect是挂起函数，其后的代码只有在collect结束时才执行