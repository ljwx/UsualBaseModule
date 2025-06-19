package com.jdcr.network.ktor.result

import com.jdcr.network.ktor.exception.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch

/**
 * 将 Ktor 请求包装成 Flow，并处理常见的 NetworkException。
 * 如果成功，发射 T 类型的数据；如果失败，通过 catch 捕获并发出 Error 类型。
 *
 * @param client HttpClient 实例
 * @param requestBuilder HttpRequestBuilder 的配置 lambda
 * @param mapSuccessBody 可选的映射函数，用于将成功的 HttpResponse body 映射到业务数据类型T
 * @return 一个 Flow，它会发出 NetworkResult<T>。
 */
inline fun <reified T> HttpClient.requestAsFlow(
    crossinline requestBuilder: HttpRequestBuilder.() -> Unit,
    noinline mapSuccessBody: (suspend (HttpResponse) -> T)? = null
): Flow<NetworkRequestResult<T>> = flow {
    val response = this@requestAsFlow.request {
        requestBuilder()
    }
    if (response.status.isSuccess()) {
        val data = mapSuccessBody?.invoke(response) ?: response.body<T>()
        emit(NetworkRequestResult.Success(data))
    } else {
        // 尽管 CommonResponsePlugin 会抛出异常，这里作为兜底，如果 somehow 2xx 以外的响应没抛异常，
        // 也能将其包装成错误。但这通常不应该被执行到。
        val errorBody = try {
            response.bodyAsText()
        } catch (e: Exception) {
            "无法读取错误响应体"
        }
        emit(
            NetworkRequestResult.Exception(
                NetworkException.UnknownException(
                    response.status.value,
                    errorBody,
                    response.call.request.url.toString()
                )
            )
        )
    }
}.catch { e ->
    // 在这里捕获所有上游抛出的异常，包括 NetworkException 及其子类
    when (e) {
        is NetworkException -> emit(NetworkRequestResult.Failure(e))
        else -> {
            // 其他未被自定义的异常，如 IOException, SocketTimeoutException 等
            emit(NetworkRequestResult.Exception(e))
        }
    }
}

// 辅助函数：如果只需要 HttpResponse 本身
inline fun HttpClient.requestResponseAsFlow(
    crossinline requestBuilder: HttpRequestBuilder.() -> Unit
): Flow<NetworkRequestResult<HttpResponse>> = requestAsFlow(requestBuilder) { it }

// 辅助函数：直接映射到数据类
inline fun <reified T> HttpClient.requestDataAsFlow(
    crossinline requestBuilder: HttpRequestBuilder.() -> Unit
): Flow<NetworkRequestResult<T>> = requestAsFlow(requestBuilder) { it.body<T>() }