package com.jdcr.basedefine.router

import android.content.Context
import androidx.activity.result.ActivityResult

interface IBaseRouter {

    fun startActivity(
        context: Context,
        clazz: Class<*>,
        from: String? = null,
        requestCode: Int? = null
    )

    fun startActivity(
        context: Context,
        clazz: Class<*>,
        from: Int? = null,
        resultCallback: ((result: ActivityResult) -> Unit)? = null
    )

}