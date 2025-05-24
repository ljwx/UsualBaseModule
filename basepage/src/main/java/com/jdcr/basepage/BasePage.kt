package com.jdcr.basepage

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import com.jdcr.basebase.BaseModuleLog
import com.jdcr.basedefine.display.page.IBasePageCommon

abstract class BasePage(
    @LayoutRes private val layoutResID: Int,
    private val pageName: String? = null
) : IBasePageCommon {

    override fun getLayoutRes(): Int {
        return layoutResID
    }

    override fun onCrate(savedInstanceState: Bundle?) {
        BaseModuleLog.dActivity("生命周期onCreate", pageName)
    }

    override fun onViewCreated(view: View) {
        BaseModuleLog.dActivity("onViewCreated", pageName)
    }

    override fun onStart() {
        BaseModuleLog.dActivity("生命周期onStart", pageName)
    }

    override fun onResume() {
        BaseModuleLog.dActivity("生命周期onResume", pageName)
    }

    override fun onPause() {
        BaseModuleLog.dActivity("生命周期onPause", pageName)
    }

    override fun onStop() {
        BaseModuleLog.dActivity("生命周期onStop", pageName)
    }

    override fun onDestroy() {
        BaseModuleLog.dActivity("生命周期onDestroy", pageName)
    }


}