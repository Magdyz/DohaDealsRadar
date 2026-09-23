package eg.deals.radar

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
import kotlinx.coroutines.launch
import eg.deals.radar.util.AppContext
import eg.deals.radar.fcm.EgyptDealsFirebaseMessagingService
import eg.deals.radar.BuildConfig

/**
 * ========================================
 * ✨ EGYPT DEAL RADAR APPLICATION
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
class EgyptDealsApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        Log.d("EgyptDealsApp", "📱 Application onCreate() called")

        // ✅ EXISTING: Make appContext available to core modules (e.g., DataStore)
        AppContext.init(this)

        Log.d("EgyptDealsApp", "✅ AppContext initialized")

        // 🔐 Secure sessions: migrate old logins, react to expired sessions
        eg.deals.radar.auth.AuthManager.init(this)

        // 🔔 Create the deals notification channel up front (used by background FCM notifications)
        EgyptDealsFirebaseMessagingService.ensureNotificationChannel(this)

        // 🔔 New-deal alerts on by default (opt-out lives in notification settings)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            eg.deals.radar.manager.NotificationManager.getInstance(this@EgyptDealsApp)
                .applyDefaultSubscriptions(this@EgyptDealsApp)
        }

        // Privacy-first: no third-party analytics SDKs (stats come from our own database)
    }

    /**
     * ✨ COIL 3.0: Factory method to create ImageLoader singleton
     *
     * This is called automatically by Coil when the first image is requested.
     * Replaces the old ImageLoaderFactory.newImageLoader() pattern.
     */
    override fun newImageLoader(context: Context): ImageLoader {
        Log.d("EgyptDealsApp", "🖼️ Creating Coil 3.0 ImageLoader...")

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
            // ✅ FIXED: Only enable debug logging in debug builds
            // ========================================
            .apply {
                // Only enable debug logging in debug builds
                // This prevents performance overhead and log spam in production
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }

            .build()
            .also {
                Log.d("EgyptDealsApp", "✅ Coil 3.0 ImageLoader created successfully")
            }
    }
}