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
/**
 * ✅ NEW: Pending vote waiting for authentication
 */
data class PendingVote(
    val dealId: String,
    val voteType: String  // "hot" or "cold"
)

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
    // ✅ NEW: Vote authentication dialog state
    val showVoteAuthDialog: Boolean = false,
    val pendingVote: PendingVote? = null
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

    // ========================================
    // ✅ UPDATED: Vote HOT with User Authentication + Optimistic Update
    // Migration: device_id → user_id
    // ========================================
    /**
     * Cast a HOT vote on a deal
     *
     * Flow (Instagram/YouTube 2025 pattern):
     * 1. Check authentication → Show dialog if anonymous
     * 2. Check duplicate vote
     * 3. Optimistic UI update → Instant feedback
     * 4. API call → Server validation
     * 5. Success → Clear optimistic state (server data is source of truth)
     * 6. Failure → Revert changes + show error
     */
    fun voteHot(dealId: String) {
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
                    Log.d("Feed", "⚠️ Anonymous user tried to vote - showing auth dialog")
                    uiState = uiState.copy(
                        showVoteAuthDialog = true,
                        pendingVote = PendingVote(dealId, "hot")
                    )
                    return@launch
                }

                // ========================================
                // STEP 2: Determine Vote Action (NEW/SWITCH/REMOVE)
                // ✅ UPDATED 2025-11-20: Support vote switching
                // ========================================
                val voteAction = deviceIdManager.getVoteAction(userId, dealId, "hot")
                val actionDescription = deviceIdManager.getVoteActionDescription(userId, dealId, "hot")
                val existingVoteType = deviceIdManager.getUserVoteType(userId, dealId)

                Log.d("Feed", "🗳️ Vote action: $actionDescription")

                // ========================================
                // STEP 3: Optimistic UI Update (Based on Action)
                // ✅ UPDATED 2025-11-20: Handle +1/-1 for switches
                // ========================================
                val currentDeal = deals.value.find { it.id == dealId }
                if (currentDeal == null) {
                    Log.e("Feed", "❌ Deal $dealId not found")
                    return@launch
                }

                val currentHotCount = currentDeal.hotCount ?: 0
                val currentColdCount = currentDeal.coldCount ?: 0

                val optimisticCounts = when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW -> {
                        // New vote: +1 to hot
                        Pair(currentHotCount + 1, currentColdCount)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        // Switch from cold to hot: -1 cold, +1 hot
                        Pair(currentHotCount + 1, currentColdCount - 1)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        // Remove hot vote: -1 hot (with floor at 0)
                        Pair((currentHotCount - 1).coerceAtLeast(0), currentColdCount)
                    }
                }

                Log.d("Feed", "🔥 Optimistic hot vote: $dealId (counts: ${optimisticCounts.first}, ${optimisticCounts.second})")

                // Update UI immediately
                val updatedCounts = uiState.optimisticCounts.toMutableMap()
                updatedCounts[dealId] = optimisticCounts

                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                if (voteAction == qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE) {
                    updatedVotedDeals.remove(dealId)
                } else {
                    updatedVotedDeals[dealId] = "hot"
                }

                uiState = uiState.copy(
                    optimisticCounts = updatedCounts,
                    votedDeals = updatedVotedDeals
                )

                // Update local storage based on action
                when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW,
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        deviceIdManager.recordUserVote(userId, dealId, "hot")
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }
                }

                // ========================================
                // STEP 4: API Call
                // ========================================
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "hot",
                    userId = userId,
                    userEmail = userEmail,
                    deviceId = deviceIdManager.getDeviceId()  // Analytics only
                )

                // ========================================
                // STEP 5: Handle Response
                // ========================================
                if (result.success == true) {
                    // ✅ SUCCESS: Clear optimistic state (server data is now source of truth)
                    Log.d("Feed", "✅ Vote ${voteAction.name.lowercase()} successful")

                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)
                    uiState = uiState.copy(optimisticCounts = clearedCounts)

                } else {
                    // ❌ FAILURE: Revert optimistic changes to previous state
                    Log.e("Feed", "❌ Vote failed: ${result.error}")

                    // Revert local storage to previous state
                    if (existingVoteType != null) {
                        deviceIdManager.recordUserVote(userId, dealId, existingVoteType)
                    } else {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }

                    // Revert UI to show previous state
                    val revertedCounts = uiState.optimisticCounts.toMutableMap()
                    revertedCounts.remove(dealId)

                    val revertedVotedDeals = uiState.votedDeals.toMutableMap()
                    if (existingVoteType != null) {
                        revertedVotedDeals[dealId] = existingVoteType
                    } else {
                        revertedVotedDeals.remove(dealId)
                    }

                    uiState = uiState.copy(
                        optimisticCounts = revertedCounts,
                        votedDeals = revertedVotedDeals
                    )
                }

            } catch (e: Exception) {
                Log.e("Feed", "❌ Vote exception: ${e.message}", e)

                // Revert on exception to previous state
                val userId = deviceIdManager.getUserId()
                val existingVoteType = if (userId != null) {
                    deviceIdManager.getUserVoteType(userId, dealId)
                } else null

                if (userId != null) {
                    if (existingVoteType != null) {
                        deviceIdManager.recordUserVote(userId, dealId, existingVoteType)
                    } else {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }
                }

                val revertedCounts = uiState.optimisticCounts.toMutableMap()
                revertedCounts.remove(dealId)

                val revertedVotedDeals = uiState.votedDeals.toMutableMap()
                if (existingVoteType != null) {
                    revertedVotedDeals[dealId] = existingVoteType
                } else {
                    revertedVotedDeals.remove(dealId)
                }

                uiState = uiState.copy(
                    optimisticCounts = revertedCounts,
                    votedDeals = revertedVotedDeals
                )
            }
        }
    }

    // ========================================
    // ✅ UPDATED: Vote COLD with User Authentication (Same Pattern as voteHot)
    // ========================================
    fun voteCold(dealId: String) {
        viewModelScope.launch {
            try {
                val userId = deviceIdManager.getUserId()
                val userEmail = if (userId != null) {
                    userRepo.getCachedUser(userId)?.email
                } else null

                if (userId == null) {
                    uiState = uiState.copy(
                        showVoteAuthDialog = true,
                        pendingVote = PendingVote(dealId, "cold")
                    )
                    return@launch
                }

                // ========================================
                // STEP 2: Determine Vote Action (NEW/SWITCH/REMOVE)
                // ✅ UPDATED 2025-11-20: Support vote switching
                // ========================================
                val voteAction = deviceIdManager.getVoteAction(userId, dealId, "cold")
                val actionDescription = deviceIdManager.getVoteActionDescription(userId, dealId, "cold")
                val existingVoteType = deviceIdManager.getUserVoteType(userId, dealId)

                Log.d("Feed", "🗳️ Vote action: $actionDescription")

                // ========================================
                // STEP 3: Optimistic UI Update (Based on Action)
                // ✅ UPDATED 2025-11-20: Handle +1/-1 for switches
                // ========================================
                val currentDeal = deals.value.find { it.id == dealId } ?: return@launch
                val currentHotCount = currentDeal.hotCount ?: 0
                val currentColdCount = currentDeal.coldCount ?: 0

                val optimisticCounts = when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW -> {
                        // New vote: +1 to cold
                        Pair(currentHotCount, currentColdCount + 1)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        // Switch from hot to cold: -1 hot, +1 cold
                        Pair(currentHotCount - 1, currentColdCount + 1)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        // Remove cold vote: -1 cold (with floor at 0)
                        Pair(currentHotCount, (currentColdCount - 1).coerceAtLeast(0))
                    }
                }

                Log.d("Feed", "❄️ Optimistic cold vote: $dealId (counts: ${optimisticCounts.first}, ${optimisticCounts.second})")

                val updatedCounts = uiState.optimisticCounts.toMutableMap()
                updatedCounts[dealId] = optimisticCounts

                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                if (voteAction == qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE) {
                    updatedVotedDeals.remove(dealId)
                } else {
                    updatedVotedDeals[dealId] = "cold"
                }

                uiState = uiState.copy(
                    optimisticCounts = updatedCounts,
                    votedDeals = updatedVotedDeals
                )

                // Update local storage based on action
                when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW,
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        deviceIdManager.recordUserVote(userId, dealId, "cold")
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }
                }

                // ========================================
                // STEP 4: API Call
                // ========================================
                val result = repo.castVote(
                    dealId = dealId,
                    voteType = "cold",
                    userId = userId,
                    userEmail = userEmail,
                    deviceId = deviceIdManager.getDeviceId()
                )

                // ========================================
                // STEP 5: Handle Response
                // ========================================
                if (result.success == true) {
                    Log.d("Feed", "✅ Vote ${voteAction.name.lowercase()} successful")
                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)
                    uiState = uiState.copy(optimisticCounts = clearedCounts)

                } else {
                    // ❌ FAILURE: Revert optimistic changes to previous state
                    Log.e("Feed", "❌ Vote failed: ${result.error}")

                    // Revert local storage to previous state
                    if (existingVoteType != null) {
                        deviceIdManager.recordUserVote(userId, dealId, existingVoteType)
                    } else {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }

                    // Revert UI to show previous state
                    val revertedCounts = uiState.optimisticCounts.toMutableMap()
                    revertedCounts.remove(dealId)

                    val revertedVotedDeals = uiState.votedDeals.toMutableMap()
                    if (existingVoteType != null) {
                        revertedVotedDeals[dealId] = existingVoteType
                    } else {
                        revertedVotedDeals.remove(dealId)
                    }

                    uiState = uiState.copy(
                        optimisticCounts = revertedCounts,
                        votedDeals = revertedVotedDeals
                    )
                }

            } catch (e: Exception) {
                Log.e("Feed", "❌ Vote exception: ${e.message}", e)

                // Revert on exception to previous state
                val userId = deviceIdManager.getUserId()
                val existingVoteType = if (userId != null) {
                    deviceIdManager.getUserVoteType(userId, dealId)
                } else null

                if (userId != null) {
                    if (existingVoteType != null) {
                        deviceIdManager.recordUserVote(userId, dealId, existingVoteType)
                    } else {
                        deviceIdManager.clearUserVote(userId, dealId)
                    }
                }

                val revertedCounts = uiState.optimisticCounts.toMutableMap()
                revertedCounts.remove(dealId)

                val revertedVotedDeals = uiState.votedDeals.toMutableMap()
                if (existingVoteType != null) {
                    revertedVotedDeals[dealId] = existingVoteType
                } else {
                    revertedVotedDeals.remove(dealId)
                }

                uiState = uiState.copy(
                    optimisticCounts = revertedCounts,
                    votedDeals = revertedVotedDeals
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
        uiState = uiState.copy(
            showVoteAuthDialog = false,
            pendingVote = null
        )
    }

    /**
     * Retry pending vote after user authenticates
     * Called from FeedScreen after successful login/email verification
     */
    fun retryPendingVote() {
        val pending = uiState.pendingVote ?: return
        uiState = uiState.copy(
            showVoteAuthDialog = false,
            pendingVote = null
        )

        when (pending.voteType) {
            "hot" -> voteHot(pending.dealId)
            "cold" -> voteCold(pending.dealId)
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