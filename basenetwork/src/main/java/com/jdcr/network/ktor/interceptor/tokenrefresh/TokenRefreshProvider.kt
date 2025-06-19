package com.jdcr.network.ktor.interceptor.tokenrefresh

interface TokenRefreshProvider {

    /**
     * 获取当前访问令牌 (Access Token)
     */
    fun getAccessToken(): String?

    /**
     * 获取当前刷新令牌 (Refresh Token)
     */
    fun getRefreshToken(): String?

    /**
     * 异步刷新令牌。
     * @param refreshToken 当前的刷新令牌
     * @return 返回一个新的 Pair(newAccessToken, newRefreshToken)，如果刷新失败则返回 null
     */
    suspend fun refreshTokens(refreshToken: String): Pair<String, String>?

    /**
     * 当令牌成功刷新后，库会调用此方法，以便 App 可以持久化新的令牌
     * @param accessToken 新的访问令牌
     * @param refreshToken 新的刷新令牌
     */
    fun onTokensRefreshed(accessToken: String, refreshToken: String)

    /**
     * 当令牌刷新失败时，库会调用此方法
     * @param cause 失败原因
     */
    fun onRefreshFailed(cause: Throwable)

    /**
     * (可选) 判断某个请求是否需要携带认证信息
     * @param url 请求的 URL
     * @param method 请求的 HTTP 方法
     * @return 如果需要认证则返回 true，否则返回 false
     */
    fun isRequestRequiresAuth(url: io.ktor.http.Url, method: io.ktor.http.HttpMethod): Boolean {
        // 默认所有请求都需要认证，App可以覆盖此逻辑，例如排除登录/注册接口
        return true
    }

}