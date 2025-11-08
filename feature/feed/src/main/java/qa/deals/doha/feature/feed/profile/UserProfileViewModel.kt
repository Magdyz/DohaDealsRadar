package qa.deals.doha.feature.feed.profile

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import qa.deals.doha.db.DealEntity
import qa.deals.doha.db.UserEntity
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.repository.UserRepository

/**
 * UI State for User Profile Screen
 */
data class UserProfileUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val user: UserEntity? = null,
    val dealsLoading: Boolean = false,
    val dealsError: String? = null
)

/**
 * ViewModel for User Profile Screen
 * Displays user information, role, and their submitted deals
 */
class UserProfileViewModel(
    private val context: Context,
    private val userRepo: UserRepository = UserRepository(),
    private val dealRepo: DealRepository = DealRepository()
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    // Current user being viewed
    private val _userId = MutableStateFlow<String?>(null)

    // User's submitted deals (reactive from local cache)
    val userDeals: StateFlow<List<DealEntity>> = _userId
        .filterNotNull()
        .flatMapLatest { userId ->
            dealRepo.getCachedDealsByUser(userId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        Log.d("UserProfileVM", "Initializing UserProfileViewModel")
    }

    /**
     * Load user profile by ID
     * Tries cache first, then fetches from API if needed
     */
    fun loadUserProfile(userId: String) {
        _userId.value = userId

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(loading = true, error = null) }
                Log.d("UserProfileVM", "Loading profile for user: $userId")

                // Try cache first
                val cachedUser = userRepo.getCachedUser(userId)

                if (cachedUser != null) {
                    Log.d("UserProfileVM", "User found in cache: ${cachedUser.username}")
                    _uiState.update { it.copy(loading = false, user = cachedUser) }
                } else {
                    // Fetch from API
                    Log.d("UserProfileVM", "User not in cache, fetching from API...")
                    val result = userRepo.getUserProfile(userId)

                    if (result.isSuccess) {
                        val user = result.getOrNull()
                        _uiState.update { it.copy(loading = false, user = user) }
                        Log.d("UserProfileVM", "User profile loaded: ${user?.username}")
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Failed to load user profile"
                        _uiState.update { it.copy(loading = false, error = error) }
                        Log.e("UserProfileVM", "Failed to load user profile: $error")
                    }
                }

                // Also fetch user's deals
                fetchUserDeals(userId)

            } catch (e: Exception) {
                Log.e("UserProfileVM", "Error loading user profile", e)
                _uiState.update { it.copy(loading = false, error = "Error: ${e.message}") }
            }
        }
    }

    /**
     * Fetch user's submitted deals from API
     */
    private fun fetchUserDeals(userId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(dealsLoading = true, dealsError = null) }
                Log.d("UserProfileVM", "Fetching deals for user: $userId")

                val result = dealRepo.getDealsByUser(
                    requestingUserId = userId, // User viewing their own profile
                    targetUserId = userId,
                    page = 1
                )

                if (result.isSuccess) {
                    _uiState.update { it.copy(dealsLoading = false) }
                    Log.d("UserProfileVM", "User deals fetched successfully")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to load deals"
                    _uiState.update { it.copy(dealsLoading = false, dealsError = error) }
                    Log.e("UserProfileVM", "Failed to fetch user deals: $error")
                }
            } catch (e: Exception) {
                Log.e("UserProfileVM", "Error fetching user deals", e)
                _uiState.update { it.copy(dealsLoading = false, dealsError = "Error: ${e.message}") }
            }
        }
    }

    /**
     * Refresh user profile and deals
     */
    fun refresh() {
        val userId = _userId.value
        if (userId != null) {
            loadUserProfile(userId)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null, dealsError = null) }
    }

    // ========================================
    // COMPUTED PROPERTIES FOR UI
    // ========================================

    /**
     * Check if user is a moderator
     */
    val isModerator: StateFlow<Boolean> = _uiState
        .map { state ->
            state.user?.role == "moderator" || state.user?.role == "admin"
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Check if user is an admin
     */
    val isAdmin: StateFlow<Boolean> = _uiState
        .map { state -> state.user?.role == "admin" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Check if user has auto-approve privilege
     */
    val hasAutoApprove: StateFlow<Boolean> = _uiState
        .map { state -> state.user?.autoApprove == true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Get role display name
     */
    val roleDisplayName: StateFlow<String> = _uiState
        .map { state ->
            when (state.user?.role) {
                "admin" -> "Admin"
                "moderator" -> "Moderator"
                "user" -> if (state.user.autoApprove) "Trusted User" else "User"
                else -> "User"
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "User"
        )

    /**
     * Get role badge color (for UI)
     */
    fun getRoleBadgeColor(): String {
        return when (_uiState.value.user?.role) {
            "admin" -> "#DC2626" // Red
            "moderator" -> "#2563EB" // Blue
            else -> if (_uiState.value.user?.autoApprove == true) "#10B981" else "#6B7280" // Green or Gray
        }
    }

    /**
     * Get statistics for display
     */
    val userStats: StateFlow<UserStats> = combine(
        _uiState,
        userDeals
    ) { state, deals ->
        UserStats(
            totalDeals = deals.size,
            approvedDeals = state.user?.approvedDealsCount ?: 0,
            pendingDeals = deals.count { it.status == "pending" },
            rejectedDeals = deals.count { it.status == "rejected" }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserStats()
    )
}

/**
 * User statistics for profile display
 */
data class UserStats(
    val totalDeals: Int = 0,
    val approvedDeals: Int = 0,
    val pendingDeals: Int = 0,
    val rejectedDeals: Int = 0
)
