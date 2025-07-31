package com.jdcr.baseeventbus

data class FlowEventBusConfig(val autoClean: AutoClean = AutoClean()) {

    companion object {
        val DEFAULT = FlowEventBusConfig()
    }

    data class AutoClean(val enable: Boolean = false, val debounce: Long = 10_000L)

}