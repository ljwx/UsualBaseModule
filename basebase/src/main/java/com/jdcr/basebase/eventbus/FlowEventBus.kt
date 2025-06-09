package com.jdcr.basebase.eventbus

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 基于SharedFlow的增强型事件总线实现
 * 支持基于key的事件发送和监听
 */
class FlowEventBus private constructor(
    private val config: FlowEventBusConfig
) : IFlowEventBus<Any?>,
    IFlowEventBus.EventPoster<Any?>,
    IFlowEventBus.EventSubscriber<Any?>,
    IFlowEventBus.EventFlowOperator<Any?>,
    IFlowEventBus.ResourceManager {

    companion object {
        @Volatile
        private var instance: FlowEventBus? = null

        /**
         * 获取事件总线实例
         */
        fun getInstance(): FlowEventBus {
            return instance ?: synchronized(this) {
                instance ?: FlowEventBus(FlowEventBusConfig.DEFAULT).also {
                    instance = it
                }
            }
        }

        /**
         * 重置事件总线实例
         */
        fun reset() {
            instance = null
        }
    }

    // 事件流映射表
    private val eventFlows = ConcurrentHashMap<String, MutableSharedFlow<FlowEventWrapper<Any?>>>()

    // 粘性事件存储
    private val stickyEvents = ConcurrentHashMap<String, FlowEventWrapper<Any?>>()

    // 活跃订阅者计数
    private val activeSubscribers = AtomicInteger(0)

    // 订阅者管理
    private val subscriberScopes = ConcurrentHashMap<String, CoroutineScope>()

    // 监控器
    internal val monitor = if (config.monitoringConfig.enablePerformanceMonitoring ||
        config.monitoringConfig.enableEventTracking
    ) {
        FlowEventBusMonitor<Any?>(config.monitoringConfig)
    } else null

    // 协程配置
    internal val coroutineConfig = config.coroutineConfig

    /**
     * 获取或创建指定key的事件流
     */
    private fun getOrCreateEventFlow(key: String): MutableSharedFlow<FlowEventWrapper<Any?>> {
        return eventFlows.getOrPut(key) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = config.performanceConfig.eventBufferSize
            )
        }
    }

    /**
     * 清理未使用的事件流
     */
    private fun cleanupUnusedEventFlows() {
        val keysToRemove = eventFlows.keys.filter { key ->
            !subscriberScopes.containsKey(key) && !stickyEvents.containsKey(key)
        }
        keysToRemove.forEach { key ->
            eventFlows.remove(key)
        }
    }

    /**
     * 发送事件
     */
    override suspend fun post(key: String, event: Any?) {
        val eventWrapper = FlowEventWrapper.create(event)
        monitor?.onEventPosted(eventWrapper)

        try {
            getOrCreateEventFlow(key).emit(eventWrapper)
            monitor?.onEventProcessed(eventWrapper, 0)
        } catch (e: Exception) {
            monitor?.onError(e)
            throw FlowEventBusException.EventPostException("Failed to post event: ${e.message}", e)
        }
    }

    /**
     * 延迟发送事件
     */
    override suspend fun postDelayed(key: String, event: Any?, delayMillis: Long) {
        CoroutineScope(config.coroutineConfig.defaultDispatcher).launch {
            delay(delayMillis)
            post(key, event)
        }
    }

    /**
     * 发送粘性事件
     */
    override suspend fun postSticky(key: String, event: Any?) {
        val eventWrapper = FlowEventWrapper.create(event)
        // 检查是否超过最大粘性事件数量
        if (stickyEvents.size >= config.performanceConfig.maxStickyEvents) {
            // 如果超过限制，移除最早的事件
            val oldestKey = stickyEvents.keys.firstOrNull()
            if (oldestKey != null) {
                stickyEvents.remove(oldestKey)
            }
        }
        stickyEvents[key] = eventWrapper
        post(key, event)
    }

    /**
     * 获取粘性事件
     */
    override fun getSticky(key: String): Any? = stickyEvents[key]?.value

    /**
     * 移除粘性事件
     */
    override suspend fun removeSticky(key: String) {
        stickyEvents.remove(key)
    }

    /**
     * 订阅事件
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> subscribe(
        key: String,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher?,
        priority: Int,
        filter: ((T?) -> Boolean)?,
        onError: ((Throwable) -> Unit)?,
        onEvent: (T?) -> Unit
    ): String {
        val subscriberId = activeSubscribers.getAndIncrement().toString()
        
        // 记录订阅者scope
        subscriberScopes[subscriberId] = scope

        // 监听scope的取消
        scope.launch(dispatcher ?: config.coroutineConfig.defaultDispatcher) {
            try {
                getOrCreateEventFlow(key)
                    .filter { event -> 
                        filter?.invoke(event.value as? T) ?: true 
                    }
                    .catch { e ->
                        onError?.invoke(e)
                        throw FlowEventBusException.EventSubscribeException(
                            "Failed to subscribe to events: ${e.message}",
                            e
                        )
                    }
                    .collect { event ->
                        try {
                            onEvent(event.value as? T)
                        } catch (e: Exception) {
                            onError?.invoke(e)
                            throw FlowEventBusException.EventSubscribeException(
                                "Error processing event: ${e.message}",
                                e
                            )
                        }
                    }
            } finally {
                activeSubscribers.decrementAndGet()
                subscriberScopes.remove(subscriberId)
                // 清理未使用的事件流
                cleanupUnusedEventFlows()
            }
        }

        return subscriberId
    }

    /**
     * 简化版订阅方法，默认使用 Any? 类型
     * 默认在主线程处理事件
     */
    override fun subscribe(
        key: String,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher?,  // 默认使用主线程
        priority: Int,
        filter: ((Any?) -> Boolean)?,
        onError: ((Throwable) -> Unit)?,
        onEvent: (Any?) -> Unit
    ): String {
        return subscribe<Any?>(
            key = key,
            scope = scope,
            dispatcher = dispatcher,
            priority = priority,
            filter = filter,
            onError = onError,
            onEvent = onEvent
        )
    }

    /**
     * 获取事件流
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> asFlow(key: String): Flow<T?> = 
        getOrCreateEventFlow(key).map { it.value as? T }

    /**
     * 重置事件总线
     */
    override fun reset() {
        eventFlows.clear()
        stickyEvents.clear()
        subscriberScopes.clear()
        activeSubscribers.set(0)
        monitor?.reset()
        instance = null
    }

    /**
     * 批量发送事件
     */
    override suspend fun postBatch(key: String, events: List<Any?>) {
        events.forEach { post(key, it) }
    }
}