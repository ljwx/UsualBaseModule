package com.jdcr.basedefine.display.page

import android.os.Bundle
import com.jdcr.basedefine.display.page.loading.IPageLoading
import com.jdcr.basedefine.display.page.multplestate.IPageMultipleState
import com.jdcr.basedefine.display.page.refresh.IPageRefresh
import com.jdcr.basedefine.display.page.usualstep.IPageUsualStep

interface IBasePage : IPageUsualStep, IPageLoading, IPageMultipleState, IPageRefresh, IPageCommon {

    fun onCrate(savedInstanceState: Bundle?)

    fun onStart()

    fun onResume()

    fun onPause()

    fun onStop()

    fun onDestroy()

}