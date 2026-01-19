package com.jdcr.basekmplog

import co.touchlab.kermit.LogcatWriter
import co.touchlab.kermit.Logger

object LoggerAndroid {

    fun initLogger(isDebug: Boolean) {
        AppLogger.initialize(isDebug)
        Logger.addLogWriter(LogcatWriter())
    }

}