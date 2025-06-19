package com.jdcr.network.ktor.interceptor.response

import com.jdcr.network.ktor.exception.NetworkException

interface CommonResponseProvider {

    fun onClientError(e: NetworkException.ClientException)

    fun onServerError(e: NetworkException.ServerException)

    fun onUnknownError(e: NetworkException.UnknownException)

}