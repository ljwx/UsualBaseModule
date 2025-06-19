package com.jdcr.network.okhttp

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitManager {
    private var customRetrofit: Retrofit? = null
    
    private val defaultRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.example.com/") // 这里需要替换为实际的 baseUrl
            .client(OkHttpClientManager.getInstance())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun setCustomRetrofit(retrofit: Retrofit) {
        customRetrofit = retrofit
    }

    fun getInstance(): Retrofit = customRetrofit ?: defaultRetrofit

    // 提供一个便捷方法来创建 API 接口实例
    inline fun <reified T> createApi(): T {
        return getInstance().create(T::class.java)
    }
} 