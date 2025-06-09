package com.jdcr.baseeventbus

import java.util.concurrent.atomic.AtomicLong

/**
 * 事件总线监控器
 */
class FlowEventBusMonitor<T>(
    private val config: FlowEventBusConfig.MonitoringConfig
) {
    // 事件计数器
    private val totalEvents = AtomicLong(0)
    private val errorCount = AtomicLong(0)
    private var lastError: Throwable? = null
    private val processingTimes = mutableListOf<Long>()

    // 事件追踪
    private val postedEvents = mutableListOf<FlowEventWrapper<T?>>()
    private val processedEvents = mutableListOf<FlowEventWrapper<T?>>()
    private val cancelledEvents = mutableSetOf<String>()

    /**
     * 事件发送回调
     */
    fun onEventPosted(event: FlowEventWrapper<T?>) {
        if (!config.enableEventTracking) return
        
        postedEvents.add(event)
        totalEvents.incrementAndGet()
    }

    /**
     * 事件处理回调
     */
    fun onEventProcessed(event: FlowEventWrapper<T?>, processingTime: Long) {
        if (!config.enableEventTracking) return
        
        processedEvents.add(event)
        processingTimes.add(processingTime)
    }

    /**
     * 事件取消回调
     */
    fun onEventCancelled(eventId: String) {
        if (!config.enableEventTracking) return
        cancelledEvents.add(eventId)
    }

    /**
     * 错误处理回调
     */
    fun onError(error: Throwable) {
//        if (config.enableLogging) {
//            Log.e(config.tag, "FlowEventBus error: ${error.message}", error)
//        }
        errorCount.incrementAndGet()
        lastError = error
    }

    /**
     * 获取事件统计
     */
    fun getEventStats(): EventStats {
        return EventStats(
            totalEvents = totalEvents.get(),
            activeSubscribers = 0, // 这个值需要从外部传入
            stickyEvents = 0, // 这个值需要从外部传入
            errorCount = errorCount.get(),
            averageProcessingTime = if (processingTimes.isNotEmpty()) {
                processingTimes.average().toLong()
            } else 0
        )
    }

    /**
     * 获取事件追踪统计
     */
    fun getTrackingStats(): EventTrackingStats {
        return EventTrackingStats(
            postedCount = postedEvents.size,
            processedCount = processedEvents.size,
            cancelledCount = cancelledEvents.size,
            lostCount = postedEvents.size - processedEvents.size - cancelledEvents.size
        )
    }

    /**
     * 重置监控器
     */
    fun reset() {
        totalEvents.set(0)
        errorCount.set(0)
        lastError = null
        processingTimes.clear()
        postedEvents.clear()
        processedEvents.clear()
        cancelledEvents.clear()
    }
}

/**
 * 事件追踪统计
 */
data class EventTrackingStats(
    val postedCount: Int,
    val processedCount: Int,
    val cancelledCount: Int,
    val lostCount: Int
)

/**
 * 事件统计
 */
data class EventStats(
    val totalEvents: Long = 0,
    val activeSubscribers: Int = 0,
    val stickyEvents: Int = 0,
    val errorCount: Long = 0,
    val averageProcessingTime: Long = 0
)