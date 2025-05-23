package com.jdcr.basedefine.display.page.binding

import androidx.viewbinding.ViewBinding

interface IPageViewBinding<VBinding : ViewBinding> {

    fun getViewBinding(): VBinding

}