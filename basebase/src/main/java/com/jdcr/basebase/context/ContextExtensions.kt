package com.jdcr.basebase.context

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View

inline fun Context?.getActivity(): Activity? {
    var ctx = this
    while (ctx != null) {
        if (ctx is Activity) {
            return ctx
        }
        ctx = if (ctx is ContextWrapper) {
            val baseContext = ctx.baseContext
            if (baseContext == ctx) null else baseContext
        } else {
            null
        }
    }
    return null
}

inline fun Context?.getRootView(): View? {
    val activity = getActivity()
    return if (activity is Activity) {
        activity.window?.decorView?.rootView
    } else {
        null
    }
}