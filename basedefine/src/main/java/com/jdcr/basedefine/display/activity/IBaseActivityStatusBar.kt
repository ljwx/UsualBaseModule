package com.jdcr.basedefine.display.activity

import androidx.annotation.ColorInt

interface IBaseActivityStatusBar {

    fun hideStatusBar()

    fun setStatusBarLight()

    fun setStatusBarDark()

    fun setStatusBarBackgroundColor(@ColorInt color: Int)

    fun setStatusBarFontDark(dark: Boolean)

    fun setStatusBarTransparent()

}