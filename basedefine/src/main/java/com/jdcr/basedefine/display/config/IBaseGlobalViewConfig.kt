package com.jdcr.basedefine.display.config

import android.view.View

interface IBaseGlobalViewConfig {

    fun setCommonDialogLayout(view: View)

    fun setCommonMultiStateLoading(view: View)

    fun setCommonMultiStateError(view: View)

    fun setCommonMultiStateEmpty(view: View)

    fun setCommonMultiStateNoNetWork(view: View)

    fun setCommonMultiStateNoLogin(view: View)

    fun setCommonRefreshHeader(view: View)

}