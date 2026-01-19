package com.jdcr.basekmplog

import co.touchlab.kermit.Logger
import co.touchlab.kermit.NSLogWriter

object LoggerIOS {

    fun initLogger(isDebug: Boolean) {
        AppLogger.initialize(isDebug)
        Logger.addLogWriter(NSLogWriter())
    }

}