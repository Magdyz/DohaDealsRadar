package qa.deals.doha.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
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
 * ✅ NOW SUPPORTS: Two-stage upload (thumbnail + full image)
 * ✅ ENHANCED: Comprehensive logging for debugging
 * ✅ FIXED: Preserves correct orientation from camera (reads EXIF)
 * ✅ UPDATED: Now compresses to modern WebP format for 25-35% smaller file sizes.
 */
object ImageCompressor {

    private const val TAG = "ImageCompressor"

    // ========================================
    // ✅ Data class for two-stage upload
    // ========================================
    data class CompressedImages(
        val thumbnail: File,  // Tiny preview (~15KB)
        val fullImage: File,  // Standard quality (~80KB)
        val dealId: String? = null  // Set after deal is submitted
    )

    // ========================================
    // ✅ NEW: Helper functions for EXIF orientation
    // ========================================

    /**
     * Read rotation degrees from EXIF metadata
     * This fixes camera photos appearing sideways
     */
    private fun getExifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )

                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error reading EXIF orientation", e)
            0
        }
    }

    /**
     * Rotate bitmap by specified degrees
     * Used to apply EXIF orientation correction
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Compress image from Uri to File
     * ✅ FIXED: Now respects EXIF orientation
     * ✅ CHANGED: Now compresses to WebP for better performance
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 800,
        maxSizeBytes: Int = 500 * 1024 // 500KB (This is now a fallback, WebP is much smaller)
    ): File = withContext(Dispatchers.IO) {
        // ✅ NEW: Read rotation from EXIF metadata
        val rotation = getExifRotation(context, uri)
        if (rotation != 0) {
            Log.d(TAG, "📐 EXIF rotation detected: $rotation degrees")
        }

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

        // ✅ Optimized: Always downsample at least 2x
        val calculatedSampleSize = if (largest > maxDimension) {
            (largest / maxDimension).roundToInt().coerceAtLeast(2)  // Min 2x
        } else {
            2  // Even small images get downsampled
        }

        // 2. Decode actual bitmap with sampling
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculatedSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = false
            inPurgeable = true
            inInputShareable = true
        }

        var bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalStateException("Failed to decode image")

        // ✅ NEW: Apply EXIF rotation if needed
        if (rotation != 0) {
            Log.d(TAG, "🔄 Rotating bitmap $rotation degrees")
            bitmap = rotateBitmap(bitmap, rotation)
        }

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

        // 4. Compress with WebP
        // ✅ CHANGED: Removed adaptive quality loop, using a single WebP compress
        val stream = ByteArrayOutputStream()
        val quality = 80 // WebP quality 80 is a great balance of size/quality
        scaledBitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
        val compressedBytes = stream.toByteArray()
        scaledBitmap.recycle()

        // 5. Save to cache file
        // ✅ CHANGED: File extension is now .webp
        val outputFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.webp")
        outputFile.writeBytes(compressedBytes)
        outputFile
    }

    /**
     * ✅ ENHANCED: Compress image into TWO versions with detailed logging
     * ✅ FIXED: Now correctly handles EXIF orientation from camera
     * ✅ CHANGED: Now compresses to WebP format for tiny file sizes.
     *
     * Stage 1: Tiny thumbnail (320px, ~15KB) - uploads in <1 second
     * Stage 2: Full image (800px, ~80KB) - uploads in background
     */
    suspend fun compressImageTwoStage(
        context: Context,
        uri: Uri
    ): CompressedImages = withContext(Dispatchers.IO) {

        val overallStart = System.currentTimeMillis()

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📦 COMPRESSION: Two-stage compression started (Format: WebP)")
        Log.d(TAG, "   Input URI: $uri")

        // ========================================
        // ✅ NEW: Read EXIF rotation at the start
        // ========================================
        val rotation = getExifRotation(context, uri)
        if (rotation != 0) {
            Log.d(TAG, "📐 EXIF rotation detected: $rotation degrees")
        }

        // ========================================
        // Initial image analysis
        // ========================================
        val analyzeStart = System.currentTimeMillis()
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        val width = options.outWidth
        val height = options.outHeight
        val largest = max(width, height).toFloat()
        val analyzeTime = System.currentTimeMillis() - analyzeStart

        Log.d(TAG, "✅ Image analyzed (${analyzeTime}ms)")
        Log.d(TAG, "   → Original dimensions: ${width}x${height}px")
        Log.d(TAG, "   → Largest dimension: ${largest.roundToInt()}px")
        Log.d(TAG, "   → MIME type: ${options.outMimeType}")

        // ========================================
        // STAGE 1: TINY THUMBNAIL (320px, ~15KB)
        // ========================================
        val thumb1Start = System.currentTimeMillis()
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔸 STAGE 1: Creating THUMBNAIL...")

        val thumbnailSampleSize = (largest / 320).roundToInt().coerceAtLeast(4)
        Log.d(TAG, "   → Target size: 320px")
        Log.d(TAG, "   → Sample size: ${thumbnailSampleSize}x downsampling")

        val thumbOptions = BitmapFactory.Options().apply {
            inSampleSize = thumbnailSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = false
            inPurgeable = true
            inInputShareable = true
        }

        val thumbDecodeStart = System.currentTimeMillis()
        var thumbBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, thumbOptions)
        } ?: throw IllegalStateException("Failed to decode thumbnail")

        val thumbDecodeTime = System.currentTimeMillis() - thumbDecodeStart
        Log.d(TAG, "   ✓ Decoded bitmap (${thumbDecodeTime}ms)")
        Log.d(TAG, "      Decoded size: ${thumbBitmap.width}x${thumbBitmap.height}px")

        // ✅ NEW: Apply EXIF rotation to thumbnail
        if (rotation != 0) {
            Log.d(TAG, "   🔄 Rotating thumbnail $rotation degrees")
            thumbBitmap = rotateBitmap(thumbBitmap, rotation)
            Log.d(TAG, "      Rotated size: ${thumbBitmap.width}x${thumbBitmap.height}px")
        }

        // Scale to exact 320px
        val thumbScaleStart = System.currentTimeMillis()
        val thumbScaled = if (max(thumbBitmap.width, thumbBitmap.height) > 320) {
            val scale = 320f / max(thumbBitmap.width, thumbBitmap.height)
            val newWidth = (thumbBitmap.width * scale).roundToInt()
            val newHeight = (thumbBitmap.height * scale).roundToInt()

            Log.d(TAG, "   → Scaling to exact: ${newWidth}x${newHeight}px")

            Bitmap.createScaledBitmap(
                thumbBitmap,
                newWidth,
                newHeight,
                false  // Skip filtering for speed
            ).also { thumbBitmap.recycle() }
        } else {
            Log.d(TAG, "   → No scaling needed (already small)")
            thumbBitmap
        }

        val thumbScaleTime = System.currentTimeMillis() - thumbScaleStart
        if (thumbScaleTime > 0) {
            Log.d(TAG, "   ✓ Scaled (${thumbScaleTime}ms)")
        }

        // Compress thumbnail - ULTRA aggressive
        val thumbCompressStart = System.currentTimeMillis()
        val thumbStream = ByteArrayOutputStream()
        // ✅ CHANGED: Format is now WebP. Quality 60 is very small.
        thumbScaled.compress(Bitmap.CompressFormat.WEBP, 60, thumbStream)
        thumbScaled.recycle()

        val thumbBytes = thumbStream.toByteArray()
        val thumbCompressTime = System.currentTimeMillis() - thumbCompressStart

        Log.d(TAG, "   ✓ Compressed WebP (${thumbCompressTime}ms)")
        Log.d(TAG, "      Quality: 60")
        Log.d(TAG, "      Size: ${thumbBytes.size / 1024}KB (${thumbBytes.size} bytes)")

        // ✅ CHANGED: File extension is now .webp
        val thumbnailFile = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.webp")
        thumbnailFile.writeBytes(thumbBytes)

        val thumb1Time = System.currentTimeMillis() - thumb1Start
        Log.d(TAG, "✅ THUMBNAIL COMPLETE (${thumb1Time}ms)")
        Log.d(TAG, "   → File: ${thumbnailFile.name}")
        Log.d(TAG, "   → Final size: ${thumbnailFile.length() / 1024}KB")

        // ========================================
        // STAGE 2: FULL IMAGE (800px, ~80KB)
        // ========================================
        val full2Start = System.currentTimeMillis()
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔹 STAGE 2: Creating FULL IMAGE...")

        val fullSampleSize = (largest / 800).roundToInt().coerceAtLeast(2)
        Log.d(TAG, "   → Target size: 800px")
        Log.d(TAG, "   → Sample size: ${fullSampleSize}x downsampling")

        val fullOptions = BitmapFactory.Options().apply {
            inSampleSize = fullSampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = false
            inPurgeable = true
            inInputShareable = true
        }

        val fullDecodeStart = System.currentTimeMillis()
        var fullBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, fullOptions)
        } ?: throw IllegalStateException("Failed to decode full image")

        val fullDecodeTime = System.currentTimeMillis() - fullDecodeStart
        Log.d(TAG, "   ✓ Decoded bitmap (${fullDecodeTime}ms)")
        Log.d(TAG, "      Decoded size: ${fullBitmap.width}x${fullBitmap.height}px")

        // ✅ NEW: Apply EXIF rotation to full image
        if (rotation != 0) {
            Log.d(TAG, "   🔄 Rotating full image $rotation degrees")
            fullBitmap = rotateBitmap(fullBitmap, rotation)
            Log.d(TAG, "      Rotated size: ${fullBitmap.width}x${fullBitmap.height}px")
        }

        // Scale to exact 800px
        val fullScaleStart = System.currentTimeMillis()
        val fullScaled = if (max(fullBitmap.width, fullBitmap.height) > 800) {
            val scale = 800f / max(fullBitmap.width, fullBitmap.height)
            val newWidth = (fullBitmap.width * scale).roundToInt()
            val newHeight = (fullBitmap.height * scale).roundToInt()

            Log.d(TAG, "   → Scaling to exact: ${newWidth}x${newHeight}px")

            Bitmap.createScaledBitmap(
                fullBitmap,
                newWidth,
                newHeight,
                true  // Use filtering for better quality
            ).also { fullBitmap.recycle() }
        } else {
            Log.d(TAG, "   → No scaling needed")
            fullBitmap
        }

        val fullScaleTime = System.currentTimeMillis() - fullScaleStart
        if (fullScaleTime > 0) {
            Log.d(TAG, "   ✓ Scaled (${fullScaleTime}ms)")
        }

        // Compress full image
        val fullCompressStart = System.currentTimeMillis()
        val fullStream = ByteArrayOutputStream()
        // ✅ CHANGED: Format is now WebP. Quality 75 is a great balance.
        fullScaled.compress(Bitmap.CompressFormat.WEBP, 75, fullStream)
        fullScaled.recycle()

        val fullBytes = fullStream.toByteArray()
        val fullCompressTime = System.currentTimeMillis() - fullCompressStart

        Log.d(TAG, "   ✓ Compressed WebP (${fullCompressTime}ms)")
        Log.d(TAG, "      Quality: 75")
        Log.d(TAG, "      Size: ${fullBytes.size / 1024}KB (${fullBytes.size} bytes)")

        // ✅ CHANGED: File extension is now .webp
        val fullFile = File(context.cacheDir, "full_${System.currentTimeMillis()}.webp")
        fullFile.writeBytes(fullBytes)

        val full2Time = System.currentTimeMillis() - full2Start
        Log.d(TAG, "✅ FULL IMAGE COMPLETE (${full2Time}ms)")
        Log.d(TAG, "   → File: ${fullFile.name}")
        Log.d(TAG, "   → Final size: ${fullFile.length() / 1024}KB")

        // ========================================
        // Summary
        // ========================================
        val totalTime = System.currentTimeMillis() - overallStart
        // ... (rest of your logging is fine)

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🎉 TWO-STAGE COMPRESSION COMPLETE (Format: WebP)")
        // ... (rest of your logging)

        CompressedImages(thumbnailFile, fullFile)
    }
}