package qa.deals.doha.feature.feed.moderator

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import qa.deals.doha.db.DealEntity
import qa.deals.doha.network.PaginationMeta
import qa.deals.doha.network.ReportWithDetailsDto
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
    val actionError: String? = null,
    // ✅ NEW: Reports state (2025-11-22)
    val reportsCurrentPage: Int = 1,
    val reportsHasMorePages: Boolean = true,
    val isLoadingReports: Boolean = false,
    val isLoadingMoreReports: Boolean = false
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

    // ✅ NEW: Reports state (2025-11-22)
    private val _reports = MutableStateFlow<List<ReportWithDetailsDto>>(emptyList())
    val reports: StateFlow<List<ReportWithDetailsDto>> = _reports.asStateFlow()

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
        val wasModerator = _uiState.value.isModerator
        val isModerator = role == "moderator" || role == "admin"

        _uiState.update { it.copy(
            currentUserRole = role,
            isModerator = isModerator,
            isAdmin = role == "admin"
        )}
        Log.d("ModeratorVM", "User role updated: $role (isModerator=$isModerator)")

        // ✅ FIX: Automatically fetch pending deals when role loads and user is moderator
        if (!wasModerator && isModerator && _currentUserId.value != null) {
            Log.d("ModeratorVM", "Role loaded as moderator, fetching pending deals")
            refreshPendingDeals()
        }
    }

    /**
     * Request reports refresh from external trigger
     * This is called by ReportsScreen when it first loads
     */
    fun requestReportsRefresh() {
        Log.d("ModeratorVM", "Reports refresh requested, isModerator=${_uiState.value.isModerator}, userId=${_currentUserId.value?.take(8)}")

        if (_uiState.value.isModerator && _currentUserId.value != null) {
            // Role already loaded, fetch immediately
            refreshReports()
        } else {
            // Role not loaded yet - refreshReports will be called automatically
            // when updateRole() detects moderator/admin status
            Log.d("ModeratorVM", "Role not yet loaded, will fetch reports after role loads")
        }
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

    // ========================================
    // ✅ NEW: REPORTS MANAGEMENT (2025-11-22)
    // Functions for viewing and managing user-submitted reports
    // ========================================

    /**
     * Refresh reports from API
     * Fetches the first page of reports and replaces existing list
     */
    fun refreshReports() {
        val userId = _currentUserId.value
        Log.d("ModeratorVM", "refreshReports() called - userId=${userId?.take(8)}, isModerator=${_uiState.value.isModerator}")

        if (userId == null) {
            Log.w("ModeratorVM", "❌ Cannot refresh reports: user ID not set")
            return
        }

        if (!_uiState.value.isModerator) {
            Log.w("ModeratorVM", "❌ User is not a moderator, cannot fetch reports (role=${_uiState.value.currentUserRole})")
            _uiState.update { it.copy(error = "You don't have permission to view reports") }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingReports = true, error = null) }
                Log.d("ModeratorVM", "🚨 Fetching reports for user: $userId")

                val result = dealRepo.getReports(
                    userId = userId,
                    page = 1,
                    limit = 20
                )

                if (result.isSuccess) {
                    val reportsList = result.getOrNull() ?: emptyList()
                    _reports.value = reportsList

                    _uiState.update { it.copy(
                        isLoadingReports = false,
                        reportsCurrentPage = 1,
                        reportsHasMorePages = reportsList.size >= 20
                    )}
                    Log.d("ModeratorVM", "Reports fetched successfully: ${reportsList.size} items")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to fetch reports"
                    _uiState.update { it.copy(isLoadingReports = false, error = error) }
                    Log.e("ModeratorVM", "Failed to fetch reports: $error")
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error refreshing reports", e)
                _uiState.update { it.copy(isLoadingReports = false, error = "Error: ${e.message}") }
            }
        }
    }

    /**
     * Load more reports (pagination)
     * Appends the next page of reports to the existing list
     */
    fun loadMoreReports() {
        val userId = _currentUserId.value ?: return
        if (!_uiState.value.reportsHasMorePages || _uiState.value.isLoadingMoreReports) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingMoreReports = true) }
                val nextPage = _uiState.value.reportsCurrentPage + 1

                Log.d("ModeratorVM", "Loading more reports, page: $nextPage")

                val result = dealRepo.getReports(
                    userId = userId,
                    page = nextPage,
                    limit = 20
                )

                if (result.isSuccess) {
                    val newReports = result.getOrNull() ?: emptyList()
                    _reports.value = _reports.value + newReports

                    _uiState.update { it.copy(
                        isLoadingMoreReports = false,
                        reportsCurrentPage = nextPage,
                        reportsHasMorePages = newReports.size >= 20
                    )}
                    Log.d("ModeratorVM", "Loaded ${newReports.size} more reports")
                } else {
                    _uiState.update { it.copy(isLoadingMoreReports = false) }
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error loading more reports", e)
                _uiState.update { it.copy(isLoadingMoreReports = false) }
            }
        }
    }

    /**
     * Dismiss a report without taking action
     * Removes the report from the list
     */
    fun dismissReport(reportId: String, reason: String? = null) {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w("ModeratorVM", "Cannot dismiss report: user ID not set")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    actionInProgress = true,
                    actionError = null,
                    actionSuccess = null
                )}

                Log.d("ModeratorVM", "Dismissing report: $reportId")

                val result = dealRepo.dismissReport(reportId, userId, reason)

                if (result.isSuccess) {
                    // Remove dismissed report from list
                    _reports.value = _reports.value.filter { it.id != reportId }

                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionSuccess = "Report dismissed"
                    )}
                    Log.d("ModeratorVM", "Report dismissed: $reportId")

                    clearActionMessage()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to dismiss report"
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionError = error
                    )}
                    Log.e("ModeratorVM", "Failed to dismiss report: $error")
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error dismissing report", e)
                _uiState.update { it.copy(
                    actionInProgress = false,
                    actionError = "Error: ${e.message}"
                )}
            }
        }
    }

    /**
     * Resolve a report with action
     * Takes specific action on the reported content
     */
    fun resolveReport(reportId: String, action: String, reason: String? = null) {
        val userId = _currentUserId.value
        if (userId == null) {
            Log.w("ModeratorVM", "Cannot resolve report: user ID not set")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    actionInProgress = true,
                    actionError = null,
                    actionSuccess = null
                )}

                Log.d("ModeratorVM", "Resolving report: $reportId with action: $action")

                val result = dealRepo.resolveReport(reportId, userId, action, reason)

                if (result.isSuccess) {
                    // Remove resolved report from list
                    _reports.value = _reports.value.filter { it.id != reportId }

                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionSuccess = "Report resolved - $action"
                    )}
                    Log.d("ModeratorVM", "Report resolved: $reportId")

                    clearActionMessage()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to resolve report"
                    _uiState.update { it.copy(
                        actionInProgress = false,
                        actionError = error
                    )}
                    Log.e("ModeratorVM", "Failed to resolve report: $error")
                }
            } catch (e: Exception) {
                Log.e("ModeratorVM", "Error resolving report", e)
                _uiState.update { it.copy(
                    actionInProgress = false,
                    actionError = "Error: ${e.message}"
                )}
            }
        }
    }

    /**
     * Get the current count of reports
     * Useful for displaying badges in the UI
     */
    fun getReportsCount(): Int {
        return _reports.value.size
    }
}
