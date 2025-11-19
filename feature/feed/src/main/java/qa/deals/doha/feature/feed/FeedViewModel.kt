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
 * ✨ NEW: SORT OPTIONS FOR FEED
 * ========================================
 *
 * Created: 2025-11-13
 * - HOTTEST: Sort by vote count (highest to lowest) - DEFAULT
 * - NEWEST: Sort by creation date (newest to oldest)
 */
enum class SortOption {
    HOTTEST,  // Sort by hotCount descending (default)
    NEWEST    // Sort by createdAt descending
}

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
    val showModeratorButton: Boolean = false,
    // ✨ NEW: Filtering/Sorting loading state
    val isFilteringSorting: Boolean = false,
    // ✨ NEW: Vote authentication dialog state
    val showLoginDialog: Boolean = false,
    val pendingVoteDealId: String? = null,
    val pendingVoteType: String? = null
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

    // ✨ NEW: Sort Option State (default: HOTTEST)
    private val _sortOption = MutableStateFlow(SortOption.HOTTEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // ✅ SPRINT 5: Authentication state

    val isAuthenticated: StateFlow<Boolean> = deviceIdManager.userIdFlow

        .map { userId -> userId != null }

        .stateIn(

            scope = viewModelScope,

            started = SharingStarted.WhileSubscribed(5000),

            initialValue = false

        )


    val currentUserId: StateFlow<String?> = deviceIdManager.userIdFlow

        .stateIn(

            scope = viewModelScope,

            started = SharingStarted.WhileSubscribed(5000),

            initialValue = null

        )


    val currentUserRole: StateFlow<String> = deviceIdManager.userIdFlow

        .map { userId ->

            if (userId != null) {

                val user = userRepo.getCachedUser(userId)

                user?.role ?: "user"

            } else {

                "user"

            }

        }

        .stateIn(

            scope = viewModelScope,

            started = SharingStarted.WhileSubscribed(5000),

            initialValue = "user"

        )

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
    // ✅ NEW: Admin status detection (for delete button)

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

    // ✅ PRESERVED + UPDATED: Deals StateFlow with Search + Category Filtering + Sorting
// ✅ FIX: Use getCachedApprovedActiveDeals() to ensure only approved deals show
// This prevents pending/rejected deals from appearing in the feed
    val deals: StateFlow<List<DealEntity>> = combine(
        repo.getCachedApprovedActiveDeals(),
        _searchQuery,
        _selectedCategory,
        _sortOption  // ✨ NEW: Add sort option to the combine
    ) { allDeals, query, category, sortOption ->
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

        // ✨ NEW: Apply sorting based on selected option
        when (sortOption) {
            SortOption.HOTTEST -> filteredDeals.sortedByDescending { it.hotCount ?: 0 }
            SortOption.NEWEST -> filteredDeals.sortedByDescending { it.createdAt }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ✅ PRESERVED + UPDATED: UI State
    var uiState by mutableStateOf(FeedUiState())
        private set

    // ✨ NEW: Track in-flight vote requests to prevent race conditions
    private val votingInProgress = mutableSetOf<String>()

    // ✅ PRESERVED + UPDATED: Initialization
    init {
        Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("FeedViewModel", "🚀 Initializing FeedViewModel - SPRINT 5")
        Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        try {
            refreshDeals()
            Log.d("FeedViewModel", "✅ refreshDeals() completed")
        } catch (e: Exception) {
            Log.e("FeedViewModel", "💥 refreshDeals() failed", e)
        }

        try {
            loadVoteStatus()
            Log.d("FeedViewModel", "✅ loadVoteStatus() completed")
        } catch (e: Exception) {
            Log.e("FeedViewModel", "💥 loadVoteStatus() failed", e)
        }

        // ✅ SPRINT 5: Monitor moderator status and update UI
        viewModelScope.launch {
            try {
                Log.d("FeedViewModel", "🔄 Starting isModerator collection...")
                isModerator.collect { isMod ->
                    Log.d("FeedViewModel", "🛡️ Moderator status changed: $isMod")
                    uiState = uiState.copy(showModeratorButton = isMod)
                    Log.d("FeedViewModel", "   Updated UI state: showModeratorButton=$isMod")
                }
            } catch (e: Exception) {
                Log.e("FeedViewModel", "💥 isModerator collection failed", e)
            }
        }

        // ✅ SPRINT 5: Fetch user profile if logged in but not cached
        viewModelScope.launch {
            try {
                val userId = deviceIdManager.getUserId()
                Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("FeedViewModel", "🔍 CHECKING USER PROFILE CACHE")
                Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d(
                    "FeedViewModel",
                    "👤 UserId from DeviceIdManager: ${userId?.take(8) ?: "NULL"}"
                )

                if (userId != null) {
                    Log.d("FeedViewModel", "✅ User is logged in, checking cache...")

                    // Check if user is cached locally
                    val cachedUser = userRepo.getCachedUser(userId)
                    if (cachedUser == null) {
                        Log.d(
                            "FeedViewModel",
                            "📥 User NOT cached in Room, fetching from backend..."
                        )
                        try {
                            val result = userRepo.fetchUserProfile(userId)
                            if (result.isSuccess) {
                                val user = result.getOrNull()
                                Log.d("FeedViewModel", "✅ User profile fetched and cached")
                                Log.d("FeedViewModel", "   Username: ${user?.username}")
                                Log.d("FeedViewModel", "   Role: ${user?.role}")
                                Log.d("FeedViewModel", "   AutoApprove: ${user?.autoApprove}")
                            } else {
                                Log.e(
                                    "FeedViewModel",
                                    "❌ Failed to fetch user profile: ${result.exceptionOrNull()?.message}"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("FeedViewModel", "💥 Error fetching user profile", e)
                        }
                    } else {
                        Log.d("FeedViewModel", "✅ User already cached in Room")
                        Log.d("FeedViewModel", "   Username: ${cachedUser.username}")
                        Log.d("FeedViewModel", "   Role: ${cachedUser.role}")
                        Log.d("FeedViewModel", "   AutoApprove: ${cachedUser.autoApprove}")

                        // ✅ CRITICAL: Manually check if user is moderator
                        val isMod = cachedUser.role == "moderator" || cachedUser.role == "admin"
                        Log.d("FeedViewModel", "🛡️ Is Moderator/Admin: $isMod")
                    }
                } else {
                    Log.d("FeedViewModel", "❌ No userId - User not logged in")
                }
                Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            } catch (e: Exception) {
                Log.e("FeedViewModel", "💥 User profile check crashed", e)
            }
        }

        Log.d("FeedViewModel", "✅ FeedViewModel init complete")
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

    // ✅ UPDATED: Toggle Category Filter (press again to deselect)
    fun toggleCategory(category: DealCategory?) {
        Log.d("FeedViewModel", "🏷️ Toggling category: ${category?.displayName ?: "All"}")

        // Show loading spinner
        uiState = uiState.copy(isFilteringSorting = true)

        viewModelScope.launch {
            if (_selectedCategory.value == category) {
                // Pressing same category again → deselect it
                _selectedCategory.value = null
                Log.d("FeedViewModel", "   → Category deselected, showing all")
            } else {
                // Selecting a different category
                _selectedCategory.value = category
                Log.d("FeedViewModel", "   → Category selected: ${category?.displayName}")
            }

            // Wait briefly for data to settle, then hide spinner
            kotlinx.coroutines.delay(200)
            uiState = uiState.copy(isFilteringSorting = false)
        }
    }

    // ✨ NEW: Toggle between All (HOTTEST) and Newest
    fun toggleSortToNewest() {
        // Show loading spinner
        uiState = uiState.copy(isFilteringSorting = true)

        viewModelScope.launch {
            if (_sortOption.value == SortOption.NEWEST) {
                // Pressing Newest again → go back to All (HOTTEST)
                _sortOption.value = SortOption.HOTTEST
                Log.d("FeedViewModel", "🔄 Sort changed to: HOTTEST (All)")
            } else {
                // Switching from All to Newest
                _sortOption.value = SortOption.NEWEST
                Log.d("FeedViewModel", "🔄 Sort changed to: NEWEST")
            }

            // Wait briefly for data to settle, then hide spinner
            kotlinx.coroutines.delay(200)
            uiState = uiState.copy(isFilteringSorting = false)
        }
    }

    // ✨ NEW: Set sort to All (HOTTEST) - used when pressing All chip
    fun setSortToAll() {
        if (_sortOption.value != SortOption.HOTTEST) {
            // Show loading spinner
            uiState = uiState.copy(isFilteringSorting = true)

            viewModelScope.launch {
                _sortOption.value = SortOption.HOTTEST
                Log.d("FeedViewModel", "🔄 Sort changed to: HOTTEST (All)")

                // Wait briefly for data to settle, then hide spinner
                kotlinx.coroutines.delay(200)
                uiState = uiState.copy(isFilteringSorting = false)
            }
        }
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

    // ✅ UPDATED: Vote HOT with Authentication Check & Optimistic Update
    // Updated: 2025-11-19 - Added authentication requirement + race condition fix
    fun voteHot(dealId: String) {
        // ✅ NEW: Check authentication first
        val userId = deviceIdManager.getUserId()
        if (userId == null) {
            // Show login dialog for anonymous users
            uiState = uiState.copy(
                showLoginDialog = true,
                pendingVoteDealId = dealId,
                pendingVoteType = "hot"
            )
            Log.d("FeedVote", "⚠️ User not authenticated, showing login dialog")
            return
        }

        // Check if already voted
        if (hasVoted(dealId)) {
            Log.d("FeedVote", "⚠️ User already voted on deal: $dealId")
            return
        }

        // ✨ NEW: Check if vote already in progress to prevent race conditions
        synchronized(votingInProgress) {
            if (votingInProgress.contains(dealId)) {
                Log.d("FeedVote", "⚠️ Vote already in progress for deal: $dealId, ignoring")
                return
            }
            votingInProgress.add(dealId)
        }

        viewModelScope.launch {
            try {
                // ✅ Optimistic update (instant UI feedback)
                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val newHotCount = (currentDeal.hotCount ?: 0) + 1
                    val currentColdCount = currentDeal.coldCount ?: 0

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(newHotCount, currentColdCount)
                    uiState = uiState.copy(optimisticCounts = updatedCounts)
                }

                // Record vote locally for instant feedback
                deviceIdManager.recordVote(dealId, "hot")
                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                updatedVotedDeals[dealId] = "hot"
                uiState = uiState.copy(votedDeals = updatedVotedDeals)

                // ✅ UPDATED: Send to backend with user_id
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "hot",
                    userId = userId  // Changed from deviceId
                )

                // Clear optimistic count on success (use server data)
                if (result.success == true) {
                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)
                    uiState = uiState.copy(optimisticCounts = clearedCounts)
                    Log.d("FeedVote", "✅ Hot vote successful for deal: $dealId")
                }
            } catch (t: Throwable) {
                Log.e("FeedVote", "💥 Failed to vote hot", t)
                // TODO: Revert optimistic update on failure
            } finally {
                // ✨ NEW: Always remove from in-progress set
                synchronized(votingInProgress) {
                    votingInProgress.remove(dealId)
                }
            }
        }
    }

    // ✅ UPDATED: Vote COLD with Authentication Check & Optimistic Update
    // Updated: 2025-11-19 - Added authentication requirement + race condition fix
    fun voteCold(dealId: String) {
        // ✅ NEW: Check authentication first
        val userId = deviceIdManager.getUserId()
        if (userId == null) {
            // Show login dialog for anonymous users
            uiState = uiState.copy(
                showLoginDialog = true,
                pendingVoteDealId = dealId,
                pendingVoteType = "cold"
            )
            Log.d("FeedVote", "⚠️ User not authenticated, showing login dialog")
            return
        }

        // Check if already voted
        if (hasVoted(dealId)) {
            Log.d("FeedVote", "⚠️ User already voted on deal: $dealId")
            return
        }

        // ✨ NEW: Check if vote already in progress to prevent race conditions
        synchronized(votingInProgress) {
            if (votingInProgress.contains(dealId)) {
                Log.d("FeedVote", "⚠️ Vote already in progress for deal: $dealId, ignoring")
                return
            }
            votingInProgress.add(dealId)
        }

        viewModelScope.launch {
            try {
                // ✅ Optimistic update (instant UI feedback)
                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal != null) {
                    val currentHotCount = currentDeal.hotCount ?: 0
                    val newColdCount = (currentDeal.coldCount ?: 0) + 1

                    val updatedCounts = uiState.optimisticCounts.toMutableMap()
                    updatedCounts[dealId] = Pair(currentHotCount, newColdCount)
                    uiState = uiState.copy(optimisticCounts = updatedCounts)
                }

                // Record vote locally for instant feedback
                deviceIdManager.recordVote(dealId, "cold")
                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                updatedVotedDeals[dealId] = "cold"
                uiState = uiState.copy(votedDeals = updatedVotedDeals)

                // ✅ UPDATED: Send to backend with user_id
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "cold",
                    userId = userId  // Changed from deviceId
                )

                // Clear optimistic count on success (use server data)
                if (result.success == true) {
                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)
                    uiState = uiState.copy(optimisticCounts = clearedCounts)
                    Log.d("FeedVote", "✅ Cold vote successful for deal: $dealId")
                }
            } catch (t: Throwable) {
                Log.e("FeedVote", "💥 Failed to vote cold", t)
                // TODO: Revert optimistic update on failure
            } finally {
                // ✨ NEW: Always remove from in-progress set
                synchronized(votingInProgress) {
                    votingInProgress.remove(dealId)
                }
            }
        }
    }

    // ✨ NEW: Dismiss login dialog
    // Updated: 2025-11-19
    fun dismissLoginDialog() {
        uiState = uiState.copy(
            showLoginDialog = false,
            pendingVoteDealId = null,
            pendingVoteType = null
        )
        Log.d("FeedVote", "Login dialog dismissed")
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
                    Log.e("FeedViewModel", "❌ Cannot delete deal: User not logged in")
                    uiState = uiState.copy(error = "Please log in to delete deals")
                    return@launch
                }

                // Check if user is admin
                val userIsAdmin = userRepo.isAdmin(userId)
                if (!userIsAdmin) {
                    Log.e("FeedViewModel", "❌ Cannot delete deal: User is not admin")
                    uiState = uiState.copy(error = "Only admins can permanently delete deals")
                    return@launch
                }

                Log.d("FeedViewModel", "🗑️ Permanently deleting deal: $dealId by admin: $userId")

                val result = repo.permanentDeleteDeal(
                    dealId = dealId,
                    userId = userId
                )

                result.onSuccess {
                    Log.d("FeedViewModel", "✅ Deal $dealId permanently deleted successfully")
                    // Refresh feed to remove the deal from list
                    refreshDeals()
                }.onFailure { error ->
                    Log.e("FeedViewModel", "💥 Failed to permanently delete deal", error)
                    uiState = uiState.copy(error = error.message)
                }
            } catch (t: Throwable) {
                Log.e("FeedViewModel", "💥 Error permanently deleting deal", t)
                uiState = uiState.copy(error = t.message)
            }
        }
    }
}