package com.jdcr.basedefine.display.page

import android.os.Bundle
import android.view.View
import com.jdcr.basedefine.display.page.loading.IPageLoading

interface IBasePageCommon : IPageLoading {

    fun getLayoutRes(): Int

    fun onCrate(savedInstanceState: Bundle?)

    fun onViewCreated(view: View)

    fun onStart()

    fun onResume()

    fun onPause()

    fun onStop()

    fun onDestroy()

}