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
 * ✨ NEW: Added return to feed dialog state
 * ========================================
 */

data class ArchiveUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",

    // ✅ Pagination state

    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false,

    // ✨ NEW: Return to Feed dialog state

    val showReturnToFeedDialog: Boolean = false,
    val selectedDealId: String? = null,
    val expiresInDays: Int = 10
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
    // Un-archives deal and extends expiry by specified days
    // ========================================

    fun showReturnToFeedDialog(dealId: String) {
        uiState = uiState.copy(
            showReturnToFeedDialog = true,
            selectedDealId = dealId,
            expiresInDays = 10
        )
    }

    fun hideReturnToFeedDialog() {
        uiState = uiState.copy(
            showReturnToFeedDialog = false,
            selectedDealId = null,
            expiresInDays = 10
        )
    }

    fun updateExpiresInDays(days: Int) {
        uiState = uiState.copy(expiresInDays = days.coerceIn(1, 30))
    }

    fun returnDealToFeed() {
        val dealId = uiState.selectedDealId ?: return
        val expiresInDays = uiState.expiresInDays

        viewModelScope.launch {
            try {
                // Hide dialog and show loading
                uiState = uiState.copy(
                    showReturnToFeedDialog = false,
                    loading = true
                )

                // ✅ FIX: Get userId directly from deviceIdManager instead of StateFlow
                val userId = deviceIdManager.getUserId()
                if (userId == null) {
                    Log.e("Archive", "❌ Cannot return deal to feed: User not logged in")
                    uiState = uiState.copy(
                        error = "Please log in to use this feature",
                        loading = false
                    )
                    return@launch
                }

                // Check if user is admin
                val userIsAdmin = userRepo.isAdmin(userId)
                if (!userIsAdmin) {
                    Log.e("Archive", "❌ Cannot return deal to feed: User is not admin (role check failed)")
                    uiState = uiState.copy(
                        error = "Only admins can return deals to feed",
                        loading = false
                    )
                    return@launch
                }

                Log.d("Archive", "🔄 Returning deal to feed: $dealId by admin: $userId")
                Log.d("Archive", "   Expires in: $expiresInDays days")

                val result = repo.returnDealToFeed(
                    dealId = dealId,
                    userId = userId,
                    expiresInDays = expiresInDays
                )

                result.onSuccess {
                    Log.d("Archive", "✅ Deal $dealId returned to feed successfully")

                    // Refresh both archive AND main feed to reflect the change everywhere
                    try {
                        // Refresh archive (remove the deal from archive list)
                        val archiveResult = repo.refreshArchivedDeals(page = 1, append = false)

                        // Refresh main feed (add the deal to active feed)
                        val feedResult = repo.refreshDeals(page = 1, append = false)

                        archiveResult.onSuccess {
                            Log.d("Archive", "✅ Archive refreshed - deal removed from archive")
                        }

                        feedResult.onSuccess {
                            Log.d("Archive", "✅ Feed refreshed - deal added to active feed")
                        }

                        // Clear loading state
                        uiState = uiState.copy(loading = false)
                    } catch (e: Exception) {
                        Log.e("Archive", "💥 Error refreshing after return to feed", e)
                        uiState = uiState.copy(loading = false, error = e.message)
                    }

                }.onFailure { error ->
                    Log.e("Archive", "💥 Failed to return deal to feed", error)
                    uiState = uiState.copy(
                        error = error.message,
                        loading = false
                    )
                }
            } catch (t: Throwable) {
                Log.e("Archive", "💥 Error returning deal to feed", t)
                uiState = uiState.copy(
                    error = t.message,
                    loading = false
                )
            }
        }
    }

    // ========================================
    // ✅ NEW: Permanently Delete Deal (Admin Only)
    // Deletes deal and image from database - cannot be undone
    // ========================================

    fun permanentDeleteDeal(dealId: String) {
        viewModelScope.launch {
            try {
                val userId = deviceIdManager.getUserId()
                if (userId == null) {
                    Log.e("Archive", "❌ Cannot delete deal: User not logged in")
                    uiState = uiState.copy(error = "Please log in to delete deals")
                    return@launch
                }

                // Check if user is admin
                val userIsAdmin = userRepo.isAdmin(userId)
                if (!userIsAdmin) {
                    Log.e("Archive", "❌ Cannot delete deal: User is not admin")
                    uiState = uiState.copy(error = "Only admins can permanently delete deals")
                    return@launch
                }

                Log.d("Archive", "🗑️ Permanently deleting deal: $dealId by admin: $userId")

                val result = repo.permanentDeleteDeal(
                    dealId = dealId,
                    userId = userId
                )

                result.onSuccess {
                    Log.d("Archive", "✅ Deal $dealId permanently deleted successfully")
                    // Refresh archive to remove the deal from list
                    refreshArchivedDeals()
                }.onFailure { error ->
                    Log.e("Archive", "💥 Failed to permanently delete deal", error)
                    uiState = uiState.copy(error = error.message)
                }
            } catch (t: Throwable) {
                Log.e("Archive", "💥 Error permanently deleting deal", t)
                uiState = uiState.copy(error = t.message)
            }
        }
    }
}