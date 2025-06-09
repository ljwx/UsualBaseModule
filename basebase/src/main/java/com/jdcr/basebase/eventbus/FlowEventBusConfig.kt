package com.jdcr.basebase.eventbus

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 事件总线配置
 */
data class FlowEventBusConfig(
    /**
     * 性能配置
     */
    val performanceConfig: PerformanceConfig = PerformanceConfig(),

    /**
     * 监控配置
     */
    val monitoringConfig: MonitoringConfig = MonitoringConfig(),

    /**
     * 协程配置
     */
    val coroutineConfig: CoroutineConfig = CoroutineConfig()
) {
    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = FlowEventBusConfig()
    }

    /**
     * 性能配置
     */
    data class PerformanceConfig(
        /**
         * 事件缓冲区大小
         */
        val eventBufferSize: Int = 64,

        /**
         * 最大粘性事件数量
         */
        val maxStickyEvents: Int = 50
    )

    /**
     * 监控配置
     */
    data class MonitoringConfig(
        /**
         * 是否启用性能监控
         */
        val enablePerformanceMonitoring: Boolean = false,

        /**
         * 是否启用事件追踪
         */
        val enableEventTracking: Boolean = false
    )

    /**
     * 协程配置
     */
    data class CoroutineConfig(
        /**
         * 默认调度器
         */
        val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    )
}