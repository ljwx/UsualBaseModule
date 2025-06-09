package com.jdcr.basebase.eventbus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * FlowEventBus 的扩展功能类
 */
object FlowEventBusExtensions {
    private val debounceTimers = ConcurrentHashMap<String, Job>()
    private val throttleTimers = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 防抖发送事件
     * @param key 事件键值
     * @param event 事件数据
     * @param delayMillis 延迟时间（毫秒）
     */
    suspend fun <T> FlowEventBus.postDebounce(key: String, event: T, delayMillis: Long = 300) {
        val eventWrapper = FlowEventWrapper.create(event)
        (this as FlowEventBus).monitor?.onEventPosted(eventWrapper)

        debounceTimers[key]?.cancel()
        debounceTimers[key] = CoroutineScope(coroutineConfig.defaultDispatcher).launch {
            try {
                delay(delayMillis)
                post(key, event)
                (this@postDebounce as FlowEventBus).monitor?.onEventProcessed(eventWrapper, 0)
            } finally {
                debounceTimers.remove(key)
            }
        }
    }

    /**
     * 节流发送事件
     * @param key 事件键值
     * @param event 事件数据
     * @param intervalMillis 间隔时间（毫秒）
     * @return 是否发送成功
     */
    suspend fun <T> FlowEventBus.postThrottle(key: String, event: T, intervalMillis: Long = 1000): Boolean {
        val lastTime = throttleTimers.getOrPut(key) { AtomicLong(0) }
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastTime.get() < intervalMillis) {
            return false
        }
        
        lastTime.set(currentTime)
        post(key, event)
        return true
    }

    /**
     * 清理所有定时器
     */
    fun cleanup() {
        debounceTimers.values.forEach { it.cancel() }
        debounceTimers.clear()
        throttleTimers.clear()
    }

    /**
     * 简化版订阅方法
     * @param key 事件键值
     * @param scope 协程作用域
     * @param onEvent 事件处理回调
     */
    fun <T> FlowEventBus.subscribe(
        key: String,
        scope: CoroutineScope,
        onEvent: (T?) -> Unit
    ): String {
        return subscribe(
            key = key,
            scope = scope,
            dispatcher = null,  // 使用默认调度器
            priority = 0,
            filter = null,
            onError = null,
            onEvent = onEvent
        )
    }

    /**
     * 带错误处理的简化版订阅方法
     * @param key 事件键值
     * @param scope 协程作用域
     * @param onError 错误处理回调
     * @param onEvent 事件处理回调
     */
    fun <T> FlowEventBus.subscribe(
        key: String,
        scope: CoroutineScope,
        onError: ((Throwable) -> Unit)?,
        onEvent: (T?) -> Unit
    ): String {
        return subscribe(
            key = key,
            scope = scope,
            dispatcher = null,  // 使用默认调度器
            priority = 0,
            filter = null,
            onError = onError,
            onEvent = onEvent
        )
    }
} 