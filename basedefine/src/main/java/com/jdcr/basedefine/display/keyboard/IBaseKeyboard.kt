package com.jdcr.basedefine.display.keyboard

import android.view.View

interface IBaseKeyboard {

    fun enableKeyboardHeightListener(): Boolean

    fun setKeyboardHeightListener(
        rootView: View,
        callback: ((height: Int, visible: Boolean) -> Unit)?
    )

    fun onKeyboardHeightChanged(height: Int, visible: Boolean)

}