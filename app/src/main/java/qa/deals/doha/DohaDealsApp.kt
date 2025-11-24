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
import qa.deals.doha.BuildConfig
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

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

        // ✨ NEW: Initialize PostHog Analytics
        initializePostHog()
    }

    /**
     * ✨ POSTHOG ANALYTICS INITIALIZATION
     *
     * Initializes PostHog analytics SDK for tracking user behavior, DAU, retention, etc.
     *
     * Features enabled:
     * - Automatic screen view tracking
     * - App lifecycle events
     * - Session replay (optional)
     * - Feature flags
     * - A/B testing
     *
     * Privacy:
     * - No PII collected by default
     * - Events are batched for efficiency
     * - Debug mode only in DEBUG builds
     */
    private fun initializePostHog() {
        try {
            val config = PostHogAndroidConfig(
                apiKey = "phc_syEtMzMy8W2JVYaW1bPcvqVBBaWnot73WjeUXQlK7k5",
                host = "https://app.posthog.com"
            ).apply {
                // Enable automatic screen tracking
                captureScreenViews = true

                // Enable app lifecycle events (app opened, app backgrounded)
                captureApplicationLifecycleEvents = true

                // ✨ Enable Session Replay (includes Rage Click detection!)
                // This records user sessions so you can watch exactly what they do
                sessionReplay = true

                // Enable debug logging only in debug builds
                debug = BuildConfig.DEBUG
            }

            PostHogAndroid.setup(this, config)

            Log.d("DohaDealsApp", "✅ PostHog Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e("DohaDealsApp", "❌ Failed to initialize PostHog Analytics", e)
        }
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
                Log.d("DohaDealsApp", "✅ Coil 3.0 ImageLoader created successfully")
            }
    }
}