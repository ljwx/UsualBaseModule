package com.jdcr.network.ktor

import com.jdcr.network.ktor.cache.CacheManager
import com.jdcr.network.ktor.interceptor.header.CommonHeadersPlugin
import com.jdcr.network.ktor.interceptor.header.DynamicHeaderProvider
import com.jdcr.network.ktor.interceptor.header.DynamicHeadersPlugin
import com.jdcr.network.ktor.interceptor.response.CommonResponsePlugin
import com.jdcr.network.ktor.interceptor.response.CommonResponseProvider
import com.jdcr.network.ktor.interceptor.tokenrefresh.TokenRefreshProvider
import com.jdcr.network.ktor.interceptor.tokenrefresh.TokenRefreshPlugin
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

internal val ktorJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

object KtorClientManager {

    private var client: HttpClient? = null
    private var tokenRefreshProvider: TokenRefreshProvider? = null
    private var commonHeaders: (() -> Map<String, String>)? = null
    private var commonResponseProvider: CommonResponseProvider? = null
    private val cacheManager = CacheManager()

    private val defaultClient: HttpClient by lazy {
        HttpClient(Android) {

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }

            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }

            install(CommonHeadersPlugin) {
                headerProvider = commonHeaders
            }

            install(DynamicHeadersPlugin) {
                headerProvider = {
                    DynamicHeaderProvider.getHeaders()
                }
            }

            install(TokenRefreshPlugin) {
                this.currentAccessToken = { tokenRefreshProvider?.getAccessToken() }
                this.currentRefreshToken = { tokenRefreshProvider?.getRefreshToken() }
                this.refreshTokens =
                    { refreshToken -> tokenRefreshProvider?.refreshTokens(refreshToken) }
                this.onTokensRefreshed = { accessToken, refreshToken ->
                    tokenRefreshProvider?.onTokensRefreshed(
                        accessToken,
                        refreshToken
                    )
                }
                this.onRefreshFailed = { cause -> tokenRefreshProvider?.onRefreshFailed(cause) }
                this.isRequestRequiresAuth = { url, method ->
                    tokenRefreshProvider?.isRequestRequiresAuth(url, method) ?: false
                }
            }

//            install(DefaultRequest) {
//                header(HttpHeaders.Authorization, "Bearer YOUR_TOKEN")
//                header("token", "")
//            }

//            install(CachePlugin(cacheManager))

            install(CommonResponsePlugin) {
                onClientError = {
                    commonResponseProvider?.onClientError(it)
                }
                onServerError = {
                    commonResponseProvider?.onServerError(it)
                }
                onUnknownError = {
                    commonResponseProvider?.onUnknownError(it)
                }
            }

            engine {
//                connectTimeout = 30000
//                socketTimeout = 30000
            }
        }
    }

    fun init(
        tokenRefreshProvider: TokenRefreshProvider? = null,
        commonHeaders: (() -> Map<String, String>)? = null,
        commonResponseProvider: CommonResponseProvider? = null,
    ) {
        this.tokenRefreshProvider = tokenRefreshProvider
        this.commonHeaders = commonHeaders
    }

    fun setCustomClient(client: HttpClient) {
        this.client = client
    }

    fun getInstance(): HttpClient = client ?: defaultClient

    fun updateDynamicHeaders(headersToUpdate: Map<String, String>) {
        DynamicHeaderProvider.updateHeaders(headersToUpdate)
    }

    fun clearDynamicHeaders() {
        DynamicHeaderProvider.clear()
    }

    fun clearCache() {
        runBlocking {
            cacheManager.clear()
        }
    }
}