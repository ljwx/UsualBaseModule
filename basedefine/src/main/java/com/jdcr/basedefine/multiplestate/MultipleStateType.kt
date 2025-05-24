package com.jdcr.basedefine.multiplestate

import androidx.annotation.StringDef

object MultipleStateType {

    const val CONTENT = "CONTENT"
    const val LOADING = "LOADING"
    const val EMPTY = "EMPTY"
    const val ERROR = "ERROR"
    const val OFFLINE = "OFFLINE"
    const val EXTEND = "EXTEND"

    @StringDef(
        CONTENT,
        LOADING,
        EMPTY,
        ERROR,
        OFFLINE,
        EXTEND,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class MultipleStateType

}