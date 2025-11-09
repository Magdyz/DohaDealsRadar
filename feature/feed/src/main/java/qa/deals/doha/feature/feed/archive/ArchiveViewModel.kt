package qa.deals.doha.feature.archive

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
import qa.deals.doha.db.DealEntity
import qa.deals.domain.DealCategory
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.repository.UserRepository
import qa.deals.doha.datastore.DeviceIdManager
import kotlinx.coroutines.flow.map

/**
 * ========================================
 * ✅ SPRINT 4: ARCHIVE UI STATE
 * Mirrors FeedUiState for consistency
 * ========================================
 */
data class ArchiveUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    // ✅ Pagination state
    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false
)

/**
 * ========================================
 * ✅ SPRINT 4: ARCHIVE VIEW MODEL
 * Manages archived deals screen logic
 * ========================================
 *
 * Created: 2025-10-27 (SPRINT 4: Archive Feature)
 * - Follows FeedViewModel pattern for consistency
 * - Supports search and category filtering
 * - Pagination for large archive collections
 * - Reactive updates via StateFlow
 */
class ArchiveViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context)
) : ViewModel() {

    // ✅ Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ✅ Category Filter State
    private val _selectedCategory = MutableStateFlow<DealCategory?>(null)
    val selectedCategory: StateFlow<DealCategory?> = _selectedCategory.asStateFlow()

    // ✅ Admin Detection (for Return to Feed button)

    val isAdmin: StateFlow<Boolean> = deviceIdManager.userIdFlow
        .map { userId ->
            if (userId != null) {
                userRepo.isAdmin(userId)
            } else {
                false
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // ✅ Current User ID
    val currentUserId: StateFlow<String?> = deviceIdManager.userIdFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // ========================================
    // ✅ ARCHIVED DEALS with Search + Category Filtering
    // Only shows deals where isArchived = true
    // ========================================
    val archivedDeals: StateFlow<List<DealEntity>> = combine(
        repo.getCachedArchivedDeals(),
        _searchQuery,
        _selectedCategory
    ) { allArchivedDeals, query, category ->
        var filteredDeals = allArchivedDeals

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

        // Sort by creation date (newest archived first)
        filteredDeals.sortedByDescending { it.createdAt }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ✅ UI State
    var uiState by mutableStateOf(ArchiveUiState())
        private set

    // ========================================
    // ✅ INITIALIZATION
    // Automatically fetch archived deals on start
    // ========================================
    init {
        Log.d("ArchiveViewModel", "🚀 Initializing Archive ViewModel")
        refreshArchivedDeals()
    }

    // ========================================
    // ✅ SEARCH QUERY UPDATE
    // ========================================
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    // ========================================
    // ✅ CATEGORY FILTER UPDATE
    // Reset pagination when filter changes
    // ========================================
    fun filterByCategory(category: DealCategory?) {
        Log.d("ArchiveViewModel", "🏷️ Filtering archived deals by: ${category?.displayName ?: "All"}")
        _selectedCategory.value = category
        // Reset pagination when filter changes
        uiState = uiState.copy(currentPage = 1, hasMorePages = true)
    }

    // ========================================
    // ✅ REFRESH ARCHIVED DEALS (Pull-to-Refresh)
    // Fetches page 1 and replaces cache
    // ========================================
    fun refreshArchivedDeals() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null, currentPage = 1)
            try {
                Log.d("Archive", "🔄 Refreshing archived deals (page 1)...")
                val result = repo.refreshArchivedDeals(page = 1, append = false)

                result.onSuccess { pagination ->
                    uiState = uiState.copy(
                        loading = false,
                        currentPage = 1,
                        hasMorePages = pagination?.hasMore ?: false
                    )
                    Log.d("Archive", "✅ Refreshed ${pagination?.limit ?: 0} archived deals (hasMore: ${pagination?.hasMore})")
                }.onFailure { error ->
                    Log.e("Archive", "💥 Failed to refresh archived deals", error)
                    uiState = uiState.copy(loading = false, error = error.message)
                }
            } catch (t: Throwable) {
                Log.e("Archive", "💥 Failed to refresh archived deals", t)
                uiState = uiState.copy(loading = false, error = t.message)
            }
        }
    }

    // ========================================
    // ✅ LOAD MORE ARCHIVED DEALS (Lazy Loading)
    // Called when user scrolls near bottom of archive
    // ========================================
    fun loadMoreArchivedDeals() {
        // Don't load if already loading, no more pages, or initial load in progress
        if (uiState.isLoadingMore || !uiState.hasMorePages || uiState.loading) {
            Log.d("Archive", "⏸️ Skipping loadMore (loading: ${uiState.isLoadingMore}, hasMore: ${uiState.hasMorePages})")
            return
        }

        viewModelScope.launch {
            val nextPage = uiState.currentPage + 1
            uiState = uiState.copy(isLoadingMore = true)

            try {
                Log.d("Archive", "📄 Loading more archived deals (page $nextPage)...")
                val result = repo.refreshArchivedDeals(page = nextPage, append = true)

                result.onSuccess { pagination ->
                    uiState = uiState.copy(
                        isLoadingMore = false,
                        currentPage = nextPage,
                        hasMorePages = pagination?.hasMore ?: false
                    )
                    Log.d("Archive", "✅ Loaded page $nextPage (hasMore: ${pagination?.hasMore})")
                }.onFailure { error ->
                    Log.e("Archive", "💥 Failed to load more archived deals", error)
                    uiState = uiState.copy(isLoadingMore = false)
                }
            } catch (t: Throwable) {
                Log.e("Archive", "💥 Failed to load more archived deals", t)
                uiState = uiState.copy(isLoadingMore = false)
            }
        }
    }

    // ========================================
    // ✅ RETURN DEAL TO FEED (Admin Only)
    // Un-archives deal and extends expiry by 10 days
    // ========================================

    fun returnDealToFeed(dealId: String) {
        viewModelScope.launch {
            try {
                val userId = currentUserId.value
                if (userId == null) {
                    Log.e("Archive", "❌ Cannot return deal to feed: User not logged in")
                    return@launch
                }

                // Check if user is admin
                if (!isAdmin.value) {
                    Log.e("Archive", "❌ Cannot return deal to feed: User is not admin")
                    uiState = uiState.copy(error = "Only admins can return deals to feed")
                    return@launch
                }

                Log.d("Archive", "🔄 Returning deal to feed: $dealId")

                val result = repo.returnDealToFeed(
                    dealId = dealId,
                    userId = userId
                )

                result.onSuccess {
                    Log.d("Archive", "✅ Deal $dealId returned to feed successfully")

                    // Refresh archive to remove the deal from list
                    refreshArchivedDeals()
                }.onFailure { error ->
                    Log.e("Archive", "💥 Failed to return deal to feed", error)
                    uiState = uiState.copy(error = error.message)
                }
            } catch (t: Throwable) {
                Log.e("Archive", "💥 Error returning deal to feed", t)
                uiState = uiState.copy(error = t.message)
            }
        }
    }
}