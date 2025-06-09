# FlowEventBus

基于 Kotlin Flow 实现的增强型事件总线，支持事件发送、订阅、粘性事件、超时控制、重试机制等功能。

## 特性

- 基于 SharedFlow 实现
- 支持普通事件和粘性事件
- 支持事件过滤
- 支持延迟发送
- 支持批量发送
- 支持超时控制
- 支持重试机制
- 支持性能监控
- 支持事件追踪
- 线程安全
- 内存安全

## 快速开始

### 1. 获取实例

```kotlin
// 获取默认实例
val eventBus = FlowEventBus.getInstance()

// 使用自定义配置
val config = FlowEventBusConfig(
    performanceConfig = PerformanceConfig(
        eventBufferSize = 1000,
        maxStickyEvents = 100
    ),
    timeoutConfig = TimeoutConfig(
        defaultTimeoutMillis = 5000
    ),
    retryConfig = RetryConfig(
        maxRetries = 3,
        retryDelayMillis = 1000
    )
)
val customEventBus = FlowEventBus(config)
```

### 2. 发送事件

```kotlin
// 基本发送
eventBus.post("key", event)

// 带超时的发送
eventBus.post("key", event, timeoutMillis = 5000)

// 延迟发送
eventBus.postDelayed("key", event, 1000)

// 发送粘性事件
eventBus.postSticky("key", event)

// 批量发送
eventBus.postBatch("key", listOf(event1, event2, event3))
```

### 3. 订阅事件

#### 3.1 在 ViewModel 中使用

```kotlin
class MyViewModel : ViewModel() {
    private val eventBus = FlowEventBus.getInstance()
    private var subscriberId: String? = null
    
    init {
        // 订阅事件
        subscriberId = eventBus.subscribe(
            key = "key",
            scope = viewModelScope,  // 使用viewModelScope确保生命周期管理
            onEvent = { event ->
                // 处理事件
            }
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        // 取消订阅
        subscriberId?.let { eventBus.unsubscribe(it) }
    }
}
```

#### 3.2 在 Activity/Fragment 中使用

```kotlin
class MyActivity : AppCompatActivity() {
    private val eventBus = FlowEventBus.getInstance()
    private var subscriberId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 订阅事件
        subscriberId = eventBus.subscribe(
            key = "key",
            scope = lifecycleScope,  // 使用lifecycleScope确保生命周期管理
            onEvent = { event ->
                // 处理事件
            }
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消订阅
        subscriberId?.let { eventBus.unsubscribe(it) }
    }
}
```

#### 3.3 使用 Flow API

```kotlin
viewModelScope.launch {
    eventBus.asFlow<MyEvent>("key")
        .collect { event ->
            // 处理事件
        }
}
```

### 4. 粘性事件

```kotlin
// 发送粘性事件
eventBus.postSticky("key", event)

// 获取粘性事件
val event = eventBus.getSticky("key")

// 移除粘性事件
eventBus.removeSticky("key")
```

## 最佳实践

### 1. 生命周期管理

- 始终使用正确的生命周期作用域：
  - ViewModel 中使用 `viewModelScope`
  - Activity/Fragment 中使用 `lifecycleScope`
- 避免使用全局作用域（如 `CoroutineScope(Dispatchers.Main)`）
- 在组件销毁时取消订阅

### 2. 内存管理

- 及时清理不需要的粘性事件
- 避免在事件处理中持有大对象
- 使用完后及时调用 `reset()` 清理资源

### 3. 性能优化

- 合理设置事件缓冲区大小
- 避免过多粘性事件
- 使用批量发送减少事件数量
- 合理设置超时时间

### 4. 错误处理

```kotlin
// 使用 try-catch
try {
    eventBus.post("key", event)
} catch (e: FlowEventBusException.EventPostException) {
    // 处理发送异常
} catch (e: FlowEventBusException.EventTimeoutException) {
    // 处理超时异常
}

// 使用协程异常处理器
val handler = CoroutineExceptionHandler { _, exception ->
    when (exception) {
        is FlowEventBusException.EventPostException -> {
            // 处理发送异常
        }
        is FlowEventBusException.EventTimeoutException -> {
            // 处理超时异常
        }
    }
}

viewModelScope.launch(handler) {
    eventBus.post("key", event)
}
```

## 注意事项

### 1. 内存泄漏防护

- 确保使用正确的生命周期作用域
- 在组件销毁时取消订阅
- 及时清理粘性事件
- 避免在事件处理中持有外部引用

### 2. 性能考虑

- 合理设置事件缓冲区大小（默认256）
- 控制粘性事件数量（默认最大75个）
- 避免在事件处理中执行耗时操作
- 使用批量发送减少事件数量

### 3. 线程安全

- 事件总线本身是线程安全的
- 事件处理回调在指定的调度器上执行
- 注意事件处理中的线程切换

### 4. 异常处理

- 始终处理可能的异常
- 使用协程异常处理器
- 提供用户友好的错误提示
- 记录关键异常信息

## 常见问题

### 1. 事件没有被接收

可能的原因：
- 订阅的 key 不正确
- 订阅的作用域已取消
- 事件过滤器过滤了事件
- 事件处理发生异常

解决方案：
- 检查 key 是否正确
- 确保使用正确的生命周期作用域
- 检查事件过滤器
- 添加异常处理

### 2. 内存泄漏

可能的原因：
- 使用了全局作用域
- 没有取消订阅
- 粘性事件没有清理
- 事件处理中持有外部引用

解决方案：
- 使用正确的生命周期作用域
- 在组件销毁时取消订阅
- 及时清理粘性事件
- 避免在事件处理中持有外部引用

### 3. 性能问题

可能的原因：
- 事件缓冲区设置过小
- 粘性事件过多
- 事件处理耗时
- 事件发送频率过高

解决方案：
- 调整事件缓冲区大小
- 控制粘性事件数量
- 优化事件处理逻辑
- 使用批量发送或节流

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

[添加许可证信息] 