package com.jdcr.basebase.coroutine

import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class BaseSafeCoroutine(
    lifecycleCoroutineScope: LifecycleCoroutineScope? = null,
    tag: String? = null
) : SafeCoroutine {

    private val coroutineName by lazy { CoroutineName(tag ?: "") }
    private val globalExceptionHandler by lazy {
        CoroutineExceptionHandler { context, throwable ->
            Log.e((tag ?: "") + "协程异常", throwable.message ?: "无报错信息")
            throwable.printStackTrace()
        }
    }
    private val coroutineContext by lazy { SupervisorJob() + Dispatchers.IO + globalExceptionHandler + (if (tag.isNullOrEmpty()) EmptyCoroutineContext else coroutineName) }
    private val _scope by lazy { lifecycleCoroutineScope ?: CoroutineScope(coroutineContext) }

    override fun launch(
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return _scope.launch(context, block = block)
    }

    override fun launchWithTimeout(
        timeoutMs: Long,
        context: CoroutineContext,
        timeout: () -> Unit,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch(context) {
            val result = withTimeoutOrNull(timeoutMs, block = block)
            if (result == null) {
                timeout()
            }
        }
    }

    override suspend fun <T> launchWithTimeout(
        timeoutMs: Long,
        block: suspend CoroutineScope.() -> T?
    ): Result<T?> {
        val result: T? = withTimeoutOrNull(timeoutMs, block = block)
        if (result == null) {
            return Result.failure(TimeoutException("超时了"))
        } else {
            return Result.success(result)
        }
    }

    override fun <T> launchRetry(
        times: Int,
        delayMs: Long,
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> Result<T>
    ): Job {
        return launch(context) {
            repeat(times) { index ->
                val result = block()
                if (result.isSuccess) return@launch
                if (index < times - 1) delay(delayMs)
            }
        }
    }

    override fun <T> async(
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> T
    ): Deferred<T?> {
        return _scope.async(context, block = block)
    }

    override fun cancel() {
        coroutineContext[Job]?.cancelChildren()
    }

    override fun onDestroy() {
        _scope.cancel()
    }

}