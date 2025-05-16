package com.jdcr.basedefine.activity

import com.jdcr.basedefine.navigationbar.IBaseNavigationBar
import com.jdcr.basedefine.usualstep.IPageUsualStep

interface IBaseActivity : IBaseActivityScreenOrientation,
    IBaseActivityStatusBar, IBaseActivityToolbar, IBaseNavigationBar, IPageUsualStep {

    fun getLayoutRes(): Int

}