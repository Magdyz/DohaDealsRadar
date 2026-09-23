package eg.deals.radar.feature.feed.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.deals.radar.network.FeedbackAdminDto
import eg.deals.radar.repository.AdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI state for FeedbackAdminScreen
 * CREATED: 2025-11-27
 */
data class FeedbackAdminUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val statusFilter: String? = null, // null = All
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val actionInProgress: Boolean = false,
    val actionError: String? = null,
    val actionSuccess: String? = null
)

/**
 * ViewModel for the admin feedback inbox (admin_feedback function).
 */
class FeedbackAdminViewModel(
    private val repo: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackAdminUiState())
    val uiState: StateFlow<FeedbackAdminUiState> = _uiState.asStateFlow()

    private val _items = MutableStateFlow<List<FeedbackAdminDto>>(emptyList())
    val items: StateFlow<List<FeedbackAdminDto>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun setStatusFilter(status: String?) {
        if (_uiState.value.statusFilter == status) return
        _uiState.update { it.copy(statusFilter = status) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val status = _uiState.value.statusFilter
            val result = repo.listFeedback(status, page = 1)
            if (result.isSuccess) {
                val page = result.getOrNull()
                _items.value = page?.items ?: emptyList()
                _uiState.update {
                    it.copy(
                        loading = false,
                        page = 1,
                        hasMore = page?.pagination?.hasMore ?: false
                    )
                }
            } else {
                val message = result.exceptionOrNull()?.message ?: "Failed to load feedback"
                Log.e("FeedbackAdminVM", "Failed to load feedback: $message")
                _uiState.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = _uiState.value.page + 1
            val result = repo.listFeedback(_uiState.value.statusFilter, page = nextPage)
            if (result.isSuccess) {
                val page = result.getOrNull()
                _items.value = _items.value + (page?.items ?: emptyList())
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        page = nextPage,
                        hasMore = page?.pagination?.hasMore ?: false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /** Update a feedback item's status/notes; patches the row in place on success. */
    fun updateStatus(feedbackId: String, status: String, notes: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionInProgress = true, actionError = null, actionSuccess = null) }
            val result = repo.updateFeedback(feedbackId, status, notes)
            if (result.isSuccess) {
                val updated = result.getOrNull()
                if (updated != null) {
                    _items.value = _items.value.map { if (it.id == updated.id) updated else it }
                }
                _uiState.update { it.copy(actionInProgress = false, actionSuccess = "Feedback updated") }
                clearActionMessageAfterDelay()
            } else {
                val message = result.exceptionOrNull()?.message ?: "Failed to update feedback"
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
