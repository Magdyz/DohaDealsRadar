package qa.deals.doha.feature.details

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.doha.db.DealEntity
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.repository.UserRepository  // ✅ NEW: For user email lookup

/**
 * ✅ NEW: Pending vote waiting for authentication
 */
data class PendingVote(
    val voteType: String  // "hot" or "cold"
)

/**
 * ✅ UPDATED: UI state for Details screen (with vote authentication)
 */
data class DetailsUiState(
    val deal: DealEntity? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val voting: Boolean = false,
    val voteError: String? = null,
    val hasVoted: Boolean = false,
    val userVoteType: String? = null,
    val isArchived: Boolean = false,
    // ✅ NEW: Vote authentication dialog state
    val showVoteAuthDialog: Boolean = false,
    val pendingVote: PendingVote? = null
)

/**
 * ✅ UPDATED: ViewModel for the Details screen
 * Manages deal data, voting, and share functionality.
 * Migration: Added user-authenticated voting support
 */
class DetailsViewModel(
    private val dealId: String,
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context),
    private val userRepo: UserRepository = UserRepository()  // ✅ NEW: No context parameter
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        Log.d("Details", "📱 DetailsViewModel created for dealId: $dealId")
        loadDeal()
    }

    /**
     * ✅ UPDATED: Load deal from local cache and check vote status
     * Migration: Uses user-based vote tracking
     */
    private fun loadDeal() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(loading = true, error = null)

                repo.getCachedDeals().collect { deals ->
                    val deal = deals.find { it.id == dealId }

                    if (deal != null) {
                        // ✅ UPDATED: Check if user has voted (user-based, not device-based)
                        val userId = deviceIdManager.getUserId()
                        val hasVoted = if (userId != null) {
                            deviceIdManager.hasUserVoted(userId, dealId)
                        } else {
                            // Fallback: Check legacy device votes for backward compatibility
                            deviceIdManager.hasVoted(dealId)
                        }

                        val voteType = if (userId != null) {
                            deviceIdManager.getUserVoteType(userId, dealId)
                        } else {
                            deviceIdManager.getVoteType(dealId)
                        }

                        _uiState.value = _uiState.value.copy(
                            deal = deal,
                            loading = false,
                            error = null,
                            hasVoted = hasVoted,
                            userVoteType = voteType,
                            isArchived = deal.isArchived
                        )
                        Log.d("Details", "✅ Deal loaded: ${deal.title}, voted: $hasVoted")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            deal = null,
                            loading = false,
                            error = "Deal not found"
                        )
                        Log.e("Details", "❌ Deal not found: $dealId")
                    }
                }
            } catch (e: Exception) {
                Log.e("Details", "💥 Error loading deal", e)
                _uiState.value = _uiState.value.copy(
                    deal = null,
                    loading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * ✅ UPDATED: Cast a vote with user authentication + optimistic UI
     *
     * Flow (Instagram/YouTube 2025 pattern):
     * 1. Check authentication → Show dialog if anonymous
     * 2. Check duplicate vote
     * 3. Optimistic UI update → Instant feedback
     * 4. API call → Server validation
     * 5. Success → Clear optimistic state
     * 6. Failure → Revert changes
     */
    fun castVote(voteType: String) {
        viewModelScope.launch {
            try {
                // ========================================
                // STEP 1: Authentication Check
                // ========================================
                val userId = deviceIdManager.getUserId()
                val userEmail = if (userId != null) {
                    userRepo.getCachedUser(userId)?.email
                } else null

                // ✅ GATE: Show auth dialog for anonymous users
                if (userId == null) {
                    Log.d("Details", "⚠️ Anonymous user tried to vote - showing auth dialog")
                    _uiState.value = _uiState.value.copy(
                        showVoteAuthDialog = true,
                        pendingVote = PendingVote(voteType)
                    )
                    return@launch
                }

                // ========================================
                // STEP 2: Duplicate Vote Check
                // ========================================
                if (deviceIdManager.hasUserVoted(userId, dealId)) {
                    Log.d("Details", "⚠️ User already voted on deal $dealId")
                    _uiState.value = _uiState.value.copy(
                        voteError = "You have already voted on this deal"
                    )
                    return@launch
                }

                // ========================================
                // STEP 3: Optimistic UI Update
                // ========================================
                Log.d("Details", "🗳️ Casting $voteType vote (optimistic update)...")

                val currentDeal = _uiState.value.deal ?: return@launch
                val optimisticDeal = currentDeal.copy(
                    hotCount = (currentDeal.hotCount ?: 0) + if (voteType == "hot") 1 else 0,
                    coldCount = (currentDeal.coldCount ?: 0) + if (voteType == "cold") 1 else 0
                )

                _uiState.value = _uiState.value.copy(
                    deal = optimisticDeal,
                    voting = true,
                    voteError = null,
                    hasVoted = true,
                    userVoteType = voteType
                )

                // Record vote locally BEFORE API call
                deviceIdManager.recordUserVote(userId, dealId, voteType)

                // ========================================
                // STEP 4: API Call
                // ========================================
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = voteType,
                    userId = userId,
                    userEmail = userEmail,
                    deviceId = deviceIdManager.getDeviceId()  // Analytics only
                )

                // ========================================
                // STEP 5: Handle Response
                // ========================================
                if (result.success == true) {
                    Log.d("Details", "✅ Vote recorded successfully")
                    // Repository already updated cache with real data
                    _uiState.value = _uiState.value.copy(voting = false)

                } else {
                    // ❌ FAILURE: Revert optimistic update
                    Log.e("Details", "❌ Vote failed: ${result.error}")

                    _uiState.value = _uiState.value.copy(
                        deal = currentDeal,
                        voting = false,
                        voteError = result.error ?: "Failed to record vote",
                        hasVoted = false,
                        userVoteType = null
                    )

                    // Clear local vote record
                    deviceIdManager.clearUserVote(userId, dealId)
                }

            } catch (e: Exception) {
                Log.e("Details", "❌ Vote exception: ${e.message}", e)

                // Revert on exception
                val userId = deviceIdManager.getUserId()
                val currentDeal = _uiState.value.deal

                if (userId != null) {
                    deviceIdManager.clearUserVote(userId, dealId)
                }

                _uiState.value = _uiState.value.copy(
                    deal = currentDeal,
                    voting = false,
                    voteError = e.message ?: "Network error",
                    hasVoted = false,
                    userVoteType = null
                )
            }
        }
    }

    // ========================================
    // ✅ NEW: Dialog Management Methods
    // ========================================

    /**
     * Dismiss vote auth dialog
     */
    fun dismissVoteAuthDialog() {
        _uiState.value = _uiState.value.copy(
            showVoteAuthDialog = false,
            pendingVote = null
        )
    }

    /**
     * Retry pending vote after user authenticates
     */
    fun retryPendingVote() {
        val pending = _uiState.value.pendingVote ?: return
        _uiState.value = _uiState.value.copy(
            showVoteAuthDialog = false,
            pendingVote = null
        )

        castVote(pending.voteType)
    }

    /**
     * Generate share text for this deal
     */
    fun getShareText(): String {
        val deal = _uiState.value.deal ?: return ""
        return """
            🔥 Check out this deal!
            
            ${deal.title}
            ${deal.link}
            
            Shared from Doha Deals Radar
        """.trimIndent()
    }
}