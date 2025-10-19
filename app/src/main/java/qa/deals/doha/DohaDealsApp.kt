package qa.deals.doha

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
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
 * - ✨ NEW: Coil ImageLoader optimization for 2025 performance
 * - ✨ NEW: Aggressive image caching strategy
 * - ✨ NEW: Memory and disk cache configuration
 *
 * PERFORMANCE IMPROVEMENTS:
 * - Images load instantly on second visit (disk cache)
 * - Reduced memory usage (25% RAM limit)
 * - 250MB disk cache for persistent storage
 * - Optimized for slow networks
 */
class DohaDealsApp : Application(), ImageLoaderFactory {  // ✨ NEW: Implement ImageLoaderFactory

    override fun onCreate() {
        super.onCreate()

        // ✅ EXISTING: Make appContext available to core modules (e.g., DataStore)
        AppContext.init(this)

        // ✨ NEW: Coil will automatically use newImageLoader() for all image loading
        // No manual initialization needed - just implement ImageLoaderFactory
    }

    /**
     * ✨ NEW: Configure Coil ImageLoader for optimal performance
     *
     * This is called automatically by Coil when the first image is loaded.
     * Configuration applies to ALL images in the app (Feed, Details, etc.)
     *
     * PERFORMANCE FEATURES:
     * - Memory cache: 25% of available RAM
     * - Disk cache: 250MB persistent storage
     * - Cache-first strategy: Check cache before network
     * - Crossfade animations: Smooth image transitions
     * - Debug logging: Track image loads (remove in production)
     *
     * Created: 2025-10-19 14:52:06 UTC by @Magdyz
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            // ========================================
            // 💾 MEMORY CACHE CONFIGURATION
            // Stores decoded images in RAM for instant access
            // ========================================
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)  // ✅ Use 25% of app memory for image cache
                    .strongReferencesEnabled(true)  // ✅ Keep strong references for better performance
                    .build()
            }

            // ========================================
            // 💿 DISK CACHE CONFIGURATION
            // Stores images on device storage for offline access
            // ========================================
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))  // ✅ Store in app cache directory
                    .maxSizeBytes(250 * 1024 * 1024)  // ✅ 250MB max disk cache
                    .build()
            }

            // ========================================
            // 🚀 PERFORMANCE OPTIMIZATIONS
            // ========================================
            .respectCacheHeaders(false)  // ✅ Prefer local cache over server cache headers
            .diskCachePolicy(CachePolicy.ENABLED)  // ✅ Always use disk cache
            .memoryCachePolicy(CachePolicy.ENABLED)  // ✅ Always use memory cache
            .networkCachePolicy(CachePolicy.ENABLED)  // ✅ Cache network responses

            // ========================================
            // 🎨 UX ENHANCEMENTS
            // ========================================
            .crossfade(true)  // ✅ Smooth crossfade animation (300ms default)
            .crossfade(300)  // ✅ Explicit 300ms crossfade duration

            // ========================================
            // 🐛 DEBUG CONFIGURATION
            // ⚠️ REMOVE IN PRODUCTION - Only for development
            // ========================================
            .logger(DebugLogger())  // ✅ Log all image loads to Logcat
            // TODO: Remove .logger() before production release

            .build()
    }
}