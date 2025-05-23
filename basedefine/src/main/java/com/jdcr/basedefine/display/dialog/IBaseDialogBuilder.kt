package com.jdcr.basedefine.display.dialog

import android.app.Dialog
import android.graphics.drawable.Drawable
import android.view.View
import android.view.View.OnClickListener
import androidx.annotation.AnimRes
import androidx.annotation.LayoutRes

interface IBaseDialogBuilder {

    fun setView(@LayoutRes layoutResId: Int): IBaseDialogBuilder

    fun setView(view: View): IBaseDialogBuilder

    fun setTitle(title: CharSequence?): IBaseDialogBuilder

    fun setMessage(content: CharSequence?): IBaseDialogBuilder

    fun setPositiveButton(
        text: CharSequence?,
        onClickListener: OnClickListener? = null
    ): IBaseDialogBuilder

    fun setNegativeButton(
        text: CharSequence?,
        onClickListener: OnClickListener? = null
    ): IBaseDialogBuilder

    fun setCloseIcon(drawable: Drawable?): IBaseDialogBuilder

    fun setBackgroundTransparency(amount: Float): IBaseDialogBuilder

    fun setAnimation(@AnimRes animRes: Int): IBaseDialogBuilder

    fun setLayoutMatchParent(): IBaseDialogBuilder

    fun enableBottomMode(): IBaseDialogBuilder

    fun create(): Dialog


}