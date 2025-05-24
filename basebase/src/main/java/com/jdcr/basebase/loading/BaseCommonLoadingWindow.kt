package com.jdcr.basebase.loading

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setPadding
import androidx.core.graphics.drawable.toDrawable
import com.jdcr.basebase.BaseModuleLog
import com.jdcr.basebase.context.getRootView
import com.jdcr.baseresource.R

open class BaseCommonLoadingWindow(
    private val context: Context,
    customView: View? = null,
    beforeCallback: ((view: View) -> Unit)? = null
) {

    companion object {

        private var loadingLayout: Int? = null
        fun setGlobalLoadingLayout(@LayoutRes layout: Int) {
            loadingLayout = layout
        }

    }

    private val loadingView by lazy {
        customView ?: if (loadingLayout != null) LayoutInflater.from(context)
            .inflate(loadingLayout!!, null) else null
    }

    private val defaultProgressbar by lazy {
        val container = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.base_resource_shape_loading_background)
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        val progressBar = ProgressBar(context).apply {
            setPadding(45)
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        }
        container.addView(progressBar)
        container
    }

    private var windowType = 0x00

    private fun getDefaultWidth(): Int {
        return (context.resources.displayMetrics.widthPixels * 0.35).toInt()
    }

    private val dialog by lazy {
        val view = loadingView ?: defaultProgressbar
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setDimAmount(0f)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        if (loadingView == null) {
            dialog.setOnShowListener {
                dialog.window?.setLayout(getDefaultWidth(), WRAP_CONTENT)
            }
        }
        beforeCallback?.invoke(view)
        enableDialog()
        dialog
    }

    private val popupWindow by lazy {
        val view = loadingView ?: defaultProgressbar
        val popup = PopupWindow(view, WRAP_CONTENT, WRAP_CONTENT)
        popup.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        beforeCallback?.invoke(view)
        enablePopupWindow()
        popup
    }

    private fun enableDialog() {
        windowType = windowType or 0x01
    }

    private fun enablePopupWindow() {
        windowType = windowType or 0x10
    }

    private fun isDialogActive(): Boolean {
        return (windowType and 0x01) != 0
    }

    private fun isPopupActive(): Boolean {
        return (windowType and 0x10) != 0
    }

    fun setDismissListener(dialogElsePop: Boolean = true, dismiss: () -> Unit) {
        if (dialogElsePop) {
            dialog.setOnDismissListener {
                BaseModuleLog.dLoading("onDialogDismiss")
                dismiss()
            }
        } else {
            popupWindow.setOnDismissListener {
                BaseModuleLog.dLoading("onPopupDismiss")
                dismiss()
            }
        }
    }

    fun isShowing(dialogElsePop: Boolean = true): Boolean {
        if (dialogElsePop && isDialogActive()) {
            return dialog.isShowing
        }
        if (!dialogElsePop && isPopupActive()) {
            return popupWindow.isShowing
        }
        return false
    }

    fun show(dialogElsePop: Boolean = true, cancelable: Boolean = true) {
        if (dialogElsePop) {
            if (!dialog.isShowing) {
                BaseModuleLog.dLoading("显示dialog")
                dialog.setCancelable(cancelable)
                dialog.show()
            }
        } else {
            context.getRootView()?.let {
                BaseModuleLog.dLoading("显示popup")
                if (!popupWindow.isShowing) {
                    popupWindow.isFocusable = cancelable
                    popupWindow.showAtLocation(it, Gravity.CENTER, 0, 0)
                }
            }
        }
    }

    fun dismiss() {
        if (isPopupActive() && popupWindow.isShowing) {
            BaseModuleLog.dLoading("popup dismiss")
            popupWindow.dismiss()
        }
        if (isDialogActive() && dialog.isShowing) {
            BaseModuleLog.dLoading("dialog dismiss")
            dialog.dismiss()
        }
    }

}