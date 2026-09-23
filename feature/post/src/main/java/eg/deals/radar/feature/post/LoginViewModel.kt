package eg.deals.radar.feature.post

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eg.deals.radar.auth.AuthManager
import eg.deals.radar.auth.GoogleAuth
import eg.deals.radar.datastore.DeviceIdManager
import eg.deals.radar.network.ApiErrors
import eg.deals.radar.network.UserInfo
import eg.deals.radar.repository.DealRepository
import eg.deals.radar.repository.UserRepository
import eg.deals.radar.util.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the Google sign-in flow currently is. */
sealed class LoginVerificationState {
    object Initial : LoginVerificationState()
    data class Loading(val message: String) : LoginVerificationState()
    data class Verified(val user: UserInfo) : LoginVerificationState()
    data class Error(val message: String) : LoginVerificationState()
}

data class LoginUiState(
    val verificationState: LoginVerificationState = LoginVerificationState.Initial,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ========================================
 * 🔐 LOGIN (Sign in with Google)
 * ========================================
 * The system account picker returns a Google ID token; our backend verifies it
 * and returns a real session. No emails, no codes, nothing to type.
 */
class LoginViewModel(
    context: Context,
    private val dealRepo: DealRepository = DealRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    private val deviceIdManager = DeviceIdManager.getInstance(context)
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** [activityContext] must be an Activity: Credential Manager shows a system sheet. */
    fun signInWithGoogle(activityContext: Context, consent: Boolean) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(
                verificationState = LoginVerificationState.Loading(AppLanguage.string(R.string.post_verifying_code)),
                isLoading = true
            )

            when (val result = GoogleAuth.signIn(activityContext)) {
                is GoogleAuth.Result.Cancelled -> reset()
                is GoogleAuth.Result.NoAccount -> fail(AppLanguage.string(R.string.signin_no_account))
                is GoogleAuth.Result.Failed -> fail(
                    if (GoogleAuth.isConfigured) AppLanguage.string(R.string.signin_failed)
                    else AppLanguage.string(R.string.signin_not_configured)
                )
                is GoogleAuth.Result.Success -> {
                    val response = dealRepo.signInWithGoogle(
                        idToken = result.idToken,
                        nonce = result.rawNonce,
                        deviceId = deviceIdManager.getDeviceId(),
                        consent = consent
                    )
                    val user = response.user
                    if (response.success && user != null) {
                        AuthManager.onLoggedIn(appContext, user.id, user.username)
                        runCatching { userRepo.fetchUserProfile(user.id) }
                        _uiState.value = LoginUiState(
                            verificationState = LoginVerificationState.Verified(user),
                            isLoading = false
                        )
                    } else {
                        fail(response.error ?: AppLanguage.string(R.string.signin_failed))
                    }
                }
            }
        }
    }

    private fun reset() {
        _uiState.value = LoginUiState()
    }

    private fun fail(message: String) {
        _uiState.value = LoginUiState(
            verificationState = LoginVerificationState.Error(message),
            isLoading = false,
            error = message
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, verificationState = LoginVerificationState.Initial)
    }
}
