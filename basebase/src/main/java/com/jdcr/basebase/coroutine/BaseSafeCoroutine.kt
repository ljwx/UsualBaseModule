package com.jdcr.basebase.coroutine

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class BaseSafeCoroutine(
    parentScope: CoroutineScope? = null,
    private val tag: String = "BaseSafeCoroutine",
    private val defaultContext: CoroutineContext = Dispatchers.IO
) : SafeCoroutine {

    private val coroutineName = CoroutineName(tag)
    private val globalExceptionHandler by lazy {
        CoroutineExceptionHandler { context, throwable ->
            Log.e(tag + "协程异常", throwable.message ?: "无报错信息")
            throwable.printStackTrace()
        }
    }
    private val _job = SupervisorJob(parentScope?.coroutineContext?.get(Job))
    private val parentContext =
        parentScope?.coroutineContext?.minusKey(Job) ?: EmptyCoroutineContext
    private val coroutineContext by lazy { parentContext + defaultContext + _job + globalExceptionHandler + coroutineName }
    private val _scope = CoroutineScope(coroutineContext)

    override fun launch(
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job = _scope.launch(context, block = block)

    override fun launchWithTimeout(
        timeoutMs: Long,
        context: CoroutineContext,
        timeout: () -> Unit,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return launch(context) {
            try {
                withTimeout(timeoutMs, block = block)
            } catch (e: TimeoutCancellationException) {
                timeout()
            }
        }
    }

    override suspend fun <T> withTimeoutResult(
        timeoutMs: Long,
        block: suspend CoroutineScope.() -> T
    ): Result<T> {
        return try {
            val result = withTimeout(timeoutMs, block = block)
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Result.failure(TimeoutException(e.message ?: "超时了"))
        }
    }

    override suspend fun <T> retry(
        times: Int,
        delayMs: Long,
        block: suspend () -> Result<T>
    ): Result<T> {
        require(times > 0) { "次数需要大于0" }
        var lastResult: Result<T> = Result.failure(Exception("重试也没成功"))
        repeat(times) { index ->
            lastResult = block()
            if (lastResult.isSuccess) return lastResult
            if (index < times - 1) delay(delayMs)
        }
        return lastResult
    }

    override fun <T> async(
        context: CoroutineContext,
        block: suspend CoroutineScope.() -> T
    ): Deferred<T> = _scope.async(context, block = block)

    override fun cancelChildren() {
        _job.cancelChildren()
    }

    override fun cancelScope() {
        _job.cancel()
    }

}