package com.jdcr.network.ktor.result

import com.jdcr.network.ktor.exception.NetworkException

sealed class NetworkRequestResult<out T> {

    data class Success<out T>(val data: T) : NetworkRequestResult<T>()
    data class Failure(val exception: NetworkException) : NetworkRequestResult<Nothing>()
    data class Exception(val cause: Throwable) : NetworkRequestResult<Nothing>()

}