```kotlin
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File // 引入 File 类
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.sync.Mutex // 引入 Mutex
import kotlinx.coroutines.sync.withLock // 引入 withLock
import org.slf4j.LoggerFactory // 引入日志器

// 日志器
private val CacheLogger = LoggerFactory.getLogger("ResponseCachePlugin")

// 定义缓存策略，与之前相同
val CacheControlKey = AttributeKey<CachePolicy>("CachePolicy")

// 插件核心逻辑
object ResponseCachePlugin : HttpClientPlugin<ResponseCachePlugin.Config, ResponseCachePlugin.PluginInstance> {

    override val key: AttributeKey<PluginInstance> = AttributeKey("ResponseCachePlugin")

    class Config {
        var defaultCachePolicy: CachePolicy = CachePolicy.CacheFirst(5.minutes)
        var cacheDirectory: File? = null // 新增：指定缓存目录
    }

    class PluginInstance(val config: Config) {
        private val cacheMutex = Mutex() // 用于同步文件操作，防止并发冲突

        init {
            config.cacheDirectory?.let { CacheDirectoryManager.init(it) }
                ?: CacheLogger.error("ResponseCachePlugin: 缓存目录未配置，缓存功能将无法工作！")
        }

        // 存储响应到缓存文件
        suspend fun put(key: String, response: HttpResponse, bodyBytes: ByteArray, maxAge: Duration) = cacheMutex.withLock {
            if (config.cacheDirectory == null) return@withLock
            val file = CacheDirectoryManager.getCacheFile(key)
            val serializableResponse = CachedResponse(
                response.status,
                response.headers,
                bodyBytes,
                System.currentTimeMillis(),
                maxAge.inWholeMilliseconds
            ).toSerializableCachedResponse()

            try {
                withContext(Dispatchers.IO) {
                    file.writeText(json.encodeToString(serializableResponse))
                }
                CacheLogger.info("缓存已写入文件: ${file.name}, 有效期: $maxAge")
            } catch (e: Exception) {
                CacheLogger.error("写入缓存文件失败: ${file.name}, 错误: ${e.message}", e)
            }
        }

        // 从缓存文件获取响应
        suspend fun get(key: String): CachedResponse? = cacheMutex.withLock {
            if (config.cacheDirectory == null) return@withLock null
            val file = CacheDirectoryManager.getCacheFile(key)
            if (!file.exists()) {
                CacheLogger.debug("缓存文件不存在: ${file.name}")
                return@withLock null
            }

            try {
                val jsonString = withContext(Dispatchers.IO) {
                    file.readText()
                }
                val serializableResponse = json.decodeFromString<SerializableCachedResponse>(jsonString)
                val cached = serializableResponse.toCachedResponse()

                if (cached.isValid(System.currentTimeMillis())) {
                    CacheLogger.info("缓存命中且有效: ${file.name}")
                    return@withLock cached
                } else {
                    CacheLogger.info("缓存命中但已过期: ${file.name}")
                    invalidate(key) // 清除过期缓存
                    return@withLock null
                }
            } catch (e: Exception) {
                CacheLogger.error("读取或解析缓存文件失败: ${file.name}, 错误: ${e.message}", e)
                invalidate(key) // 出现错误也清除，防止脏数据
                return@withLock null
            }
        }
        
        // 清除单个缓存文件
        fun invalidate(key: String) = cacheMutex.withLock {
            if (config.cacheDirectory == null) return@withLock
            val file = CacheDirectoryManager.getCacheFile(key)
            if (file.exists()) {
                file.delete()
                CacheLogger.info("缓存文件已清除: ${file.name}")
            }
        }

        // 清除所有缓存文件
        fun clearAll() = cacheMutex.withLock {
            if (config.cacheDirectory == null) return@withLock
            try {
                config.cacheDirectory?.listFiles()?.forEach { it.delete() }
                CacheLogger.info("所有缓存文件已清除")
            } catch (e: Exception) {
                CacheLogger.error("清除所有缓存文件失败: ${e.message}", e)
            }
        }

        // 生成缓存Key
        fun getCacheKey(request: HttpRequestBuilder): String {
            return "${request.method.value}:${request.url.buildString()}"
        }
    }

    override fun prepare(block: Config.() -> Unit): PluginInstance {
        val config = Config()
        config.block()
        return PluginInstance(config)
    }

    override fun install(plugin: PluginInstance, scope: HttpClient) {
        // --- 请求拦截：检查并使用缓存 ---
        scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
            val cacheKey = plugin.getCacheKey(context)
            val policy = context.attributes.getOrNull(CacheControlKey) ?: plugin.config.defaultCachePolicy

            when (policy) {
                is CachePolicy.CacheFirst -> {
                    val cached = plugin.get(cacheKey) // 从文件获取
                    if (cached != null) {
                        val fakeResponse = call.fakeHttpResponse(
                            cached.statusCode,
                            cached.headers,
                            cached.bodyBytes
                        )
                        proceedWith(fakeResponse)
                        finish()
                        return@intercept
                    }
                    proceed()
                }
                is CachePolicy.ForceCache -> {
                    val cached = plugin.get(cacheKey) // 从文件获取
                    if (cached != null) {
                        val fakeResponse = call.fakeHttpResponse(
                            cached.statusCode,
                            cached.headers,
                            cached.bodyBytes
                        )
                        proceedWith(fakeResponse)
                        finish()
                        return@intercept
                    } else {
                        throw CacheMissException("强制缓存模式下，请求无有效缓存: $cacheKey")
                    }
                }
                else -> proceed()
            }
        }

        // --- 响应拦截：存储响应到缓存 ---
        scope.responsePipeline.intercept(HttpResponsePipeline.Receive) { (info, response) ->
            val cacheKey = plugin.getCacheKey(context.request.origin)
            val policy = context.request.attributes.getOrNull(CacheControlKey) ?: plugin.config.defaultCachePolicy

            if (response.status.isSuccess()) { // 只缓存 2xx 响应
                when (policy) {
                    is CachePolicy.CacheFirst, is CachePolicy.CacheThenNetwork -> {
                        val bodyBytes = withContext(Dispatchers.IO) {
                            response.readBytes()
                        }
                        // 存储到文件缓存
                        plugin.put(cacheKey, response, bodyBytes, policy.maxAge)
                        
                        // 重新创建一个带有相同内容的响应，以便后续管道处理
                        val newResponse = call.fakeHttpResponse(
                            response.status,
                            response.headers,
                            bodyBytes
                        )
                        proceedWith(HttpResponseContainer(info.typeInfo, newResponse))
                        return@intercept
                    }
                    else -> {
                        // NoCache 或 ForceCache 模式不缓存响应
                    }
                }
            }
            proceed()
        }
    }
}

// 辅助函数：创建假的 HttpResponse，保持不变
private suspend fun HttpClientCall.fakeHttpResponse(
    status: HttpStatusCode,
    headers: Headers,
    bodyBytes: ByteArray
): HttpResponse {
    return object : HttpResponse() {
        override val call: HttpClientCall = this@fakeHttpResponse
        override val status: HttpStatusCode = status
        override val version: HttpVersion = HttpVersion.HTTP_1_1
        override val requestTime: GMTDate = GMTDate()
        override val responseTime: GMTDate = GMTDate()
        override val headers: Headers = headers
        override val bodyAsChannel: ByteReadChannel = ByteReadChannel(bodyBytes)
        override fun toString(): String = "FakeHttpResponse[${request.url}]"
    }
}

// 如何在请求中使用缓存策略，保持不变
fun HttpRequestBuilder.cachePolicy(policy: CachePolicy) {
    attributes.put(CacheControlKey, policy)
}
```