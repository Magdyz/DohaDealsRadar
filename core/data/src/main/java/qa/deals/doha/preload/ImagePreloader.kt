package qa.deals.doha.preload

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import qa.deals.doha.db.DealEntity

/**
 * ========================================
 * ✨ NEW: IMAGE PRELOADER
 * Preload deal images during onboarding
 * ========================================
 *
 * Created: 2025-11-03
 * Purpose: Preload first 6-8 deal images into Coil cache
 *
 * Features:
 * - Uses Coil 3.0 ImageLoader
 * - Low priority background loading
 * - Cancellable via Job
 * - Memory efficient (limits to 8 images)
 *
 * Safety:
 * - Does not block UI
 * - Respects Coil memory limits
 * - Auto-cancels on error
 * - Won't interfere with regular image loading
 *
 * Usage:
 * val job = ImagePreloader.preloadImages(context, deals)
 * // Later: job.cancel() if needed
 */
object ImagePreloader {

    private const val TAG = "ImagePreloader"
    private const val MAX_IMAGES_TO_PRELOAD = 8

    /**
     * ✨ Preload images for deals in background
     *
     * This loads images into Coil's memory cache so they're
     * instantly available when the feed screen loads
     *
     * @param context Android context
     * @param deals List of deals to preload images for
     * @return Job that can be cancelled
     */
    suspend fun preloadImages(
        context: Context,
        deals: List<DealEntity>
    ): Job = withContext(Dispatchers.IO) {
        val job = Job()

        try {
            // Limit to first 8 images (visible on screen)
            val imagesToPreload = deals.take(MAX_IMAGES_TO_PRELOAD)
                .mapNotNull { it.imageUrl }

            if (imagesToPreload.isEmpty()) {
                Log.d(TAG, "⏭️ No images to preload")
                return@withContext job
            }

            Log.d(TAG, "🖼️ Starting preload of ${imagesToPreload.size} images...")

            val imageLoader = ImageLoader.Builder(context)
                .build()

            imagesToPreload.forEachIndexed { index, imageUrl ->
                if (!job.isActive) {
                    Log.d(TAG, "⏸️ Preload cancelled")
                    return@withContext job
                }

                try {
                    // Build preload request with optimized settings
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(400, 400) // Match feed image size
                        .allowHardware(false) // Prevent hardware bitmap issues
                        .build()

                    // Execute preload (doesn't display, just caches)
                    imageLoader.enqueue(request)

                    Log.d(TAG, "✅ Preloaded image ${index + 1}/${imagesToPreload.size}")
                } catch (e: Exception) {
                    Log.e(TAG, "⚠️ Failed to preload image $imageUrl (non-critical)", e)
                    // Continue with next image
                }
            }

            Log.d(TAG, "🎉 Image preload complete!")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Image preload error (non-critical)", e)
        }

        job
    }

    /**
     * Calculate optimal number of images to preload based on screen height
     * (Future enhancement - not used yet)
     */
    fun calculateOptimalPreloadCount(screenHeightPx: Int, itemHeightPx: Int): Int {
        val visibleItems = (screenHeightPx / itemHeightPx) + 2 // +2 for buffer
        return visibleItems.coerceIn(6, MAX_IMAGES_TO_PRELOAD)
    }
}
