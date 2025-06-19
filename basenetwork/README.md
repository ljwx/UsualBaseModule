# BaseNetwork

一个基于 Ktor 的 Android 网络请求库，提供了简单易用的网络请求接口和丰富的功能特性。

## 特性

- 支持所有常用 HTTP 方法（GET、POST、PUT、DELETE、PATCH）
- 统一的请求拦截器
- 响应缓存支持
- 统一的错误处理
- 支持自定义配置
- 线程安全
- 支持 Kotlin 协程

## 依赖

```gradle
dependencies {
    // Ktor
    implementation "io.ktor:ktor-client-android:2.3.7"
    implementation "io.ktor:ktor-client-core:2.3.7"
    implementation "io.ktor:ktor-client-content-negotiation:2.3.7"
    implementation "io.ktor:ktor-serialization-gson:2.3.7"
    implementation "io.ktor:ktor-client-logging:2.3.7"
    
    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
}
```

## 使用方法

### 1. 基本使用

```kotlin
// 创建 API 服务实例
val apiService = KtorApiService()

// GET 请求
val response = apiService.get("https://api.example.com/users")

// POST 请求
val user = User("John", "Doe")
val postResponse = apiService.post("https://api.example.com/users", user)

// PUT 请求
val updatedUser = User("John", "Smith")
apiService.put("https://api.example.com/users/1", updatedUser)

// DELETE 请求
apiService.delete("https://api.example.com/users/1")

// PATCH 请求
val partialUpdate = mapOf("name" to "John Smith")
apiService.patch("https://api.example.com/users/1", partialUpdate)
```

### 2. 错误处理

```kotlin
try {
    val response = apiService.get("https://api.example.com/users")
} catch (e: NetworkException) {
    when (e) {
        is NetworkException.ClientException -> {
            // 处理客户端错误（400-499）
        }
        is NetworkException.ServerException -> {
            // 处理服务器错误（500-599）
        }
        is NetworkException.UnknownException -> {
            // 处理未知错误
        }
    }
}
```

### 3. 自定义配置

```kotlin
// 自定义 HttpClient
val customClient = HttpClient(Android) {
    install(ContentNegotiation) {
        gson()
    }
    install(Logging) {
        level = LogLevel.ALL
    }
    // 添加其他配置...
}

// 设置自定义客户端
KtorClientManager.setCustomClient(customClient)
```

### 4. 缓存管理

```kotlin
// 清除缓存
KtorClientManager.clearCache()
```

## 主要组件

### KtorClientManager

网络客户端管理器，负责创建和管理 HttpClient 实例。

### ApiService

网络请求接口，提供所有 HTTP 方法的实现。

### RequestInterceptor

请求拦截器，用于：
- 添加通用请求头
- 处理响应状态码
- 统一的错误处理

### CacheManager

缓存管理器，提供：
- 内存缓存实现
- 缓存过期时间控制
- 线程安全的缓存操作

## 注意事项

1. 默认超时设置：
   - 连接超时：30秒
   - 读取超时：30秒
   - 写入超时：30秒

2. 缓存策略：
   - 默认只缓存 GET 请求
   - 缓存时间由服务器响应头控制
   - 默认缓存时间为 1 小时

3. 错误处理：
   - 所有网络请求都会抛出 NetworkException
   - 需要在使用时进行异常捕获和处理

## 贡献

欢迎提交 Issue 和 Pull Request。

## 许可证

MIT License 