package com.jdcr.basedefine.display.page.broadcast

interface IPageBroadcastScreenLock {

    fun setBroadcastScreen(callback: (intentAction: String) -> Unit)

}