package qa.deals.doha

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.util.DebugLogger
import okio.Path.Companion.toOkioPath
import qa.deals.doha.util.AppContext

/**
 * ========================================
 * ✨ DOHA DEALS APPLICATION
 * Main application class for global configuration
 * ========================================
 *
 * Created: Initial setup
 * Updated: 2025-10-19 14:52:06 UTC by @Magdyz
 *
 * CHANGES:
 * - ✅ Existing: AppContext initialization
 * - ✨ NEW: Coil 3.0 ImageLoader optimization for 2025 performance
 * - ✨ NEW: Aggressive image caching strategy
 * - ✨ NEW: Memory and disk cache configuration
 *
 * PERFORMANCE IMPROVEMENTS:
 * - Images load instantly on second visit (disk cache)
 * - Reduced memory usage (25% RAM limit)
 * - 250MB disk cache for persistent storage
 * - Optimized for slow networks
 *
 * COIL 3.0 MIGRATION:
 * - Changed from ImageLoaderFactory to SingletonImageLoader.Factory
 * - Updated imports to coil3.* namespace
 */
class DohaDealsApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        Log.d("DohaDealsApp", "📱 Application onCreate() called")

        // ✅ EXISTING: Make appContext available to core modules (e.g., DataStore)
        AppContext.init(this)

        Log.d("DohaDealsApp", "✅ AppContext initialized")
    }

    /**
     * ✨ COIL 3.0: Factory method to create ImageLoader singleton
     *
     * This is called automatically by Coil when the first image is requested.
     * Replaces the old ImageLoaderFactory.newImageLoader() pattern.
     */
    override fun newImageLoader(context: Context): ImageLoader {
        Log.d("DohaDealsApp", "🖼️ Creating Coil 3.0 ImageLoader...")

        return ImageLoader.Builder(context)
            // ========================================
            // 💾 MEMORY CACHE CONFIGURATION
            // Stores decoded images in RAM for instant access
            // ========================================
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)  // ✅ Use 25% of app memory for image cache
                    .strongReferencesEnabled(true)  // ✅ Keep strong references for better performance
                    .build()
            }

            // ========================================
            // 💿 DISK CACHE CONFIGURATION
            // Stores images on device storage for offline access
            // ========================================
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toPath().toOkioPath())  // ✅ Store in app cache directory
                    .maxSizeBytes(250 * 1024 * 1024)  // ✅ 250MB max disk cache
                    .build()
            }

            // ========================================
            // 🚀 PERFORMANCE OPTIMIZATIONS
            // ========================================
            .diskCachePolicy(CachePolicy.ENABLED)  // ✅ Always use disk cache
            .memoryCachePolicy(CachePolicy.ENABLED)  // ✅ Always use memory cache
            .networkCachePolicy(CachePolicy.ENABLED)  // ✅ Cache network responses

            // ========================================
            // 🐛 DEBUG CONFIGURATION
            // ⚠️ REMOVE IN PRODUCTION - Only for development
            // ========================================
            .logger(DebugLogger())  // ✅ Log all image loads to Logcat
            // TODO: Remove .logger() before production release

            .build()
            .also {
                Log.d("DohaDealsApp", "✅ Coil 3.0 ImageLoader created successfully")
            }
    }
}