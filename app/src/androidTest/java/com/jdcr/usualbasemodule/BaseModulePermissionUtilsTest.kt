package com.jdcr.usualbasemodule

import android.Manifest
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jdcr.basebase.permission.BaseModulePermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseModulePermissionUtilsTest {

    private lateinit var activity: MainActivity

    @Before
    fun setResultLauncher() {
        BaseModulePermissionUtils.setResultLauncher(activity)
    }

    @Test
    fun requestSingle() {
        BaseModulePermissionUtils.judgeAndRequestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            object : ActivityResultCallback<Map<String, Boolean>> {
                override fun onActivityResult(result: Map<String, Boolean>) {
                    result.forEach {
                        when (it.key) {
                            Manifest.permission.CAMERA -> {
                                if (it.value) {
                                    Toast.makeText(
                                        activity,
                                        "相机权限通过",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                } else {
                                    Toast.makeText(
                                        activity,
                                        "相机权限未通过",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }

                            Manifest.permission.RECORD_AUDIO -> {
                                if (it.value) {
                                    Toast.makeText(
                                        activity,
                                        "录音权限通过",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        activity,
                                        "录音权限未通过",
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            }
                        }
                    }
                }

            })
    }

    @Test
    fun requestMultiple() {
        activity.lifecycleScope.launch {
            BaseModulePermissionUtils.judgeAndRequestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                object : ActivityResultCallback<Map<String, Boolean>> {
                    override fun onActivityResult(result: Map<String, Boolean>) {
                        if (result.values.all { it }) {
                            Toast.makeText(activity, "相机权限通过", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(activity, "相机权限未通过", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                })
            withContext(Dispatchers.Default) { delay(1200) }
            BaseModulePermissionUtils.judgeAndRequestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                object : ActivityResultCallback<Map<String, Boolean>> {
                    override fun onActivityResult(result: Map<String, Boolean>) {
                        if (result.values.all { it }) {
                            Toast.makeText(activity, "录音权限通过", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(activity, "录音权限未通过", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }

                })
        }
    }

}