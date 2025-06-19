package com.jdcr.network.ktor.cache

import android.content.Context
import com.jdcr.network.ktor.KtorClientManager
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours

/**
 * 缓存功能使用示例
 * 
 * 这个文件展示了如何正确使用文件缓存网络请求功能
 */
object CacheUsageExample {

    /**
     * 第一步：在 Application 类中初始化缓存
     * 
     * 在您的 Application.onCreate() 方法中调用：
     */
    fun initializeCache(context: Context) {
        // 初始化 KtorClientManager 并设置缓存目录
        KtorClientManager.init(
            context = context, // 必须传入 Context 来初始化缓存目录
            // 其他可选参数...
        )
    }

    /**
     * 第二步：在网络请求中使用缓存策略
     */
    suspend fun exampleUsage() {
        val client = KtorClientManager.getInstance()

        // 示例 1: 优先使用缓存，缓存有效则不发网络请求
        val response1: HttpResponse = client.get("https://api.example.com/data") {
            cachePolicy(CachePolicy.CacheFirst(30.minutes))
        }

        // 示例 2: 强制使用缓存，没有缓存则抛出异常
        try {
            val response2: HttpResponse = client.get("https://api.example.com/data") {
                cachePolicy(CachePolicy.ForceCache(1.hours))
            }
        } catch (e: CacheMissException) {
            println("没有可用的缓存数据")
        }

        // 示例 3: 先用缓存显示，同时发网络请求更新
        val response3: HttpResponse = client.get("https://api.example.com/data") {
            cachePolicy(CachePolicy.CacheThenNetwork(15.minutes))
        }

        // 示例 4: 不使用缓存，直接请求网络
        val response4: HttpResponse = client.get("https://api.example.com/data") {
            cachePolicy(CachePolicy.NoCache)
        }

        // 示例 5: 使用默认缓存策略（如果没有指定，将使用插件的默认策略）
        val response5: HttpResponse = client.get("https://api.example.com/data")
    }

    /**
     * 第三步：缓存管理
     */
    fun cacheManagement() {
        // 清除所有缓存
        KtorClientManager.clearCache()
        
        // 注意：目前还没有提供单个缓存项的清除功能
        // 如果需要，可以扩展 CacheManager 类
    }

    /**
     * 完整的 Application 类示例
     */
    /*
    class MyApplication : Application() {
        override fun onCreate() {
            super.onCreate()
            
            // 初始化网络缓存
            KtorClientManager.init(
                context = this,
                // 可选：设置其他配置
                commonHeaders = {
                    mapOf("User-Agent" to "MyApp/1.0")
                }
            )
        }
    }
    */

    /**
     * 在 Activity 或 Fragment 中使用的示例
     */
    /*
    class MainActivity : AppCompatActivity() {
        private suspend fun loadData() {
            try {
                val client = KtorClientManager.getInstance()
                val response = client.get("https://api.example.com/users") {
                    // 缓存 10 分钟
                    cachePolicy(CachePolicy.CacheFirst(10.minutes))
                }
                val data = response.bodyAsText()
                // 处理数据...
            } catch (e: Exception) {
                // 处理错误...
            }
        }
    }
    */
}

/**
 * 缓存策略说明：
 * 
 * 1. CacheFirst(maxAge): 
 *    - 优先使用缓存
 *    - 如果缓存存在且未过期，直接返回缓存
 *    - 如果缓存不存在或已过期，发起网络请求并缓存结果
 * 
 * 2. ForceCache(maxAge):
 *    - 强制使用缓存
 *    - 如果缓存不存在，抛出 CacheMissException
 *    - 适用于离线模式
 * 
 * 3. CacheThenNetwork(maxAge):
 *    - 先返回缓存（如果有）
 *    - 同时发起网络请求更新缓存
 *    - 适用于需要快速显示但也要保持数据新鲜的场景
 * 
 * 4. NoCache:
 *    - 不使用缓存
 *    - 直接发起网络请求
 *    - 适用于实时性要求很高的数据
 */ 