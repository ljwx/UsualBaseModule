package com.jdcr.basebase.activity

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.jdcr.basebase.BaseModuleLog
import com.jdcr.basedefine.constants.BaseConstBundleKey

object BaseModuleActivityUtils {

    fun getActivity(context: Context?): Activity? {
        var ctx = context
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

    fun startActivity(
        context: Context,
        clazz: Class<*>,
        from: String? = null,
        requestCode: Int? = null
    ) {
        BaseModuleLog.dActivityStart("打开其他activity:" + clazz.simpleName)
        val intent = Intent(context, clazz)
        if (!from.isNullOrEmpty()) {
            intent.putExtra(BaseConstBundleKey.ACTIVITY_START_FROM, from)
        }
        if (requestCode == null) {
            context.startActivity(intent)
        } else {
            getActivity(context)?.startActivityForResult(intent, requestCode)
        }
    }

}