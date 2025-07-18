package com.jdcr.basecamera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.camera.view.PreviewView

class CameraManager(val toolType: String?) {

    private var cameraHelper: CameraHelper? = null
    private var container: ViewGroup? = null
    private var showView: CameraPreviewView? = null
    private var closeListener: View.OnClickListener? = null

    private fun createShowView(context: Context, option: JSICameraStartControl): CameraPreviewView {
        val showView = CameraPreviewView(context, option, closeListener)
        showView.setOnLongClickListener {
            showView.findViewById<View>(R.id.camera_rotation)?.visibility = View.VISIBLE
            true
        }
        showView.findViewById<View>(R.id.camera_rotation).setOnClickListener {
            var currentRotation = cameraHelper?.getCurrentRotation()
            currentRotation = when (currentRotation) {
                Surface.ROTATION_0 -> Surface.ROTATION_90
                Surface.ROTATION_90 -> Surface.ROTATION_180
                Surface.ROTATION_180 -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }
            cameraHelper?.changeRotation(currentRotation)
        }
        return showView
    }


    private fun addShowView(
        container: FrameLayout,
        option: JSICameraStartControl
    ): PreviewView? {
        val exist = container.findViewById<View>(R.id.toolsdk_camera_preview_layout) != null
        if (exist) {
            CameraLog.d("摄像头布局已存在,不需要再添加")
            return showView?.getPreviewView()
        }
        try {
            showView = createShowView(container.context, option)
            showView?.setCloseListener(closeListener)
            val layoutParams = option.getFrameLayoutParams(container.context)
            container.addView(showView, layoutParams)
            return showView?.getPreviewView()
        } catch (e: Exception) {
            e.printStackTrace()
            CameraLog.d("添加摄像头布局异常:$e")
            return null
        }
    }

    fun start(
        context: Context?,
        container: View?,
        option: JSICameraStartControl,
        callback: (success: Boolean, code: Int, message: String?) -> Unit
    ) {
        if (context !is ComponentActivity) {
            callback(false, CMTStatus.cameraStartFail, "activity不对:$context")
            return
        }
        if (container !is FrameLayout) {
            callback(false, CMTStatus.cameraStartFail, "父容器类型不对:$container")
            return
        }
        container.post {
            val previewView = addShowView(container, option)
            if (previewView == null) {
                callback(false, CMTStatus.cameraStartFail, "添加摄像头布局异常")
            } else {
                this.container = container
                cameraHelper = cameraHelper ?: CameraHelper(context, context, previewView) {
                    val result = cameraHelper?.start()
                    ExtLog.dCamera("启动摄像头结果:$result")
                    if (result == true) {
                        callback(true, CMTStatus.success, null)
                    } else {
                        callback(false, CMTStatus.cameraStartFail, "摄像头启动失败")
                    }
                }
            }
        }
    }

    fun startOnActivity(
        context: ComponentActivity,
        previewView: PreviewView,
        callback: (success: Boolean, code: Int, message: String?) -> Unit
    ) {
        cameraHelper = cameraHelper ?: CameraHelper(context, context, previewView) {
            val result = cameraHelper?.start()
            CameraLog.d("启动摄像头结果:$result")
            if (result == true) {
                callback(true, CMTStatus.success, null)
            } else {
                callback(false, CMTStatus.cameraStartFail, "摄像头启动失败")
            }
        }
    }

    fun switch(callback: (success: Boolean, code: Int, message: String?) -> Unit) {
        if (cameraHelper?.switch() == true) {
            callback(false, CMTStatus.success, null)
        } else {
            callback(false, CMTStatus.cameraSwitchFail, "切换摄像头失败")
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    fun capture(callback: (success: Boolean, code: Int, message: String?, base64: String?) -> Unit) {
        cameraHelper?.capture { success, message, imageProxy ->
            val image = imageProxy?.image
            if (success && image != null) {
                val base64 =
                    CTCameraUtils.processImage(
                        imageProxy,
                        cameraHelper?.getCurrentLensFacing() ?: -1
                    )
                callback(true, CMTStatus.success, null, base64)
                imageProxy.close()
            } else {
                callback(false, CMTStatus.cameraCaptureFail, message, null)
            }
        }
    }

    fun captureBackFile(
        context: Context,
        callback: (success: Boolean, message: String?, uri: Uri?) -> Unit
    ) {
        cameraHelper?.captureFile(context) { success, message, uri ->
            if (success && uri != null) {
                callback(true, null, uri)
            } else {
                callback(false, message, null)
            }
        }
    }

    fun close(): Boolean {
        cameraHelper?.close()
        if (showView != null) {
            container?.removeView(showView)
            CameraLog.d("移除摄像头布局")
        }
        cameraHelper?.destroy()
        cameraHelper = null
        return true
    }

    fun setCloseClickListener(listener: View.OnClickListener) {
        this.closeListener = listener
    }

    fun destroy() {
        close()
        closeListener = null
        container = null
    }

}