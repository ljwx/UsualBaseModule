```kotlin
// 在 Activity, Fragment 或 ViewModel 中
// 注意：压缩是耗时操作，请务必在后台线程执行！
// 这里使用协程作为示例

lifecycleScope.launch(Dispatchers.IO) {
    try {
        // 示例1：压缩到最大1024x1024，质量80，输出为ByteArray
        val sourceBitmap: Bitmap = ... // 获取你的原始Bitmap
        val byteArray = ImageCompressor.with(context)
            .load(sourceBitmap)
            .maxSize(1024, 1024)
            .quality(80)
            .asByteArray()

        // 示例2：压缩文件，直到文件大小低于 200KB，并保存到新文件
        val sourceFile = File(cacheDir, "my_source_image.jpg")
        val destinationFile = File(cacheDir, "my_compressed_image.jpg")

        val resultFile = ImageCompressor.with(context)
            .load(sourceFile)
            .maxSize(1280, 1280) // 先缩放尺寸
            .maxFileSize(200)   // 再确保文件大小不超过200KB
            .format(Bitmap.CompressFormat.JPEG)
            .toFile(destinationFile)

        // 示例3：从URI压缩，保持长宽比，并获取压缩后的Bitmap在UI上显示
        val imageUri: Uri = ... // 从相册或相机获取的URI
        val compressedBitmap = ImageCompressor.with(context)
            .load(imageUri)
            .maxSize(800, 800)
            .keepAspectRatio(true) // 默认就是true
            .asBitmap()
        
        // 切回主线程更新UI
        withContext(Dispatchers.Main) {
            imageView.setImageBitmap(compressedBitmap)
        }

    } catch (e: Exception) {
        // 处理异常，例如文件未找到、解码失败等
        e.printStackTrace()
    }
}
```