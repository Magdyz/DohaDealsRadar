package qa.deals.doha.feature.feed.account



import android.content.Context

import android.util.Log

import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.flow.*

import kotlinx.coroutines.launch

import qa.deals.doha.datastore.DeviceIdManager

import qa.deals.doha.db.DealEntity

import qa.deals.doha.network.UserDto

import qa.deals.doha.repository.DealRepository

import qa.deals.doha.repository.UserRepository



/**

 * User Statistics

 */

data class UserStats(

    val totalDeals: Int = 0,

    val approvedDeals: Int = 0,

    val pendingDeals: Int = 0,

    val rejectedDeals: Int = 0

)



/**

 * UI State for User Account Screen

 */

data class UserAccountUiState(

    val loading: Boolean = false,

    val error: String? = null,

    val user: UserDto? = null,

    val stats: UserStats = UserStats(),

    val currentPage: Int = 1,

    val hasMorePages: Boolean = true,

    val isLoadingMore: Boolean = false

)



/**

 * ViewModel for User Account Screen

 * Manages user profile, statistics, and their submitted deals

 */

class UserAccountViewModel(

    private val context: Context,

    private val dealRepo: DealRepository = DealRepository(),

    private val userRepo: UserRepository = UserRepository()

) : ViewModel() {



    private val deviceIdManager = DeviceIdManager.getInstance(context)



    // UI State

    private val _uiState = MutableStateFlow(UserAccountUiState())

    val uiState: StateFlow<UserAccountUiState> = _uiState.asStateFlow()



    // User's deals from local cache (reactive updates)

    private val _userId = MutableStateFlow<String?>(null)



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

        Log.d("UserAccountVM", "Initializing UserAccountViewModel")

        loadCurrentUser()

    }



    /**

     * Load current user from DeviceIdManager

     */

    private fun loadCurrentUser() {

        viewModelScope.launch {

            try {

                val userId = deviceIdManager.getUserId()

                if (userId != null) {

                    Log.d("UserAccountVM", "Loading user: $userId")

                    _userId.value = userId

                    loadUserProfile(userId)

                    loadUserDeals(userId)

                } else {

                    Log.w("UserAccountVM", "No userId found in DeviceIdManager")

                    _uiState.update { it.copy(

                        error = "Not logged in. Please log in to view your account."

                    )}

                }

            } catch (e: Exception) {

                Log.e("UserAccountVM", "Error loading current user", e)

                _uiState.update { it.copy(error = "Error loading user: ${e.message}") }

            }

        }

    }



    /**

     * Load user profile from cache/API

     */

    private fun loadUserProfile(userId: String) {

        viewModelScope.launch {

            try {

                _uiState.update { it.copy(loading = true, error = null) }



                // Try cache first

                val cachedUser = userRepo.getCachedUser(userId)

                if (cachedUser != null) {

                    Log.d("UserAccountVM", "User loaded from cache: ${cachedUser.username}")
                    _uiState.update { it.copy(
                        user = cachedUser.toDto(),
                        loading = false
                    )}
                    calculateStats(userId)

                    return@launch

                }



                // Fetch from API

                val result = userRepo.fetchUserProfile(userId)

                if (result.isSuccess) {

                    val userDto = result.getOrNull()

                    if (userDto != null) {

                        Log.d("UserAccountVM", "User loaded from API: ${userDto.username}")

                        _uiState.update { it.copy(

                            user = userDto,

                            loading = false

                        )}

                        calculateStats(userId)

                    }

                } else {

                    Log.e("UserAccountVM", "Failed to load user profile: ${result.exceptionOrNull()}")

                    _uiState.update { it.copy(

                        loading = false,

                        error = "Failed to load profile"

                    )}

                }

            } catch (e: Exception) {

                Log.e("UserAccountVM", "Error loading user profile", e)

                _uiState.update { it.copy(

                    loading = false,

                    error = "Error: ${e.message}"

                )}

            }

        }

    }



    /**

     * Load user's submitted deals

     */

    private fun loadUserDeals(userId: String) {

        viewModelScope.launch {

            try {

                Log.d("UserAccountVM", "Fetching deals for user: $userId")



                val result = dealRepo.getDealsByUser(

                    requestingUserId = userId,

                    targetUserId = null, // Own deals

                    page = 1

                )



                if (result.isSuccess) {

                    val pagination = result.getOrNull()

                    _uiState.update { it.copy(

                        currentPage = pagination?.page ?: 1,

                        hasMorePages = pagination?.hasMore ?: false

                    )}

                    Log.d("UserAccountVM", "Deals loaded successfully")

                    calculateStats(userId)

                } else {

                    Log.e("UserAccountVM", "Failed to load deals: ${result.exceptionOrNull()}")

                }

            } catch (e: Exception) {

                Log.e("UserAccountVM", "Error loading user deals", e)

            }

        }

    }



    /**

     * Calculate user statistics from their deals

     */

    private fun calculateStats(userId: String) {

        viewModelScope.launch {

            try {

                // Get deals from cache

                dealRepo.getCachedDealsByUser(userId).collect { deals ->

                    val stats = UserStats(

                        totalDeals = deals.size,

                        approvedDeals = deals.count { it.status == "approved" },

                        pendingDeals = deals.count { it.status == "pending" },

                        rejectedDeals = deals.count { it.status == "rejected" }

                    )



                    _uiState.update { it.copy(stats = stats) }

                    Log.d("UserAccountVM", "Stats calculated: $stats")

                }

            } catch (e: Exception) {

                Log.e("UserAccountVM", "Error calculating stats", e)

            }

        }

    }



    /**

     * Refresh user data

     */

    fun refresh() {

        val userId = _userId.value

        if (userId != null) {

            loadUserProfile(userId)

            loadUserDeals(userId)

        } else {

            loadCurrentUser()

        }

    }



    /**

     * Load more deals (pagination)

     */

    fun loadMoreDeals() {

        val userId = _userId.value ?: return

        if (!_uiState.value.hasMorePages || _uiState.value.isLoadingMore) return



        viewModelScope.launch {

            try {

                _uiState.update { it.copy(isLoadingMore = true) }

                val nextPage = _uiState.value.currentPage + 1



                val result = dealRepo.getDealsByUser(

                    requestingUserId = userId,

                    targetUserId = null,

                    page = nextPage

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

                Log.e("UserAccountVM", "Error loading more deals", e)

                _uiState.update { it.copy(isLoadingMore = false) }

            }

        }

    }



    /**

     * Logout user

     */

    fun logout() {

        viewModelScope.launch {

            try {

                Log.d("UserAccountVM", "Logging out user")

                deviceIdManager.clearUserId()

                _userId.value = null

                _uiState.value = UserAccountUiState()

                Log.d("UserAccountVM", "User logged out successfully")

            } catch (e: Exception) {

                Log.e("UserAccountVM", "Error logging out", e)

            }

        }

    }



    /**

     * Clear error

     */

    fun clearError() {

        _uiState.update { it.copy(error = null) }

    }

}

