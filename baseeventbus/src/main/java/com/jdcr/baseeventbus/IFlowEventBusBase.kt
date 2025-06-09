package com.jdcr.baseeventbus

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

interface IFlowEventBusBase {

    /**
     * 事件发送接口
     */
    interface EventPoster<T> {
        /**
         * 发送事件
         * @param key 事件键值
         * @param event 事件数据
         */
        suspend fun post(
            key: String,
            event: T? = null,
            timeoutMillis: Long? = null,
            onError: ((Throwable) -> Unit)? = null
        )

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
            onError: ((Throwable) -> Unit)? = null,
            onEvent: (R?) -> Unit
        ): String

        fun subscribe(
            key: String,
            scope: CoroutineScope,
            dispatcher: CoroutineDispatcher? = Dispatchers.Main,
            priority: Int = 0,
            onError: ((Throwable) -> Unit)? = null,
            onEvent: () -> Unit
        ): String

    }

}