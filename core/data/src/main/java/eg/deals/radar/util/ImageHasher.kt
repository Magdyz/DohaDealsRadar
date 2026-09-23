package eg.deals.radar.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Perceptual "difference hash" (dHash, 64 bits) of a photo.
 * Two photos of the same deal (even re-compressed or slightly resized) give
 * hashes that differ in only a few bits; the backend compares them to catch
 * duplicate posts. Returned as a signed decimal string (Postgres bigint).
 */
object ImageHasher {

    fun dHash(file: File): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val small = Bitmap.createScaledBitmap(decoded, 9, 8, true)
        if (small !== decoded) decoded.recycle()

        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(small.getPixel(x, y))
                val right = luminance(small.getPixel(x + 1, y))
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        small.recycle()
        hash.toString()
    }.getOrNull()

    private fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
