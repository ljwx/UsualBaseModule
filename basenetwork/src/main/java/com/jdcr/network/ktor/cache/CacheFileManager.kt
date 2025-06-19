package com.jdcr.network.ktor.cache

import android.content.Context
import com.jdcr.basekmplog.BaseKmpLog
import java.io.File

object CacheFileManager {
    // 这是一个全局变量，需要在应用启动时初始化
    // 在 Android 应用中，通常在 Application 类的 onCreate() 中完成
    private var cacheDir: File? = null

    fun init(context: Context, directoryName: String = "ktor_cache_data") {
        // 使用 context.cacheDir 获取应用缓存目录
        // 或者 context.filesDir 获取应用内部存储目录
        cacheDir = File(context.cacheDir, directoryName).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        BaseKmpLog.iNetwork("缓存目录已设置: ${cacheDir?.absolutePath}")
    }

    fun getCacheFile(key: String): File {
        // 检查 cacheDir 是否已初始化
        val currentCacheDir = cacheDir ?: throw IllegalStateException("缓存目录没有初始化")

        // 使用缓存key生成文件名，对 key 进行简单的哈希处理或编码以避免非法字符和过长的文件名
        val safeKey = key.hashCode().toString() // 使用哈希值作为文件名
        // 或者更安全的 Base64 编码文件名
        // val safeKey = Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP)
        return File(currentCacheDir, "$safeKey.json")
    }

    fun getRootCacheDir(): File? = cacheDir

}