package qa.deals.doha.feature.feed

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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

    // ✅ UPDATED: Deals StateFlow with Search Filtering ONLY
    // ✅ BACKEND DOES: Category filtering, sorting, pagination
    // ✅ CLIENT DOES: Search filtering only (local text search)
    val deals: StateFlow<List<DealEntity>> = combine(
        repo.getCachedApprovedActiveDeals(),
        _searchQuery
        // ✅ REMOVED: _selectedCategory - backend filters by category now
        // ✅ REMOVED: _sortOption - backend sorts now
    ) { allDeals, query ->
        var filteredDeals = allDeals

        // ✅ KEEP: Search filter (client-side text search is OK)
        if (query.isNotEmpty()) {
            val searchLower = query.lowercase().trim()
            filteredDeals = filteredDeals.filter { deal ->
                deal.title.lowercase().contains(searchLower) ||
                        deal.description?.lowercase()?.contains(searchLower) == true
            }
        }

        // ✅ REMOVED: Category filter - backend now handles this
        // Backend filters by category BEFORE pagination, so we get correct results

        // ✅ RETURN AS-IS: Backend already sorted and filtered
        filteredDeals
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ✅ PRESERVED + UPDATED: UI State
    var uiState by mutableStateOf(FeedUiState())
        private set

    // ========================================
    // ✅ UPDATED: Mutex-based Vote Serialization
    // Ensures network requests are processed ONE AT A TIME per deal
    // This prevents server race conditions by queueing requests
    // instead of cancelling them (which doesn't stop in-flight HTTP)
    // ========================================
    private val voteMutexes = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    // Track ongoing vote jobs for cancellation
    private val voteJobs = mutableMapOf<String, Job>()

    // ========================================
    // ✅ UPDATED: UNIFIED VOTING METHOD (Instagram Pattern + Mutex Serialization)
    // Immediate Local Write + Serialized Network Queue
    // ========================================
    /**
     * The "Big App" voting pattern with request serialization:
     * 1. Update local DB immediately (zero-lag UI)
     * 2. Queue network request (waits for previous request to finish)
     * 3. Send request when lock is available
     * 4. Rollback on network failure
     *
     * This eliminates:
     * - UI lag (local DB updates in <16ms)
     * - HTTP 500 errors (Mutex serializes requests, no race conditions)
     * - Stale optimistic state (DB is source of truth)
     * - In-flight request conflicts (withLock ensures sequential execution)
     */
    fun onVoteClicked(dealId: String, newVoteType: String) {
        // Get or create a Mutex lock for this specific deal
        // This ensures network requests for the same deal are processed serially
        val mutex = voteMutexes.getOrPut(dealId) { kotlinx.coroutines.sync.Mutex() }

        viewModelScope.launch {
            try {
                // ========================================
                // A. AUTHENTICATION CHECK
                // ========================================
                val userId = deviceIdManager.getUserId()
                val userEmail = if (userId != null) {
                    userRepo.getCachedUser(userId)?.email
                } else null

                // Gate: Show auth dialog for anonymous users
                if (userId == null) {
                    Log.d("Feed", "⚠️ Anonymous user tried to vote - showing auth dialog")
                    uiState = uiState.copy(
                        showVoteAuthDialog = true,
                        pendingVote = PendingVote(dealId, newVoteType)
                    )
                    return@launch
                }

                // ========================================
                // B. GET CURRENT STATE (for rollback if needed)
                // ========================================
                val deal = deals.value.find { it.id == dealId } ?: run {
                    Log.e("Feed", "❌ Deal $dealId not found")
                    return@launch
                }

                val oldHot = deal.hotCount ?: 0
                val oldCold = deal.coldCount ?: 0
                val oldVoteType = deviceIdManager.getUserVoteType(userId, dealId)

                // ========================================
                // C. CALCULATE NEW STATE LOCALLY
                // ========================================
                val isRemoving = oldVoteType == newVoteType

                val newHot = when {
                    newVoteType == "hot" && !isRemoving -> oldHot + 1
                    newVoteType == "hot" && isRemoving -> (oldHot - 1).coerceAtLeast(0)
                    oldVoteType == "hot" && newVoteType == "cold" -> (oldHot - 1).coerceAtLeast(0)
                    else -> oldHot
                }

                val newCold = when {
                    newVoteType == "cold" && !isRemoving -> oldCold + 1
                    newVoteType == "cold" && isRemoving -> (oldCold - 1).coerceAtLeast(0)
                    oldVoteType == "cold" && newVoteType == "hot" -> (oldCold - 1).coerceAtLeast(0)
                    else -> oldCold
                }

                Log.d("Feed", "⚡ Vote: $oldVoteType -> $newVoteType (Remove: $isRemoving)")
                Log.d("Feed", "   Counts: hot $oldHot->$newHot, cold $oldCold->$newCold")

                // ========================================
                // D. ⚡ IMMEDIATE LOCAL COMMIT (Zero-Lag Fix)
                // ========================================
                // 1. Update Room DB immediately - UI sees this in <16ms
                repo.updateDealCountsLocal(dealId, newHot, newCold)

                // 2. Update local vote status (SharedPrefs/DataStore)
                if (isRemoving) {
                    deviceIdManager.clearUserVote(userId, dealId)
                } else {
                    deviceIdManager.recordUserVote(userId, dealId, newVoteType)
                }

                // 3. Update votedDeals map for UI state
                val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                if (isRemoving) {
                    updatedVotedDeals.remove(dealId)
                } else {
                    updatedVotedDeals[dealId] = newVoteType
                }
                uiState = uiState.copy(votedDeals = updatedVotedDeals)

                // ========================================
                // E. SERIALIZED NETWORK SYNC (Mutex-based Queue)
                // ========================================
                // This block waits until previous network call finishes
                // Even if user taps rapidly, requests execute one by one
                mutex.withLock {
                    // Optional debounce: Add small delay to batch rapid clicks
                    kotlinx.coroutines.delay(300)

                    try {
                        Log.d("Feed", "📡 Sending vote to server: $newVoteType (after acquiring lock)")

                        // Send the request - guaranteed to be serial per deal
                        val result = repo.castVote(
                            dealId = dealId,
                            voteType = newVoteType,
                            userId = userId,
                            userEmail = userEmail,
                            deviceId = deviceIdManager.getDeviceId()
                        )

                        if (result.success == true && result.data != null) {
                            // Success: Server data matches local
                            Log.d("Feed", "✅ Vote synced successfully")
                            // Optional: Update with server data to be 100% sure
                            // repo.updateLocalDealFromNetwork(result.data)
                        } else {
                            throw Exception(result.error ?: "Server returned false")
                        }

                    } catch (e: Exception) {
                        Log.e("Feed", "❌ Sync failed. Rolling back.", e)

                        // F. ROLLBACK ON FAILURE
                        // Revert database to old numbers
                        repo.updateDealCountsLocal(dealId, oldHot, oldCold)

                        // Revert local vote status
                        if (oldVoteType != null) {
                            deviceIdManager.recordUserVote(userId, dealId, oldVoteType)
                        } else {
                            deviceIdManager.clearUserVote(userId, dealId)
                        }

                        // Revert UI votedDeals map
                        val revertedVotedDeals = uiState.votedDeals.toMutableMap()
                        if (oldVoteType != null) {
                            revertedVotedDeals[dealId] = oldVoteType
                        } else {
                            revertedVotedDeals.remove(dealId)
                        }
                        uiState = uiState.copy(votedDeals = revertedVotedDeals)
                    }
                }

            } catch (e: Exception) {
                Log.e("Feed", "❌ Vote failed: ${e.message}", e)
            }
        }
    }

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
    /**
     * ✅ UPDATED: Load vote status using user-based voting
     * Migration: device-based → user-based voting
     */
    private fun loadVoteStatus() {
        viewModelScope.launch {
            deals.collect { dealList ->
                val votedDeals = mutableMapOf<String, String>()
                val userId = deviceIdManager.getUserId()

                dealList.forEach { deal ->
                    val hasVoted = if (userId != null) {
                        // NEW: Check user-based votes
                        deviceIdManager.hasUserVoted(userId, deal.id)
                    } else {
                        // LEGACY: Fallback to device-based votes for backward compatibility
                        deviceIdManager.hasVoted(deal.id)
                    }

                    if (hasVoted) {
                        val voteType = if (userId != null) {
                            deviceIdManager.getUserVoteType(userId, deal.id)
                        } else {
                            deviceIdManager.getVoteType(deal.id)
                        }

                        voteType?.let {
                            votedDeals[deal.id] = it
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
    // ✅ NEW: Refreshes from backend with category parameter
    fun toggleCategory(category: DealCategory?) {
        Log.d("FeedViewModel", "🏷️ Toggling category: ${category?.displayName ?: "All"}")

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

            // ✅ NEW: Refresh from backend with new category filter
            // Backend will filter by category BEFORE pagination
            refreshDeals()
        }
    }

    // ✅ REMOVED: checkAndLoadMoreForCategory() hack
    // No longer needed - backend filters by category BEFORE pagination
    // This guarantees we always get results (if category has any deals)

    // ✨ UPDATED: Toggle between All (HOTTEST) and Newest
    // ✅ FIX: Refresh feed from backend with new sort order
    fun toggleSortToNewest() {
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

            // ✅ UPDATED: Refresh feed with new sort order from backend
            // Backend filters by category + sorts + paginates
            refreshDeals()
        }
    }

    // ✨ UPDATED: Set sort to All (HOTTEST) - used when pressing All chip
    // ✅ FIX: Refresh feed from backend with new sort order
    fun setSortToAll() {
        if (_sortOption.value != SortOption.HOTTEST) {
            viewModelScope.launch {
                _sortOption.value = SortOption.HOTTEST
                Log.d("FeedViewModel", "🔄 Sort changed to: HOTTEST (All)")

                // ✅ UPDATED: Refresh feed with new sort order from backend
                // Backend filters by category + sorts + paginates
                refreshDeals()
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
                        hasMorePages = true,
                        optimisticCounts = emptyMap()  // ✅ Clear optimistic counts on preload
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
                // ✅ UPDATED: Pass sortBy and category parameters to backend
                val sortBy = when (_sortOption.value) {
                    SortOption.HOTTEST -> "hottest"
                    SortOption.NEWEST -> "newest"
                }
                val categoryId = _selectedCategory.value?.id  // null = all categories
                Log.d("Feed", "🔄 Refreshing deals (page 1, sort: $sortBy, category: ${categoryId ?: "all"})...")
                val result = repo.refreshDeals(page = 1, append = false, sortBy = sortBy, category = categoryId)

                result.onSuccess { pagination ->
                    uiState = uiState.copy(
                        loading = false,
                        currentPage = 1,
                        hasMorePages = pagination?.hasMore ?: false,
                        optimisticCounts = emptyMap()  // ✅ Clear optimistic counts on refresh
                    )
                    Log.d("Feed", "✅ Refreshed ${pagination?.limit ?: 0} deals (optimistic counts cleared)")
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
                // ✅ UPDATED: Pass sortBy and category parameters to backend for pagination
                val sortBy = when (_sortOption.value) {
                    SortOption.HOTTEST -> "hottest"
                    SortOption.NEWEST -> "newest"
                }
                val categoryId = _selectedCategory.value?.id  // null = all categories
                Log.d("Feed", "📄 Loading more deals (page $nextPage, sort: $sortBy, category: ${categoryId ?: "all"})...")
                val result = repo.refreshDeals(page = nextPage, append = true, sortBy = sortBy, category = categoryId)

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

    /**
     * ✅ FIXED: Get optimistic counts only if vote is in progress
     * Returns null if optimistic count would match database count (prevents double-counting)
     */
    fun getOptimisticHotCount(dealId: String): Int? {
        val optimistic = uiState.optimisticCounts[dealId]?.first
        if (optimistic != null) {
            // Only return optimistic count if different from database count
            val dbCount = deals.value.find { it.id == dealId }?.hotCount ?: 0
            return if (optimistic != dbCount) optimistic else null
        }
        return null
    }

    fun getOptimisticColdCount(dealId: String): Int? {
        val optimistic = uiState.optimisticCounts[dealId]?.second
        if (optimistic != null) {
            // Only return optimistic count if different from database count
            val dbCount = deals.value.find { it.id == dealId }?.coldCount ?: 0
            return if (optimistic != dbCount) optimistic else null
        }
        return null
    }

    // ========================================
    // ✅ UPDATED: Vote HOT with User Authentication + Optimistic Update
    // Migration: device_id → user_id
    // ========================================
    /**
     * Cast a HOT vote on a deal
     *
     * Flow (Instagram/YouTube 2025 pattern):
     * 0. Cancel any pending vote request for this deal (Big App Architecture)
     * 1. Check authentication → Show dialog if anonymous
     * 2. Check duplicate vote
     * 3. Optimistic UI update → Instant feedback
     * 4. API call → Server validation
     * 5. Success → Clear optimistic state (server data is source of truth)
     * 6. Failure → Revert changes + show error
     */
    fun voteHot(dealId: String) {
        // ========================================
        // STEP 0: Big App Architecture - Cancel Previous Request
        // ========================================
        // If user rapidly clicks Hot → Cold → Hot, this prevents
        // sending multiple simultaneous requests to the server
        voteJobs[dealId]?.cancel()
        Log.d("FeedViewModel", "🚫 Cancelled any previous vote job for deal: $dealId")

        // Start new job and track it
        voteJobs[dealId] = viewModelScope.launch {
            try {
                Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("FeedViewModel", "🔥 VOTE HOT STARTED")
                Log.d("FeedViewModel", "   DealID: $dealId")
                Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // ========================================
                // STEP 1: Authentication Check
                // ========================================
                val userId = deviceIdManager.getUserId()
                val userEmail = if (userId != null) {
                    userRepo.getCachedUser(userId)?.email
                } else null

                Log.d("FeedViewModel", "📝 STEP 1: Authentication Check")
                Log.d("FeedViewModel", "   UserID: ${userId?.take(8) ?: "NULL"}")
                Log.d("FeedViewModel", "   UserEmail: $userEmail")

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

                Log.d("FeedViewModel", "📝 STEP 2: Determine Vote Action")
                Log.d("FeedViewModel", "   Vote Action: $voteAction")
                Log.d("FeedViewModel", "   Action Description: $actionDescription")
                Log.d("FeedViewModel", "   Existing Vote Type: $existingVoteType")

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

                // ✅ FIX: Use existing optimistic counts as baseline if they exist (prevents race condition during rapid voting)
                val baselineHotCount = uiState.optimisticCounts[dealId]?.first ?: currentHotCount
                val baselineColdCount = uiState.optimisticCounts[dealId]?.second ?: currentColdCount

                Log.d("FeedViewModel", "📝 STEP 3: Optimistic UI Update")
                Log.d("FeedViewModel", "   Current DB Hot Count: $currentHotCount")
                Log.d("FeedViewModel", "   Current DB Cold Count: $currentColdCount")
                Log.d("FeedViewModel", "   🎯 Baseline Hot Count: $baselineHotCount")
                Log.d("FeedViewModel", "   🎯 Baseline Cold Count: $baselineColdCount")

                val optimisticCounts = when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW -> {
                        // New vote: +1 to hot
                        Log.d("FeedViewModel", "   Action: NEW - Adding +1 to hot")
                        Pair(baselineHotCount + 1, baselineColdCount)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        // Switch from cold to hot: -1 cold, +1 hot
                        Log.d("FeedViewModel", "   Action: SWITCH - Moving from cold to hot (-1 cold, +1 hot)")
                        Pair(baselineHotCount + 1, (baselineColdCount - 1).coerceAtLeast(0))
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        // Remove hot vote: -1 hot (with floor at 0)
                        Log.d("FeedViewModel", "   Action: REMOVE - Removing hot vote (-1 hot)")
                        Pair((baselineHotCount - 1).coerceAtLeast(0), baselineColdCount)
                    }
                }

                Log.d("FeedViewModel", "   ✨ Optimistic Hot Count: ${optimisticCounts.first}")
                Log.d("FeedViewModel", "   ✨ Optimistic Cold Count: ${optimisticCounts.second}")

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
                Log.d("FeedViewModel", "📝 STEP 4: API Call")
                Log.d("FeedViewModel", "   Calling repo.castVote()...")

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
                Log.d("FeedViewModel", "📝 STEP 5: Handle Response")
                Log.d("FeedViewModel", "   Result Success: ${result.success}")
                Log.d("FeedViewModel", "   Result Error: ${result.error}")
                Log.d("FeedViewModel", "   Result Data: ${result.data}")

                // ✅ FIX: Store data in local variable to avoid smart cast issue
                val dealData = result.data
                if (result.success == true && dealData != null) {
                    // ✅ SUCCESS: Big App Style - Immediate Consistency
                    Log.d("FeedViewModel", "✅ SUCCESS: Hot vote recorded")

                    // A. INJECT DATA: Push the server's authoritative numbers into Room immediately
                    // This triggers the `deals` Flow to emit the REAL new numbers
                    Log.d("FeedViewModel", "   Injecting server data into DB...")
                    repo.updateLocalDealFromNetwork(dealData)

                    // B. CLEAR OPTIMISTIC: Now that Room has the real data, we can stop faking it.
                    // Because Room already updated in step A, there is no visual "jump" or "flash".
                    Log.d("FeedViewModel", "   Clearing optimistic counts...")
                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)

                    val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                    updatedVotedDeals[dealId] = "hot"

                    uiState = uiState.copy(
                        optimisticCounts = clearedCounts,
                        votedDeals = updatedVotedDeals
                    )

                    Log.d("FeedViewModel", "✨ Seamless transition: Optimistic State -> DB State")

                } else {
                    // ❌ FAILURE: Revert optimistic changes to previous state
                    Log.e("FeedViewModel", "❌ FAILURE: Vote failed")
                    Log.e("FeedViewModel", "   Error: ${result.error}")
                    Log.e("FeedViewModel", "   Reverting to previous state...")

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
        } // End of viewModelScope.launch (Job tracked in voteJobs map)
    }

    // ========================================
    // ✅ UPDATED: Vote COLD with User Authentication (Same Pattern as voteHot)
    // ========================================
    fun voteCold(dealId: String) {
        // ========================================
        // STEP 0: Big App Architecture - Cancel Previous Request
        // ========================================
        voteJobs[dealId]?.cancel()
        Log.d("FeedViewModel", "🚫 Cancelled any previous vote job for deal: $dealId")

        // Start new job and track it
        voteJobs[dealId] = viewModelScope.launch {
            try {
                Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("FeedViewModel", "❄️ VOTE COLD STARTED")
                Log.d("FeedViewModel", "   DealID: $dealId")
                Log.d("FeedViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                val userId = deviceIdManager.getUserId()
                val userEmail = if (userId != null) {
                    userRepo.getCachedUser(userId)?.email
                } else null

                Log.d("FeedViewModel", "📝 STEP 1: Authentication Check")
                Log.d("FeedViewModel", "   UserID: ${userId?.take(8) ?: "NULL"}")
                Log.d("FeedViewModel", "   UserEmail: $userEmail")

                if (userId == null) {
                    Log.d("FeedViewModel", "⚠️ Anonymous user tried to vote - showing auth dialog")
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

                Log.d("FeedViewModel", "📝 STEP 2: Determine Vote Action")
                Log.d("FeedViewModel", "   Vote Action: $voteAction")
                Log.d("FeedViewModel", "   Action Description: $actionDescription")
                Log.d("FeedViewModel", "   Existing Vote Type: $existingVoteType")

                // ========================================
                // STEP 3: Optimistic UI Update (Based on Action)
                // ✅ UPDATED 2025-11-20: Handle +1/-1 for switches
                // ========================================
                val currentDeal = deals.value.find { it.id == dealId } ?: return@launch
                val currentHotCount = currentDeal.hotCount ?: 0
                val currentColdCount = currentDeal.coldCount ?: 0

                // ✅ FIX: Use existing optimistic counts as baseline if they exist (prevents race condition during rapid voting)
                val baselineHotCount = uiState.optimisticCounts[dealId]?.first ?: currentHotCount
                val baselineColdCount = uiState.optimisticCounts[dealId]?.second ?: currentColdCount

                Log.d("FeedViewModel", "📝 STEP 3: Optimistic UI Update")
                Log.d("FeedViewModel", "   Current DB Hot Count: $currentHotCount")
                Log.d("FeedViewModel", "   Current DB Cold Count: $currentColdCount")
                Log.d("FeedViewModel", "   🎯 Baseline Hot Count: $baselineHotCount")
                Log.d("FeedViewModel", "   🎯 Baseline Cold Count: $baselineColdCount")

                val optimisticCounts = when (voteAction) {
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.NEW -> {
                        // New vote: +1 to cold
                        Log.d("FeedViewModel", "   Action: NEW - Adding +1 to cold")
                        Pair(baselineHotCount, baselineColdCount + 1)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.SWITCH -> {
                        // Switch from hot to cold: -1 hot, +1 cold
                        Log.d("FeedViewModel", "   Action: SWITCH - Moving from hot to cold (-1 hot, +1 cold)")
                        Pair((baselineHotCount - 1).coerceAtLeast(0), baselineColdCount + 1)
                    }
                    qa.deals.doha.datastore.DeviceIdManager.VoteAction.REMOVE -> {
                        // Remove cold vote: -1 cold (with floor at 0)
                        Log.d("FeedViewModel", "   Action: REMOVE - Removing cold vote (-1 cold)")
                        Pair(baselineHotCount, (baselineColdCount - 1).coerceAtLeast(0))
                    }
                }

                Log.d("FeedViewModel", "   ✨ Optimistic Hot Count: ${optimisticCounts.first}")
                Log.d("FeedViewModel", "   ✨ Optimistic Cold Count: ${optimisticCounts.second}")

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
                Log.d("FeedViewModel", "📝 STEP 4: API Call")
                Log.d("FeedViewModel", "   Calling repo.castVote()...")

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
                Log.d("FeedViewModel", "📝 STEP 5: Handle Response")
                Log.d("FeedViewModel", "   Result Success: ${result.success}")
                Log.d("FeedViewModel", "   Result Error: ${result.error}")
                Log.d("FeedViewModel", "   Result Data: ${result.data}")

                // ✅ FIX: Store data in local variable to avoid smart cast issue
                val dealData = result.data
                if (result.success == true && dealData != null) {
                    // ✅ SUCCESS: Big App Style - Immediate Consistency
                    Log.d("FeedViewModel", "✅ SUCCESS: Cold vote recorded")

                    // A. INJECT DATA: Push the server's authoritative numbers into Room immediately
                    // This triggers the `deals` Flow to emit the REAL new numbers
                    Log.d("FeedViewModel", "   Injecting server data into DB...")
                    repo.updateLocalDealFromNetwork(dealData)

                    // B. CLEAR OPTIMISTIC: Now that Room has the real data, we can stop faking it.
                    // Because Room already updated in step A, there is no visual "jump" or "flash".
                    Log.d("FeedViewModel", "   Clearing optimistic counts...")
                    val clearedCounts = uiState.optimisticCounts.toMutableMap()
                    clearedCounts.remove(dealId)

                    val updatedVotedDeals = uiState.votedDeals.toMutableMap()
                    updatedVotedDeals[dealId] = "cold"

                    uiState = uiState.copy(
                        optimisticCounts = clearedCounts,
                        votedDeals = updatedVotedDeals
                    )

                    Log.d("FeedViewModel", "✨ Seamless transition: Optimistic State -> DB State")

                } else {
                    // ❌ FAILURE: Revert optimistic changes to previous state
                    Log.e("FeedViewModel", "❌ FAILURE: Vote failed")
                    Log.e("FeedViewModel", "   Error: ${result.error}")
                    Log.e("FeedViewModel", "   Reverting to previous state...")

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
        } // End of viewModelScope.launch (Job tracked in voteJobs map)
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