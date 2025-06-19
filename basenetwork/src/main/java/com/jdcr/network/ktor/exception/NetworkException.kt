package com.jdcr.network.ktor.exception

sealed class NetworkException(message: String, open val requestUrl: String?) : Exception(message) {

    open class TokenRefreshException(message: String, requestUrl: String?) :
        NetworkException(message, requestUrl)

    data class RefreshFailedException(
        override val message: String,
    ) : TokenRefreshException(message, null)

    data class NoValidRefreshTokenException(
        override val message: String,
    ) : TokenRefreshException(message, null)

    data class ClientException(
        val statusCode: Int,
        val responseBody: String,
        override val requestUrl: String? = null
    ) : NetworkException(
        "客户端错误: $statusCode, 响应: $responseBody, URL: $requestUrl",
        requestUrl
    )

    data class ServerException(
        val statusCode: Int,
        val responseBody: String,
        override val requestUrl: String? = null
    ) : NetworkException(
        "服务器错误: $statusCode, 响应: $responseBody, URL: $requestUrl",
        requestUrl
    )

    data class UnknownException(
        val statusCode: Int,
        val responseBody: String,
        override val requestUrl: String? = null
    ) : NetworkException("未知错误: $statusCode, 响应: $responseBody, URL: $requestUrl", requestUrl)
}