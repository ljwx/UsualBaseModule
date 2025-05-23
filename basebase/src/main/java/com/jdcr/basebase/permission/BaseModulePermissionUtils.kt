package com.jdcr.basebase.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IntDef
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jdcr.basebase.BaseModuleLog

object BaseModulePermissionUtils {

    const val GRANTED = 0x0001
    const val NOT_REQUEST = 0x0010
    const val SHOW_RATIONAL = 0x0100
    const val DENIED = 0x1000

    @IntDef(GRANTED, NOT_REQUEST, SHOW_RATIONAL, DENIED)
    @Retention(AnnotationRetention.SOURCE)
    annotation class PermissionResultType

    private var activityResultLauncher: ActivityResultLauncher<Array<String>>? = null
    private var isPermissionRequestInProgress = false
    private val permissionsListenerMap =
        LinkedHashMap<Array<String>?, ActivityResultCallback<Map<String, Boolean>>>()
    private var currentPermissions: Array<String>? = null

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isPermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        permissions.forEach {
            if (!isPermissionGranted(context, it)) {
                return false
            }
        }
        return true
    }

    private fun isPermissionShouldShowRational(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    private fun isPermissionShouldShowRational(
        activity: Activity,
        permissions: Array<String>
    ): Boolean {
        permissions.forEach {
            if (isPermissionShouldShowRational(activity, it)) {
                return true
            }
        }
        return false
    }

    private fun isPermissionNotRequest(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_DENIED
    }

    private fun isPermissionNotRequest(context: Context, permissions: Array<String>): Boolean {
        permissions.forEach {
            if (isPermissionNotRequest(context, it)) {
                return true
            }
        }
        return false
    }

    private fun isPermissionDenied(activity: Activity, permission: String): Boolean {
        return !isPermissionShouldShowRational(
            activity,
            permission
        ) && ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_DENIED
    }

    private fun isPermissionDenied(activity: Activity, permissions: Array<String>): Boolean {
        permissions.forEach {
            if (isPermissionDenied(activity, it)) {
                return true
            }
        }
        return false
    }

    /**
     * 获取权限状态
     */
    fun getPermissionState(activity: Activity, permissions: Array<String>): Int {
        if (isPermissionsGranted(activity, permissions)) {
            BaseModuleLog.dPermission("权限都通过:" + permissions.contentToString())
            return GRANTED
        }
        if (isPermissionShouldShowRational(activity, permissions)) {
            BaseModuleLog.dPermission("权限未通过,但没选不再提示:" + permissions.contentToString())
            return SHOW_RATIONAL
        }
        if (isPermissionNotRequest(activity, permissions)) {
            BaseModuleLog.dPermission("没申请过权限:" + permissions.contentToString())
            return NOT_REQUEST
        }
        if (isPermissionDenied(activity, permissions)) {
            BaseModuleLog.dPermission("已拒绝权限:" + permissions.contentToString())
            return DENIED
        }
        return DENIED
    }

    fun openAppDetailsSettings(context: Context) {
        BaseModuleLog.dPermission("跳转系统设置界面")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.setData(uri)
        context.startActivity(intent)
    }

    private fun addPermissionsListener(
        permissions: Array<String>,
        callback: ActivityResultCallback<Map<String, Boolean>>
    ) {
        BaseModuleLog.dPermission("添加权限请求回调:" + permissions.contentToString())
        permissionsListenerMap[permissions] = callback
    }

    private fun getPermissionCallback(permissions: Array<String>?): ActivityResultCallback<Map<String, Boolean>>? {
        return permissionsListenerMap[permissions].also { BaseModuleLog.dPermission("获取权限回调:" + permissions.contentToString() + "," + it + ",剩余:" + permissionsListenerMap.size) }
    }

    fun removePermissionRequest(permissions: Array<String>?) {
        val iterator = permissionsListenerMap.iterator()
        if (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.contentEquals(permissions)) {
                iterator.remove()
                BaseModuleLog.dPermission("移除权限请求回调:" + permissions.contentToString() + ",剩余:" + permissionsListenerMap.size)
            }
        }
    }

    private fun getActivityResultLauncher(
        activity: AppCompatActivity,
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            BaseModuleLog.dPermission(
                "权限请求结果:" + result.keys.toTypedArray().contentToString()
                        + "," + result.values.toTypedArray().contentToString()
            )
            isPermissionRequestInProgress = false
            getPermissionCallback(currentPermissions)?.onActivityResult(result)
            removePermissionRequest(currentPermissions)
            justExecute()
        }
    }

    private fun justExecute() {
        if (isPermissionRequestInProgress) {
            BaseModuleLog.dPermission("已有权限正在请求，不执行")
            return
        }
        val iterator = permissionsListenerMap.iterator()
        if (iterator.hasNext()) {
            val next = iterator.next()
            BaseModuleLog.dPermission("launch权限请求:" + next.key)
            activityResultLauncher?.launch(next.key)
            isPermissionRequestInProgress = true
            currentPermissions = next.key
        }
    }

    private fun getResultLauncher(): ActivityResultLauncher<Array<String>>? {
        return activityResultLauncher
    }

    fun setResultLauncher(activity: AppCompatActivity) {
        if (activityResultLauncher == null) {
            activityResultLauncher = getActivityResultLauncher(activity)
        }
    }

    fun requestPermissions(
        resultLauncher: ActivityResultLauncher<Array<String>>?,
        permissions: Array<String>,
        callback: ActivityResultCallback<Map<String, Boolean>>
    ) {
        this.activityResultLauncher = resultLauncher
        if (activityResultLauncher == null) {
            BaseModuleLog.dPermission("权限启动器是空的")
            return
        }
        addPermissionsListener(permissions, callback)
        justExecute()
    }

    fun judgeAndRequestPermissions(
        activity: Activity,
        permissions: Array<String>,
        callback: ActivityResultCallback<Map<String, Boolean>>
    ) {
        val state = getPermissionState(activity, permissions)

        fun directResult(permissions: Array<String>, result: Boolean): Map<String, Boolean> {
            val map = HashMap<String, Boolean>()
            permissions.forEach {
                map[it] = result
            }
            return map
        }

        when (state) {
            GRANTED -> {
                callback.onActivityResult(directResult(permissions, true))
            }

            DENIED -> {
                callback.onActivityResult(directResult(permissions, false))
            }

            SHOW_RATIONAL -> {
                requestPermissions(getResultLauncher(), permissions, callback)
            }

            NOT_REQUEST -> {
                requestPermissions(getResultLauncher(), permissions, callback)
            }

        }
    }

    fun onDestroy() {
        this.activityResultLauncher = null
        this.permissionsListenerMap.clear()
        this.currentPermissions = null
        this.isPermissionRequestInProgress = false
    }

}