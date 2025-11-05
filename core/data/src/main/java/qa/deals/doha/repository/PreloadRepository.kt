package qa.deals.doha.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qa.deals.doha.db.DealEntity
import qa.deals.doha.network.DealDto
import qa.deals.doha.network.NetworkModule
import qa.deals.doha.network.toEntity

/**
 * ========================================
 * ✨ NEW: PRELOAD REPOSITORY
 * Background preloading during onboarding
 * ========================================
 *
 * Created: 2025-11-03
 * Purpose: Preload deals data during onboarding slides
 *
 * Features:
 * - In-memory cache with timestamp
 * - 60-second expiry
 * - Thread-safe operations
 * - Does NOT replace existing feed loading
 *
 * Safety:
 * - If preload fails, normal feed loading continues
 * - Cache expires after 60 seconds
 * - Memory efficient (stores max 15 deals)
 *
 * Usage:
 * - Call preloadDeals() from OnboardingScreen
 * - FeedViewModel checks cache before loading
 * - Falls back to normal load if cache empty/expired
 *
 * ⚠️ IMPORTANT: This is a "bonus optimization layer"
 * Existing feed loading remains unchanged
 */
class PreloadRepository {

    private val api = NetworkModule.api

    // In-memory cache with timestamp
    @Volatile
    private var cachedDeals: List<DealEntity>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    companion object {
        private const val TAG = "PreloadRepository"
        private const val CACHE_EXPIRY_MS = 60_000L // 60 seconds
        private const val MAX_PRELOAD_DEALS = 15 // Limit to first 15 deals

        @Volatile
        private var INSTANCE: PreloadRepository? = null

        /**
         * Get singleton instance
         */
        fun getInstance(): PreloadRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreloadRepository().also {
                    INSTANCE = it
                    Log.d(TAG, "✅ PreloadRepository instance created")
                }
            }
        }
    }

    /**
     * ✨ Preload deals in background (called from OnboardingScreen)
     *
     * This runs in background without blocking UI
     * Safe to call multiple times (won't reload if already cached)
     *
     * @return true if preload successful, false otherwise
     */
    suspend fun preloadDeals(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Skip if cache is still fresh
            if (isCacheFresh()) {
                Log.d(TAG, "⏭️ Cache is still fresh, skipping preload")
                return@withContext true
            }

            Log.d(TAG, "🚀 Starting background preload...")
            val response = api.getDeals(page = 1, limit = MAX_PRELOAD_DEALS)

            if (response.success == true && response.data != null) {
                val entities: List<DealEntity> = response.data.map { dto: DealDto -> dto.toEntity() }

                synchronized(this) {
                    cachedDeals = entities
                    cacheTimestamp = System.currentTimeMillis()
                }

                Log.d(TAG, "✅ Preloaded ${entities.size} deals successfully")
                true
            } else {
                Log.e(TAG, "❌ Preload failed: ${response.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 Preload error (non-critical, normal load will continue)", e)
            false
        }
    }

    /**
     * ✨ Get cached deals (called from FeedViewModel)
     *
     * Returns null if:
     * - Cache is empty
     * - Cache is expired (>60 seconds old)
     *
     * @return List of cached deals or null
     */
    @Synchronized
    fun getCachedDeals(): List<DealEntity>? {
        if (!isCacheFresh()) {
            Log.d(TAG, "⏰ Cache expired or empty")
            return null
        }

        Log.d(TAG, "✅ Returning ${cachedDeals?.size ?: 0} cached deals")
        return cachedDeals
    }

    /**
     * Check if cache is fresh (<60 seconds old)
     */
    private fun isCacheFresh(): Boolean {
        if (cachedDeals == null) return false
        val age = System.currentTimeMillis() - cacheTimestamp
        return age < CACHE_EXPIRY_MS
    }

    /**
     * Get cache age in seconds (for debugging)
     */
    fun getCacheAge(): Int {
        if (cacheTimestamp == 0L) return -1
        return ((System.currentTimeMillis() - cacheTimestamp) / 1000).toInt()
    }

    /**
     * Clear cache (for testing or manual refresh)
     */
    @Synchronized
    fun clearCache() {
        cachedDeals = null
        cacheTimestamp = 0L
        Log.d(TAG, "🗑️ Cache cleared")
    }
}
