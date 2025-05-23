package com.jdcr.basedefine.display.page.refresh

interface IPageRefresh {

    fun initPullRefreshView()

    fun enablePullRefreshView(enable: Boolean = true)

    fun onPullRefresh()

    fun onPullRefreshFinished()

}