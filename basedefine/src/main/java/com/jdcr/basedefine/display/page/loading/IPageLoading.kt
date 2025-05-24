package com.jdcr.basedefine.display.page.loading

interface IPageLoading {

    fun showLoadingWindow(dialogElsePop: Boolean = true, cancelable: Boolean = true)

    fun dismissLoadingWindow()

}