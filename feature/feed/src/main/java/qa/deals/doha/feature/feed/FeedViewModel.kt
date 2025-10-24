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
import qa.deals.domain.DealCategory
import qa.deals.doha.network.PaginationMeta
import qa.deals.doha.repository.DealRepository

/**
 * ========================================
 * ✅ UPDATED: UI STATE WITH PAGINATION
 * ========================================
 *
 * Updated: 2025-10-23
 * - Added pagination support
 * - All existing state preserved
 */
data class FeedUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val votedDeals: Map<String, String> = emptyMap(),
    val optimisticCounts: Map<String, Pair<Int, Int>> = emptyMap(),
    // ✅ NEW: Pagination state
    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false
)

/**
 * ========================================
 * ✅ UPDATED: FEED VIEW MODEL WITH PAGINATION
 * ========================================
 *
 * Updated: 2025-10-23
 * - Lazy loading support
 * - Modern 2025 pagination pattern
 * - All existing features preserved
 */
class FeedViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context)
) : ViewModel() {

    // ✅ PRESERVED: Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ✅ PRESERVED: Category Filter State
    private val _selectedCategory = MutableStateFlow<DealCategory?>(null)
    val selectedCategory: StateFlow<DealCategory?> = _selectedCategory.asStateFlow()

    // ✅ PRESERVED + UPDATED: Deals StateFlow with Search + Category Filtering
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

    // ✅ PRESERVED + UPDATED: UI State
    var uiState by mutableStateOf(FeedUiState())
        private set

    // ✅ PRESERVED: Initialization
    init {
        Log.d("FeedViewModel", "🚀 Initializing with pagination support")
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
            }
        }
    }

    // ✅ PRESERVED: Search Query Update
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // ✅ PRESERVED: Category Filter Update
    fun filterByCategory(category: DealCategory?) {
        Log.d("FeedViewModel", "🏷️ Filtering by category: ${category?.displayName ?: "All"}")
        _selectedCategory.value = category
        // Reset pagination when filter changes
        uiState = uiState.copy(currentPage = 1, hasMorePages = true)
        refreshDeals()
    }

    // ✅ UPDATED: Refresh Deals from Network (Pull-to-Refresh)
    fun refreshDeals() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null, currentPage = 1)
            try {
                Log.d("Feed", "🔄 Refreshing deals (page 1)...")
                val result = repo.refreshDeals(page = 1, append = false)

                result.onSuccess { pagination ->
                    uiState = uiState.copy(
                        loading = false,
                        currentPage = 1,
                        hasMorePages = pagination?.hasMore ?: false
                    )
                    Log.d("Feed", "✅ Refreshed ${pagination?.limit ?: 0} deals (hasMore: ${pagination?.hasMore})")
                }.onFailure { error ->
                    Log.e("Feed", "💥 Failed to refresh deals", error)
                    uiState = uiState.copy(loading = false, error = error.message)
                }
            } catch (t: Throwable) {
                Log.e("Feed", "💥 Failed to refresh deals", t)
                uiState = uiState.copy(loading = false, error = t.message)
            }
        }
    }

    // ========================================
    // ✅ NEW: Load More Deals (Lazy Loading)
    // Called when user scrolls near bottom
    // ========================================
    fun loadMoreDeals() {
        // Don't load if already loading, no more pages, or initial load in progress
        if (uiState.isLoadingMore || !uiState.hasMorePages || uiState.loading) {
            Log.d("Feed", "⏸️ Skipping loadMore (loading: ${uiState.isLoadingMore}, hasMore: ${uiState.hasMorePages})")
            return
        }

        viewModelScope.launch {
            val nextPage = uiState.currentPage + 1
            uiState = uiState.copy(isLoadingMore = true)

            try {
                Log.d("Feed", "📄 Loading more deals (page $nextPage)...")
                val result = repo.refreshDeals(page = nextPage, append = true)

                result.onSuccess { pagination ->
                    uiState = uiState.copy(
                        isLoadingMore = false,
                        currentPage = nextPage,
                        hasMorePages = pagination?.hasMore ?: false
                    )
                    Log.d("Feed", "✅ Loaded page $nextPage (hasMore: ${pagination?.hasMore})")
                }.onFailure { error ->
                    Log.e("Feed", "💥 Failed to load more deals", error)
                    uiState = uiState.copy(isLoadingMore = false)
                }
            } catch (t: Throwable) {
                Log.e("Feed", "💥 Failed to load more deals", t)
                uiState = uiState.copy(isLoadingMore = false)
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
            return
        }

        viewModelScope.launch {
            try {
                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val newHotCount = (currentDeal.hotCount ?: 0) + 1
                    val currentColdCount = currentDeal.coldCount ?: 0

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(newHotCount, currentColdCount)

                    uiState = uiState.copy(optimisticCounts = updatedCounts)
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
            return
        }

        viewModelScope.launch {
            try {
                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val currentHotCount = currentDeal.hotCount ?: 0
                    val newColdCount = (currentDeal.coldCount ?: 0) + 1

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(currentHotCount, newColdCount)

                    uiState = uiState.copy(optimisticCounts = updatedCounts)
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