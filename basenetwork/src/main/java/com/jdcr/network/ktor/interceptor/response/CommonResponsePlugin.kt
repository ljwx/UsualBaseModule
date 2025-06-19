package com.jdcr.network.ktor.interceptor.response

import com.jdcr.basekmplog.BaseKmpLog
import com.jdcr.network.ktor.exception.NetworkException
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.util.*

object CommonResponsePlugin :
    HttpClientPlugin<CommonResponsePlugin.Config, CommonResponsePlugin.ResponsePlugin> {

    override val key: AttributeKey<ResponsePlugin> = AttributeKey("CommonResponsePlugin")

    class Config {
        var onClientError: ((e: NetworkException.ClientException) -> Unit)? = null
        var onServerError: ((e: NetworkException.ServerException) -> Unit)? = null
        var onUnknownError: ((e: NetworkException.UnknownException) -> Unit)? = null
    }

    class ResponsePlugin(val config: Config)

    override fun prepare(block: Config.() -> Unit): ResponsePlugin {
        val config = Config()
        config.block()
        return ResponsePlugin(config)
    }

    override fun install(plugin: ResponsePlugin, scope: HttpClient) {
        scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {

            val statusCode = context.response.status.value
            val requestUrl = context.response.call.request.url
            val responseBody = try {
                context.response.bodyAsText()
            } catch (e: Exception) {
                BaseKmpLog.iNetwork("无法读取响应体:$requestUrl, 错误: ${e.message}")
                "无法读取响应体: ${e.message}"
            }

            when (context.response.status.value) {
                in 200..299 -> proceed()
                in 400..499 -> {
                    val exception = NetworkException.ClientException(
                        statusCode,
                        responseBody
                    )
                    plugin.config.onClientError?.invoke(exception)
                    throw exception
                }

                in 500..599 -> {
                    val exception = NetworkException.ServerException(
                        statusCode,
                        responseBody
                    )
                    plugin.config.onServerError?.invoke(exception)
                    throw exception
                }

                else -> {
                    val exception = NetworkException.UnknownException(
                        statusCode,
                        responseBody
                    )
                    plugin.config.onUnknownError?.invoke(exception)
                    throw exception
                }
            }
        }
    }

}