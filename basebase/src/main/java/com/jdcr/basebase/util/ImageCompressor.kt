package com.jdcr.basebase.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 一个功能强大且灵活的图片压缩工具类。
 *
 * 特点:
 * - 支持链式调用，自由组合压缩选项。
 * - 支持多种输入源 (Bitmap, File, Uri, InputStream, ByteArray)。
 * - 支持多种输出形式 (Bitmap, ByteArray, File)。
 * - 可配置输出格式 (JPEG, PNG, WEBP)。
 * - 可配置图片质量。
 * - 可配置最大宽度和高度，并保持长宽比。
 * - 可配置目标文件大小上限，工具会自动调整质量以满足要求。
 * - 内存友好，通过采样率(inSampleSize)避免加载大图导致OOM。
 *
 * @author Gemini
 *
 * 使用方法:
 * ```
 * // 必须在后台线程调用
 * val resultFile = ImageCompressor.with(context)
 * .load(sourceFile)          // 加载源文件
 * .maxSize(1280, 1280)       // 设置最大宽高
 * .maxFileSize(200)          // 设置最大文件大小 (KB)
 * .quality(85)               // 初始质量
 * .format(Bitmap.CompressFormat.JPEG) // 输出格式
 * .toFile(destinationFile)   // 输出为文件
 * ```
 */
class ImageCompressor private constructor(private val context: Context) {

    private lateinit var builder: Builder

    companion object {
        /**
         * 开始构建压缩任务。
         * @param context Context对象，用于访问ContentResolver等。
         */
        fun with(context: Context): ImageCompressor {
            return ImageCompressor(context)
        }
    }

    /**
     * 加载 Bitmap 作为压缩源。
     */
    fun load(bitmap: Bitmap): Builder {
        builder = Builder(Input.BitmapInput(bitmap))
        return builder
    }

    /**
     * 加载 File 作为压缩源。
     */
    fun load(file: File): Builder {
        builder = Builder(Input.FileInput(file))
        return builder
    }

    /**
     * 加载 Uri 作为压缩源 (例如: from ContentResolver)。
     */
    fun load(uri: Uri): Builder {
        builder = Builder(Input.UriInput(uri, context))
        return builder
    }

    /**
     * 加载 ByteArray 作为压缩源。
     */
    fun load(byteArray: ByteArray): Builder {
        builder = Builder(Input.ByteArrayInput(byteArray))
        return builder
    }

    /**
     * 加载 InputStream 作为压缩源。
     * **注意**: InputStream将被完全消耗且不会被关闭。
     */
    fun load(inputStream: InputStream): Builder {
        builder = Builder(Input.StreamInput(inputStream))
        return builder
    }

    inner class Builder internal constructor(private val input: Input) {
        private var maxWidth: Int = 1080
        private var maxHeight: Int = 1920
        private var quality: Int = 90
        private var maxFileSizeKb: Int? = null
        private var keepAspectRatio: Boolean = true
        private var outputFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG

        /**
         * 设置压缩后的图片最大宽度和高度。
         * @param maxWidth 最大宽度 (px)。
         * @param maxHeight 最大高度 (px)。
         */
        fun maxSize(maxWidth: Int, maxHeight: Int): Builder = apply {
            this.maxWidth = maxWidth
            this.maxHeight = maxHeight
        }

        /**
         * 设置是否保持原始图片的长宽比。默认为 true。
         * 如果设为 false，图片将被拉伸至 `maxSize` 指定的尺寸。
         */
        fun keepAspectRatio(keep: Boolean): Builder = apply {
            this.keepAspectRatio = keep
        }

        /**
         * 设置压缩质量。范围 0-100。
         * 对于JPEG和WEBP格式有效。如果设置了 `maxFileSize`，此值为初始尝试质量。
         */
        fun quality(quality: Int): Builder = apply {
            this.quality = quality.coerceIn(0, 100)
        }

        /**
         * 设置压缩后文件的最大大小（单位 KB）。
         * 工具会通过循环降低质量来尝试达到此目标。
         * @param sizeInKb 文件大小上限 (KB)。
         */
        fun maxFileSize(sizeInKb: Int): Builder = apply {
            this.maxFileSizeKb = sizeInKb
        }

        /**
         * 设置输出图片的格式。
         * @param format [Bitmap.CompressFormat.JPEG], [Bitmap.CompressFormat.PNG], [Bitmap.CompressFormat.WEBP]。
         */
        fun format(format: Bitmap.CompressFormat): Builder = apply {
            this.outputFormat = format
        }

        /**
         * 执行压缩并返回一个 Bitmap。
         * **警告**: 此方法不执行基于文件大小的压缩，因为它返回的是内存中的Bitmap。
         * 如需控制文件大小，请使用 `asByteArray()` 或 `toFile()`。
         */
        fun asBitmap(): Bitmap {
            val decodedBitmap = decodeSampledBitmap()
            return scaleBitmap(decodedBitmap)
        }

        /**
         * 执行压缩并返回一个 ByteArray。
         */
        fun asByteArray(): ByteArray {
            val scaledBitmap = asBitmap()
            return compressToByteArray(scaledBitmap)
        }

        /**
         * 执行压缩并将结果保存到指定文件。
         * @param destinationFile 目标文件。
         * @return 写入成功的 [File] 对象。
         */
        fun toFile(destinationFile: File): File {
            val byteArray = asByteArray()
            FileOutputStream(destinationFile).use { it.write(byteArray) }
            return destinationFile
        }

        // --- 内部核心方法 ---

        /**
         * 步骤1：从输入源解码Bitmap，同时使用inSampleSize进行第一次缩放，避免OOM。
         */
        private fun decodeSampledBitmap(): Bitmap {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            input.decodeBounds(options)

            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            return input.decode(options)
                ?: throw IllegalStateException("Failed to decode bitmap from the input source.")
        }

        /**
         * 步骤2：对解码后的Bitmap进行第二次精确缩放。
         */
        private fun scaleBitmap(bitmap: Bitmap): Bitmap {
            val (srcWidth, srcHeight) = bitmap.width to bitmap.height
            if (srcWidth <= maxWidth && srcHeight <= maxHeight) {
                return bitmap // 尺寸已达标，无需缩放
            }

            val scale: Float = if (keepAspectRatio) {
                min(maxWidth.toFloat() / srcWidth, maxHeight.toFloat() / srcHeight)
            } else {
                // 不保持比例时，不应小于0，这里只是为了代码统一
                max(maxWidth.toFloat() / srcWidth, maxHeight.toFloat() / srcHeight)
            }

            val targetWidth: Int
            val targetHeight: Int

            if (keepAspectRatio) {
                targetWidth = (srcWidth * scale).toInt()
                targetHeight = (srcHeight * scale).toInt()
            } else {
                targetWidth = maxWidth
                targetHeight = maxHeight
            }

            if (targetWidth == srcWidth && targetHeight == srcHeight) {
                return bitmap
            }

            val matrix = Matrix().apply { setScale(scale, scale) }
            val scaled = Bitmap.createBitmap(bitmap, 0, 0, srcWidth, srcHeight, matrix, true)
            if (scaled != bitmap) {
                bitmap.recycle()
            }
            return scaled
        }

        /**
         * 步骤3：将最终的Bitmap压缩为ByteArray，如果需要，会循环降低质量以满足文件大小要求。
         */
        private fun compressToByteArray(bitmap: Bitmap): ByteArray {
            val outputStream = ByteArrayOutputStream()
            var currentQuality = this.quality

            // 如果是PNG格式，则为无损压缩，质量和文件大小控制无意义
            if (outputStream.use { bitmap.compress(outputFormat, currentQuality, it) }) {
                if (outputFormat == Bitmap.CompressFormat.PNG || maxFileSizeKb == null) {
                    val result = outputStream.toByteArray()
                    bitmap.recycle()
                    return result
                }
            }

            // 循环压缩以满足文件大小要求 (仅对JPEG/WEBP有效)
            while (outputStream.size() / 1024 > maxFileSizeKb!! && currentQuality > 0) {
                outputStream.reset()
                currentQuality -= 5 // 每次降低5点质量
                bitmap.compress(outputFormat, currentQuality.coerceAtLeast(0), outputStream)
            }

            val result = outputStream.toByteArray()
            bitmap.recycle() // 释放内存
            outputStream.close()
            return result
        }

        /**
         * 计算最佳采样率 inSampleSize。
         */
        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height: Int, width: Int) = options.outHeight to options.outWidth
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
    }

    // --- 输入源的抽象，用于统一处理不同类型的输入 ---
    sealed class Input {
        abstract fun decodeBounds(options: BitmapFactory.Options)
        abstract fun decode(options: BitmapFactory.Options): Bitmap?

        class BitmapInput(private val bitmap: Bitmap) : Input() {
            override fun decodeBounds(options: BitmapFactory.Options) {
                options.outWidth = bitmap.width
                options.outHeight = bitmap.height
            }
            override fun decode(options: BitmapFactory.Options): Bitmap? = bitmap
        }

        class FileInput(private val file: File) : Input() {
            override fun decodeBounds(options: BitmapFactory.Options) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            }
            override fun decode(options: BitmapFactory.Options): Bitmap? =
                BitmapFactory.decodeFile(file.absolutePath, options)
        }

        class UriInput(private val uri: Uri, private val context: Context) : Input() {
            private fun openInputStream(): InputStream? = context.contentResolver.openInputStream(uri)

            override fun decodeBounds(options: BitmapFactory.Options) {
                openInputStream()?.use { BitmapFactory.decodeStream(it, null, options) }
            }
            override fun decode(options: BitmapFactory.Options): Bitmap? =
                openInputStream()?.use { BitmapFactory.decodeStream(it, null, options) }
        }

        class ByteArrayInput(private val byteArray: ByteArray): Input() {
            override fun decodeBounds(options: BitmapFactory.Options) {
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            }
            override fun decode(options: BitmapFactory.Options): Bitmap? =
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
        }

        class StreamInput(private val inputStream: InputStream): Input() {
            // InputStream无法重复读取，所以需要先读入byte array
            private val byteArray = inputStream.readBytes()

            override fun decodeBounds(options: BitmapFactory.Options) {
                ByteArrayInputStream(byteArray).use { BitmapFactory.decodeStream(it, null, options) }
            }
            override fun decode(options: BitmapFactory.Options): Bitmap? =
                ByteArrayInputStream(byteArray).use { BitmapFactory.decodeStream(it, null, options) }
        }
    }
}