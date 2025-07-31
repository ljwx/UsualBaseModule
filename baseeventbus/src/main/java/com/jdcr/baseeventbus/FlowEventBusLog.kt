package com.jdcr.baseeventbus

import android.util.Log

@PublishedApi
internal object FlowEventBusLog {

    private val TAG = "floweventbus"

    fun d(content: String) {
        Log.d(TAG, content)
    }

    fun e(content: String, e: Throwable? = null) {
        Log.e(TAG, content + (if (e == null) "" else "," + e.message))
    }

}