package eg.deals.radar.feature.feed

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.deals.domain.DealCategory
import eg.deals.domain.Governorate
import eg.deals.radar.auth.AuthManager
import eg.deals.radar.datastore.DeviceIdManager
import eg.deals.radar.db.DealEntity
import eg.deals.radar.network.ApiErrors
import eg.deals.radar.repository.DealRepository
import eg.deals.radar.repository.PreloadRepository
import eg.deals.radar.db.feedKey
import eg.deals.radar.repository.UserRepository
import eg.deals.radar.util.AppLanguage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Feed sort order (backend sorts). */
enum class SortOption(val apiValue: String) {
    HOTTEST("hottest"),
    NEWEST("newest"),
    TOP_WEEK("top_week")
}

/** A vote waiting for the user to log in. */
data class PendingVote(val dealId: String, val voteType: String)

data class FeedUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val isOffline: Boolean = false,
    val votedDeals: Map<String, String> = emptyMap(),
    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false,
    val showModeratorButton: Boolean = false,
    val isFilteringSorting: Boolean = false,
    val showVoteAuthDialog: Boolean = false,
    val pendingVote: PendingVote? = null,
    /** One-off message for a snackbar (vote refused, etc.). Cleared by the screen. */
    val message: String? = null,
    /** True once the first network load finished (to tell "empty" from "not loaded yet"). */
    val loadedOnce: Boolean = false
)

/**
 * ========================================
 * 🏠 FEED
 * ========================================
 * - Backend does filtering (category, governorate, search), sorting and
 *   cursor pagination; Room caches the result for instant/offline display.
 * - Votes: instant local update, then the server's answer (user_vote +
 *   counts) is applied. Same vote again = remove. Clear messages on refusal.
 */
class FeedViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context),
    private val preloadRepo: PreloadRepository = PreloadRepository.getInstance(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<DealCategory?>(null)
    val selectedCategory: StateFlow<DealCategory?> = _selectedCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.HOTTEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _governorate = MutableStateFlow<Governorate?>(null)
    val governorate: StateFlow<Governorate?> = _governorate.asStateFlow()

    val isAuthenticated: StateFlow<Boolean> = deviceIdManager.userIdFlow
        .map { it != null && AuthManager.isLoggedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentUserId: StateFlow<String?> = deviceIdManager.userIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentUserRole: StateFlow<String> = deviceIdManager.userIdFlow
        .map { id -> id?.let { userRepo.getCachedUser(it)?.role } ?: "user" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "user")

    val isModerator: StateFlow<Boolean> = deviceIdManager.userIdFlow
        .map { id -> id != null && userRepo.isModerator(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAdmin: StateFlow<Boolean> = deviceIdManager.userIdFlow
        .map { id -> id != null && userRepo.isAdmin(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Cache key of the tab on screen (filters + search); every tab keeps its own cached list. */
    private val _feedKey = MutableStateFlow(currentKey())

    /** Cached deals of the current tab (Room is the source for the UI). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val deals: StateFlow<List<DealEntity>> = _feedKey
        .flatMapLatest { repo.observeFeed(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Governorate the category tabs were prefetched for ("" = everywhere); null = not yet. */
    private var prefetchedFor: String? = null

    var uiState by mutableStateOf(FeedUiState())
        private set

    private val voteMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingVoteIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        // Deals preloaded during onboarding go into the default tab first
        viewModelScope.launch {
            val preloaded = preloadRepo.getCachedDeals()
            if (!preloaded.isNullOrEmpty() && isDefaultFilter()) {
                runCatching { repo.insertPreloadedDeals(preloaded) }
                preloadRepo.clearCache()
            }
            openTab()
        }

        // Vote state comes from the server (deal.userVote) - the same on every device
        viewModelScope.launch {
            deals.collect { list ->
                if (!AuthManager.isLoggedIn) {
                    if (uiState.votedDeals.isNotEmpty()) uiState = uiState.copy(votedDeals = emptyMap())
                    return@collect
                }
                val fromServer = list.mapNotNull { d -> d.userVote?.let { d.id to it } }.toMap()
                // Keep in-flight optimistic votes for deals the server hasn't confirmed yet
                val merged = fromServer + uiState.votedDeals.filterKeys { it in pendingVoteIds }
                if (merged != uiState.votedDeals) uiState = uiState.copy(votedDeals = merged)
            }
        }

        viewModelScope.launch { isModerator.collect { uiState = uiState.copy(showModeratorButton = it) } }

        viewModelScope.launch {
            val userId = deviceIdManager.getUserId()
            if (userId != null && AuthManager.isLoggedIn && userRepo.getCachedUser(userId) == null) {
                runCatching { userRepo.fetchUserProfile(userId) }
            }
        }
    }

    // ========================================
    // 🔎 Filters & search
    // ========================================

    /** Search runs on the server (Arabic/English, typo-tolerant); debounced while typing. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(if (query.isBlank()) 0 else 400)
            openTab()
        }
    }

    fun toggleCategory(category: DealCategory?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
        openTab()
    }

    fun setGovernorate(governorate: Governorate?) {
        if (_governorate.value == governorate) return
        _governorate.value = governorate
        openTab()
    }

    fun toggleSortToNewest() {
        _sortOption.value = if (_sortOption.value == SortOption.NEWEST) SortOption.HOTTEST else SortOption.NEWEST
        openTab()
    }

    fun toggleSortToTopWeek() {
        _sortOption.value = if (_sortOption.value == SortOption.TOP_WEEK) SortOption.HOTTEST else SortOption.TOP_WEEK
        openTab()
    }

    fun setSortToAll() {
        if (_sortOption.value != SortOption.HOTTEST) {
            _sortOption.value = SortOption.HOTTEST
            openTab()
        }
    }

    fun clearMessage() {
        if (uiState.message != null) uiState = uiState.copy(message = null)
    }

    // ========================================
    // 🔄 Loading
    // ========================================

    /** The filters of one request, captured so a slow answer can't mix tabs. */
    private data class Filters(val sortBy: String, val category: String?, val governorate: String?, val query: String?) {
        val key get() = feedKey(sortBy, category, governorate, query)
    }

    private fun currentFilters() = Filters(
        _sortOption.value.apiValue,
        _selectedCategory.value?.id,
        _governorate.value?.id,
        _searchQuery.value.trim().takeIf { it.isNotEmpty() }
    )

    private fun currentKey() = currentFilters().key

    /**
     * Shows the current tab: its cached list appears at once. The network is only
     * used when the tab was never loaded or its data is older than [FRESH_MS]; a
     * cached tab refreshes silently in the background.
     */
    private fun openTab() {
        val filters = currentFilters()
        _feedKey.value = filters.key
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        uiState = uiState.copy(isLoadingMore = false, error = null)
        refreshJob = viewModelScope.launch {
            val meta = repo.getFeedMeta(filters.key)
            if (meta != null) {
                uiState = uiState.copy(
                    loading = false, isFilteringSorting = false, loadedOnce = true,
                    currentPage = meta.pagesLoaded, hasMorePages = meta.hasMore
                )
                if (System.currentTimeMillis() - meta.updatedAt < FRESH_MS) return@launch
            }
            load(filters, spinner = meta == null && uiState.loadedOnce, silent = meta != null)
        }
    }

    /** Pull-to-refresh / retry: always reloads the current tab from the network. */
    fun refreshDeals(showFilteringSpinner: Boolean = false) {
        val filters = currentFilters()
        _feedKey.value = filters.key
        refreshJob?.cancel()
        loadMoreJob?.cancel()
        uiState = uiState.copy(isLoadingMore = false)
        refreshJob = viewModelScope.launch { load(filters, spinner = showFilteringSpinner, silent = false) }
    }

    private suspend fun load(filters: Filters, spinner: Boolean, silent: Boolean) {
        uiState = uiState.copy(loading = !silent, isFilteringSorting = spinner, error = null)
        val result = repo.refreshDeals(
            page = 1, append = false,
            sortBy = filters.sortBy, category = filters.category,
            governorate = filters.governorate, query = filters.query
        )
        // The answer is cached for its tab either way; only update the screen if it's still showing it
        if (_feedKey.value != filters.key) return
        result.onSuccess { pagination ->
            uiState = uiState.copy(
                loading = false, isFilteringSorting = false, isOffline = false, loadedOnce = true,
                currentPage = 1, hasMorePages = pagination?.hasMore ?: false
            )
            maybePrefetchCategoryTabs(filters)
        }.onFailure { error ->
            val offline = (error.cause as? java.io.IOException) != null
            uiState = uiState.copy(
                loading = false, isFilteringSorting = false, loadedOnce = true,
                isOffline = offline,
                // A cached tab stays on screen; only an empty tab shows the error
                error = if (deals.value.isNotEmpty() && (offline || silent)) null else error.message
            )
        }
    }

    /**
     * After the main feed loads, fetch page 1 of every category in ONE request
     * (once per governorate per session) so the first tap on any category is instant.
     */
    private fun maybePrefetchCategoryTabs(filters: Filters) {
        if (filters.sortBy != SortOption.HOTTEST.apiValue || filters.query != null) return
        val gov = filters.governorate ?: ""
        if (prefetchedFor == gov) return
        prefetchedFor = gov
        viewModelScope.launch {
            delay(800) // let the visible page's images load first
            repo.prefetchCategoryTabs(filters.governorate)
        }
    }

    fun loadMoreDeals() {
        if (uiState.isLoadingMore || !uiState.hasMorePages || uiState.loading) return
        val filters = currentFilters()
        loadMoreJob = viewModelScope.launch {
            val nextPage = uiState.currentPage + 1
            uiState = uiState.copy(isLoadingMore = true)
            val result = repo.refreshDeals(
                page = nextPage, append = true,
                sortBy = filters.sortBy, category = filters.category,
                governorate = filters.governorate, query = filters.query
            )
            if (_feedKey.value != filters.key) return@launch
            result.onSuccess { pagination ->
                uiState = uiState.copy(isLoadingMore = false, currentPage = nextPage, hasMorePages = pagination?.hasMore ?: false)
            }.onFailure {
                uiState = uiState.copy(isLoadingMore = false)
            }
        }
    }

    private fun isDefaultFilter() =
        _selectedCategory.value == null && _governorate.value == null &&
            _sortOption.value == SortOption.HOTTEST && _searchQuery.value.isBlank()

    // ========================================
    // 🔥 Voting
    // ========================================

    fun hasVoted(dealId: String): Boolean = uiState.votedDeals.containsKey(dealId)
    fun getVoteType(dealId: String): String? = uiState.votedDeals[dealId]

    fun onVoteClicked(dealId: String, voteType: String) {
        if (!AuthManager.isLoggedIn || deviceIdManager.getUserId() == null) {
            uiState = uiState.copy(showVoteAuthDialog = true, pendingVote = PendingVote(dealId, voteType))
            return
        }
        val deal = deals.value.find { it.id == dealId } ?: return
        val mutex = voteMutexes.getOrPut(dealId) { Mutex() }

        viewModelScope.launch {
            // Optimistic update (instant feedback)
            val oldVote = uiState.votedDeals[dealId]
            val oldHot = deal.hotCount ?: 0
            val oldCold = deal.coldCount ?: 0
            val removing = oldVote == voteType
            val newVote = if (removing) null else voteType
            val newHot = oldHot + (if (newVote == "hot") 1 else 0) - (if (oldVote == "hot") 1 else 0)
            val newCold = oldCold + (if (newVote == "cold") 1 else 0) - (if (oldVote == "cold") 1 else 0)

            pendingVoteIds.add(dealId)
            setLocalVote(dealId, newVote)
            repo.updateDealCountsLocal(dealId, newHot.coerceAtLeast(0), newCold.coerceAtLeast(0))

            mutex.withLock {
                val result = runCatching { repo.castVote(dealId = dealId, voteType = voteType) }
                val envelope = result.getOrNull()
                val data = envelope?.data
                if (envelope?.success == true && data != null) {
                    // Server is the source of truth for counts and the user's vote
                    repo.updateLocalDealFromNetwork(data.copy(userVote = envelope.userVote))
                    setLocalVote(dealId, envelope.userVote)
                } else {
                    // Roll back and explain
                    setLocalVote(dealId, oldVote)
                    repo.updateDealCountsLocal(dealId, oldHot, oldCold)
                    when (envelope?.code) {
                        ApiErrors.UNAUTHORIZED -> uiState = uiState.copy(
                            showVoteAuthDialog = true, pendingVote = PendingVote(dealId, voteType)
                        )
                        null -> uiState = uiState.copy(message = ApiErrors.message(result.exceptionOrNull() ?: Exception()))
                        else -> uiState = uiState.copy(message = ApiErrors.message(envelope, ApiErrors.Context.VOTE))
                    }
                }
            }
            pendingVoteIds.remove(dealId)
        }
    }

    private fun setLocalVote(dealId: String, vote: String?) {
        val map = uiState.votedDeals.toMutableMap()
        if (vote == null) map.remove(dealId) else map[dealId] = vote
        uiState = uiState.copy(votedDeals = map)
    }

    fun dismissVoteAuthDialog() {
        uiState = uiState.copy(showVoteAuthDialog = false, pendingVote = null)
    }

    /** Called after logging in from the vote prompt. */
    fun retryPendingVote() {
        val pending = uiState.pendingVote ?: return
        uiState = uiState.copy(showVoteAuthDialog = false, pendingVote = null)
        onVoteClicked(pending.dealId, pending.voteType)
    }

    // ========================================
    // 🗑️ Admin: permanent delete
    // ========================================

    fun permanentDeleteDeal(dealId: String) {
        viewModelScope.launch {
            val userId = deviceIdManager.getUserId() ?: return@launch
            repo.permanentDeleteDeal(dealId = dealId, userId = userId)
                .onSuccess { refreshDeals() }
                .onFailure { uiState = uiState.copy(message = it.message ?: AppLanguage.string(eg.deals.radar.core.data.R.string.err_server)) }
        }
    }

    companion object {
        /** A cached tab younger than this is shown without asking the server again. */
        private const val FRESH_MS = 60_000L
    }

    override fun onCleared() {
        Log.d("FeedViewModel", "cleared")
        super.onCleared()
    }
}
