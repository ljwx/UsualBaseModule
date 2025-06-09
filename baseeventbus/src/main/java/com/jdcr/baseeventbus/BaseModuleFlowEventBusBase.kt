package com.jdcr.baseeventbus

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * 基础模块Flow事件总线实现
 *
 * 版本兼容性说明：
 * - 支持Kotlin 1.6.0及以上版本
 * - 支持kotlinx-coroutines 1.6.0及以上版本
 * - 使用稳定的Flow API和协程API
 */
abstract class BaseModuleFlowEventBusBase(private val config: FlowEventBusConfig) :
    IFlowEventBusBase.EventPoster<Any?>, IFlowEventBusBase.EventSubscriber<Any?>,
    IFlowEventBus.ResourceManager {

    // 事件流映射表
    internal val eventFlows =
        ConcurrentHashMap<String, MutableSharedFlow<FlowEventWrapper<Any?>>>()

    // 粘性事件存储
    internal val stickyEvents by lazy { ConcurrentHashMap<String, FlowEventWrapper<Any?>>() }

    // 活跃订阅者计数
    internal val activeSubscribers = AtomicInteger(0)

    // 订阅者管理
    internal val subscriberScopes = ConcurrentHashMap<String, CoroutineScope>()

    // 协程配置
    internal val coroutineConfig = config.coroutineConfig

    /**
     * 获取或创建指定key的事件流
     *
     * 注意：MutableSharedFlow的构造函数参数可能会在未来版本中变化
     * 建议在配置中提供这些参数，以便于未来版本升级时调整
     */
    protected fun getOrCreateEventFlow(key: String): MutableSharedFlow<FlowEventWrapper<Any?>> {
        return eventFlows.getOrPut(key) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = config.performanceConfig.eventBufferSize
            )
        }
    }

    /**
     * 发送事件
     */
    override suspend fun post(
        key: String,
        event: Any?,
        timeoutMillis: Long?,
        onError: ((Throwable) -> Unit)?
    ) {
        val eventWrapper = FlowEventWrapper.create(event)
        getEventBusMonitor()?.onEventPosted(eventWrapper)
        try {
            withTimeoutOrNull(timeoutMillis ?: config.timeoutConfig.defaultTimeoutMillis) {
                getOrCreateEventFlow(key).emit(eventWrapper)
            } ?: run {
                val timeoutException =
                    FlowEventBusException.EventTimeoutException("EventBus post timeout")
                getEventBusMonitor()?.onError(timeoutException)
                onError?.invoke(timeoutException)
            }
            getEventBusMonitor()?.onEventProcessed(eventWrapper, 0)
        } catch (e: Exception) {
            val postException =
                FlowEventBusException.EventPostException(
                    "EventBus post fail",
                    e
                )
            getEventBusMonitor()?.onError(postException)
            onError?.invoke(postException)
        }
    }

    /**
     * 延迟发送事件
     */
    override suspend fun postDelayed(key: String, event: Any?, delayMillis: Long) {
        val job = SupervisorJob()
        val scope = CoroutineScope(config.coroutineConfig.defaultDispatcher + job)
        scope.launch {
            try {
                delay(delayMillis)
                post(key, event)
            } finally {
                job.cancel() // 确保job被取消
            }
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
     * 批量发送事件
     */
    override suspend fun postBatch(key: String, events: List<Any?>) {
        events.forEach { post(key, it) }
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
        onError: ((Throwable) -> Unit)?,
        onEvent: (T?) -> Unit
    ): String {
        val subscriberId = activeSubscribers.getAndIncrement().toString()
        subscriberScopes[subscriberId] = scope

        scope.launch(dispatcher ?: config.coroutineConfig.defaultDispatcher) {
            try {
                getOrCreateEventFlow(key)
                    .collect { event ->
                        try {
                            // 添加类型安全检查
                            val value = event.value
                            if (value == null) {
                                onEvent(null)
                            } else {
                                try {
                                    onEvent(value as T)
                                } catch (e: ClassCastException) {
                                    val error = FlowEventBusException.TypeMismatchException(
                                        "EventBus type mismatch. Actual type: ${value.javaClass.name}"
                                    )
                                    onError?.invoke(error)
                                }
                            }
                        } catch (e: Exception) {
                            val subscribeException =
                                FlowEventBusException.EventSubscribeException(
                                    "EventBus subscribe fail",
                                    e
                                )
                            onError?.invoke(subscribeException)
                            getEventBusMonitor()?.onError(subscribeException)
                        }
                    }
            } catch (e: Exception) {
                val subscribeException =
                    FlowEventBusException.EventSubscribeException(
                        "EventBus subscribe fail",
                        e
                    )
                onError?.invoke(subscribeException)
                getEventBusMonitor()?.onError(subscribeException)
            } finally {
                activeSubscribers.decrementAndGet()
                subscriberScopes.remove(subscriberId)
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
        dispatcher: CoroutineDispatcher?,
        priority: Int,
        onError: ((Throwable) -> Unit)?,
        onEvent: () -> Unit
    ): String {
        return subscribe<Any?>(
            key = key,
            scope = scope,
            dispatcher = dispatcher,
            priority = priority,
            onError = onError,
            onEvent = {
                onEvent()
            }
        )
    }

    open fun getEventBusMonitor(): FlowEventBusMonitor<Any?>? {
        return null
    }

}