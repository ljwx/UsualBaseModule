package com.jdcr.basebase.loading

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.graphics.drawable.toDrawable
import com.jdcr.basebase.BaseModuleLog
import com.jdcr.basebase.context.getRootView

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
        val container = FrameLayout(context)
        val progressBar = ProgressBar(context)
        progressBar.setPadding(45)
        val layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        layoutParams.gravity = Gravity.CENTER
        progressBar.layoutParams = layoutParams
        container.addView(progressBar)
        container
    }

    private var windowType = 0x00

    private val dialog by lazy {
        val view = loadingView ?: defaultProgressbar
        val dialog = AlertDialog.Builder(context).setView(view).create()
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        beforeCallback?.invoke(view)
        windowType = windowType or 0x01
        dialog
    }

    private val popupWindow by lazy {
        val view = loadingView ?: defaultProgressbar
        val popup = PopupWindow(view, WRAP_CONTENT, WRAP_CONTENT)
        popup.setBackgroundDrawable(
            ContextCompat.getColor(context, android.R.color.darker_gray).toDrawable()
        )
        beforeCallback?.invoke(view)
        windowType = windowType or 0x10
        popup
    }

    fun show(dialogElsePop: Boolean = true, cancelable: Boolean = true) {
        if (dialogElsePop) {
            if (!dialog.isShowing) {
                dialog.setCancelable(cancelable)
                dialog.show()
            }
        } else {
            context.getRootView()?.let {
                BaseModuleLog.dLoading("根布局不为空")
                if (!popupWindow.isShowing) {
                    popupWindow.isFocusable = cancelable
                    popupWindow.showAtLocation(it, Gravity.CENTER, 0, 0)
                }
            }
        }
    }

    fun dismiss() {
        if ((windowType and 0x10) != 0 && popupWindow.isShowing) {
            popupWindow.dismiss()
        }
        if ((windowType and 0x01) != 0 && dialog.isShowing) {
            dialog.dismiss()
        }
    }

}