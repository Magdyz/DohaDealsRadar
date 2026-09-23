package eg.deals.radar.feature.feed.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.deals.radar.network.AdminAuditLogEntryDto
import eg.deals.radar.repository.AdminRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * UI state for AuditLogScreen
 * CREATED: 2025-11-27
 */
data class AuditLogUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val category: String? = null, // null = All
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false
)

/**
 * ViewModel for the admin audit log (admin_audit_log function). Read-only.
 */
class AuditLogViewModel(
    private val repo: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuditLogUiState())
    val uiState: StateFlow<AuditLogUiState> = _uiState.asStateFlow()

    private val _items = MutableStateFlow<List<AdminAuditLogEntryDto>>(emptyList())
    val items: StateFlow<List<AdminAuditLogEntryDto>> = _items.asStateFlow()

    init {
        refresh()
    }

    fun setCategory(category: String?) {
        if (_uiState.value.category == category) return
        _uiState.update { it.copy(category = category) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repo.getAuditLog(_uiState.value.category, page = 1)
            if (result.isSuccess) {
                val page = result.getOrNull()
                _items.value = page?.items ?: emptyList()
                _uiState.update { it.copy(loading = false, page = 1, hasMore = page?.pagination?.hasMore ?: false) }
            } else {
                val message = result.exceptionOrNull()?.message ?: "Failed to load audit log"
                Log.e("AuditLogVM", "Failed to load audit log: $message")
                _uiState.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val nextPage = _uiState.value.page + 1
            val result = repo.getAuditLog(_uiState.value.category, page = nextPage)
            if (result.isSuccess) {
                val page = result.getOrNull()
                _items.value = _items.value + (page?.items ?: emptyList())
                _uiState.update { it.copy(isLoadingMore = false, page = nextPage, hasMore = page?.pagination?.hasMore ?: false) }
            } else {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}
