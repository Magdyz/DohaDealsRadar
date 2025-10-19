package qa.deals.doha.feature.feed

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.doha.db.DealEntity
import qa.deals.domain.DealCategory  // ✅ CORRECTED: From actual package
import qa.deals.doha.repository.DealRepository

/**
 * ========================================
 * ✨ UI STATE CONTAINER FOR FEED SCREEN
 * ========================================
 *
 * Updated: 2025-10-19 19:58:11 UTC by @Magdyz
 *
 * Data class that holds all UI-related state for the feed screen.
 */
data class FeedUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val votedDeals: Map<String, String> = emptyMap(),
    val optimisticCounts: Map<String, Pair<Int, Int>> = emptyMap()
)

/**
 * ========================================
 * ✨ FEED VIEW MODEL - 2025 UPDATED
 * ========================================
 *
 * Updated: 2025-10-19 19:58:11 UTC by @Magdyz
 */
class FeedViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context)
) : ViewModel() {

    // ✅ PRESERVED: Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ✨ NEW: Category Filter State (FIXED - explicit type)
    private val _selectedCategory = MutableStateFlow<DealCategory?>(null)
    val selectedCategory: StateFlow<DealCategory?> = _selectedCategory.asStateFlow()

    // ✅ PRESERVED + ✨ UPDATED: Deals StateFlow with Search + Category Filtering
    val deals: StateFlow<List<DealEntity>> = combine(
        repo.getCachedDeals(),
        _searchQuery,
        _selectedCategory
    ) { allDeals, query, category ->
        var filteredDeals = allDeals

        // Apply search filter
        if (query.isNotEmpty()) {
            val searchLower = query.lowercase().trim()
            filteredDeals = filteredDeals.filter { deal ->
                deal.title.lowercase().contains(searchLower) ||
                        deal.description?.lowercase()?.contains(searchLower) == true
            }
        }

        // Apply category filter
        if (category != null) {
            filteredDeals = filteredDeals.filter { deal ->
                deal.category == category.id
            }
        }

        // Sort by hot votes (highest to lowest)
        filteredDeals.sortedByDescending { it.hotCount ?: 0 }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ✅ PRESERVED: UI State
    var uiState by mutableStateOf(FeedUiState())
        private set

    // ✅ PRESERVED: Initialization
    init {
        Log.d("FeedViewModel", "📱 Initializing with DeviceIdManager")
        refreshDeals()
        loadVoteStatus()
    }

    // ✅ PRESERVED: Load Vote Status
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

    // ✅ PRESERVED: Search Query Update
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        Log.d("FeedViewModel", "🔍 Search query updated: $query")
    }

    // ✨ NEW: Category Filter Update
    fun filterByCategory(category: DealCategory?) {
        Log.d("FeedViewModel", "🏷️ Filtering by category: ${category?.displayName ?: "All"}")
        _selectedCategory.value = category
        refreshDeals()
    }

    // ✅ PRESERVED: Refresh Deals from Network
    fun refreshDeals() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                Log.d("Feed", "🔄 Refreshing deals from network...")
                repo.refreshDeals()
                uiState = uiState.copy(loading = false)

                val categoryInfo = _selectedCategory.value?.let {
                    " (category: ${it.displayName})"
                } ?: ""
                Log.d("Feed", "✅ Deals refreshed successfully$categoryInfo")

            } catch (t: Throwable) {
                Log.e("Feed", "💥 Failed to refresh deals", t)
                uiState = uiState.copy(loading = false, error = t.message)
            }
        }
    }

    // ✅ PRESERVED: Vote Status Check
    fun hasVoted(dealId: String): Boolean {
        return uiState.votedDeals.containsKey(dealId)
    }

    // ✅ PRESERVED: Get Vote Type
    fun getVoteType(dealId: String): String? {
        return uiState.votedDeals[dealId]
    }

    // ✅ PRESERVED: Get Optimistic Hot Count
    fun getOptimisticHotCount(dealId: String): Int? {
        return uiState.optimisticCounts[dealId]?.first
    }

    // ✅ PRESERVED: Get Optimistic Cold Count
    fun getOptimisticColdCount(dealId: String): Int? {
        return uiState.optimisticCounts[dealId]?.second
    }

    // ✅ PRESERVED: Vote HOT with Optimistic Update
    fun voteHot(dealId: String) {
        if (hasVoted(dealId)) {
            Log.d("FeedVote", "⚠️ User already voted on deal: $dealId")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("FeedVote", "🔥 Casting HOT vote for deal: $dealId")

                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val newHotCount = (currentDeal.hotCount ?: 0) + 1
                    val currentColdCount = currentDeal.coldCount ?: 0

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(newHotCount, currentColdCount)

                    uiState = uiState.copy(optimisticCounts = updatedCounts)
                    Log.d("FeedVote", "⚡ Optimistic update: hot count = $newHotCount")
                }

                deviceIdManager.recordVote(dealId, "hot")
                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                updatedVotedDeals[dealId] = "hot"
                uiState = uiState.copy(votedDeals = updatedVotedDeals)

                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "hot",
                    deviceId = deviceIdManager.getDeviceId()
                )

                if (result.success == true) {
                    Log.d("FeedVote", "✅ HOT vote recorded successfully")
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

    // ✅ PRESERVED: Vote COLD with Optimistic Update
    fun voteCold(dealId: String) {
        if (hasVoted(dealId)) {
            Log.d("FeedVote", "⚠️ User already voted on deal: $dealId")
            return
        }

        viewModelScope.launch {
            try {
                Log.d("FeedVote", "❄️ Casting COLD vote for deal: $dealId")

                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val currentHotCount = currentDeal.hotCount ?: 0
                    val newColdCount = (currentDeal.coldCount ?: 0) + 1

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(currentHotCount, newColdCount)

                    uiState = uiState.copy(optimisticCounts = updatedCounts)
                    Log.d("FeedVote", "⚡ Optimistic update: cold count = $newColdCount")
                }

                deviceIdManager.recordVote(dealId, "cold")
                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                updatedVotedDeals[dealId] = "cold"
                uiState = uiState.copy(votedDeals = updatedVotedDeals)

                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "cold",
                    deviceId = deviceIdManager.getDeviceId()
                )

                if (result.success == true) {
                    Log.d("FeedVote", "✅ COLD vote recorded successfully")
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