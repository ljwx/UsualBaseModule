package com.jdcr.basekmplog

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

object AppLogger {
    fun initialize(isDebug: Boolean) {
        // 设置全局最小日志级别
        Logger.setMinSeverity(if (isDebug) Severity.Verbose else Severity.Info)

        // 在 commonMain 中通常不直接设置 logWriters
        // LogWriters 应该在平台特定的代码中添加，因为它们依赖于平台特定的实现（如 LogcatWriter, NSLogWriter）
        // 如果您有跨平台的自定义 LogWriter，可以在这里添加：
        // Logger.addLogWriter(YourCommonLogWriter())
    }
}