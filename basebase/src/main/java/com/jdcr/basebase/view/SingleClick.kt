package com.jdcr.basebase.view

import android.view.View

const val clickInterval = 300L

abstract class OnClickListenerSingle(private val interval: Long = clickInterval) :
    View.OnClickListener {

    private var lastClickTime: Long = 0

    override fun onClick(v: View?) {
        if (System.currentTimeMillis() - lastClickTime > interval) {
            lastClickTime = System.currentTimeMillis()
            onSingleClick(v)
        }
    }

    abstract fun onSingleClick(v: View?)

}

open class SingleClickListener(
    private val period: Long = clickInterval,
    private var block: (View.() -> Unit)?
) : View.OnClickListener {

    private var lastClickTime: Long = 0

    override fun onClick(v: View) {
        if (System.currentTimeMillis() - lastClickTime > period) {
            lastClickTime = System.currentTimeMillis()
            block?.invoke(v)
        }
    }
}

inline fun View.singleClick(
    period: Long = clickInterval,
    noinline block: View.() -> Unit
) {
    setOnClickListener(SingleClickListener(period, block))
}