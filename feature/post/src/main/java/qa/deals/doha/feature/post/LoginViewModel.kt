package qa.deals.doha.feature.post



import android.content.Context

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.doha.network.UserInfo
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.repository.UserRepository



/**

 * Email Verification State for Login

 */

sealed class LoginVerificationState {

    object Initial : LoginVerificationState()

    data class Loading(val message: String) : LoginVerificationState()

    data class CodeSent(val email: String) : LoginVerificationState()

    data class Verified(val user: UserInfo) : LoginVerificationState()

    data class Error(val message: String) : LoginVerificationState()

}



/**

 * UI State for Login Screen

 */

data class LoginUiState(

    val verificationState: LoginVerificationState = LoginVerificationState.Initial,

    val isLoading: Boolean = false,

    val error: String? = null

)



/**

 * ViewModel for Login/Authentication Screen

 * Handles email verification flow for user login

 */

class LoginViewModel(

    context: Context,

    private val dealRepo: DealRepository = DealRepository(),

    private val userRepo: UserRepository = UserRepository()

) : ViewModel() {



    private val deviceIdManager = DeviceIdManager.getInstance(context)



    private val _uiState = MutableStateFlow(LoginUiState())

    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()



    init {

        Log.d("LoginViewModel", "Initializing LoginViewModel")

    }



    /**

     * Send verification code to email

     */

    fun sendVerificationCode(email: String) {

        viewModelScope.launch {

            try {

                Log.d("LoginViewModel", "📧 Sending verification code to: $email")

                _uiState.value = _uiState.value.copy(

                    verificationState = LoginVerificationState.Loading("Sending code..."),

                    isLoading = true,

                    error = null

                )



                val response = dealRepo.sendVerificationCode(email)



                if (response.success) {

                    Log.d("LoginViewModel", "✅ Code sent successfully")

                    _uiState.value = _uiState.value.copy(

                        verificationState = LoginVerificationState.CodeSent(email),

                        isLoading = false

                    )

                } else {

                    Log.e("LoginViewModel", "❌ Failed to send code: ${response.error}")

                    _uiState.value = _uiState.value.copy(

                        verificationState = LoginVerificationState.Error(

                            response.error ?: "Failed to send code. Please try again."

                        ),

                        isLoading = false,

                        error = response.error

                    )

                }

            } catch (e: Exception) {

                Log.e("LoginViewModel", "💥 Error sending code", e)

                _uiState.value = _uiState.value.copy(

                    verificationState = LoginVerificationState.Error(

                        e.message ?: "Network error. Please check your connection."

                    ),

                    isLoading = false,

                    error = e.message

                )

            }

        }

    }



    /**

     * Verify code and authenticate user

     */

    fun verifyCode(email: String, code: String) {

        viewModelScope.launch {

            try {

                Log.d("LoginViewModel", "🔒 Verifying code: $code for email: $email")

                _uiState.value = _uiState.value.copy(

                    verificationState = LoginVerificationState.Loading("Verifying code..."),

                    isLoading = true,

                    error = null

                )



                val deviceId = deviceIdManager.getDeviceId()



                val response = dealRepo.verifyCodeAndGetUser(
                    email = email,
                    code = code,
                    deviceId = deviceId
                )

                val user = response.user

                if (response.success && user != null) {
                    Log.d("LoginViewModel", "✅ Verification successful! User: ${user.username}")

                    // Store userId and username in DeviceIdManager

                    deviceIdManager.saveUserId(user.id)
                    deviceIdManager.saveUsername(user.username)
                    Log.d("LoginViewModel", "💾 UserId and username stored: ${user.id.take(8)}... / ${user.username}")
                    _uiState.value = _uiState.value.copy(
                        verificationState = LoginVerificationState.Verified(user),
                        isLoading = false
                    )
                } else {
                    Log.e("LoginViewModel", "❌ Invalid code: ${response.error}")
                    _uiState.value = _uiState.value.copy(
                        verificationState = LoginVerificationState.Error(
                            response.error ?: "Invalid code. Please try again."
                        ),

                        isLoading = false,

                        error = response.error

                    )

                }

            } catch (e: Exception) {

                Log.e("LoginViewModel", "💥 Error verifying code", e)

                _uiState.value = _uiState.value.copy(

                    verificationState = LoginVerificationState.Error(

                        e.message ?: "Network error. Please check your connection."

                    ),

                    isLoading = false,

                    error = e.message

                )

            }

        }

    }



    /**

     * Reset verification state

     */

    fun resetState() {

        _uiState.value = LoginUiState()

    }



    /**

     * Clear error

     */

    fun clearError() {

        _uiState.value = _uiState.value.copy(error = null)

    }

}
