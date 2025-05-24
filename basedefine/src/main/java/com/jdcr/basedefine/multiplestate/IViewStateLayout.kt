package com.jdcr.basedefine.multiplestate

import android.view.View
import android.view.animation.Animation

interface IViewStateLayout {

    fun addLoadingView(view: View)

    fun addContentView(view: View)

    fun addEmptyView(view: View)

    fun addErrorView(view: View)

    fun addOfflineView(view: View)

    fun addExtensionView(view: View)

    fun showLoading()

    fun showContent()

    fun showEmpty()

    fun showError()

    fun showOffline()

    fun showExtension()

    fun setInAnimation(animation: Animation)

    fun setOutAnimation(animation: Animation)

}