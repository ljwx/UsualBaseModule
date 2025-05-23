package com.jdcr.basedefine.display.page.infochange

import com.jdcr.basedefine.globalinfo.IBaseAppConfig
import com.jdcr.basedefine.globalinfo.IBaseUserConfig
import com.jdcr.basedefine.globalinfo.IBaseUserInfo


interface IBaseInfoChange {

    fun onUserInfoChange(userInfo: IBaseUserInfo)

    fun onUserConfigChange(userConfig: IBaseUserConfig)

    fun onAppConfigChange(appConfig: IBaseAppConfig)

}