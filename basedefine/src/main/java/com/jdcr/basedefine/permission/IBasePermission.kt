package com.jdcr.basedefine.permission

interface IBasePermission {

    fun isPermissionGranted(permission: String): Boolean

    fun isPermissionsGranted(permissions: Array<String>): Boolean

    //用户拒绝了权限请求，但未勾选“不再询问”
    //判断是否需要向用户解释权限的用途。
    fun isPermissionShouldShowRational(permission: String): Boolean

    //权限未申请过
    fun isPermissionNotRequest(permission: String): Boolean

    //权限被永久拒绝
    fun isPermissionDenied(permission: String): Boolean

    fun requestPermission(permission: String)

    fun requestPermissions(permission: Array<String>)

    fun addPermissionsListener(
        permissions: Array<String>,
        listener: (Map<String, @JvmSuppressWildcards Boolean>) -> Unit
    )

    fun onPermissionsResult(result: Map<String, @JvmSuppressWildcards Boolean>)

    fun showPermissionRationale(permission: String, listener: (positive: Boolean) -> Unit)

    fun openAppDetailsSettings()

    fun handlePermission(permission: String, callback: (granted: Boolean, denied: Boolean) -> Unit)
}