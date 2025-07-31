package com.jdcr.baseeventbus

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow

data class FlowEventConfig(
    val flow: MutableSharedFlow<Any>,
    val isSticky: Boolean,
    val monitorJob: Job? = null
)