package com.jdcr.baseeventbus

import com.jdcr.baseeventbus.api.IFlowEventBus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * 一个基于 Kotlin Flow 的事件总线。
 */
class FlowEventBus private constructor(private val config: FlowEventBusConfig) : IFlowEventBus {

    companion object {
        @Volatile
        private var instance: FlowEventBus? = null

        fun init(config: FlowEventBusConfig) {
            if (instance != null) {
                FlowEventBusLog.d("实例已创建")
                return
            }
            synchronized(this) {
                if (instance == null) {
                    instance = FlowEventBus(config)
                }
                return
            }
        }

        fun getInstance(): FlowEventBus {
            return instance ?: synchronized(this) {
                instance ?: FlowEventBus(FlowEventBusConfig.DEFAULT).also { instance = it }
            }
        }

        fun destroy() {
            instance?.destroy()
            instance = null
        }
    }

    private val bus = ConcurrentHashMap<Pair<String, KClass<*>>, FlowEventConfig>()

    @PublishedApi
    internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 获取或创建一个 Flow 通道。
     *
     * 使用 @PublishedApi 注解，使其在二进制接口中为 public，以供 public inline 函数调用，
     * 同时在源代码中保持 internal 的封装性，对库用户不可见。
     * 这是处理此问题的标准、正确方式。
     */
    @PublishedApi
    internal fun getOrCreateFlow(
        key: String,
        type: KClass<*>,
        isSticky: Boolean
    ): MutableSharedFlow<Any> {
        val internalKey = Pair(key, type)
        FlowEventBusLog.d("创建或获取flow:$internalKey")
        val existingConfig = bus[internalKey]
        if (existingConfig != null) {
            if (existingConfig.isSticky != isSticky) {
                FlowEventBusLog.e("事件的粘性属性不对")
                throw IllegalArgumentException(
                    "Event '$key' with type '${type.simpleName}' was already registered as ${if (existingConfig.isSticky) "sticky" else "non-sticky"}."
                )
            }
            FlowEventBusLog.d("flow已存在:$internalKey")
            return existingConfig.flow
        }

        val newFlow = MutableSharedFlow<Any>(
            replay = if (isSticky) 1 else 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val monitorJob = if (!isSticky && config.autoClean.enable) {
            scope.launch {
                newFlow.subscriptionCount.debounce(config.autoClean.debounce)
                    .filter { count -> count == 0 }.take(1).collect {
                        bus.remove(internalKey)
                    }
            }
        } else null

        val newConfig = FlowEventConfig(newFlow, isSticky, monitorJob)
        val raceConfig = bus.putIfAbsent(internalKey, newConfig)
        val finalConfig = raceConfig ?: newConfig

        if (finalConfig !== newConfig) {
            monitorJob?.cancel()
        }

        if (finalConfig.isSticky != isSticky) {
            FlowEventBusLog.e("事件的粘性属性不对2")
            throw IllegalArgumentException(
                "Event '$key' with type '${type.simpleName}' was already registered as ${if (finalConfig.isSticky) "sticky" else "non-sticky"}."
            )
        }
        FlowEventBusLog.d("返回可用flow:$internalKey")
        return finalConfig.flow
    }

    /**
     * 仅发送事件
     */
    override fun post(
        key: String,
        delay: Long?
    ) = post<Unit>(key, Unit, delay)

    /**
     * 发送事件和数据
     */
    inline fun <reified T : Any> post(
        key: String,
        data: T,
        delay: Long?
    ) {
        FlowEventBusLog.d("发送事件:$key,$data")
        scope.launch {
            if (delay != null) {
                delay(delay)
            }
            getOrCreateFlow(key, T::class, isSticky = false).emit(data)
        }
    }

    /**
     * 发送粘性事件及数据
     */
    inline fun <reified T : Any> postSticky(
        key: String,
        data: T,
        delay: Long?
    ) {
        FlowEventBusLog.d("发送粘性事件:$key,$data")
        scope.launch {
            if (delay != null) {
                delay(delay)
            }
            getOrCreateFlow(key, T::class, isSticky = true).emit(data)
        }
    }

    /**
     * 仅发送粘性事件
     */
    override fun postSticky(
        key: String,
        delay: Long?
    ) = postSticky<Unit>(key, Unit, delay)

    /**
     * 订阅事件及数据
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> subscribe(
        key: String,
        scope: CoroutineScope,
        crossinline onReceived: suspend (T) -> Unit,
    ): Job {
        val flow = getOrCreateFlow(key, T::class, isSticky = false) as Flow<T>
        return flow.onEach { data ->
            FlowEventBusLog.d("接收到事件:$key,$data")
            onReceived(data)
        }.launchIn(scope)
    }

    /**
     * 订阅事件
     */
    override fun subscribe(
        key: String,
        scope: CoroutineScope,
        onReceived: suspend () -> Unit
    ): Job = subscribe<Unit>(key, scope) { onReceived() }

    /**
     * 订阅粘性事件及数据
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> subscribeSticky(
        key: String,
        scope: CoroutineScope,
        crossinline onReceived: suspend (T) -> Unit
    ): Job {
        val flow = getOrCreateFlow(key, T::class, isSticky = true) as Flow<T>
        return flow.onEach { data ->
            FlowEventBusLog.d("接收到事件:$key,$data")
            onReceived(data)
        }.launchIn(scope)
    }

    /**
     * 订阅粘性事件
     */
    override fun subscribeSticky(
        key: String,
        scope: CoroutineScope,
        onReceived: suspend () -> Unit
    ): Job = subscribeSticky<Unit>(key, scope) { onReceived() }

    private fun destroy() {
        bus.values.forEach { it.monitorJob?.cancel() }
        bus.clear()
        scope.cancel()
    }

}