package com.jdcr.baseeventbus.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

interface IFlowEventBus {

    fun post(key: String, delay: Long? = null)

    fun postSticky(key: String, delay: Long? = null)

    fun subscribe(
        key: String,
        scope: CoroutineScope,
        onReceived: suspend () -> Unit
    ): Job

    fun subscribeSticky(
        key: String,
        scope: CoroutineScope,
        onReceived: suspend () -> Unit
    ): Job

}