package com.jdcr.basedefine.display.page.usualstep

interface IPageUsualStep {

    fun enableUsualSteps()

    fun onUsualStep1InitData()

    fun onUsualStep2InitView()

    fun onUsualStep3ObserveData()

    fun onUsualStep4SetViewListener()

    fun onUsualStep5FetchData(refresh: Boolean = true)

}