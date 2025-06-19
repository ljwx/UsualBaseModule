package com.jdcr.network.ktor.cache

import android.util.Base64
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlin.time.Duration

// 定义缓存策略
sealed class CachePolicy {
    // 基础缓存策略，包含maxAge属性
    abstract val maxAge: Duration?

    // 优先使用缓存，但即使有缓存也发网络请求更新
    data class CacheThenNetwork(override val maxAge: Duration) : CachePolicy()

    // 优先使用缓存，缓存有效则不发网络请求
    data class CacheFirst(override val maxAge: Duration) : CachePolicy()

    // 不使用缓存
    object NoCache : CachePolicy() {
        override val maxAge: Duration? = null
    }

    // 强制使用缓存，即使过期也尝试使用
    data class ForceCache(override val maxAge: Duration) : CachePolicy()
}

// 可序列化的缓存条目数据类
@Serializable
data class SerializableCachedResponse(
    val statusCodeValue: Int, // HttpStatusCode 的 value
    val headersMap: Map<String, List<String>>, // Headers 转换为 Map
    val bodyBase64: String, // 响应体字节数组转换为 Base64 字符串
    val timestamp: Long,
    val maxAgeMillis: Long
) {
    // 将序列化后的数据转换回运行时使用的 CachedResponse 对象
    fun toCachedResponse(): CachedResponse {
        return CachedResponse(
            HttpStatusCode.fromValue(statusCodeValue),
            Headers.build {
                headersMap.forEach { (key, values) -> appendAll(key, values) }
            },
            Base64.decode(bodyBase64, Base64.DEFAULT),
            timestamp,
            maxAgeMillis
        )
    }
}

// 缓存条目
data class CachedResponse(
    val statusCode: HttpStatusCode,
    val headers: Headers,
    val bodyBytes: ByteArray,
    val timestamp: Long, // 缓存时间戳
    val maxAgeMillis: Long // 缓存有效期（毫秒）
) {
    fun isValid(currentTime: Long): Boolean {
        return (currentTime - timestamp) < maxAgeMillis
    }

    // 将运行时对象转换为可序列化的对象
    fun toSerializableCachedResponse(): SerializableCachedResponse {
        return SerializableCachedResponse(
            statusCode.value,
            headers.entries().associate { it.key to it.value },
            Base64.encodeToString(bodyBytes, Base64.DEFAULT),
            timestamp,
            maxAgeMillis
        )
    }

    // 重写 equals 和 hashCode，以正确比较包含 ByteArray 的数据类
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CachedResponse

        if (statusCode != other.statusCode) return false
        if (headers != other.headers) return false
        if (!bodyBytes.contentEquals(other.bodyBytes)) return false
        if (timestamp != other.timestamp) return false
        if (maxAgeMillis != other.maxAgeMillis) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statusCode.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + bodyBytes.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + maxAgeMillis.hashCode()
        return result
    }
}

class CacheMissException(message: String) : Exception(message)
