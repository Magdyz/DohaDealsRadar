package qa.deals.doha.feature.feed

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.db.DealEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import qa.deals.doha.datastore.DeviceIdManager

/**
 * UI state container for Feed screen
 */
data class FeedUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val votedDeals: Map<String, String> = emptyMap(),           // dealId -> voteType
    val optimisticCounts: Map<String, Pair<Int, Int>> = emptyMap() // ✅ NEW: dealId -> (hotCount, coldCount)
)

/**
 * ViewModel that exposes cached deals from Room and refreshes from network.
 * ✅ FIX 2: Added optimistic vote count updates
 */
class FeedViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context)
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val deals: StateFlow<List<DealEntity>> = repo.getCachedDeals()
        .combine(_searchQuery) { allDeals, query ->
            if (query.isEmpty()) {
                allDeals
            } else {
                val searchLower = query.lowercase().trim()
                allDeals.filter { deal ->
                    deal.title.lowercase().contains(searchLower) ||
                            deal.description?.lowercase()?.contains(searchLower) == true
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var uiState by mutableStateOf(FeedUiState())
        private set

    init {
        Log.d("FeedViewModel", "📱 Initializing with DeviceIdManager")
        refreshDeals()
        loadVoteStatus()
    }

    private fun loadVoteStatus() {
        viewModelScope.launch {
            deals.collect { dealList ->
                val votedDeals = mutableMapOf<String, String>()
                dealList.forEach { deal ->
                    if (deviceIdManager.hasVoted(deal.id)) {
                        deviceIdManager.getVoteType(deal.id)?.let { voteType ->
                            votedDeals[deal.id] = voteType
                        }
                    }
                }
                uiState = uiState.copy(votedDeals = votedDeals)
                Log.d("FeedViewModel", "✅ Loaded vote status: ${votedDeals.size} deals voted")
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun refreshDeals() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                Log.d("Feed", "🔄 Refreshing deals from network...")
                repo.refreshDeals()
                uiState = uiState.copy(loading = false)
                Log.d("Feed", "✅ Deals refreshed successfully")
            } catch (t: Throwable) {
                Log.e("Feed", "💥 Failed to refresh deals", t)
                uiState = uiState.copy(loading = false, error = t.message)
            }
        }
    }

    fun hasVoted(dealId: String): Boolean {
        return uiState.votedDeals.containsKey(dealId)
    }

    fun getVoteType(dealId: String): String? {
        return uiState.votedDeals[dealId]
    }

    // ========================================
    // ✅ FIX 2: Get optimistic count for a deal
    // ========================================
    fun getOptimisticHotCount(dealId: String): Int? {
        return uiState.optimisticCounts[dealId]?.first
    }

    fun getOptimisticColdCount(dealId: String): Int? {
        return uiState.optimisticCounts[dealId]?.second
    }

    /**
     * ✅ FIX 2: Vote HOT with optimistic count update
     */
    fun voteHot(dealId: String) {
        if (hasVoted(dealId)) {
            Log.d("FeedVote", "⚠️ User already voted on deal: $dealId")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("FeedVote", "🔥 Casting HOT vote for deal: $dealId")

                // ========================================
                // ✅ FIX 2: OPTIMISTIC UPDATE - Update count immediately
                // ========================================
                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val newHotCount = (currentDeal.hotCount ?: 0) + 1
                    val currentColdCount = currentDeal.coldCount ?: 0

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(newHotCount, currentColdCount)

                    uiState = uiState.copy(optimisticCounts = updatedCounts)
                    Log.d("FeedVote", "⚡ Optimistic update: hot count = $newHotCount")
                }

                // Record vote locally
                deviceIdManager.recordVote(dealId, "hot")
                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                updatedVotedDeals[dealId] = "hot"
                uiState = uiState.copy(votedDeals = updatedVotedDeals)

                // Make API call
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "hot",
                    deviceId = deviceIdManager.getDeviceId()
                )

                if (result.success == true) {
                    Log.d("FeedVote", "✅ HOT vote recorded successfully")
                    // ✅ FIX 2: Clear optimistic count - real count from API will show
                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)
                    uiState = uiState.copy(optimisticCounts = clearedCounts)
                } else {
                    Log.e("FeedVote", "❌ Vote failed: ${result.error}")
                }
            } catch (t: Throwable) {
                Log.e("FeedVote", "💥 Failed to vote hot on deal: $dealId", t)
            }
        }
    }

    /**
     * ✅ FIX 2: Vote COLD with optimistic count update
     */
    fun voteCold(dealId: String) {
        if (hasVoted(dealId)) {
            Log.d("FeedVote", "⚠️ User already voted on deal: $dealId")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("FeedVote", "❄️ Casting COLD vote for deal: $dealId")

                // ========================================
                // ✅ FIX 2: OPTIMISTIC UPDATE - Update count immediately
                // ========================================
                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val currentHotCount = currentDeal.hotCount ?: 0
                    val newColdCount = (currentDeal.coldCount ?: 0) + 1

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(currentHotCount, newColdCount)

                    uiState = uiState.copy(optimisticCounts = updatedCounts)
                    Log.d("FeedVote", "⚡ Optimistic update: cold count = $newColdCount")
                }

                // Record vote locally
                deviceIdManager.recordVote(dealId, "cold")
                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                updatedVotedDeals[dealId] = "cold"
                uiState = uiState.copy(votedDeals = updatedVotedDeals)

                // Make API call
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "cold",
                    deviceId = deviceIdManager.getDeviceId()
                )

                if (result.success == true) {
                    Log.d("FeedVote", "✅ COLD vote recorded successfully")
                    // ✅ FIX 2: Clear optimistic count - real count from API will show
                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)
                    uiState = uiState.copy(optimisticCounts = clearedCounts)
                } else {
                    Log.e("FeedVote", "❌ Vote failed: ${result.error}")
                }
            } catch (t: Throwable) {
                Log.e("FeedVote", "💥 Failed to vote cold on deal: $dealId", t)
            }
        }
    }
}