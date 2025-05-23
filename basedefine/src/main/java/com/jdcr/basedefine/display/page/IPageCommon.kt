package com.jdcr.basedefine.display.page

import android.view.View
import androidx.activity.result.ActivityResult

interface IPageCommon {

    fun getLayoutRes(): Int

    fun getRootView(): View?

}