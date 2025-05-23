package com.jdcr.basedefine.display.page.loading

interface IPageLoading {

    fun showLoadingDialog(show: Boolean, cancelable: Boolean = true)

    fun showLoadingPopup(show: Boolean, cancelable: Boolean = true)

    fun dismissLoadingDialog()

    fun dismissLoadingPopup()

}