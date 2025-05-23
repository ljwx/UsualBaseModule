package com.jdcr.basedefine.display.navigationbar

import android.view.View

interface IBaseNavigationBar {

    fun hideNavigationBar()

    fun getNavigationBarHeight(rootView: View): Int

}