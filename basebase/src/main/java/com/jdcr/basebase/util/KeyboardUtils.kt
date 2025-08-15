package com.jdcr.basebase.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object KeyboardUtils {

    fun registerHeightChangedListener(
        rootView: View,
        callback: (visible: Boolean, height: Int) -> Unit
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val height = imeInsets.bottom
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
            callback(visible, height)
            insets
        }
    }

    fun isFloatingMode(height: Int): Boolean {
        return height < 320
    }

}