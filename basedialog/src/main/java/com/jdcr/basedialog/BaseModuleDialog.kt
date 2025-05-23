package com.jdcr.basedialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.core.graphics.drawable.toDrawable
import com.jdcr.basedefine.display.dialog.IBaseDialogBuilder

open class BaseModuleDialog(
    context: Context,
    theme: Int,
    private val builder: Builder
) : Dialog(context, theme) {

    init {
        initDialogStyle()
        initDialogView()
    }

    open fun initDialogStyle() {
        clearStatusBarAndBackground()
        builder.dimAmount?.let { setBackgroundTransparency(it) }
        builder.layoutMatchParent?.let { setDialogLayoutSize(it) }
        builder.bottomMode?.let { enableBottomMode(true) }
        builder.animation?.let { setDialogAnimation(it) }
    }

    fun clearStatusBarAndBackground() {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
    }

    private fun setBackgroundTransparency(amount: Float) {
        if (amount > 0) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )
            window?.setDimAmount(amount)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0f)
        }
    }

    private fun setDialogLayoutSize(matchParent: Boolean) {
        if (matchParent) {
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            window?.setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun enableBottomMode(isFillHorizontal: Boolean) {
        window?.setGravity(Gravity.BOTTOM or if (isFillHorizontal) Gravity.FILL_HORIZONTAL else Gravity.CENTER_HORIZONTAL)
    }

    private fun initDialogView() {
        window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        setDialogView()
        setCloseCustom(builder.closeIcon)
        setTitleCustom(builder.title)
        setMessageCustom(builder.message)
        setPositiveButton(builder.positiveText, builder.positiveListener)
        setNegativeButton(builder.negativeText, builder.negativeListener)
    }


    private fun setDialogView() {
        if (builder.rootLayout != null) {
            setContentView(builder.rootLayout!!)
        } else if (builder.rootView != null) {
            setContentView(builder.rootView!!)
        }
    }

    private fun setCloseCustom(drawable: Drawable?) {
        findViewById<View>(com.jdcr.basedefine.R.id.base_module_dialog_close)?.let {
            it.visibility = if (drawable == null) View.GONE else View.VISIBLE
            if (it is ImageView && drawable != null) {
                it.setImageDrawable(drawable)
                it.setOnClickListener {
                    dismiss()
                }
            }
        }
    }

    private fun setTitleCustom(title: CharSequence?) {
        findViewById<View>(com.jdcr.basedefine.R.id.base_module_dialog_title)?.let {
            it.visibility = if (title == null) View.GONE else View.VISIBLE
            if (it is TextView && title != null) {
                it.text = title
            }
        }
    }

    private fun setMessageCustom(message: CharSequence?) {
        findViewById<View>(com.jdcr.basedefine.R.id.base_module_dialog_message)?.let {
            it.visibility = if (message == null) View.GONE else View.VISIBLE
            if (it is TextView && message != null) {
                it.text = message
            }
        }
    }

    private fun setPositiveButton(text: CharSequence?, clickListener: View.OnClickListener?) {
        findViewById<View>(com.jdcr.basedefine.R.id.base_module_dialog_positive)?.let {
            it.visibility = if (text == null) View.GONE else View.VISIBLE
            if (it is TextView && text != null) {
                it.text = text
                if (clickListener == null) {
                    it.setOnClickListener {
                        dismiss()
                    }
                } else {
                    it.setOnClickListener(clickListener)
                }
            }
        }
    }

    private fun setNegativeButton(text: CharSequence?, clickListener: View.OnClickListener?) {
        findViewById<View>(com.jdcr.basedefine.R.id.base_module_dialog_negative)?.let {
            it.visibility = if (text == null) View.GONE else View.VISIBLE
            if (it is TextView && text != null) {
                it.text = text
                if (clickListener == null) {
                    it.setOnClickListener {
                        dismiss()
                    }
                } else {
                    it.setOnClickListener(clickListener)
                }
            }
        }
    }

    private fun setDialogAnimation(animation: Int) {
        window?.setWindowAnimations(animation)
    }

    class Builder(
        private val context: Context,
        @StyleRes private val theme: Int = 0
    ) : IBaseDialogBuilder {

        var rootView: View? = null
            private set
        var rootLayout: Int? = null
            private set
        var closeIcon: Drawable? = null
            private set
        var title: String? = null
            private set
        var message: CharSequence? = null
            private set
        var positiveText: CharSequence? = null
            private set
        var positiveListener: View.OnClickListener? = null
            private set
        var negativeText: CharSequence? = null
            private set
        var negativeListener: View.OnClickListener? = null
            private set
        var dimAmount: Float? = null
            private set
        var animation: Int? = null
            private set
        var layoutMatchParent: Boolean? = null
            private set
        var bottomMode: Boolean? = null
        private var dialog: BaseModuleDialog? = null

        override fun setView(view: View): IBaseDialogBuilder {
            this.rootView = view
            return this
        }

        override fun setView(layoutResId: Int): IBaseDialogBuilder {
            this.rootLayout = layoutResId
            return this
        }

        override fun setTitle(title: CharSequence?): IBaseDialogBuilder {
            this.title = title.toString()
            return this
        }

        override fun setCloseIcon(drawable: Drawable?): IBaseDialogBuilder {
            this.closeIcon = drawable
            return this
        }

        override fun setMessage(content: CharSequence?): IBaseDialogBuilder {
            this.message = content
            return this
        }

        override fun setPositiveButton(
            text: CharSequence?,
            onClickListener: View.OnClickListener?
        ): IBaseDialogBuilder {
            this.positiveText = text
            positiveListener = onClickListener
            return this
        }

        override fun setNegativeButton(
            text: CharSequence?,
            onClickListener: View.OnClickListener?
        ): IBaseDialogBuilder {
            this.negativeText = text
            negativeListener = onClickListener
            return this
        }

        override fun setBackgroundTransparency(amount: Float): IBaseDialogBuilder {
            this.dimAmount = amount
            return this
        }

        override fun setAnimation(animRes: Int): IBaseDialogBuilder {
            this.animation = animRes
            return this
        }

        override fun setLayoutMatchParent(): IBaseDialogBuilder {
            this.layoutMatchParent = true
            return this
        }

        override fun enableBottomMode(): IBaseDialogBuilder {
            this.bottomMode = true
            return this
        }

        override fun create(): Dialog {
            val dialog = this.dialog ?: BaseModuleDialog(context, theme, this)
            return dialog
        }

    }

}