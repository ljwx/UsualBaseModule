package com.jdcr.basebase.eventbus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 事件总线核心接口
 */
interface IFlowEventBus<T> {
    /**
     * 事件发送接口
     */
    interface EventPoster<T> {
        /**
         * 发送事件
         * @param key 事件键值
         * @param event 事件数据
         */
        suspend fun post(key: String, event: T? = null)

        /**
         * 延迟发送事件
         * @param key 事件键值
         * @param event 事件数据
         * @param delayMillis 延迟时间（毫秒）
         */
        suspend fun postDelayed(key: String, event: T? = null, delayMillis: Long)

        /**
         * 批量发送事件
         * @param key 事件键值
         * @param events 事件列表
         */
        suspend fun postBatch(key: String, events: List<T>)

        /**
         * 发送粘性事件
         * @param key 事件键值
         * @param event 事件数据
         */
        suspend fun postSticky(key: String, event: T? = null)

        /**
         * 移除粘性事件
         * @param key 事件键值
         */
        suspend fun removeSticky(key: String)

        /**
         * 获取粘性事件
         * @param key 事件键值
         * @return 事件数据
         */
        fun getSticky(key: String): T?
    }

    /**
     * 事件订阅接口
     */
    interface EventSubscriber<T> {
        /**
         * 订阅事件
         * @param key 事件键值
         * @param scope 协程作用域
         * @param dispatcher 协程调度器
         * @param priority 优先级（为未来扩展预留）
         * @param filter 事件过滤器
         * @param onError 错误处理
         * @param onEvent 事件处理
         * @return 订阅ID
         */
        fun <R : T> subscribe(
            key: String,
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher? = Dispatchers.Main,
            priority: Int = 0,
            filter: ((R?) -> Boolean)? = null,
            onError: ((Throwable) -> Unit)? = null,
            onEvent: (R?) -> Unit
        ): String

        fun subscribe(
            key: String,
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher? = Dispatchers.Main,  // 默认使用主线程
            priority: Int = 0,
            filter: ((Any?) -> Boolean)? = null,
            onError: ((Throwable) -> Unit)? = null,
            onEvent: (Any?) -> Unit
        ): String

    }

    /**
     * 事件流操作接口
     */
    interface EventFlowOperator<T> {
        /**
         * 获取事件流
         * @param key 事件键值
         */
        fun <R : T> asFlow(key: String): Flow<R?>
    }

    /**
     * 资源管理接口
     */
    interface ResourceManager {
        /**
         * 重置事件总线
         * 清理所有资源并重置实例
         */
        fun reset()
    }
}