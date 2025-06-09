package com.jdcr.baseeventbus

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 基于SharedFlow的增强型事件总线实现
 * 支持基于key的事件发送和监听
 */
class FlowEventBus private constructor(
    private val config: FlowEventBusConfig
) : BaseModuleFlowEventBusBase(config), IFlowEventBus<Any?>, IFlowEventBus.EventFlowOperator<Any?> {

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


    // 监控器
    internal val monitor = if (config.monitoringConfig.enablePerformanceMonitoring ||
        config.monitoringConfig.enableEventTracking
    ) {
        FlowEventBusMonitor<Any?>(config.monitoringConfig)
    } else null

    /**
     * 清理未使用的事件流
     */
    override fun cleanupUnusedEventFlows() {
        val keysToRemove = eventFlows.keys.filter { key ->
            !subscriberScopes.containsKey(key) && !stickyEvents.containsKey(key)
        }
        keysToRemove.forEach { key ->
            eventFlows.remove(key)
        }
    }

    /**
     * 带重试的发送事件
     */
    suspend fun postWithRetry(key: String, event: Any?) {
        var retries = 0
        var lastException: Exception? = null

        while (retries < config.retryConfig.maxRetries) {
            try {
                post(key, event)
                return
            } catch (e: Exception) {
                lastException = e
                retries++
                if (retries < config.retryConfig.maxRetries) {
                    delay(config.retryConfig.retryDelayMillis)
                }
            }
        }
        lastException?.let { monitor?.onError(it) }
        throw FlowEventBusException.EventRetryException(
            "Failed to post event after ${config.retryConfig.maxRetries} retries",
            lastException
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
}