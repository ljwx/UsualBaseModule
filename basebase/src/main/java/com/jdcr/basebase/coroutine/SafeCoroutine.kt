package com.jdcr.basebase.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface SafeCoroutine {

    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit
    ): Job

    fun launchWithTimeout(
        timeoutMs: Long,
        context: CoroutineContext = EmptyCoroutineContext,
        timeout: () -> Unit,
        block: suspend CoroutineScope.() -> Unit
    ): Job

    suspend fun <T> launchWithTimeout(
        timeoutMs: Long,
        block: suspend CoroutineScope.() -> T?
    ): Result<T?>

    fun <T> launchRetry(
        times: Int = 3,
        delayMs: Long = 1000,
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Result<T>
    ): Job

    fun <T> async(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> T
    ): Deferred<T?>

    fun cancel()

    fun onDestroy()

}