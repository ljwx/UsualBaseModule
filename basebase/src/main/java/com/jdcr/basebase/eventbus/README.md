# FlowEventBus

基于 Kotlin Flow 的增强型事件总线实现，支持基于 key 的事件发送和监听。

## 特性

- 基于 Kotlin Flow 实现，支持协程
- 支持基于 key 的事件发送和订阅
- 支持粘性事件（Sticky Events）
- 支持事件过滤
- 支持延迟发送
- 支持批量发送
- 支持错误处理
- 支持性能监控
- 线程安全
- 默认在主线程处理事件回调

## 快速开始

### 获取实例

```kotlin
val eventBus = FlowEventBus.getInstance()
```

### 发送事件

```kotlin
// 发送普通事件
eventBus.post("event_key", eventData)

// 发送粘性事件
eventBus.postSticky("event_key", eventData)

// 延迟发送事件
eventBus.postDelayed("event_key", eventData, 1000L)

// 批量发送事件
eventBus.postBatch("event_key", listOf(event1, event2, event3))
```

### 订阅事件

```kotlin
// 方式1：简化版订阅（推荐）
// 注意：默认在主线程处理事件回调
eventBus.subscribe(
    key = "event_key",
    scope = lifecycleScope  // 使用 lifecycleScope 避免内存泄漏
) { event ->
    // 处理事件（在主线程执行）
}

// 方式2：指定其他线程处理事件
eventBus.subscribe(
    key = "event_key",
    scope = lifecycleScope,
    dispatcher = Dispatchers.IO  // 在 IO 线程处理事件
) { event ->
    // 处理事件（在 IO 线程执行）
}

// 方式3：带过滤器的订阅
eventBus.subscribe(
    key = "event_key",
    scope = lifecycleScope,
    filter = { event -> 
        // 过滤条件
        event.someCondition 
    }
) { event ->
    // 处理事件（在主线程执行）
}

// 方式4：带错误处理的订阅
eventBus.subscribe(
    key = "event_key",
    scope = lifecycleScope,
    onError = { error ->
        // 处理错误（在主线程执行）
    }
) { event ->
    // 处理事件（在主线程执行）
}

// 方式5：完整参数订阅
eventBus.subscribe(
    key = "event_key",
    scope = lifecycleScope,
    dispatcher = Dispatchers.Main,  // 可选，指定协程调度器
    priority = 0,                   // 可选，指定优先级
    filter = { event -> true },     // 可选，事件过滤
    onError = { error -> },         // 可选，错误处理
) { event ->
    // 处理事件
}

// 方式6：指定泛型类型的订阅
eventBus.subscribe<YourEventType>(
    key = "event_key",
    scope = lifecycleScope
) { event ->
    // 处理事件，event 类型为 YourEventType
}
```

### 获取事件流

```kotlin
// 获取事件流
val eventFlow = eventBus.asFlow<EventType>("event_key")

// 使用事件流
lifecycleScope.launch {
    eventFlow.collect { event ->
        // 处理事件
    }
}
```

### 粘性事件操作

```kotlin
// 获取粘性事件
val stickyEvent = eventBus.getSticky("event_key")

// 移除粘性事件
eventBus.removeSticky("event_key")
```

## 最佳实践

1. **生命周期管理**
   - 在 ViewModel 中使用 `viewModelScope`
   - 在 Activity/Fragment 中使用 `lifecycleScope`
   - 确保在适当的生命周期中订阅和取消订阅
   - 避免使用 `GlobalScope`，可能导致内存泄漏

2. **线程处理**
   - 默认在主线程处理事件回调
   - 如果需要处理耗时操作，请指定 `Dispatchers.IO`
   - 注意线程切换可能带来的性能影响

3. **错误处理**
   - 始终提供错误处理回调
   - 在错误处理中记录日志或进行降级处理
   - 错误处理回调也在主线程执行

4. **性能优化**
   - 使用事件过滤减少不必要的处理
   - 合理设置事件缓冲区大小
   - 及时清理不需要的粘性事件
   - 避免在主线程处理耗时操作

5. **内存管理**
   - 及时取消不需要的订阅
   - 定期清理未使用的事件流
   - 避免存储过多粘性事件
   - 使用正确的 CoroutineScope 避免内存泄漏

## 注意事项

1. 事件总线是单例的，不要创建多个实例
2. 确保在适当的时机取消订阅，避免内存泄漏
3. 粘性事件会占用内存，及时清理不需要的粘性事件
4. 大量事件时注意性能影响
5. 建议使用常量定义事件 key，避免拼写错误
6. 默认在主线程处理事件回调，注意避免耗时操作
7. 使用 `lifecycleScope` 或 `viewModelScope` 避免内存泄漏

## 示例代码

```kotlin
// 在 ViewModel 中使用
class MyViewModel : ViewModel() {
    private val eventBus = FlowEventBus.getInstance()
    
    // 定义事件数据类
    data class UpdateEvent(val data: String)
    
    init {
        // 订阅事件
        eventBus.subscribe(
            key = "data_update",
            scope = viewModelScope,  // 使用 viewModelScope 避免内存泄漏
            onError = { error ->
                // 处理错误（在主线程执行）
                Log.e("MyViewModel", "Error: ${error.message}")
            }
        ) { event ->
            // 处理数据更新（在主线程执行）
            when (event) {
                is UpdateEvent -> handleUpdate(event.data)
                else -> Log.w("MyViewModel", "Unknown event type")
            }
        }
    }
    
    fun updateData() {
        viewModelScope.launch {
            // 发送事件
            eventBus.post("data_update", UpdateEvent("new data"))
        }
    }
    
    private fun handleUpdate(data: String) {
        // 处理更新逻辑
    }
}
```

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

[添加许可证信息] 