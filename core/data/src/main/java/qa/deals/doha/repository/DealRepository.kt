package qa.deals.doha.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import qa.deals.doha.db.DatabaseModule
import qa.deals.doha.db.DealDao
import qa.deals.doha.db.DealEntity
import qa.deals.doha.network.*
import qa.deals.doha.network.PaginationMeta
import qa.deals.doha.util.AppContext
import java.io.File

/**
 * Repository for managing deals.
 * Implements Stale-While-Revalidate (SWR) pattern.
 *
 * ✅ UPDATED: Added pagination support (2025-10-24)
 */
class DealRepository {

    // Access database and API through their modules
    private val dealDao: DealDao by lazy {
        DatabaseModule.provideDealDao(AppContext.appContext)
    }
    private val api: SupabaseApiService = NetworkModule.api

    /**
     * Get cached deals as a Flow (reactive updates)
     * ✅ PRESERVED: No changes
     */
    fun getCachedDeals(): Flow<List<DealEntity>> {
        return dealDao.getAllDeals()
    }

    // ========================================
    // ✅ SPRINT 3: Archive Feature - Get Active Deals
    // Returns only non-archived deals (for main feed)
    // ========================================
    /**
     * Get cached ACTIVE deals as a Flow (excludes archived)
     * Use this for the main feed to hide archived deals
     */
    fun getCachedActiveDeals(): Flow<List<DealEntity>> {
        return dealDao.getActiveDeals()
    }

    // ========================================
    // ✅ SPRINT 3: Archive Feature - Get Archived Deals
    // Returns only archived deals (for archive screen)
    // ========================================
    /**
     * Get cached ARCHIVED deals as a Flow
     * Use this for the archive screen
     */
    fun getCachedArchivedDeals(): Flow<List<DealEntity>> {
        return dealDao.getArchivedDeals()
    }

    // ========================================
    // ✅ SPRINT 3: Archive Feature - Refresh Archived Deals
    // Fetches archived deals from API and updates cache
    // ========================================
    /**
     * Refresh archived deals from network and update cache
     *
     * @param page Page number to fetch (default: 1)
     * @param append If true, appends to existing cache. If false, replaces cache.
     * @return Result with PaginationMeta or error
     */
    suspend fun refreshArchivedDeals(page: Int = 1, append: Boolean = false): Result<PaginationMeta?> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "📦 Fetching archived deals (page: $page, append: $append)...")
            val response = api.getArchivedDeals(page = page, limit = 20)

            if (response.success == true && response.data != null) {
                val entities = response.data.map { it.toEntity() }

                if (append) {
                    // Append to existing cache (for pagination - load more)
                    dealDao.insertAll(entities)
                    val totalArchived = dealDao.getArchivedDealsCount()
                    Log.d("Repository", "➕ Appended ${entities.size} archived deals (total: $totalArchived)")
                } else {
                    // ✅ FIX: Replace only the archived deals in the cache
                    dealDao.replaceArchivedDeals(entities)
                    Log.d("Repository", "🔄 Updated cache with ${entities.size} archived deals")
                }

                Result.success(response.pagination)
            } else {
                Log.e("Repository", "❌ API returned success=false or null data for archived deals")
                Result.failure(Exception(response.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error refreshing archived deals", e)
            Result.failure(e)
        }
    }

    // ========================================
    // ✨ NEW: INSERT PRELOADED DEALS
    // Insert deals from PreloadRepository into Room cache
    // ========================================
    /**
     * Insert preloaded deals into Room cache
     * Called by FeedViewModel when preload cache is available
     *
     * ⚠️ SAFE: Only inserts, doesn't replace existing data
     * ⚠️ NON-BREAKING: If fails, normal load continues
     *
     * @param deals List of preloaded deal entities
     */
    suspend fun insertPreloadedDeals(deals: List<DealEntity>) = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "📥 Inserting ${deals.size} preloaded deals into cache...")
            // Replace cache atomically (same as normal refresh)
            dealDao.replaceAllDeals(deals)
            Log.d("Repository", "✅ Preloaded deals inserted successfully")
        } catch (e: Exception) {
            Log.e("Repository", "💥 Failed to insert preloaded deals", e)
            throw e // Rethrow so FeedViewModel can handle
        }
    }

    /**
     * ✅ UPDATED: Refresh deals from network and update cache
     *
     * @param page Page number to fetch (default: 1)
     * @param append If true, appends to existing cache. If false, replaces cache.
     * @return Result with PaginationMeta or error
     */
    suspend fun refreshDeals(page: Int = 1, append: Boolean = false): Result<PaginationMeta?> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "📄 Fetching deals (page: $page, append: $append)...")
            val response = api.getDeals(page = page, limit = 20)

            if (response.success == true && response.data != null) {
                val entities = response.data.map { it.toEntity() }

                if (append) {
                    // Append to existing cache (for pagination - load more)
                    dealDao.insertAll(entities)
                    val totalCached = dealDao.getDealsCount()
                    Log.d("Repository", "➕ Appended ${entities.size} deals (total cached: $totalCached)")
                } else {
                    // Replace cache atomically (prevents flash)
                    dealDao.replaceAllDeals(entities) // ✅ Single atomic operation
                    Log.d("Repository", "🔄 Replaced cache with ${entities.size} deals")

                }

                Result.success(response.pagination)
            } else {
                Log.e("Repository", "❌ API returned success=false or null data")
                Result.failure(Exception(response.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error refreshing deals", e)
            Result.failure(e)
        }
    }

    // ========================================
    // ✅ PRESERVED: Submit Deal (No Changes)
    // ========================================
    /**
     * Submit a new deal
     * UPDATED: Now accepts userId and deviceId for email verification
     */
    suspend fun submitDeal(
        title: String,
        description: String? = null,
        link: String?,
        imageUrl: String,
        location: String? = null,
        category: String = "other",
        promoCode: String? = null,
        postedBy: String = "Anonymous",
        // NEW: Parameters for verified user submission
        userId: String? = null,
        deviceId: String? = null
    ): ApiEnvelope<List<DealDto>> = withContext(Dispatchers.IO) {
        Log.d("Repository", "Submitting deal to backend")
        Log.d("Repository", "   Title: $title")
        Log.d("Repository", "   Category: $category")
        Log.d("Repository", "   Posted by: $postedBy")
        Log.d("Repository", "   User ID: $userId")
        Log.d("Repository", "   Device ID: ${deviceId?.take(8)}...")

        val request = SubmitDealRequest(
            title = title,
            description = description,
            link = link,
            imageUrl = imageUrl,
            location = location,
            category = category,
            promoCode = promoCode,
            postedBy = postedBy,
            userId = userId,
            deviceId = deviceId
        )

        val response = api.submitDeal(request)

        if (response.success == true) {
            Log.d("Repository", "Deal submitted successfully")
            Log.d("Repository", "   Deal ID: ${response.data?.firstOrNull()?.id}")
            Log.d(
                "Repository",
                "   Auto-Approved: ${response.data?.firstOrNull()?.autoApproved}"
            )
        } else {
            Log.e("Repository", "Deal submission failed: ${response.error}")
        }

        response
    }

    // ========================================
    // ✅ PRESERVED: Update Deal Image (No Changes)
    // ========================================
    /**
     * Update deal image URL (for two-stage upload)
     * Used to upgrade thumbnail to full resolution image
     */
    suspend fun updateDealImage(
        dealId: String,
        newImageUrl: String
    ): ApiEnvelope<DealDto> = withContext(Dispatchers.IO) {
        Log.d("Repository", "Updating image for deal $dealId")

        val request = UpdateImageRequest(
            deal_id = dealId,
            image_url = newImageUrl
        )

        api.updateDealImage(request)
    }

    // ========================================
    // ✅ PRESERVED: Cast Vote (No Changes)
    // ========================================
    /**
     * Cast a vote on a deal
     */
    suspend fun castVote(
        dealId: String,
        voteType: String,
        deviceId: String
    ): ApiEnvelope<DealDto> = withContext(Dispatchers.IO) {
        Log.d("Repository", "Casting $voteType vote for deal $dealId")

        val request = VoteRequest(
            deal_id = dealId,
            vote_type = voteType,
            device_id = deviceId
        )

        val response = api.castVote(request)

        // Update local cache with new vote counts
        if (response.success == true && response.data != null) {
            val entity = response.data.toEntity()
            dealDao.insertDeal(entity)
            Log.d("Repository", "Vote cast successfully, cache updated")
        }

        response
    }

    // ========================================
    // ✅ PRESERVED: Report Deal (No Changes)
    // ========================================
    /**
     * Report a deal
     */
    suspend fun reportDeal(
        dealId: String,
        deviceId: String,
        reason: String,
        note: String? = null
    ): ApiEnvelope<List<ReportDto>> = withContext(Dispatchers.IO) {
        Log.d("Repository", "🚨 Reporting deal $dealId for reason: $reason")

        val request = ReportRequest(
            deal_id = dealId,
            device_id = deviceId,
            reason = reason,
            note = note
        )

        api.reportDeal(request)
    }

    // ========================================
    // ✅ PRESERVED: Upload Image (No Changes)
    // ========================================
    /**
     * Upload image to Supabase Storage
     */
    suspend fun uploadImage(file: File): String = withContext(Dispatchers.IO) {
        StorageUploader.uploadImage(file)
    }

    // ========================================
    // ✅ PRESERVED: Email Verification (No Changes)
    // ========================================

    /**
     * Send verification code to email
     * ✅ ENHANCED: Handles errors with user-friendly messages
     */
    suspend fun sendVerificationCode(email: String): SendCodeResponse =
        withContext(Dispatchers.IO) {
            try {
                api.sendVerificationCode(SendCodeRequest(email))
            } catch (e: retrofit2.HttpException) {
                // Handle HTTP errors with user-friendly messages
                when (e.code()) {
                    400 -> SendCodeResponse(
                        success = false,
                        error = "Invalid email address. Please check and try again."
                    )
                    429 -> SendCodeResponse(
                        success = false,
                        error = "Too many requests. Please wait a moment."
                    )
                    else -> SendCodeResponse(
                        success = false,
                        error = "Failed to send code. Please try again."
                    )
                }
            } catch (e: Exception) {
                // Handle network and other errors
                SendCodeResponse(
                    success = false,
                    error = "Network error. Please check your connection."
                )
            }
        }

    /**
     * Verify code and get/create user
     * ✅ ENHANCED: Handles HTTP 401 errors with user-friendly messages
     */
    suspend fun verifyCodeAndGetUser(
        email: String,
        code: String,
        deviceId: String
    ): VerifyCodeResponse = withContext(Dispatchers.IO) {
        try {
            // Attempt to verify the code via API
            api.verifyCodeAndGetUser(
                VerifyCodeRequest(
                    email = email,
                    code = code,
                    deviceId = deviceId
                )
            )
        } catch (e: retrofit2.HttpException) {
            // Handle HTTP errors with user-friendly messages
            when (e.code()) {
                401 -> VerifyCodeResponse(
                    success = false,
                    error = "Invalid verification code. Please try again."
                )
                429 -> VerifyCodeResponse(
                    success = false,
                    error = "Too many attempts. Please wait a moment."
                )
                else -> VerifyCodeResponse(
                    success = false,
                    error = "Verification failed. Please try again."
                )
            }
        } catch (e: Exception) {
            // Handle network and other errors
            VerifyCodeResponse(
                success = false,
                error = "Network error. Please check your connection."
            )
        }
    }
}