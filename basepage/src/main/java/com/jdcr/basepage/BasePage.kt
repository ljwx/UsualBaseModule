package com.jdcr.basepage

import android.os.Bundle
import androidx.annotation.LayoutRes
import com.jdcr.basedefine.display.page.IBasePage

abstract class BasePage(@LayoutRes private val layoutResID: Int) : IBasePage {

    override fun getLayoutRes(): Int {
        return layoutResID
    }

    override fun onCrate(savedInstanceState: Bundle?) {

    }

    override fun onStart() {

    }

    override fun onResume() {

    }

    override fun onPause() {

    }

    override fun onStop() {

    }

    override fun onDestroy() {

    }


}