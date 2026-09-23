package eg.deals.radar.feature.feed.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.deals.radar.network.AdminUserDto
import eg.deals.radar.repository.AdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI state for UserManagementScreen
 * CREATED: 2025-11-27
 */
data class UserManagementUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val filter: String? = null, // null = All
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
    val actionSuccess: String? = null
)

/**
 * ViewModel for admin user management (admin_users + update_user_role functions).
 * Search is debounced 400ms; filters trigger an immediate refresh.
 */
class UserManagementViewModel(
    private val repo: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserManagementUiState())
    val uiState: StateFlow<UserManagementUiState> = _uiState.asStateFlow()

    private val _items = MutableStateFlow<List<AdminUserDto>>(emptyList())
    val items: StateFlow<List<AdminUserDto>> = _items.asStateFlow()

    private val _queryInput = MutableStateFlow("")

    init {
        viewModelScope.launch {
            _queryInput
                .debounce(400)
                .distinctUntilChanged()
                .collect { refresh() }
        }
        refresh()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        _queryInput.value = query
    }

    fun setFilter(filter: String?) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val state = _uiState.value
            val result = repo.listUsers(state.query.takeIf { it.isNotBlank() }, state.filter, page = 1)
            if (result.isSuccess) {
                val page = result.getOrNull()
                _items.value = page?.items ?: emptyList()
                _uiState.update {
                    it.copy(loading = false, page = 1, hasMore = page?.pagination?.hasMore ?: false)
                }
            } else {
                val message = result.exceptionOrNull()?.message ?: "Failed to load users"
                Log.e("UserManagementVM", "Failed to load users: $message")
                _uiState.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val state = _uiState.value
            val nextPage = state.page + 1
            val result = repo.listUsers(state.query.takeIf { it.isNotBlank() }, state.filter, page = nextPage)
            if (result.isSuccess) {
                val page = result.getOrNull()
                _items.value = _items.value + (page?.items ?: emptyList())
                _uiState.update {
                    it.copy(isLoadingMore = false, page = nextPage, hasMore = page?.pagination?.hasMore ?: false)
                }
            } else {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun banUser(targetUserId: String, reason: String?) = runUserAction {
        repo.banUser(targetUserId, reason)
    }

    fun unbanUser(targetUserId: String, reason: String?) = runUserAction {
        repo.unbanUser(targetUserId, reason)
    }

    fun setAutoApprove(targetUserId: String, value: Boolean) = runUserAction {
        repo.setAutoApprove(targetUserId, value)
    }

    fun resetStrikes(targetUserId: String) = runUserAction {
        repo.resetStrikes(targetUserId)
    }

    /** Changes a user's role; patches just the role field in place (server returns no full row). */
    fun changeRole(targetUserId: String, newRole: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, actionError = null, actionSuccess = null) }
            val result = repo.updateUserRole(targetUserId, newRole)
            if (result.isSuccess) {
                val role = result.getOrNull() ?: newRole
                _items.value = _items.value.map { if (it.id == targetUserId) it.copy(role = role) else it }
                _uiState.update { it.copy(actionInProgress = false, actionSuccess = "Role updated to $role") }
                clearActionMessageAfterDelay()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Failed to update role"
                _uiState.update { it.copy(actionInProgress = false, actionError = message) }
            }
        }
    }

    private fun runUserAction(call: suspend () -> Result<AdminUserDto>) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, actionError = null, actionSuccess = null) }
            val result = call()
            if (result.isSuccess) {
                val updated = result.getOrNull()
                if (updated != null) {
                    _items.value = _items.value.map { if (it.id == updated.id) updated else it }
                }
                _uiState.update { it.copy(actionInProgress = false, actionSuccess = "Updated") }
                clearActionMessageAfterDelay()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Action failed"
                _uiState.update { it.copy(actionInProgress = false, actionError = message) }
            }
        }
    }

    private fun clearActionMessageAfterDelay() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.update { it.copy(actionSuccess = null, actionError = null) }
        }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }
}
