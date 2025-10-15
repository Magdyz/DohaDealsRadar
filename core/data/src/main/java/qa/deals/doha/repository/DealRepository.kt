package qa.deals.doha.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import qa.deals.doha.db.DatabaseModule
import qa.deals.doha.db.DealDao
import qa.deals.doha.db.DealEntity
import qa.deals.doha.network.*
import qa.deals.doha.util.AppContext
import java.io.File

/**
 * Repository for managing deals.
 * Implements Stale-While-Revalidate (SWR) pattern.
 */
class DealRepository {

    // Access database and API through their modules
    private val dealDao: DealDao by lazy {
        DatabaseModule.provideDealDao(AppContext.appContext)
    }
    private val api: SupabaseApiService = NetworkModule.api

    /**
     * Get cached deals as a Flow (reactive updates)
     */
    fun getCachedDeals(): Flow<List<DealEntity>> {
        return dealDao.getAllDeals()
    }

    /**
     * Refresh deals from network and update cache
     */
    suspend fun refreshDeals(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d("Repository", "🔄 Fetching deals from network...")
            val response = api.getDeals()

            if (response.success == true && response.data != null) {
                val entities = response.data.map { it.toEntity() }
                dealDao.insertAll(entities)
                Log.d("Repository", "✅ Cached ${entities.size} deals")
                Result.success(Unit)
            } else {
                Log.e("Repository", "❌ API returned success=false or null data")
                Result.failure(Exception(response.error ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Log.e("Repository", "💥 Error refreshing deals", e)
            Result.failure(e)
        }
    }

    /**
     * Submit a new deal
     */
    suspend fun submitDeal(
        title: String,
        description: String? = null,
        link: String?,
        imageUrl: String,
        location: String? = null
    ): ApiEnvelope<List<DealDto>> = withContext(Dispatchers.IO) {
        val request = SubmitDealRequest(
            title = title,
            description = description,
            link = link,
            image_url = imageUrl,
            location = location
        )
        api.submitDeal(request)
    }

    // ========================================
    // ✅ NEW FUNCTION: Add after submitDeal()
    // ========================================
    /**
     * Update deal image URL (for two-stage upload)
     * Used to upgrade thumbnail to full resolution image
     */
    suspend fun updateDealImage(
        dealId: String,
        newImageUrl: String
    ): ApiEnvelope<DealDto> = withContext(Dispatchers.IO) {
        Log.d("Repository", "🖼️ Updating image for deal $dealId")

        val request = UpdateImageRequest(
            deal_id = dealId,
            image_url = newImageUrl
        )

        api.updateDealImage(request)
    }
    // ========================================
    // ✅ END OF NEW FUNCTION
    // ========================================

    /**
     * Cast a vote on a deal
     */
    suspend fun castVote(
        dealId: String,
        voteType: String,
        deviceId: String
    ): ApiEnvelope<DealDto> = withContext(Dispatchers.IO) {
        Log.d("Repository", "🗳️ Casting $voteType vote for deal $dealId")

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
            Log.d("Repository", "✅ Vote cast successfully, cache updated")
        }

        response
    }

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

    /**
     * Upload image to Supabase Storage
     */
    suspend fun uploadImage(file: File): String = withContext(Dispatchers.IO) {
        StorageUploader.uploadImage(file)
    }
}