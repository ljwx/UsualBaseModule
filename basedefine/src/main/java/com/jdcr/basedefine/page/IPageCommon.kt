package com.jdcr.basedefine.page

import android.view.View
import androidx.activity.result.ActivityResult

interface IPageCommon {

    fun getRootView(): View

    fun startActivitySimple(
        clazz: Class<*>,
        from: Int? = null,
        resultCallback: ((result: ActivityResult) -> Unit)? = null
    )

}