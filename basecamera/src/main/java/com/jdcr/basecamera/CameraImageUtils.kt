package com.jdcr.basecamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object CameraImageUtils {

    fun processImageProxyRotation(
        image: ImageProxy,
        isFront: Boolean,
        previewViewRotation: Float
    ): Bitmap? {
        // 1. ImageProxy转为Bitmap (使用健壮的转换函数)
        val sourceBitmap = imageProxyToBitmap(image) ?: return null

        // 2. 创建变换矩阵，严格按照“所见即所得”的顺序
        val matrix = Matrix().apply {
            // 步骤1: 修正物理方向，将照片“摆正”
            postRotate(image.imageInfo.rotationDegrees.toFloat())

            // 步骤2: 匹配预览镜像。前置摄像头需要水平翻转
            if (isFront) {
                postScale(-1f, 1f, sourceBitmap.width / 2f, sourceBitmap.height / 2f)
            }

            // 步骤3: 应用UI旋转，匹配预览的最终视觉方向
            postRotate(previewViewRotation)
        }

        // 3. 应用矩阵，生成最终Bitmap
        return Bitmap.createBitmap(
            sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true
        )
    }

    /**
     * [核心修正] 将ImageProxy转换为Bitmap的健壮实现。
     * 支持JPEG和YUV_420_888格式。
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return when (image.format) {
            ImageFormat.JPEG -> {
                val buffer = image.planes[0].buffer
                buffer.rewind()
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }

            ImageFormat.YUV_420_888 -> {
                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer
                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val nv21 = ByteArray(ySize + uSize + vSize)
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)

                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
                val imageBytes = out.toByteArray()
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            }

            else -> {
                CameraLog.e("不支持的图片格式: ${image.format}")
                null
            }
        }
    }

}