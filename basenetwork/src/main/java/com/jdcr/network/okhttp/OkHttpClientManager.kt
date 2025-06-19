package com.jdcr.network.okhttp

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpClientManager {
    private var customClient: OkHttpClient? = null
    
    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun setCustomClient(client: OkHttpClient) {
        customClient = client
    }

    fun getInstance(): OkHttpClient = customClient ?: defaultClient
} 