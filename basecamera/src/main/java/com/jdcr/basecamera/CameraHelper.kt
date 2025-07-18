package com.jdcr.basecamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.net.Uri
import android.util.Base64
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.impl.ImageOutputConfig.RotationValue
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraHelper(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val prepareListener: (() -> Unit)
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var commonStateListener: CameraStateListener? = null

    interface CameraStateListener {
        fun onCameraReady()
        fun onCameraError(code: Int, message: String)
        fun onCameraClose()
    }

    init {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                CameraLog.d("获取cameraProvider")
                prepareListener.invoke()
                startCameraInternal()
            } catch (e: Exception) {
                onError(1000, "获取cameraProvider异常:$e")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    //预览
    private fun getPreview(previewView: PreviewView): Preview {
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        val rotation = previewView.display.rotation
        CameraLog.d("preview方向:$rotation")
        preview = preview ?: Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        return preview!!
    }

    //选择摄像头
    private fun getSelector(lensFacing: Int): CameraSelector {
        return CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }

    //拍照
    private fun getCapture(): ImageCapture {
        capture = capture ?: ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        return capture!!
    }

    private fun startCamera(
        cameraProvider: ProcessCameraProvider?,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int
    ): Boolean {
        if (cameraProvider == null) {
            onError(1001, "cameraProvider为空,无法启用摄像头")
            return false
        }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                getSelector(lensFacing),
                getPreview(previewView),
                getCapture()
            )
            camera?.cameraInfo?.cameraState?.observe(lifecycleOwner) { state ->
                state.error?.let { error ->
                    // 相机错误处理
                    when (error.code) {
                        CameraState.ERROR_STREAM_CONFIG -> {
                            CameraLog.d("流配置错误")
                        }

                        CameraState.ERROR_CAMERA_IN_USE -> {
                            CameraLog.d("相机已被占用")
                        }

                        CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                            CameraLog.d("已达到最大相机使用数量")
                        }

                        CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                            CameraLog.d("其他可恢复错误")
                        }

                        CameraState.ERROR_CAMERA_DISABLED -> {
                            CameraLog.d("相机被禁用")
                        }

                        CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                            CameraLog.d("相机致命错误")
                        }

                        CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                            CameraLog.d("请勿打扰模式已启用")
                        }

                        else -> {
                            CameraLog.d("未知错误回调")
                        }
                    }
                }

                // 相机状态变化
                when (state.type) {
                    CameraState.Type.PENDING_OPEN -> CameraLog.d("相机待启动")
                    CameraState.Type.OPENING -> CameraLog.d("相机正在启动")
                    CameraState.Type.OPEN -> CameraLog.d("相机已启动，可预览")
                    CameraState.Type.CLOSING -> CameraLog.d("相机正在关闭")
                    CameraState.Type.CLOSED -> CameraLog.d("相机已关闭")
                }
            }
            commonStateListener?.onCameraReady()
            CameraLog.d("摄像头启动成功")
            return true
        } catch (e: Exception) {
            camera = null
            onError(1002, "摄像头启用失败:$e")
            return false
        }

    }

    private fun startCameraInternal(): Boolean {
        return startCamera(cameraProvider, lifecycleOwner, previewView, lensFacing)
    }

    fun start(): Boolean {
        return startCameraInternal()
    }

    fun capture(result: (success: Boolean, message: String?, imageProxy: ImageProxy?) -> Unit) {
        getCapture().takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                CameraLog.d("拍照原始结果:" + image.imageInfo + ",format:" + image.format)
                result(true, null, image)
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                result(false, exception.message, null)
            }

        })
    }

    fun captureFile(
        context: Context,
        callback: (success: Boolean, message: String?, uri: Uri?) -> Unit
    ) {
        val option = CTCameraUtils.getCacheOptions(context)
        getCapture().takePicture(
            option,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    CameraLog.d("拍照成功,结果:" + outputFileResults.savedUri.toString())
                    callback.invoke(true, null, outputFileResults.savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    CameraLog.d("拍照失败:$exception")
                    callback.invoke(false, exception.message, null)
                }

            })
    }

    fun changeRotation(@RotationValue rotation: Int) {
        preview = Preview.Builder().setTargetRotation(rotation).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        startCameraInternal()
        CameraLog.d("修改当前方向为:$rotation")
    }

    fun switch(): Boolean {
        lensFacing =
            if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        return startCameraInternal()
    }

    fun close() {
        cameraProvider?.unbindAll()
        commonStateListener?.onCameraClose()
    }

    fun getCurrentLensFacing(): Int {
        return lensFacing
    }

    fun getCurrentRotation(): Int {
        return preview?.targetRotation ?: Surface.ROTATION_0
    }

    private fun onError(code: Int, message: String) {
        CameraLog.e(message)
        commonStateListener?.onCameraError(code, message)
    }

    fun setCommonStateListener(listener: CameraStateListener) {
        this.commonStateListener = listener
    }

    fun destroy() {
        close()
        cameraProvider = null
        camera = null
        capture = null
        cameraExecutor.shutdown()
    }

}