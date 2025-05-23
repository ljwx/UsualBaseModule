package com.jdcr.basedefine.display.page.multplestate

import android.view.View

interface IPageMultipleState {

    fun enableMultiState(enable: Boolean = true)

    fun initMultiState()

    fun setMultiStateViewClickListener(id: Int, listener: View.OnClickListener)

    fun showMultiStateLoading()

    fun showMultiStateContent()

    fun showMultiStateError()

    fun showMultiStateEmpty()

    fun showMultiStateNoNetwork()

    fun showMultiStateNotLogin()

    fun showMultiStateExtension()

    fun isMultiStateFirstGetData(): Boolean

    fun showMultiStateLoadingWhenFirstGetData()

}