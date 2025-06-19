package com.jdcr.network.ktor.interceptor.header

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.util.AttributeKey

class DynamicHeadersPlugin(private val headerProvider: () -> Map<String, String>) {

    class Config {
        var headerProvider: (() -> Map<String, String>)? = null
    }

    companion object Plugin : HttpClientPlugin<Config, DynamicHeadersPlugin> {
        override val key = AttributeKey<DynamicHeadersPlugin>("DynamicHeaders")

        override fun prepare(block: Config.() -> Unit) = DynamicHeadersPlugin(
            headerProvider = Config().apply(block).headerProvider ?: { emptyMap() }
        )

        override fun install(plugin: DynamicHeadersPlugin, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                plugin.headerProvider().forEach { (key, value) ->
                    context.headers.append(key, value)
                }
            }
        }
    }
}