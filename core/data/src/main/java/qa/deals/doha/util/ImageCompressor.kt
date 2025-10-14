package qa.deals.doha.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Modern 2025 image compressor
 * - Memory efficient
 * - Adaptive quality
 * - Target size: ~500KB max
 */
object ImageCompressor {

    /**
     * Compress image from Uri to File
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1280,
        maxSizeBytes: Int = 500 * 1024 // 500KB
    ): File = withContext(Dispatchers.IO) {
        // 1. Decode with inSampleSize to save memory
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        // Calculate inSampleSize
        val width = options.outWidth
        val height = options.outHeight
        val largest = max(width, height).toFloat()

        val calculatedSampleSize = if (largest > maxDimension) {
            (largest / maxDimension).roundToInt().coerceAtLeast(1)
        } else {
            1
        }

        // 2. Decode actual bitmap with sampling
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculatedSampleSize  // ✅ Use the calculated value
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode image")

        // 3. Scale if still too large
        val scaledBitmap = if (max(bitmap.width, bitmap.height) > maxDimension) {
            val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).roundToInt()
            val newHeight = (bitmap.height * scale).roundToInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                bitmap.recycle()
            }
        } else {
            bitmap
        }

        // 4. Compress with adaptive quality
        var quality = 85
        var compressedBytes: ByteArray

        do {
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            compressedBytes = stream.toByteArray()
            quality -= 5
        } while (compressedBytes.size > maxSizeBytes && quality >= 60)

        scaledBitmap.recycle()

        // 5. Save to cache file
        val outputFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        outputFile.writeBytes(compressedBytes)
        outputFile
    }
}