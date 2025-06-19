package com.jdcr.network.ktor.interceptor.tokenrefresh

import com.jdcr.basekmplog.BaseKmpLog
import com.jdcr.network.ktor.exception.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.util.AttributeKey
import io.ktor.util.reflect.typeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class TokenRefreshPlugin(private val config: Config) {

    class Config {
        // 判断请求是否需要认证，默认所有请求都需要
        var isRequestRequiresAuth: (url: Url, method: HttpMethod) -> Boolean = { _, _ -> true }

        // 获取当前访问令牌，由外部提供最新值
        var currentAccessToken: () -> String? = { null }

        // 获取刷新令牌
        var currentRefreshToken: () -> String? = { null }

        // 刷新令牌的suspend函数，返回新的访问令牌和刷新令牌
        var refreshTokens: suspend (refreshToken: String) -> Pair<String, String>? = { null }

        // 令牌刷新成功后的回调，通知外部更新令牌
        var onTokensRefreshed: (accessToken: String, refreshToken: String) -> Unit = { _, _ -> }

        // 令牌刷新失败的回调
        var onRefreshFailed: (cause: Throwable) -> Unit = { }

    }

    companion object : HttpClientPlugin<Config, TokenRefreshPlugin> {
        override val key = AttributeKey<TokenRefreshPlugin>("TokenRefreshPlugin")

        private val AuthRequiredKey = AttributeKey<Boolean>("AuthRequired")

        private val IsRetryRequestKey = AttributeKey<Boolean>("IsRetryRequest")

        override fun prepare(block: Config.() -> Unit) = TokenRefreshPlugin(Config().apply(block))

        override fun install(plugin: TokenRefreshPlugin, scope: HttpClient) {

            // 互斥锁，确保令牌刷新操作的原子性
            val refreshMutex = Mutex()
            // 原子布尔值，表示是否正在进行令牌刷新
            val isRefreshing = AtomicBoolean(false)

            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                val requiresAuth = plugin.config.isRequestRequiresAuth(
                    context.url.build(),
                    context.method
                )
                context.attributes.put(AuthRequiredKey, requiresAuth)

            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                val response = context.response
                val originalRequest = context.request

                // 如果响应是 401 Unauthorized 且请求需要认证且不是重试请求
                if (response.status == HttpStatusCode.Unauthorized &&
                    (originalRequest.attributes.getOrNull(AuthRequiredKey) == true) &&
                    originalRequest.attributes.getOrNull(IsRetryRequestKey) != true
                ) {
                    BaseKmpLog.iNetwork("需要刷新token:" + context.request.url.encodedPath)

                    val refreshToken = plugin.config.currentRefreshToken()

                    if (refreshToken == null) {
                        BaseKmpLog.iNetwork("当前token也为空,无法刷新token")
                        plugin.config.onRefreshFailed(NetworkException.NoValidRefreshTokenException("没有有效的token"))
                        proceed() // 继续处理 401 响应，或抛出异常
                        return@intercept
                    }

                    // 使用 Mutex 确保只有一个线程进行令牌刷新
                    val newResponse = refreshMutex.withLock {
                        if (!isRefreshing.get()) { // 第一次进入，开始刷新
                            isRefreshing.set(true)
                            try {
                                BaseKmpLog.iNetwork("开始执行刷新token")
                                val newTokens = plugin.config.refreshTokens(refreshToken)
                                if (newTokens != null) {
                                    val (newAccessToken, newRefreshToken) = newTokens
                                    plugin.config.onTokensRefreshed(newAccessToken, newRefreshToken)
                                    BaseKmpLog.iNetwork("新token刷新成功")
                                    // 令牌刷新成功，重试原始请求
                                    retryRequest(scope, originalRequest, newAccessToken)
                                } else {
                                    // 令牌刷新失败
                                    val e = NetworkException.RefreshFailedException("token刷新失败")
                                    plugin.config.onRefreshFailed(e)
                                    BaseKmpLog.iNetwork(e.message)
                                    throw e
                                }
                            } catch (e: Exception) {
                                if (e !is NetworkException.TokenRefreshException) {
                                    BaseKmpLog.iNetwork("刷新token出现异常: ${e.message}", e)
                                }
                                plugin.config.onRefreshFailed(e)
                                throw e
                            } finally {
                                isRefreshing.set(false)
                            }
                        } else {
                            BaseKmpLog.iNetwork("token正在刷新中:" + context.request.url.encodedPath)
                            while (isRefreshing.get()) {
                                delay(100) // 短暂延迟，避免忙等
                            }
                            val currentAccessToken = plugin.config.currentAccessToken()
                            if (currentAccessToken != null) {
                                BaseKmpLog.iNetwork("用新token重试原始请求")
                                retryRequest(scope, originalRequest, currentAccessToken)
                            } else {
                                val e = NetworkException.RefreshFailedException("token完成刷新,但没有拿到新token")
                                plugin.config.onRefreshFailed(e)
                                BaseKmpLog.iNetwork(e.message)
                                throw e
                            }
                        }
                    }
                    BaseKmpLog.iNetwork("用新的响应替换旧响应")
                    proceedWith(
                        HttpResponseContainer(
                            typeInfo<HttpResponse>(),
                            newResponse
                        )
                    )
                    return@intercept
                }

                proceed()
            }
        }

        /**
         * 重试请求的辅助函数,注意,如果body是一个流,无法自动重试,ktor的机制
         * @param scope HttpClient 实例
         * @param originalRequest 原始的 HttpRequest
         * @param newAccessToken 新的访问令牌
         * @return 重试后的 HttpResponse
         */
        private suspend fun retryRequest(
            scope: HttpClient,
            originalRequest: HttpRequest,
            newAccessToken: String
        ): HttpResponse {
            BaseKmpLog.iNetwork("TokenRefreshPlugin: Retrying request ${originalRequest.url.encodedPath} with new token.")
            val builder = HttpRequestBuilder().apply {
                // 修复 Type mismatch 报错：使用 takeFrom 来复制 URL
                url.takeFrom(originalRequest.url)
                method = originalRequest.method

                // 重要的改变：不显式复制 body。
                // Ktor 客户端通常会处理好非流式请求体的重用。
                // 如果你的请求体是流式的（例如文件上传），那么自动重试通常需要更复杂的设计，
                // 或者需要你重新提供一个输入流/数据源。
                // 对于大部分 REST API 的 JSON/Form Url Encoded 请求体，Ktor 会自动处理。

                // 复制所有原始请求头，除了 Authorization
                originalRequest.headers.forEach { key, values ->
                    if (key != HttpHeaders.Authorization) {
                        headers.appendAll(key, values)
                    }
                }
                // 添加新的认证头
                headers.append(HttpHeaders.Authorization, "Bearer $newAccessToken")

                // 标记为重试请求，避免再次进入 401 处理逻辑
                attributes.put(IsRetryRequestKey, true)
            }
            return scope.request(builder)
        }
    }
}