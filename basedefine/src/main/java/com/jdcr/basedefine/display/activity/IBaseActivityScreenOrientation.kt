package com.jdcr.basedefine.display.activity

interface IBaseActivityScreenOrientation {

    fun getActivityOrientation(): Int

    fun setActivityOrientation(orientation: Int)

    fun isActivityLandscape(): Boolean

    fun setActivityLandscape()

    fun setActivityPortrait()

}