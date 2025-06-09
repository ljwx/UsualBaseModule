package com.jdcr.baseeventbus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 事件总线核心接口
 */
interface IFlowEventBus<T> {

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
         * 清理未使用的事件流
         */
        fun cleanupUnusedEventFlows()

        /**
         * 重置事件总线
         * 清理所有资源并重置实例
         */
        fun reset()
    }
}