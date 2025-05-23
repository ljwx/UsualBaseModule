package com.jdcr.basedefine.display.dialog

import android.app.Dialog

interface IBaseDialog {

    fun createCommonDialog(
        title: String? = null,
        content: String? = null,
        positiveText: String? = null,
    ): Dialog?

}