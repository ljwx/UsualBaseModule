package com.jdcr.baseeventbus

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * 事件包装类
 */
data class FlowEventWrapper<T>(
    /**
     * 事件数据
     */
    val value: T?,

    /**
     * 事件ID
     */
    val id: String = UUID.randomUUID().toString(),

    /**
     * 事件时间戳
     */
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * 事件优先级（内部使用，为未来扩展预留）
     * @internal
     */
    internal val priority: Int = 0,

) {
    companion object {
        private val counter = AtomicLong(0)

        /**
         * 创建事件包装器
         */
        fun create(
            value: Any?,
            priority: Int = 0,
        ): FlowEventWrapper<Any?> {
            return FlowEventWrapper(
                value = value,
                id = "${counter.incrementAndGet()}_${System.currentTimeMillis()}",
                timestamp = System.currentTimeMillis(),
                priority = priority,
            )
        }
    }
}

/**
 * 事件总线异常
 */
sealed class FlowEventBusException : Exception() {
    /**
     * 事件发送异常
     */
    data class EventPostException(
        override val message: String,
        override val cause: Throwable? = null
    ) : FlowEventBusException()

    /**
     * 事件订阅异常
     */
    data class EventSubscribeException(
        override val message: String,
        override val cause: Throwable? = null
    ) : FlowEventBusException()

    data class EventTimeoutException(
        override val message: String,
        override val cause: Throwable? = null
    ) : FlowEventBusException()

    class EventRetryException(
        override val message: String,
        override val cause: Throwable? = null
    ) : FlowEventBusException()

    class TypeMismatchException(
        override val message: String,
        override val cause: Throwable? = null
    ) : FlowEventBusException()
}