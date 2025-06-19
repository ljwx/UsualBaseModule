package com.jdcr.network.ktor.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 简单的缓存管理器
 * 提供基本的缓存清理功能
 */
class CacheManager {
    private val mutex = Mutex()
    
    /**
     * 清除所有缓存
     */
    suspend fun clear() = mutex.withLock {
        try {
            // 使用 CacheFileManager 清除所有缓存文件
            if (CacheFileManager.getRootCacheDir() != null) {
                CacheFileManager.getRootCacheDir()?.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // 记录错误但不抛出异常
            e.printStackTrace()
        }
    }
    
    /**
     * 获取缓存目录大小（字节）
     */
    fun getCacheSize(): Long {
        return try {
            CacheFileManager.getRootCacheDir()?.let { dir ->
                dir.walkTopDown()
                    .filter { it.isFile }
                    .map { it.length() }
                    .sum()
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 获取缓存文件数量
     */
    fun getCacheFileCount(): Int {
        return try {
            CacheFileManager.getRootCacheDir()?.listFiles()?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
} 