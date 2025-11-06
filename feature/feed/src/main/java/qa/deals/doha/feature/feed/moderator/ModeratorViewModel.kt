package qa.deals.doha.feature.feed.moderator

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import qa.deals.doha.db.DealEntity
import qa.deals.doha.network.PaginationMeta
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.repository.UserRepository

/**
 * UI State for Moderator Dashboard
 */
data class ModeratorUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val currentUserRole: String? = null,
    val isModerator: Boolean = false,
    val isAdmin: Boolean = false,
    val currentPage: Int = 1,
    val hasMorePages: Boolean = true,
    val isLoadingMore: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionSuccess: String? = null,
    val actionError: String? = null
)

/**
 * ViewModel for Moderator Dashboard
 * Manages pending deals queue and moderation actions
 */
class ModeratorViewModel(
    private val context: Context,
    private val dealRepo: DealRepository = DealRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    // Current user ID (in production, get from authentication manager)
    // TODO: Replace with actual authenticated user ID
    private val _currentUserId = MutableStateFlow<String?>(null)

    // UI State
    private val _uiState = MutableStateFlow(ModeratorUiState())
    val uiState: StateFlow<ModeratorUiState> = _uiState.asStateFlow()

    // Pending deals from local cache (reactive updates)
    val pendingDeals: StateFlow<List<DealEntity>> = dealRepo.getCachedPendingDeals()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        Log.d("ModeratorVM", "Initializing ModeratorViewModel")
    }

    /**
     * Set the current user ID and load their role
     * Call this when user logs in or ViewModel is created
     */
    fun setCurrentUser(userId: String) {
        _currentUserId.value = userId
        loadUserRole(userId)
        refreshPendingDeals()
    }

    /**
     * Load current user's role from cache/API
     */
    private fun loadUserRole(userId: String) {
        viewModelScope.launch {
            try {
                Log.d("ModeratorVM", "Loading role for user: $userId")

                // Try cache first
                val cachedRole = userRepo.getCachedUserRole(userId)

                if (cachedRole != null) {
                    updateRole(cachedRole)
                } else {
                    // Fetch from API
                    val result = userRepo.fetchUserProfile(userId)
                    if (result.isSuccess) {
                        val userDto = result.getOrNull()
                        updateRole(userDto?.role ?: "user")
                    } else {
                        Log.e("ModeratorVM", "Failed to fetch user role: ${result.exceptionOrNull()}")
                        _uiState.update { it.copy(
                            error = "Failed to load user role"
                        )}
                    }
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error loading user role", e)
                _uiState.update { it.copy(error = "Error loading user role: ${e.message}") }
            }
        }
    }

    /**
     * Update role state
     */
    private fun updateRole(role: String) {
        _uiState.update { it.copy(
            currentUserRole = role,
            isModerator = role == "moderator" || role == "admin",
            isAdmin = role == "admin"
        )}
        Log.d("ModeratorVM", "User role updated: $role (isModerator=${_uiState.value.isModerator})")
    }

    /**
     * Refresh pending deals from API
     */
    fun refreshPendingDeals() {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w("ModeratorVM", "Cannot refresh pending deals: user ID not set")
            return
        }

        if (!_uiState.value.isModerator) {
            Log.w("ModeratorVM", "User is not a moderator, cannot fetch pending deals")
            _uiState.update { it.copy(error = "You don't have permission to view pending deals") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(loading = true, error = null) }
                Log.d("ModeratorVM", "Fetching pending deals for user: $userId")

                val result = dealRepo.getPendingDeals(
                    userId = userId,
                    page = 1,
                    append = false
                )

                if (result.isSuccess) {
                    val pagination = result.getOrNull()
                    _uiState.update { it.copy(
                        loading = false,
                        currentPage = pagination?.page ?: 1,
                        hasMorePages = pagination?.hasMore ?: false
                    )}
                    Log.d("ModeratorVM", "Pending deals fetched successfully")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to fetch pending deals"
                    _uiState.update { it.copy(loading = false, error = error) }
                    Log.e("ModeratorVM", "Failed to fetch pending deals: $error")
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error refreshing pending deals", e)
                _uiState.update { it.copy(loading = false, error = "Error: ${e.message}") }
            }
        }
    }

    /**
     * Load more pending deals (pagination)
     */
    fun loadMorePendingDeals() {
        val userId = _currentUserId.value ?: return
        if (!_uiState.value.hasMorePages || _uiState.value.isLoadingMore) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingMore = true) }
                val nextPage = _uiState.value.currentPage + 1

                Log.d("ModeratorVM", "Loading more pending deals, page: $nextPage")

                val result = dealRepo.getPendingDeals(
                    userId = userId,
                    page = nextPage,
                    append = true // Append to existing cache
                )

                if (result.isSuccess) {
                    val pagination = result.getOrNull()
                    _uiState.update { it.copy(
                        isLoadingMore = false,
                        currentPage = nextPage,
                        hasMorePages = pagination?.hasMore ?: false
                    )}
                } else {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error loading more pending deals", e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /**
     * Approve a pending deal
     */
    fun approveDeal(dealId: String) {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w("ModeratorVM", "Cannot approve deal: user ID not set")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    actionInProgress = true,
                    actionError = null,
                    actionSuccess = null
                )}

                Log.d("ModeratorVM", "Approving deal: $dealId")

                val result = dealRepo.approveDeal(dealId, userId)

                if (result.isSuccess) {
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionSuccess = "Deal approved successfully"
                    )}
                    Log.d("ModeratorVM", "Deal approved: $dealId")

                    // Clear success message after 3 seconds
                    clearActionMessage()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to approve deal"
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionError = error
                    )}
                    Log.e("ModeratorVM", "Failed to approve deal: $error")
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error approving deal", e)
                _uiState.update { it.copy(
                    actionInProgress = false,
                    actionError = "Error: ${e.message}"
                )}
            }
        }
    }

    /**
     * Reject a pending deal
     */
    fun rejectDeal(dealId: String, reason: String? = null) {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w("ModeratorVM", "Cannot reject deal: user ID not set")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    actionInProgress = true,
                    actionError = null,
                    actionSuccess = null
                )}

                Log.d("ModeratorVM", "Rejecting deal: $dealId")

                val result = dealRepo.rejectDeal(dealId, userId, reason)

                if (result.isSuccess) {
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionSuccess = "Deal rejected"
                    )}
                    Log.d("ModeratorVM", "Deal rejected: $dealId")

                    clearActionMessage()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to reject deal"
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionError = error
                    )}
                    Log.e("ModeratorVM", "Failed to reject deal: $error")
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error rejecting deal", e)
                _uiState.update { it.copy(
                    actionInProgress = false,
                    actionError = "Error: ${e.message}"
                )}
            }
        }
    }

    /**
     * Delete a deal (soft delete)
     */
    fun deleteDeal(dealId: String, reason: String? = null) {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w("ModeratorVM", "Cannot delete deal: user ID not set")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    actionInProgress = true,
                    actionError = null,
                    actionSuccess = null
                )}

                Log.d("ModeratorVM", "Deleting deal: $dealId")

                val result = dealRepo.deleteDeal(dealId, userId, reason)

                if (result.isSuccess) {
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionSuccess = "Deal deleted"
                    )}
                    Log.d("ModeratorVM", "Deal deleted: $dealId")

                    clearActionMessage()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to delete deal"
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionError = error
                    )}
                    Log.e("ModeratorVM", "Failed to delete deal: $error")
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error deleting deal", e)
                _uiState.update { it.copy(
                    actionInProgress = false,
                    actionError = "Error: ${e.message}"
                )}
            }
        }
    }

    /**
     * Clear action success/error messages after delay
     */
    private fun clearActionMessage() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000) // 3 seconds
            _uiState.update { it.copy(
                actionSuccess = null,
                actionError = null
            )}
        }
    }

    /**
     * Clear error message manually
     */
    fun clearError() {
        _uiState.update { it.copy(error = null, actionError = null) }
    }
}
