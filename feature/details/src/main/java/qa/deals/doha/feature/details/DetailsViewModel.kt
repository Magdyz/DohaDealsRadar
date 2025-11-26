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

    // ========================================
    // ✅ NEW: Track if we've tried to fetch from network
    // Prevents infinite refresh loop
    // ========================================
    private var hasTriedNetworkFetch = false

    /**
     * ✅ UPDATED: Load deal from local cache and check vote status
     * Migration: Uses user-based vote tracking
     * Deep Link Fix: Fetches from network if deal not in cache (2025 pattern)
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

                        // ✅ FIX: During voting, preserve optimistic counts while updating other fields
                        val finalDeal = if (_uiState.value.voting && _uiState.value.deal != null) {
                            // Keep optimistic counts from current state, use fresh data for everything else
                            deal.copy(
                                hotCount = _uiState.value.deal!!.hotCount,
                                coldCount = _uiState.value.deal!!.coldCount
                            )
                        } else {
                            // Not voting, use fresh data from database
                            deal
                        }

                        _uiState.value = _uiState.value.copy(
                            deal = finalDeal,
                            loading = false,
                            error = null,
                            hasVoted = hasVoted,
                            userVoteType = voteType,
                            isArchived = deal.isArchived
                        )
                        Log.d("Details", "✅ Deal loaded: ${deal.title}, voted: $hasVoted${if (_uiState.value.voting) " (preserving optimistic counts)" else ""}")
                    } else {
                        // ========================================
                        // ✅ NEW: 2025 Deep Link Pattern - Fetch from network if not in cache
                        // Why: When user taps notification, local DB might be empty
                        // Solution: Trigger network refresh, collect will re-run when data arrives
                        // ========================================
                        if (!hasTriedNetworkFetch) {
                            hasTriedNetworkFetch = true
                            Log.d("Details", "📡 Deal not in cache, fetching from network...")

                            // Keep loading state true while fetching
                            _uiState.value = _uiState.value.copy(loading = true, error = null)

                            // ✅ FIX: Fetch by "newest" to include newly approved deals (they have 0 votes)
                            // Trigger network refresh (this will populate the cache)
                            val result = repo.refreshDeals(page = 1, append = false, sortBy = "newest")

                            result.onFailure { error ->
                                Log.e("Details", "💥 Failed to fetch deal from network", error)
                                _uiState.value = _uiState.value.copy(
                                    deal = null,
                                    loading = false,
                                    error = "Deal not found. ${error.message}"
                                )
                            }
                            // On success, collect will automatically re-run with new data
                        } else {
                            // Already tried network, deal genuinely doesn't exist
                            _uiState.value = _uiState.value.copy(
                                deal = null,
                                loading = false,
                                error = "Deal not found"
                            )
                            Log.e("Details", "❌ Deal not found even after network fetch: $dealId")
                        }
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
                Log.d("DetailsViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("DetailsViewModel", "🗳️ VOTE CAST STARTED - Type: $voteType")
                Log.d("DetailsViewModel", "   DealID: $dealId")
                Log.d("DetailsViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // ========================================
                // STEP 1: Authentication Check
                // ========================================
                val userId = deviceIdManager.getUserId()
                val userEmail = if (userId != null) {
                    userRepo.getCachedUser(userId)?.email
                } else null

                Log.d("DetailsViewModel", "📝 STEP 1: Authentication Check")
                Log.d("DetailsViewModel", "   UserID: ${userId?.take(8) ?: "NULL"}")
                Log.d("DetailsViewModel", "   UserEmail: $userEmail")

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
                // STEP 2: Determine Vote Action (NEW/SWITCH/REMOVE)
                // ✅ UPDATED 2025-11-20: Support vote switching
                // ========================================
                val voteAction = deviceIdManager.getVoteAction(userId, dealId, voteType)
                val actionDescription = deviceIdManager.getVoteActionDescription(userId, dealId, voteType)
                val existingVoteType = deviceIdManager.getUserVoteType(userId, dealId)

                Log.d("DetailsViewModel", "📝 STEP 2: Determine Vote Action")
                Log.d("DetailsViewModel", "   Vote Action: $voteAction")
                Log.d("DetailsViewModel", "   Action Description: $actionDescription")
                Log.d("DetailsViewModel", "   Existing Vote Type: $existingVoteType")

                // ========================================
                // STEP 3: Optimistic UI Update (Based on Action)
                // ✅ UPDATED 2025-11-20: Handle +1/-1 for switches
                // ✅ FIX 2025-11-21: Use optimistic counts as baseline during rapid voting
                // ========================================
                val currentDeal = _uiState.value.deal ?: return@launch
                val currentHotCount = currentDeal.hotCount ?: 0
                val currentColdCount = currentDeal.coldCount ?: 0

                // ✅ FIX: During rapid voting, currentDeal already has optimistic counts
                // (because loadDeal() skips DB updates when voting=true)
                // This prevents race condition where stale DB counts cause negative numbers
                val baselineHotCount = currentHotCount
                val baselineColdCount = currentColdCount

                Log.d("DetailsViewModel", "📝 STEP 3: Optimistic UI Update")
                Log.d("DetailsViewModel", "   Current Deal Hot Count: $currentHotCount")
                Log.d("DetailsViewModel", "   Current Deal Cold Count: $currentColdCount")
                Log.d("DetailsViewModel", "   🎯 Baseline Hot Count: $baselineHotCount (${if (_uiState.value.voting) "optimistic" else "from DB"})")
                Log.d("DetailsViewModel", "   🎯 Baseline Cold Count: $baselineColdCount (${if (_uiState.value.voting) "optimistic" else "from DB"})")

                val optimisticDeal = when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW -> {
                        // New vote: +1 to selected type
                        Log.d("DetailsViewModel", "   Action: NEW - Adding +1 to $voteType")
                        currentDeal.copy(
                            hotCount = currentHotCount + if (voteType == "hot") 1 else 0,
                            coldCount = currentColdCount + if (voteType == "cold") 1 else 0
                        )
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        // Switch vote: -1 from old type, +1 to new type
                        Log.d("DetailsViewModel", "   Action: SWITCH - Moving to $voteType")
                        currentDeal.copy(
                            hotCount = (currentHotCount + if (voteType == "hot") 1 else -1).coerceAtLeast(0),
                            coldCount = (currentColdCount + if (voteType == "cold") 1 else -1).coerceAtLeast(0)
                        )
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        // Remove vote: -1 from current type (with floor at 0)
                        Log.d("DetailsViewModel", "   Action: REMOVE - Removing $voteType vote")
                        currentDeal.copy(
                            hotCount = (currentHotCount - if (voteType == "hot") 1 else 0).coerceAtLeast(0),
                            coldCount = (currentColdCount - if (voteType == "cold") 1 else 0).coerceAtLeast(0)
                        )
                    }
                }

                Log.d("DetailsViewModel", "   ✨ Optimistic Hot Count: ${optimisticDeal.hotCount}")
                Log.d("DetailsViewModel", "   ✨ Optimistic Cold Count: ${optimisticDeal.coldCount}")

                _uiState.value = _uiState.value.copy(
                    deal = optimisticDeal,
                    voting = true,
                    voteError = null,
                    hasVoted = voteAction != qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE,
                    userVoteType = if (voteAction == qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE) null else voteType
                )

                // Update local storage based on action
                when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW,
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        deviceIdManager.recordUserVote(userId, dealId, voteType)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }
                }

                // ========================================
                // STEP 4: API Call
                // ========================================
                Log.d("DetailsViewModel", "📝 STEP 4: API Call")
                Log.d("DetailsViewModel", "   Calling repo.castVote()...")

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
                Log.d("DetailsViewModel", "📝 STEP 5: Handle Response")
                Log.d("DetailsViewModel", "   Result Success: ${result.success}")
                Log.d("DetailsViewModel", "   Result Error: ${result.error}")
                Log.d("DetailsViewModel", "   Result Data: ${result.data}")

                if (result.success == true) {
                    Log.d("DetailsViewModel", "✅ SUCCESS: Vote recorded")
                    Log.d("DetailsViewModel", "   Waiting 300ms for DB to update...")

                    // Wait for database to propagate, then clear optimistic state
                    kotlinx.coroutines.delay(300)

                    // Don't need to update deal - loadDeal() Flow will handle it
                    Log.d("DetailsViewModel", "   Clearing voting state - DB Flow will update counts")
                    _uiState.value = _uiState.value.copy(voting = false)

                } else {
                    // ❌ FAILURE: Revert optimistic update
                    Log.e("DetailsViewModel", "❌ FAILURE: Vote failed")
                    Log.e("DetailsViewModel", "   Error: ${result.error}")
                    Log.e("DetailsViewModel", "   Reverting to previous state...")

                    // Revert local storage to previous state
                    if (existingVoteType != null) {
                        deviceIdManager.recordUserVote(userId, dealId, existingVoteType)
                    } else {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }

                    _uiState.value = _uiState.value.copy(
                        deal = currentDeal,  // Restore original deal data
                        voting = false,
                        voteError = result.error ?: "Failed to record vote",
                        hasVoted = existingVoteType != null,
                        userVoteType = existingVoteType
                    )
                }

            } catch (e: Exception) {
                Log.e("Details", "❌ Vote exception: ${e.message}", e)

                // Revert on exception
                val userId = deviceIdManager.getUserId()
                val currentDeal = _uiState.value.deal
                val existingVoteType = if (userId != null) {
                    deviceIdManager.getUserVoteType(userId, dealId)
                } else null

                // Restore previous vote state in local storage
                if (userId != null) {
                    if (existingVoteType != null) {
                        deviceIdManager.recordUserVote(userId, dealId, existingVoteType)
                    } else {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    deal = currentDeal,
                    voting = false,
                    voteError = e.message ?: "Network error",
                    hasVoted = existingVoteType != null,
                    userVoteType = existingVoteType
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