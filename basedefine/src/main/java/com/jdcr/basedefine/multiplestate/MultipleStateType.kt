package com.jdcr.basedefine.multiplestate

import androidx.annotation.IntDef

object MultipleStateType {

    const val CONTENT = 0
    const val LOADING = 1
    const val EMPTY = 2
    const val ERROR = 3
    const val OFFLINE = 4
    const val EXTEND = 5

    @IntDef(
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