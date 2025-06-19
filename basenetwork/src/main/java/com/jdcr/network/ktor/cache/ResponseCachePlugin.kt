package com.jdcr.network.ktor.cache

import com.jdcr.network.ktor.ktorJson
import io.ktor.client.*
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.GMTDate
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

// 日志器
private val CacheLogger = LoggerFactory.getLogger("ResponseCachePlugin")

// 定义一个 AttributeKey 用于标记请求是否需要缓存，或是否可以跳过缓存
val CacheControlKey = AttributeKey<CachePolicy>("NetworkCachePolicy")

// 配置类
class ResponseCacheConfig {
    var defaultCachePolicy: CachePolicy = CachePolicy.CacheFirst(5.minutes)
}

// 使用新的 Ktor 2.x API 创建客户端插件
val ResponseCachePlugin = createClientPlugin("ResponseCachePlugin", ::ResponseCacheConfig) {
    val defaultCachePolicy = pluginConfig.defaultCachePolicy
    val cacheMutex = Mutex()

    // 缓存操作函数
    suspend fun putCache(
        key: String,
        response: HttpResponse,
        bodyBytes: ByteArray,
        maxAge: Duration
    ) = cacheMutex.withLock {
        if (CacheFileManager.getRootCacheDir() == null) {
            CacheLogger.warn("缓存目录未初始化，无法写入缓存。")
            return@withLock
        }
        val file = CacheFileManager.getCacheFile(key)
        val serializableResponse = CachedResponse(
            response.status,
            response.headers,
            bodyBytes,
            System.currentTimeMillis(),
            maxAge.inWholeMilliseconds
        ).toSerializableCachedResponse()

        try {
            withContext(Dispatchers.IO) {
                file.writeText(
                    ktorJson.encodeToString(
                        SerializableCachedResponse.serializer(),
                        serializableResponse
                    )
                )
            }
            CacheLogger.info("缓存已写入文件: ${file.name}, 有效期: $maxAge")
        } catch (e: Exception) {
            CacheLogger.error("写入缓存文件失败: ${file.name}, 错误: ${e.message}", e)
        }
    }

    suspend fun getCache(key: String): CachedResponse? = cacheMutex.withLock {
        if (CacheFileManager.getRootCacheDir() == null) {
            CacheLogger.warn("缓存目录未初始化，无法读取缓存。")
            return@withLock null
        }
        val file = CacheFileManager.getCacheFile(key)
        if (!file.exists()) {
            CacheLogger.debug("缓存文件不存在: ${file.name}")
            return@withLock null
        }

        try {
            val jsonString = withContext(Dispatchers.IO) {
                file.readText()
            }
            val serializableResponse =
                ktorJson.decodeFromString<SerializableCachedResponse>(jsonString)
            val cached = serializableResponse.toCachedResponse()

            if (cached.isValid(System.currentTimeMillis())) {
                CacheLogger.info("缓存命中且有效: ${file.name}")
                return@withLock cached
            } else {
                CacheLogger.info("缓存命中但已过期: ${file.name}")
                // 直接在这里删除过期文件
                try {
                    file.delete()
                    CacheLogger.info("缓存文件已清除: ${file.name}")
                } catch (e: Exception) {
                    CacheLogger.error("清除过期缓存文件失败: ${file.name}, 错误: ${e.message}", e)
                }
                return@withLock null
            }
        } catch (e: Exception) {
            CacheLogger.error("读取或解析缓存文件失败: ${file.name}, 错误: ${e.message}", e)
            // 直接在这里删除损坏的文件
            try {
                file.delete()
                CacheLogger.info("损坏的缓存文件已清除: ${file.name}")
            } catch (deleteE: Exception) {
                CacheLogger.error("清除损坏缓存文件失败: ${file.name}, 错误: ${deleteE.message}", deleteE)
            }
            return@withLock null
        }
    }

    suspend fun invalidateCache(key: String) = cacheMutex.withLock {
        if (CacheFileManager.getRootCacheDir() == null) {
            CacheLogger.warn("缓存目录未初始化，无法清除缓存。")
            return@withLock
        }
        val file = CacheFileManager.getCacheFile(key)
        if (file.exists()) {
            file.delete()
            CacheLogger.info("缓存文件已清除: ${file.name}")
        }
    }

    fun getCacheKey(method: String, url: String): String {
        return "${method}:${url}"
    }

    // 使用新的 API 处理请求
    onRequest { request, _ ->
        val cacheKey = getCacheKey(request.method.value, request.url.toString())
        val policy = request.attributes.getOrNull(CacheControlKey) ?: defaultCachePolicy

        when (policy) {
            is CachePolicy.CacheFirst -> {
                val cached = getCache(cacheKey)
                if (cached != null) {
                    CacheLogger.info("使用缓存响应: $cacheKey")
                    // 在 Ktor 2.x 新 API 中，我们无法直接返回缓存响应
                    // 这里只是记录日志，实际的缓存响应需要在响应阶段处理
                }
            }
            is CachePolicy.ForceCache -> {
                val cached = getCache(cacheKey)
                if (cached == null) {
                    throw CacheMissException("强制缓存模式下，请求无有效缓存: $cacheKey")
                }
            }
            else -> {
                // NoCache 或 CacheThenNetwork，继续正常请求
            }
        }
    }

    // 使用新的 API 处理响应
    onResponse { response ->
        val request = response.request
        val cacheKey = getCacheKey(request.method.value, request.url.toString())
        val policy = request.attributes.getOrNull(CacheControlKey) ?: defaultCachePolicy

        if (response.status.isSuccess()) {
            when (policy) {
                is CachePolicy.CacheFirst, is CachePolicy.CacheThenNetwork, is CachePolicy.ForceCache -> {
                    policy.maxAge?.let { maxAge ->
                        try {
                            val bodyBytes = response.readBytes()
                            putCache(cacheKey, response, bodyBytes, maxAge)
                        } catch (e: Exception) {
                            CacheLogger.error("缓存响应失败: ${e.message}", e)
                        }
                    }
                }
                is CachePolicy.NoCache -> {
                    // NoCache 模式不缓存响应
                }
            }
        }
    }
}

// 如何在请求中使用缓存策略
fun HttpRequestBuilder.cachePolicy(policy: CachePolicy) {
    attributes.put(CacheControlKey, policy)
}