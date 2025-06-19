package com.jdcr.network.ktor.interceptor.header

import java.util.concurrent.ConcurrentHashMap

object DynamicHeaderProvider {

    private val dynamicHeaders = ConcurrentHashMap<String, String>()

    fun updateHeaders(headersToUpdate: Map<String, String>) {
        dynamicHeaders.putAll(headersToUpdate)
    }

    fun removeHeader(headerName: String) {
        dynamicHeaders.remove(headerName)
    }

    fun clear() {
        dynamicHeaders.clear()
    }

    internal fun getHeaders(): Map<String, String> {
        return dynamicHeaders.toMap()
    }

}