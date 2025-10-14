package qa.deals.doha.feature.feed

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.db.DealEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine




/**
 * UI state container for Feed screen
 */
data class FeedUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""

)

/**
 * ViewModel that exposes cached deals from Room and refreshes from network.
 */
class FeedViewModel(
    private val repo: DealRepository = DealRepository()
) : ViewModel() {

    // ✅ NEW: Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ✅ MODIFIED: Filtered deals based on search
    val deals: StateFlow<List<DealEntity>> = repo.getCachedDeals()
        .combine(_searchQuery) { allDeals, query ->
            if (query.isEmpty()) {
                allDeals
            } else {
                val searchLower = query.lowercase().trim()
                allDeals.filter { deal ->
                    deal.title.lowercase().contains(searchLower) ||
                            deal.description?.lowercase()?.contains(searchLower) == true
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var uiState by mutableStateOf(FeedUiState())
        private set

    init {
        // 🔄 Automatically refresh on first load
        refreshDeals()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /**
     * Refresh deals from Supabase → cache → Flow updates UI
     */
    fun refreshDeals() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                Log.d("Feed", "🔄 Refreshing deals from network...")
                repo.refreshDeals()
                uiState = uiState.copy(loading = false)
                Log.d("Feed", "✅ Deals refreshed successfully")
            } catch (t: Throwable) {
                Log.e("Feed", "💥 Failed to refresh deals", t)
                uiState = uiState.copy(loading = false, error = t.message)
            }
        }
        // ✅ ADD THIS NEW FUNCTION
        fun onSearchQueryChange(query: String) {
            _searchQuery.value = query
        }

    }
}