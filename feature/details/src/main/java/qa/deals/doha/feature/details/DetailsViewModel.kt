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

/**
 * UI state for Details screen
 * Updated: 2025-11-19 - Added login dialog state
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
    // ✨ NEW: Vote authentication dialog state
    val showLoginDialog: Boolean = false,
    val pendingVoteType: String? = null
)

/**
 * ViewModel for the Details screen.
 * Manages deal data, voting, and share functionality.
 */
class DetailsViewModel(
    private val dealId: String,
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    // ✅ Race condition prevention: Track in-flight vote requests
    private val votingInProgress = mutableSetOf<String>()

    init {
        Log.d("Details", "📱 DetailsViewModel created for dealId: $dealId")
        loadDeal()
    }

    /**
     * Load deal from local cache and check vote status
     */
    private fun loadDeal() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(loading = true, error = null)

                repo.getCachedDeals().collect { deals ->
                    val deal = deals.find { it.id == dealId }

                    if (deal != null) {
                        // Check if user has already voted
                        val hasVoted = deviceIdManager.hasVoted(dealId)
                        val voteType = deviceIdManager.getVoteType(dealId)

                        _uiState.value = DetailsUiState(
                            deal = deal,
                            loading = false,
                            error = null,
                            hasVoted = hasVoted,
                            userVoteType = voteType,
                            isArchived = deal.isArchived
                        )
                        Log.d("Details", "✅ Deal loaded: ${deal.title}, voted: $hasVoted")
                    } else {
                        _uiState.value = DetailsUiState(
                            deal = null,
                            loading = false,
                            error = "Deal not found"
                        )
                        Log.e("Details", "❌ Deal not found: $dealId")
                    }
                }
            } catch (e: Exception) {
                Log.e("Details", "💥 Error loading deal", e)
                _uiState.value = DetailsUiState(
                    deal = null,
                    loading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Cast a vote on this deal with Single Source of Truth pattern (YouTube/Instagram 2025)
     * Updated: 2025-11-19 - Refactored to use Repository optimistic updates
     *
     * How it works (Instagram pattern):
     * 1. Repository updates Room DB immediately (instant UI)
     * 2. Room Flow emits → DetailsViewModel observes → UI updates automatically
     * 3. Repository calls API in background
     * 4. On success: Repository updates Room with server data → UI auto-updates
     * 5. On error: Repository reverts Room to original → UI auto-reverts
     *
     * Benefits:
     * - Instant feedback (no lag)
     * - Cross-screen sync (FeedScreen & DetailsScreen always show same counts)
     * - No false information (single source of truth)
     * - No manual state management needed
     */
    fun castVote(voteType: String) {
        // ✅ Check authentication first
        val userId = deviceIdManager.getUserId()
        if (userId == null) {
            _uiState.value = _uiState.value.copy(
                showLoginDialog = true,
                pendingVoteType = voteType
            )
            Log.d("Details", "⚠️ User not authenticated, showing login dialog")
            return
        }

        // Prevent voting if already voted
        if (_uiState.value.hasVoted) {
            Log.d("Details", "⚠️ User already voted, ignoring")
            _uiState.value = _uiState.value.copy(
                voteError = "You have already voted on this deal"
            )
            return
        }

        // ✅ Prevent race conditions (concurrent vote requests)
        synchronized(votingInProgress) {
            if (votingInProgress.contains(dealId)) {
                Log.d("Details", "⚠️ Vote already in progress for deal: $dealId, ignoring")
                return
            }
            votingInProgress.add(dealId)
        }

        viewModelScope.launch {
            try {
                Log.d("Details", "🗳️ Casting $voteType vote...")

                // ✅ Record vote locally for UI state (voted/not voted indicator)
                deviceIdManager.recordVote(dealId, voteType)

                // Update local UI state to show voting in progress
                _uiState.value = _uiState.value.copy(
                    voting = true,
                    voteError = null,
                    hasVoted = true,
                    userVoteType = voteType
                )

                // ✅ Repository handles optimistic update in Room DB + API call
                // UI will update automatically via Room Flow observation
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = voteType,
                    userId = userId
                )

                if (result.success == true) {
                    Log.d("Details", "✅ Vote recorded successfully")
                    // Repository already updated Room DB with server data
                    // Flow will emit new data, UI will update automatically
                    _uiState.value = _uiState.value.copy(
                        voting = false
                    )
                } else {
                    Log.e("Details", "❌ Vote failed: ${result.error}")
                    // Repository already reverted Room DB to original state
                    _uiState.value = _uiState.value.copy(
                        voting = false,
                        voteError = result.error ?: "Failed to record vote",
                        hasVoted = false,
                        userVoteType = null
                    )
                }
            } catch (e: Exception) {
                Log.e("Details", "💥 Error casting vote", e)
                // Repository already reverted Room DB on exception
                _uiState.value = _uiState.value.copy(
                    voting = false,
                    voteError = e.message ?: "Network error",
                    hasVoted = false,
                    userVoteType = null
                )
            } finally {
                synchronized(votingInProgress) {
                    votingInProgress.remove(dealId)
                }
                Log.d("Details", "🧹 Cleared vote lock for deal: $dealId")
            }
        }
    }

    /**
     * Dismiss login dialog
     * Updated: 2025-11-19
     */
    fun dismissLoginDialog() {
        _uiState.value = _uiState.value.copy(
            showLoginDialog = false,
            pendingVoteType = null
        )
        Log.d("Details", "Login dialog dismissed")
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