package com.jdcr.network.ktor.interceptor.header

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.util.AttributeKey

class CommonHeadersPlugin(private val headerProvider: () -> Map<String, String>) {

    class Config {
        var headerProvider: (() -> Map<String, String>)? = null
    }

    companion object Plugin : HttpClientPlugin<Config, CommonHeadersPlugin> {
        override val key = AttributeKey<CommonHeadersPlugin>("CommonHeaders")

        override fun prepare(block: Config.() -> Unit) = CommonHeadersPlugin(
            headerProvider = Config().apply(block).headerProvider ?: { emptyMap() }
        )

        override fun install(plugin: CommonHeadersPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                plugin.headerProvider().forEach { (key, value) ->
                    context.headers.append(key, value)
                }
            }
        }
    }
}