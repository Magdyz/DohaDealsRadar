package eg.deals.radar.repository

import eg.deals.radar.core.data.R
import eg.deals.radar.util.AppLanguage
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import eg.deals.radar.db.DatabaseModule
import eg.deals.radar.db.DealDao
import eg.deals.radar.db.DealEntity
import eg.deals.radar.network.*
import eg.deals.radar.network.PaginationMeta
import eg.deals.radar.util.AppContext
import eg.deals.radar.auth.SessionStore
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
            dealDao.replaceFeed(deals)
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
     * @param sortBy Sort option: "hottest" or "newest" (default: "hottest")
     * @param category Category filter (optional): "food_dining", "shopping_fashion", etc.
     *                 - If null → returns all categories (backend filters)
     *                 - If specific category → filters to that category only (backend filters)
     * @return Result with PaginationMeta or error
     */
    suspend fun refreshDeals(
        page: Int = 1,
        append: Boolean = false,
        sortBy: String = "hottest",
        category: String? = null,
        governorate: String? = null,
        query: String? = null
    ): Result<PaginationMeta?> = withContext(Dispatchers.IO) {
        try {
            // Keyset (cursor) pagination: page 1 starts fresh, "load more" continues
            // from the cursor of the previous page of the SAME filters.
            val filterKey = "$sortBy|$category|$governorate|$query"
            // The newest page-1 request defines the filters the feed is showing
            if (!append) activeFilterKey = filterKey
            val cursor = if (append && filterKey == lastFilterKey) nextCursor else null
            if (append && cursor == null && page > 1 && filterKey == lastFilterKey && !lastHasMore) {
                return@withContext Result.success(PaginationMeta(page = page, hasMore = false))
            }
            val response = api.getDeals(
                page = page, limit = 20, sortBy = sortBy, category = category,
                governorate = governorate, query = query?.takeIf { it.isNotBlank() }, cursor = cursor
            )

            if (response.success == true && response.data != null) {
                val entities = response.data.map { it.toEntity() }
                val written = feedWriteMutex.withLock {
                    // A late answer for filters the user already left (e.g. "load more" of
                    // All arriving after tapping Food) must not touch the feed.
                    if (filterKey != activeFilterKey) return@withLock false
                    lastFilterKey = filterKey
                    nextCursor = response.pagination?.nextCursor
                    lastHasMore = response.pagination?.hasMore == true
                    if (append) dealDao.appendFeed(entities) else dealDao.replaceFeed(entities)
                    true
                }
                if (!written) {
                    Log.d("Repository", "⏭️ Dropped stale feed page for $filterKey")
                    return@withContext Result.failure(StaleFeedPageException())
                }
                Log.d("Repository", "🔄 Feed ${if (append) "appended" else "replaced"}: ${entities.size} deals")

                Result.success(response.pagination)
            } else {
                Result.failure(Exception(ApiErrors.message(response)))
            }
        } catch (e: Exception) {
            Log.w("Repository", "Error refreshing deals: ${e.javaClass.simpleName}")
            Result.failure(Exception(ApiErrors.message(e), e))
        }
    }

    // Cursor state for the feed (see refreshDeals)
    @Volatile private var nextCursor: String? = null
    @Volatile private var lastFilterKey: String? = null
    @Volatile private var lastHasMore: Boolean = true
    @Volatile private var activeFilterKey: String? = null
    private val feedWriteMutex = Mutex()

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
        deviceId: String? = null,

        // NEW: Expiration duration in days

        expiresInDays: Int = 10,

        // ✨ NEW: Price fields (2025-11-16)
        originalPrice: Double? = null,
        discountedPrice: Double? = null,
        // ✨ Egypt 2.0
        governorate: String = "all_egypt",
        imageHash: String? = null,
        confirmNotDuplicate: Boolean = false
    ): ApiEnvelope<List<DealDto>> = withContext(Dispatchers.IO) {
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
            deviceId = deviceId,
            expiresInDays = expiresInDays,
            originalPrice = originalPrice,        // ✨ NEW: Original price (2025-11-16)
            discountedPrice = discountedPrice,    // ✨ NEW: Discounted price (2025-11-16)
            governorate = governorate,
            imageHash = imageHash,
            confirmNotDuplicate = confirmNotDuplicate
        )

        api.submitDeal(request)
    }

    // ========================================
    // ✨ Egypt 2.0: quality & privacy endpoints
    // ========================================

    /** Early duplicate warning while the user is filling the form. */
    suspend fun checkDuplicate(link: String?, title: String?, imageHash: String?): ApiEnvelope<Unit>? =
        withContext(Dispatchers.IO) {
            runCatching { api.checkDuplicate(DuplicateCheckRequest(link, title, imageHash)) }.getOrNull()
        }

    /** Pre-fill title / price / photo from a pasted store link (best effort). */
    suspend fun linkPreview(link: String): LinkPreviewDto? = withContext(Dispatchers.IO) {
        runCatching { api.linkPreview(LinkPreviewRequest(link)) }.getOrNull()
            ?.takeIf { it.success == true }?.preview
    }

    /** "Is this deal expired?" confirmation. */
    suspend fun markExpired(dealId: String): ApiEnvelope<Unit> = withContext(Dispatchers.IO) {
        try {
            api.markExpired(DealIdRequest(dealId))
        } catch (e: Exception) {
            ApiEnvelope(success = false, code = ApiErrors.NETWORK, error = e.message)
        }
    }

    /** Permanently delete the logged-in user's account and data. */
    suspend fun deleteAccount(): ApiEnvelope<Unit> = withContext(Dispatchers.IO) {
        try {
            api.deleteAccount(DeleteAccountRequest()).also {
                if (it.success == true) SessionStore.clear()
            }
        } catch (e: Exception) {
            ApiEnvelope(success = false, code = ApiErrors.NETWORK, error = e.message)
        }
    }

    /** Everything we store about the user, as pretty JSON text. */
    suspend fun exportMyData(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = api.exportMyData(emptyMap())
            if (res.success == true && res.data != null) {
                Result.success(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(res.data))
            } else {
                Result.failure(Exception(ApiErrors.message(res)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrors.message(e), e))
        }
    }

    /** Aggregate statistics for the moderator dashboard. */
    suspend fun getStats(): Result<StatsDto> = withContext(Dispatchers.IO) {
        try {
            val res = api.getStats(emptyMap())
            if (res.success == true && res.data != null) Result.success(res.data)
            else Result.failure(Exception(ApiErrors.message(res)))
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrors.message(e), e))
        }
    }

    /** Log out locally (the session is also dropped from secure storage). */
    fun logout() {
        SessionStore.clear()
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
    // ✅ UPDATED: Cast Vote (User-Authenticated)
    // ========================================
    /**
     * Cast a vote on a deal
     *
     * MIGRATION: Updated to support user-authenticated voting
     * - Prioritizes user_id for authenticated users
     * - Falls back to device_id for legacy support
     * - Backend performs duplicate vote check
     *
     * @param dealId Deal UUID to vote on
     * @param voteType "hot" or "cold"
     * @param userId Authenticated user UUID (REQUIRED for authenticated votes)
     * @param userEmail Alternative to userId (backend looks up user)
     * @param deviceId Device ID (optional, for analytics/legacy support)
     * @return ApiEnvelope with updated deal data
     */
    suspend fun castVote(
        dealId: String,
        voteType: String,
        userId: String? = null,       // ✅ NEW: User ID (preferred)
        userEmail: String? = null,    // ✅ NEW: User email (alternative)
        deviceId: String? = null      // ✅ UPDATED: Now optional
    ): ApiEnvelope<DealDto> = withContext(Dispatchers.IO) {
        val userIdentifier = userId ?: userEmail ?: "anonymous"
        Log.d("Repository", "Casting $voteType vote for deal $dealId (user: $userIdentifier)")

        val request = VoteRequest(
            deal_id = dealId,
            vote_type = voteType,
            user_id = userId,         // ✅ NEW
            user_email = userEmail,   // ✅ NEW
            device_id = deviceId      // ✅ Optional
        )

        val response = api.castVote(request)

        // ✅ FIX: DO NOT update cache here to prevent race conditions
        // When multiple votes are cast in quick succession, server responses
        // may arrive out of order, causing stale data to overwrite newer local state.
        //
        // Cache updates now happen via:
        // 1. Optimistic updates in ViewModel (instant feedback)
        // 2. Pull-to-refresh (eventual consistency)
        if (response.success == true) {
            Log.d("Repository", "✅ Vote cast successfully (cache NOT updated to prevent race condition)")
        } else {
            Log.w("Repository", "❌ Vote failed: ${response.error}")
        }

        response
    }

    // ========================================
    // ✅ NEW: Big App Voting Fix - Immediate Consistency
    // Manually inject server data into Local DB
    // This bridges the gap between "Network Success" and "UI Update"
    // ========================================
    /**
     * Update local deal cache with fresh data from network
     *
     * This method is called after a successful vote to immediately
     * update the Room database with the server's authoritative vote counts.
     * This eliminates the race condition where optimistic UI is cleared
     * before the database Flow emits updated values.
     *
     * @param dealDto Fresh deal data from server
     */
    suspend fun updateLocalDealFromNetwork(dealDto: DealDto) {
        withContext(Dispatchers.IO) {
            try {
                val entity = dealDto.toEntity()
                dealDao.updateDeal(entity)
                Log.d("Repository", "✅ Cache manually updated for deal: ${dealDto.id}")
            } catch (e: Exception) {
                Log.e("Repository", "❌ Failed to update local cache", e)
            }
        }
    }

    // ========================================
    // ✅ NEW: Instant Local Vote Count Update (Zero-Lag UI)
    // Updates only the vote counts without touching the network
    // ========================================
    /**
     * Update vote counts in local database immediately
     *
     * This is the "Instagram Pattern" - update the local database instantly
     * for zero-lag UI, then sync with server in the background.
     * If the network fails, the ViewModel will rollback these counts.
     *
     * @param dealId Deal UUID to update
     * @param hotCount New hot vote count
     * @param coldCount New cold vote count
     */
    suspend fun updateDealCountsLocal(dealId: String, hotCount: Int, coldCount: Int) {
        withContext(Dispatchers.IO) {
            try {
                dealDao.updateCounts(dealId, hotCount, coldCount)
                Log.d("Repository", "⚡ Local counts updated instantly: deal=$dealId hot=$hotCount cold=$coldCount")
            } catch (e: Exception) {
                Log.e("Repository", "❌ Failed to update local counts", e)
            }
        }
    }

    /** A deal was archived (e.g. marked expired): hide it from the cached feed. */
    suspend fun markArchivedLocal(dealId: String) = withContext(Dispatchers.IO) {
        runCatching { dealDao.markArchived(dealId) }
    }

    /**
     * Details opened for a deal that isn't cached (e.g. from a notification):
     * cache the newest deals so it can be found, WITHOUT replacing the feed
     * the user is looking at.
     */
    suspend fun cacheNewestDeals(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.getDeals(page = 1, limit = 20, sortBy = "newest", category = null,
                governorate = null, query = null, cursor = null)
            if (response.success == true && response.data != null) {
                dealDao.insertAll(response.data.map { it.toEntity() })
                Result.success(Unit)
            } else {
                Result.failure(Exception(ApiErrors.message(response)))
            }
        } catch (e: Exception) {
            Result.failure(Exception(ApiErrors.message(e), e))
        }
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
    suspend fun uploadImage(file: File, contentType: String = "image/jpeg"): String = withContext(Dispatchers.IO) {
        StorageUploader.uploadImage(file, contentType)
    }

    // ========================================
    // 🔐 Email verification -> real session
    // ========================================

    /**
     * Signs in with a Google ID token from Credential Manager. On success the
     * session is stored encrypted and used for every later request.
     * `consent` = the user accepted the privacy policy (required for new accounts).
     */
    suspend fun signInWithGoogle(
        idToken: String,
        nonce: String?,
        deviceId: String,
        consent: Boolean = false
    ): VerifyCodeResponse = withContext(Dispatchers.IO) {
        try {
            val res = api.signInWithGoogle(
                GoogleSignInRequest(idToken = idToken, nonce = nonce, deviceId = deviceId, consent = consent)
            )
            val s = res.session
            if (res.success && s != null) {
                SessionStore.save(
                    eg.deals.radar.auth.Session(
                        accessToken = s.accessToken,
                        refreshToken = s.refreshToken,
                        expiresAt = s.expiresAt ?: (System.currentTimeMillis() / 1000 + (s.expiresIn ?: 3600))
                    )
                )
                res
            } else {
                res.copy(success = false, error = ApiErrors.message(res.code, res.error, res.retryAfter, res.field, context = ApiErrors.Context.LOGIN))
            }
        } catch (e: Exception) {
            VerifyCodeResponse(success = false, error = ApiErrors.message(e), code = ApiErrors.NETWORK)
        }
    }

    // ========================================
    // ✅ SPRINT 3: USER ROLES & MODERATION METHODS
    // ========================================

    /**
     * Get pending deals from API (moderator/admin only)
     * @param userId User ID (must be moderator or admin)
     * @param page Page number
     * @param append If true, appends to cache. If false, replaces cache.
     * @return Result with PaginationMeta or error
     */
    suspend fun getPendingDeals(
        userId: String,
        page: Int = 1,
        append: Boolean = false
    ): Result<PaginationMeta?> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "📋 Fetching pending deals (page: $page, user: $userId)...")

            val response = api.getPendingDeals(
                GetPendingDealsRequest(
                    userId = userId,
                    page = page,
                    limit = 20
                )
            )

            if (response.success == true && response.data != null) {
                val entities = response.data.map { it.toEntity() }

                if (append) {
                    // Append to existing cache
                    dealDao.insertAll(entities)
                    Log.d("Repository", "➕ Appended ${entities.size} pending deals")
                } else {
                    // Insert pending deals into cache (don't replace all deals)
                    dealDao.insertAll(entities)
                    Log.d("Repository", "💾 Cached ${entities.size} pending deals")
                }

                Result.success(response.pagination)
            } else {
                Log.e("Repository", "❌ Failed to fetch pending deals: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to fetch pending deals"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error fetching pending deals", e)
            Result.failure(e)
        }
    }

    /**
     * Get cached pending deals from local database (uses Flow for reactive updates)
     * @return Flow of pending deals
     */
    fun getCachedPendingDeals(): Flow<List<DealEntity>> {
        return dealDao.getPendingDeals()
    }

    /**
     * Get deals by a specific user from API
     * @param requestingUserId User ID making the request
     * @param targetUserId User ID whose deals to fetch (null = requesting user's own deals)
     * @param page Page number
     * @return Result with UserDealsPage (pagination + optional stats on page 1) or error
     */
    suspend fun getDealsByUser(
        requestingUserId: String,
        targetUserId: String? = null,
        page: Int = 1
    ): Result<UserDealsPage> = withContext(Dispatchers.IO) {
        try {
            val userToFetch = targetUserId ?: requestingUserId
            Log.d("Repository", "👤 Fetching deals by user: $userToFetch (page: $page)...")

            val response = api.getUserDeals(
                GetUserDealsRequest(
                    userId = requestingUserId,
                    targetUserId = targetUserId,
                    page = page,
                    limit = 20
                )
            )

            if (response.success == true && response.data != null) {
                val entities = response.data.map { it.toEntity() }

                // Cache the deals
                dealDao.insertAll(entities)
                Log.d("Repository", "💾 Cached ${entities.size} deals for user: $userToFetch")

                Result.success(UserDealsPage(pagination = response.pagination, stats = response.stats))
            } else {
                Log.e("Repository", "❌ Failed to fetch user deals: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to fetch user deals"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error fetching user deals", e)
            Result.failure(e)
        }
    }

    /**
     * Get cached deals by user from local database (uses Flow for reactive updates)
     * @param userId User ID whose deals to get
     * @return Flow of deals by the user
     */
    fun getCachedDealsByUser(userId: String): Flow<List<DealEntity>> {
        return dealDao.getDealsByUser(userId)
    }

    /**
     * Approve a pending deal (moderator/admin only)
     * @param dealId Deal ID to approve
     * @param userId User ID (must be moderator or admin)
     * @return Result with success/error
     */
    suspend fun approveDeal(
        dealId: String,
        userId: String
    ): Result<DealDto> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "✅ Approving deal: $dealId by user: $userId")

            val response = api.approveDeal(
                ApproveDealRequest(
                    userId = userId,
                    dealId = dealId
                )
            )

            if (response.success && response.data != null) {
                // Update local cache with approved deal
                val entity = response.data.toEntity()
                dealDao.insertDeal(entity)

                Log.d("Repository", "✅ Deal approved and cache updated: $dealId")
                Result.success(response.data)
            } else {
                Log.e("Repository", "❌ Failed to approve deal: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to approve deal"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error approving deal", e)
            Result.failure(e)
        }
    }

    /**
     * Soft delete a deal (moderator/admin can delete any, users can delete own)
     * @param dealId Deal ID to delete
     * @param userId User ID
     * @param reason Deletion reason
     * @return Result with success/error
     */
    suspend fun deleteDeal(
        dealId: String,
        userId: String,
        reason: String? = null
    ): Result<DealDto> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🗑️ Deleting deal: $dealId by user: $userId")

            val response = api.deleteDeal(
                DeleteDealRequest(
                    userId = userId,
                    dealId = dealId,
                    reason = reason
                )
            )

            if (response.success && response.data != null) {
                // Update local cache with deleted deal (has deleted_at timestamp)
                val entity = response.data.toEntity()
                dealDao.insertDeal(entity)

                Log.d("Repository", "🗑️ Deal deleted and cache updated: $dealId")
                Result.success(response.data)
            } else {
                Log.e("Repository", "❌ Failed to delete deal: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to delete deal"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error deleting deal", e)
            Result.failure(e)
        }
    }

    /**
     * Reject a pending deal (moderator/admin only)
     * @param dealId Deal ID to reject
     * @param userId User ID (must be moderator or admin)
     * @param reason Rejection reason
     * @return Result with success/error
     */
    suspend fun rejectDeal(
        dealId: String,
        userId: String,
        reason: String? = null
    ): Result<DealDto> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "❌ Rejecting deal: $dealId by user: $userId")

            val response = api.rejectDeal(
                RejectDealRequest(
                    userId = userId,
                    dealId = dealId,
                    reason = reason
                )
            )

            if (response.success && response.data != null) {
                // Update local cache with rejected deal
                val entity = response.data.toEntity()
                dealDao.insertDeal(entity)

                Log.d("Repository", "❌ Deal rejected and cache updated: $dealId")
                Result.success(response.data)
            } else {
                Log.e("Repository", "❌ Failed to reject deal: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to reject deal"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error rejecting deal", e)
            Result.failure(e)
        }
    }

    /**
     * Get approved active deals (non-deleted) from local cache
     * This is a more restrictive filter than getActiveDeals()
     * @return Flow of approved, active, non-deleted deals
     */
    fun getCachedApprovedActiveDeals(): Flow<List<DealEntity>> {
        return dealDao.getApprovedActiveDeals()
    }

    /**
     * Return an archived deal back to feed (admin only)
     * - Un-archives the deal (isArchived = false)
     * - Extends expiry by 10 days from now
     * - Keeps original createdAt intact for accurate age display
     *
     * @param dealId Deal ID to return to feed
     * @param userId Admin user ID
     * @return Result with success/error
     */

    suspend fun returnDealToFeed(
        dealId: String,
        userId: String,
        expiresInDays: Int = 10
    ): Result<DealDto> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🔄 Returning deal to feed: $dealId by admin: $userId")
            Log.d("Repository", "   Expires in: $expiresInDays days")

            val response = api.returnDealToFeed(
                ReturnToFeedRequest(
                    userId = userId,
                    dealId = dealId,
                    expiresInDays = expiresInDays
                )
            )

            if (response.success && response.data != null) {
                // Update local cache with un-archived deal
                val entity = response.data.toEntity()
                dealDao.insertDeal(entity)

                Log.d("Repository", "✅ Deal returned to feed and cache updated: $dealId")
                Result.success(response.data)
            } else {
                Log.e("Repository", "❌ Failed to return deal to feed: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to return deal to feed"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error returning deal to feed", e)
            Result.failure(e)
        }
    }

    /**
     * Permanently delete a deal and its image from database (admin only)
     * - Deletes the deal record from database
     * - Deletes the image file from Supabase storage
     * - Cannot be undone
     *
     * @param dealId Deal ID to permanently delete
     * @param userId Admin user ID
     * @return Result with success/error
     */

    suspend fun permanentDeleteDeal(
        dealId: String,
        userId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🗑️ Permanently deleting deal: $dealId by admin: $userId")

            val response = api.permanentDeleteDeal(
                PermanentDeleteDealRequest(
                    userId = userId,
                    dealId = dealId
                )
            )

            if (response.success) {
                // Remove from local cache
                dealDao.deleteDealById(dealId)

                Log.d("Repository", "✅ Deal permanently deleted and removed from cache: $dealId")
                Result.success(Unit)
            } else {
                Log.e("Repository", "❌ Failed to permanently delete deal: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to permanently delete deal"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error permanently deleting deal", e)
            Result.failure(e)
        }
    }

    // ========================================
    // ✅ NEW: REPORTS MANAGEMENT (2025-11-22)
    // Methods for viewing and managing user-submitted reports
    // ========================================

    /**
     * Get all submitted reports with details from API (moderator/admin only)
     * Returns reports with joined deal and user information
     *
     * @param userId User ID (must be moderator or admin)
     * @param page Page number
     * @param limit Items per page
     * @return Result with list of reports or error
     */
    suspend fun getReports(
        userId: String,
        page: Int = 1,
        limit: Int = 20
    ): Result<List<ReportWithDetailsDto>> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🚨 Fetching reports (page: $page, user: $userId)...")

            val response = api.getReports(
                GetReportsRequest(
                    userId = userId,
                    page = page,
                    limit = limit
                )
            )

            if (response.success == true && response.data != null) {
                Log.d("Repository", "✅ Fetched ${response.data.size} reports")
                Result.success(response.data)
            } else {
                Log.e("Repository", "❌ Failed to fetch reports: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to fetch reports"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error fetching reports", e)
            Result.failure(e)
        }
    }

    /**
     * Dismiss a report without taking action (moderator/admin only)
     * Marks the report as reviewed but no action needed
     *
     * @param reportId Report ID to dismiss
     * @param userId User ID (must be moderator or admin)
     * @param reason Optional reason for dismissing
     * @return Result with success/error
     */
    suspend fun dismissReport(
        reportId: String,
        userId: String,
        reason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "❌ Dismissing report: $reportId by user: $userId")

            val response = api.dismissReport(
                DismissReportRequest(
                    reportId = reportId,
                    userId = userId,
                    reason = reason
                )
            )

            if (response.success) {
                Log.d("Repository", "✅ Report dismissed: $reportId")
                Result.success(Unit)
            } else {
                Log.e("Repository", "❌ Failed to dismiss report: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to dismiss report"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error dismissing report", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve a report with action (moderator/admin only)
     * Takes action on a report (e.g., delete deal, warn user)
     *
     * @param reportId Report ID to resolve
     * @param userId User ID (must be moderator or admin)
     * @param action Action to take (e.g., "delete_deal", "warn_user")
     * @param reason Optional reason for action
     * @return Result with success/error
     */
    suspend fun resolveReport(
        reportId: String,
        userId: String,
        action: String,
        reason: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "⚡ Resolving report: $reportId with action: $action by user: $userId")

            val response = api.resolveReport(
                ResolveReportRequest(
                    reportId = reportId,
                    userId = userId,
                    action = action,
                    reason = reason
                )
            )

            if (response.success) {
                Log.d("Repository", "✅ Report resolved: $reportId")
                Result.success(Unit)
            } else {
                Log.e("Repository", "❌ Failed to resolve report: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to resolve report"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error resolving report", e)
            Result.failure(e)
        }
    }

    /**
     * Submit user feedback
     *
     * @param deviceId Device ID of the user
     * @param feedbackText Feedback content (max 500 chars)
     * @param userId Optional user ID if authenticated
     * @param email Optional email if user wants a response
     * @return Result with feedback ID on success
     */
    suspend fun submitFeedback(
        deviceId: String,
        feedbackText: String,
        userId: String? = null,
        email: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "💬 Submitting feedback from device: ${deviceId.take(8)}...")

            val response = api.submitFeedback(
                SubmitFeedbackRequest(
                    deviceId = deviceId,
                    feedbackText = feedbackText,
                    userId = userId,
                    email = email
                )
            )

            if (response.success == true && response.data != null) {
                Log.d("Repository", "✅ Feedback submitted: ${response.data.id}")
                Result.success(response.data.id)
            } else {
                Log.e("Repository", "❌ Failed to submit feedback: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to submit feedback"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error submitting feedback", e)
            Result.failure(e)
        }
    }
}
/** A feed page that arrived after the user switched filters; it was not written. */
class StaleFeedPageException : Exception("Stale feed page")

/** Result of [DealRepository.getDealsByUser]: pagination info, plus stats when the server includes them (page 1). */
data class UserDealsPage(
    val pagination: PaginationMeta?,
    val stats: UserDealStatsDto?
)
