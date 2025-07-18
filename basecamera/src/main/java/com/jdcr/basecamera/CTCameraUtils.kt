package com.jdcr.basecamera

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import com.codemao.toolssdk.utils.ExtLog
import com.codemao.toolssdk.utils.ImageCompressUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale


object CTCameraUtils {

    fun hasCamera(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun getContentValues(fileName: String? = null): ContentValues {
        val name = fileName ?: SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss-SSS",
            Locale.US
        ).format(System.currentTimeMillis())
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Images")
            }
        }
    }

    fun getOutputOptions(context: Context): ImageCapture.OutputFileOptions {
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            getContentValues()
        ).build()
    }

    private fun getCacheDir(context: Context): String {
        val path = context.cacheDir.path + "/kn_add_material"
        if (!File(path).exists()) {
            File(path).mkdirs()
        }
        return path
    }

    fun getCacheOptions(context: Context): ImageCapture.OutputFileOptions {
        return ImageCapture.OutputFileOptions.Builder(
            File(
                getCacheDir(context),
                "material_" + System.currentTimeMillis() + ".jpg"
            )
        ).build()
    }

    fun imageProxy2Bitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val planes = imageProxy.planes
            if (planes.size < 3) {
                CameraLog.d("图片有问题(${planes.size})")
                return null
            }

            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize).apply {
                yBuffer.get(this, 0, ySize)
                vBuffer.get(this, ySize, vSize)
                uBuffer.get(this, ySize + vSize, uSize)
            }

            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            ByteArrayOutputStream().use { out ->
                yuvImage.compressToJpeg(
                    Rect(0, 0, imageProxy.width, imageProxy.height),
                    100,
                    out
                )
                BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            }
        } catch (e: Exception) {
            CameraLog.e("拍照结果转换异常: ${e.message}")
            null
        }
    }

    /**
     * 处理图像
     */
    @SuppressLint("UnsafeOptInUsageError")
    fun processImage(imageProxy: ImageProxy, lensFacing: Int): String? {
        try {
            val image = imageProxy.image ?: return null
            // 记录摄像头原始宽高
            val cameraWidth = image.width
            val cameraHeight = image.height
            ExtLog.dCamera("摄像头原始宽高: ${cameraWidth}x${cameraHeight}")

            val originalBitmap = try {
                processImageOrientation(imageProxy, lensFacing)
            } catch (e: Exception) {
                null
            }
            if (originalBitmap != null) {
                // 记录拍照结果宽高
                val captureWidth = originalBitmap.width
                val captureHeight = originalBitmap.height
                ExtLog.dCamera("拍照结果宽高: ${captureWidth}x${captureHeight}")

                // 处理图像方向
                val processedBitmap = originalBitmap

                // 记录处理后的宽高
                val processedWidth = processedBitmap.width
                val processedHeight = processedBitmap.height
                ExtLog.dCamera("处理后宽高: ${processedWidth}x${processedHeight}")

                // 压缩处理后的图片
                val compressResult = ImageCompressUtils.compressBitmap(
                    processedBitmap,
                    ImageCompressUtils.createQuickCompressConfig()
                )

                if (compressResult.success) {
                    // 记录压缩后的宽高
                    val compressedWidth = compressResult.bitmap?.width ?: 0
                    val compressedHeight = compressResult.bitmap?.height ?: 0
                    ExtLog.dCamera("压缩后宽高: ${compressedWidth}x${compressedHeight}")
                    ExtLog.dCamera("拍照并压缩成功，原始大小: ${compressResult.originalSize / 1024}KB，压缩后: ${compressResult.fileSize / 1024}KB")
                    return compressResult.base64!!
                } else {
                    // 压缩失败，使用处理后的原始图片
                    val base64Data = bitmapToBase64(processedBitmap)
                    ExtLog.dCamera("压缩失败，使用处理后的原始图片: ${compressResult.errorMessage}")
                    return base64Data
                }

            } else {
                // 解码失败，使用原始图片
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val base64Data = Base64.encodeToString(bytes, Base64.DEFAULT)
                val originalBase64 = "data:image/jpeg;base64,$base64Data"
                ExtLog.dCamera("图片解码失败，使用原始图片")
                return originalBase64
            }
        } catch (e: Exception) {
            ExtLog.dCamera("处理图像失败：${e.message}")
            return null
        }
    }

    /**
     * 处理图像方向
     */
    fun processImageOrientation(imageProxy: ImageProxy, lensFacing: Int): Bitmap {
        // 1. 将 ImageProxy 的 buffer 转换为 ByteArray
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // 2. 将 ByteArray 解码为原始的 Bitmap
        val sourceBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // 3. 创建一个变换矩阵
        val matrix = Matrix()

        // 4. 根据需要对前置摄像头进行水平翻转（镜像）
        // 这是为了让照片和预览的镜像效果保持一致
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f, sourceBitmap.width / 2f, sourceBitmap.height / 2f)
        }

        // 5. 应用修正旋转角度
        // 这是为了修正传感器方向和设备方向的差异
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

        // 6. 应用矩阵变换，生成最终的、方向正确的 Bitmap
        return Bitmap.createBitmap(
            sourceBitmap,
            0,
            0,
            sourceBitmap.width,
            sourceBitmap.height,
            matrix,
            true
        )
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)

        val byteArray = outputStream.toByteArray()
        outputStream.close()

        val base64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
        return "data:image/jpeg;base64,$base64"
    }

}