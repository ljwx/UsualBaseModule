package com.jdcr.basedefine.activity

import androidx.appcompat.widget.Toolbar

interface IBaseActivityToolbar {

    fun initToolbar(title: CharSequence? = null)

    fun getToolbar(): Toolbar?

    fun setToolbarTitle(title: CharSequence)

}