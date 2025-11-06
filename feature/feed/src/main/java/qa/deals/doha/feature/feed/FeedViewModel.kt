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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.doha.db.DealEntity
import qa.deals.domain.DealCategory
import qa.deals.doha.network.PaginationMeta
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.repository.PreloadRepository
import qa.deals.doha.repository.UserRepository  // ✅ SPRINT 5: NEW IMPORT

/**
 * ========================================
 * ✅ UPDATED: UI STATE WITH PAGINATION + MODERATOR
 * ========================================
 *
 * Updated: Sprint 5
 * - Added moderator button visibility
 */
data class FeedUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val votedDeals: Map<String, String> = emptyMap(),
    val optimisticCounts: Map<String, Pair<Int, Int>> = emptyMap(),
    // ✅ PRESERVED: Pagination state
    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false,
    // ✅ SPRINT 5: Moderator UI state
    val showModeratorButton: Boolean = false
)

/**
 * ========================================
 * ✅ UPDATED: FEED VIEW MODEL WITH MODERATOR SUPPORT
 * ========================================
 *
 * Updated: Sprint 5
 * - Moderator detection and UI control
 */
class FeedViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context),
    private val preloadRepo: PreloadRepository = PreloadRepository.getInstance(),
    private val userRepo: UserRepository = UserRepository()  // ✅ SPRINT 5: NEW DEPENDENCY
) : ViewModel() {

    // ✅ PRESERVED: Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ✅ PRESERVED: Category Filter State
    private val _selectedCategory = MutableStateFlow<DealCategory?>(null)
    val selectedCategory: StateFlow<DealCategory?> = _selectedCategory.asStateFlow()

    // ✅ SPRINT 5: Moderator status detection
    val isModerator: StateFlow<Boolean> = deviceIdManager.userIdFlow
        .map { userId ->
            if (userId != null) {
                userRepo.isModerator(userId)
            } else {
                false
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // ✅ PRESERVED + UPDATED: Deals StateFlow with Search + Category Filtering
    val deals: StateFlow<List<DealEntity>> = combine(
        repo.getCachedActiveDeals(),
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

    // ✅ PRESERVED + UPDATED: Initialization
    init {
        Log.d("FeedViewModel", "🚀 Initializing with pagination and moderator support")
        refreshDeals()
        loadVoteStatus()

        // ✅ SPRINT 5: Monitor moderator status and update UI
        viewModelScope.launch {
            isModerator.collect { isMod ->
                uiState = uiState.copy(showModeratorButton = isMod)
                Log.d("FeedViewModel", "🛡️ Moderator status: $isMod")
            }
        }
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
    }

    // ✅ UPDATED: Refresh Deals from Network (Pull-to-Refresh)
    fun refreshDeals() {
        viewModelScope.launch {
            // Check preload cache first
            val preloadedDeals = preloadRepo.getCachedDeals()
            if (preloadedDeals != null && preloadedDeals.isNotEmpty()) {
                Log.d("Feed", "⚡ Using preloaded deals (${preloadedDeals.size} deals)")
                try {
                    repo.insertPreloadedDeals(preloadedDeals)
                    uiState = uiState.copy(
                        loading = false,
                        currentPage = 1,
                        hasMorePages = true
                    )
                    preloadRepo.clearCache()
                    return@launch
                } catch (e: Exception) {
                    Log.e("Feed", "⚠️ Failed to insert preloaded deals", e)
                }
            }

            // Normal load from network
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
                    Log.d("Feed", "✅ Refreshed ${pagination?.limit ?: 0} deals")
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

    // ✅ PRESERVED: Load More Deals (Lazy Loading)
    fun loadMoreDeals() {
        if (uiState.isLoadingMore || !uiState.hasMorePages || uiState.loading) {
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
                    Log.d("Feed", "✅ Loaded page $nextPage")
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

    // ✅ PRESERVED: Vote Status Checks
    fun hasVoted(dealId: String): Boolean = uiState.votedDeals.containsKey(dealId)
    fun getVoteType(dealId: String): String? = uiState.votedDeals[dealId]
    fun getOptimisticHotCount(dealId: String): Int? = uiState.optimisticCounts[dealId]?.first
    fun getOptimisticColdCount(dealId: String): Int? = uiState.optimisticCounts[dealId]?.second

    // ✅ PRESERVED: Vote HOT with Optimistic Update
    fun voteHot(dealId: String) {
        if (hasVoted(dealId)) return

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
                }
            } catch (t: Throwable) {
                Log.e("FeedVote", "💥 Failed to vote hot", t)
            }
        }
    }

    // ✅ PRESERVED: Vote COLD with Optimistic Update
    fun voteCold(dealId: String) {
        if (hasVoted(dealId)) return

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
                }
            } catch (t: Throwable) {
                Log.e("FeedVote", "💥 Failed to vote cold", t)
            }
        }
    }
}