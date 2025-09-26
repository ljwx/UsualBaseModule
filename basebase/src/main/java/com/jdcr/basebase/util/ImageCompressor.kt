package com.jdcr.basebase.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.*

/**
 * 一个健壮且灵活的图片压缩器，支持链式调用配置。
 *
 * 设计为 class 而非 object，以确保每个压缩任务的配置是独立的，从而实现线程安全。
 *
 * 功能:
 * - 支持从 File 或 Uri 加载图片。
 * - 按尺寸缩放（保持/不保持宽高比）。
 * - 按质量压缩。
 * - 按目标文件大小压缩（例如，压缩到500KB以下）。
 * - 支持输出 JPEG, PNG, WEBP 格式。
 * - 自动处理EXIF旋转信息。
 * - 注重内存管理，防止OOM和内存泄漏。
 *
 * 使用示例:
 * ```
 * // 必须在后台线程调用!
 * val compressedFile = ImageCompressor.with(context, imageUri)
 *     .resize(1080)
 *     .targetSize(500) // 压缩到500KB以下
 *     .format(ImageCompressor.Format.WEBP)
 *     .toFile(destinationFile) // 返回 File
 * ```
 */
class ImageCompressor private constructor(
    // 使用 InputStream 的提供者 (lambda)，因为流只能被消费一次
    // 我们需要能够重复打开新的流（一次用于读尺寸，一次用于解码）
    private val inputStreamProvider: () -> InputStream?
) {

    // 默认配置
    private var maxSize = 0
    private var keepAspectRatio = true
    private var quality = 90
    private var outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    private var targetSizeKb = 0

    companion object {
        /**
         * 从 File 初始化压缩器。
         */
        @JvmStatic
        fun with(imageFile: File): ImageCompressor {
            val provider = {
                try {
                    FileInputStream(imageFile)
                } catch (e: IOException) {
                    null
                }
            }
            return ImageCompressor(provider)
        }

        /**
         * 从 Uri 初始化压缩器。Context 在这里是必需的，用于访问 ContentResolver。
         */
        @JvmStatic
        fun with(context: Context, imageUri: Uri): ImageCompressor {
            val provider = {
                try {
                    context.contentResolver.openInputStream(imageUri)
                } catch (e: IOException) {
                    null
                }
            }
            return ImageCompressor(provider)
        }
    }

    // --- 配置方法 ---

    fun resize(maxSize: Int, keepAspectRatio: Boolean = true): ImageCompressor {
        this.maxSize = maxSize
        this.keepAspectRatio = keepAspectRatio
        return this
    }

    fun quality(quality: Int): ImageCompressor {
        this.quality = quality.coerceIn(0, 100)
        return this
    }

    fun format(format: Bitmap.CompressFormat): ImageCompressor {
        this.outputFormat = format
        return this
    }

    fun targetSize(kb: Int): ImageCompressor {
        this.targetSizeKb = kb
        return this
    }

    // --- 最终执行方法 ---

    /**
     * 执行压缩并返回一个 Bitmap 对象。
     * 警告：返回的 Bitmap 对象需要由调用者手动管理和回收，以避免内存泄漏。
     * @return 压缩后的 Bitmap，如果失败则返回 null。
     */
    fun get(): Bitmap? {
        var bitmap = processBitmap() ?: return null

        val stream = if (targetSizeKb > 0) {
            compressToTargetSize(bitmap)
        } else {
            compressByQuality(bitmap)
        }

        val resultBytes = stream.use { it.toByteArray() }

        // 回收中间过程的bitmap
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }

        // 将最终的字节流解码回 Bitmap
        return BitmapFactory.decodeByteArray(resultBytes, 0, resultBytes.size)
    }

    /**
     * 执行压缩并将结果保存到文件。
     * 这是推荐的方式，因为它处理了所有I/O和流，并自动管理内存。
     * @param destinationFile 目标文件。
     * @return 压缩成功则返回目标文件，否则返回 null。
     */
    fun toFile(destinationFile: File): File? {
        val bitmap = processBitmap() ?: return null

        val stream = if (targetSizeKb > 0) {
            compressToTargetSize(bitmap)
        } else {
            compressByQuality(bitmap)
        }

        return try {
            FileOutputStream(destinationFile).use { fos ->
                stream.writeTo(fos)
            }
            destinationFile
        } catch (e: IOException) {
            e.printStackTrace()
            null
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            try {
                stream.close()
            } catch (e: IOException) { /* ignore */
            }
        }
    }

    /**
     * 统一处理Bitmap的加载、旋转和缩放。
     */
    private fun processBitmap(): Bitmap? {
        // 1. 安全地加载初步缩放的Bitmap
        var bitmap = decodeSampledBitmap() ?: return null

        // 2. 修正图片方向
        val rotatedBitmap = rotateBitmapIfRequired(bitmap)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        bitmap = rotatedBitmap

        // 3. 根据设置调整尺寸
        if (maxSize > 0) {
            val resizedBitmap = if (keepAspectRatio) {
                resizeKeepingAspectRatio(bitmap)
            } else {
                resizeAndCenterCrop(bitmap)
            }
            if (resizedBitmap != bitmap) {
                bitmap.recycle()
            }
            bitmap = resizedBitmap
        }
        return bitmap
    }

    // --- 核心实现（私有方法） ---

    private fun decodeSampledBitmap(): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        inputStreamProvider()?.use { BitmapFactory.decodeStream(it, null, options) }

        val reqSize = if (maxSize > 0) maxSize else 2048 // 默认最大2048px，避免加载过大图片
        options.inSampleSize = calculateInSampleSize(options, reqSize, reqSize)

        options.inJustDecodeBounds = false
        return try {
            inputStreamProvider()?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap): Bitmap {
        val exif = inputStreamProvider()?.use { ExifInterface(it) } ?: return bitmap

        val orientation =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeKeepingAspectRatio(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= maxSize && bitmap.height <= maxSize) return bitmap
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val (finalWidth, finalHeight) = if (ratio > 1) {
            maxSize to (maxSize / ratio).toInt()
        } else {
            (maxSize * ratio).toInt() to maxSize
        }
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private fun resizeAndCenterCrop(bitmap: Bitmap): Bitmap {
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        val targetSize = maxSize

        val scale = if (sourceWidth * 1f / targetSize > sourceHeight * 1f / targetSize) {
            targetSize * 1f / sourceHeight
        } else {
            targetSize * 1f / sourceWidth
        }

        val scaledWidth = (scale * sourceWidth).toInt()
        val scaledHeight = (scale * sourceHeight).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val x = (scaledBitmap.width - targetSize) / 2
        val y = (scaledBitmap.height - targetSize) / 2
        val croppedBitmap = Bitmap.createBitmap(scaledBitmap, x, y, targetSize, targetSize)

        if (scaledBitmap != croppedBitmap) scaledBitmap.recycle()
        return croppedBitmap
    }

    private fun compressByQuality(bitmap: Bitmap): ByteArrayOutputStream {
        val stream = ByteArrayOutputStream()
        bitmap.compress(outputFormat, quality, stream)
        return stream
    }

    private fun compressToTargetSize(bitmap: Bitmap): ByteArrayOutputStream {
        var stream = ByteArrayOutputStream()
        var currentQuality = 95
        bitmap.compress(outputFormat, currentQuality, stream)
        while (stream.size() / 1024 > targetSizeKb && currentQuality > 10) {
            stream.reset()
            currentQuality -= 5
            bitmap.compress(outputFormat, currentQuality, stream)
        }
        return stream
    }
}