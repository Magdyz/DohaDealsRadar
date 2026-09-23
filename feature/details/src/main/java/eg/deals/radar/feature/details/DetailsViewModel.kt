package eg.deals.radar.feature.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.deals.radar.auth.AuthManager
import eg.deals.radar.datastore.DeviceIdManager
import eg.deals.radar.db.DealEntity
import eg.deals.radar.network.ApiErrors
import eg.deals.radar.repository.DealRepository
import eg.deals.radar.util.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A vote waiting for the user to log in. */
data class PendingVote(val voteType: String)

data class DetailsUiState(
    val deal: DealEntity? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val voting: Boolean = false,
    val voteError: String? = null,
    val hasVoted: Boolean = false,
    val userVoteType: String? = null,
    val isArchived: Boolean = false,
    val showVoteAuthDialog: Boolean = false,
    val pendingVote: PendingVote? = null,
    /** One-off message (snackbar), e.g. "Thanks, we'll check it". */
    val message: String? = null,
    val markingExpired: Boolean = false,
    val markedExpired: Boolean = false
)

/**
 * ========================================
 * 📄 DEAL DETAILS
 * ========================================
 * - Deal from the local cache (fetched once from the network if missing,
 *   e.g. opened from a notification)
 * - Votes: instant local update, server answer applied (user_vote + counts)
 * - "Expired?" confirmation: 3 people -> the deal is archived
 */
class DetailsViewModel(
    private val dealId: String,
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val deviceIdManager: DeviceIdManager = DeviceIdManager.getInstance(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val voteMutex = Mutex()
    private var hasTriedNetworkFetch = false

    init {
        loadDeal()
    }

    private fun loadDeal() {
        viewModelScope.launch {
            repo.getCachedDeals().collect { deals ->
                val deal = deals.find { it.id == dealId }
                if (deal != null) {
                    val s = _uiState.value
                    // While a vote is in flight keep the optimistic counts/vote on screen
                    val shown = if (s.voting && s.deal != null) deal.copy(hotCount = s.deal.hotCount, coldCount = s.deal.coldCount) else deal
                    val vote = if (s.voting) s.userVoteType else if (AuthManager.isLoggedIn) deal.userVote else null
                    _uiState.value = s.copy(
                        deal = shown,
                        loading = false,
                        error = null,
                        hasVoted = vote != null,
                        userVoteType = vote,
                        isArchived = deal.isArchived
                    )
                } else if (!hasTriedNetworkFetch) {
                    hasTriedNetworkFetch = true
                    _uiState.value = _uiState.value.copy(loading = true, error = null)
                    repo.cacheNewestDeals().onFailure { error ->
                        _uiState.value = _uiState.value.copy(deal = null, loading = false, error = error.message)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        deal = null, loading = false, error = AppLanguage.string(R.string.details_error_not_found)
                    )
                }
            }
        }
    }

    fun castVote(voteType: String) {
        if (!AuthManager.isLoggedIn || deviceIdManager.getUserId() == null) {
            _uiState.value = _uiState.value.copy(showVoteAuthDialog = true, pendingVote = PendingVote(voteType))
            return
        }
        val before = _uiState.value
        val deal = before.deal ?: return

        viewModelScope.launch {
            // Optimistic update
            val oldVote = before.userVoteType
            val newVote = if (oldVote == voteType) null else voteType
            val hot = (deal.hotCount ?: 0) + (if (newVote == "hot") 1 else 0) - (if (oldVote == "hot") 1 else 0)
            val cold = (deal.coldCount ?: 0) + (if (newVote == "cold") 1 else 0) - (if (oldVote == "cold") 1 else 0)
            _uiState.value = before.copy(
                deal = deal.copy(hotCount = hot.coerceAtLeast(0), coldCount = cold.coerceAtLeast(0)),
                voting = true,
                voteError = null,
                hasVoted = newVote != null,
                userVoteType = newVote
            )

            voteMutex.withLock {
                val result = runCatching { repo.castVote(dealId = dealId, voteType = voteType) }
                val env = result.getOrNull()
                val data = env?.data
                if (env?.success == true && data != null) {
                    repo.updateLocalDealFromNetwork(data.copy(userVote = env.userVote))
                    _uiState.value = _uiState.value.copy(
                        voting = false,
                        hasVoted = env.userVote != null,
                        userVoteType = env.userVote,
                        deal = _uiState.value.deal?.copy(hotCount = data.hotCount, coldCount = data.coldCount)
                    )
                } else {
                    val message = when {
                        env?.code == ApiErrors.UNAUTHORIZED -> null
                        env != null -> ApiErrors.message(env, ApiErrors.Context.VOTE)
                        else -> ApiErrors.message(result.exceptionOrNull() ?: Exception())
                    }
                    _uiState.value = _uiState.value.copy(
                        deal = deal,
                        voting = false,
                        hasVoted = oldVote != null,
                        userVoteType = oldVote,
                        message = message,
                        showVoteAuthDialog = env?.code == ApiErrors.UNAUTHORIZED,
                        pendingVote = if (env?.code == ApiErrors.UNAUTHORIZED) PendingVote(voteType) else null
                    )
                }
            }
        }
    }

    /** "Is this deal expired?" */
    fun markExpired() {
        if (!AuthManager.isLoggedIn) {
            _uiState.value = _uiState.value.copy(showVoteAuthDialog = true, pendingVote = null)
            return
        }
        if (_uiState.value.markingExpired) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(markingExpired = true)
            val res = repo.markExpired(dealId)
            _uiState.value = when {
                res.success == true -> _uiState.value.copy(
                    markingExpired = false,
                    markedExpired = true,
                    isArchived = res.archived == true || _uiState.value.isArchived,
                    message = AppLanguage.string(if (res.archived == true) R.string.details_expired_done else R.string.details_expired_thanks)
                )
                else -> _uiState.value.copy(
                    markingExpired = false,
                    markedExpired = res.code == ApiErrors.ALREADY_DONE,
                    message = ApiErrors.message(res, ApiErrors.Context.EXPIRED)
                )
            }
            if (res.archived == true) repo.markArchivedLocal(dealId)
        }
    }

    fun clearMessage() {
        if (_uiState.value.message != null) _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissVoteAuthDialog() {
        _uiState.value = _uiState.value.copy(showVoteAuthDialog = false, pendingVote = null)
    }

    fun retryPendingVote() {
        val pending = _uiState.value.pendingVote ?: return
        _uiState.value = _uiState.value.copy(showVoteAuthDialog = false, pendingVote = null)
        castVote(pending.voteType)
    }

    fun getShareText(): String {
        val deal = _uiState.value.deal ?: return ""
        return AppLanguage.string(R.string.details_share_text, deal.title, deal.link)
    }
}
