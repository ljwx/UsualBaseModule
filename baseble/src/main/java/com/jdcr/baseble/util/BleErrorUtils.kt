package com.jdcr.baseble.util

object BleErrorUtils {
    private val errorCodes = mapOf(
        0 to "成功",
        8 to "连接超时(距离远/设备没电)",
        13 to "数据非法(格式或长度错误)",
        19 to "设备端断开(被对方踢掉)",
        22 to "手机端断开(主动断开/系统异常)",
        34 to "链路层响应超时(底层信号差)",
        62 to "握手失败(建立连接瞬间崩溃)",
        129 to "内部错误(常见于发现服务失败)",
        133 to "通用故障(句柄满/缓存冲突/133大坑)",
        257 to "系统操作失败"
    )

    fun getPrintableStatus(status: Int): String {
        return "Status(0x${Integer.toHexString(status)}): ${errorCodes[status] ?: "UNKNOWN_ERROR"}"
    }
}