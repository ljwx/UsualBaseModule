package com.jdcr.basedefine.display.activity

import com.jdcr.basedefine.navigationbar.IBaseNavigationBar
import com.jdcr.basedefine.display.page.usualstep.IPageUsualStep

interface IBaseActivity : IBaseActivityCreateStep, IBaseActivityScreenOrientation,
    IBaseActivityStatusBar, IBaseActivityToolbar, IBaseNavigationBar, IPageUsualStep {

    fun getLayoutRes(): Int

}