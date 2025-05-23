package com.jdcr.basedefine.display.activity

import android.view.View

interface IBaseActivityCreateStep {

    fun onBeforeSetContentView()

    fun onSetContentView()

    fun onViewCreated(rootView: View)

}