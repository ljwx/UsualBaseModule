package com.jdcr.basedefine.globalinfo

interface IBaseUserInfo {

    fun isLogin(): Boolean

    fun getUserId(): String?

    fun getUserName(): String?

    fun getUserInfoChangeType(): String

}