package eg.deals.radar.feature.post

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Login screen (Sign in with Google -> secure session).
 * On success: onLoginSuccess(userId, username, email, role); the caller
 * navigates to the moderator dashboard or the account screen by role.
 * `email` is empty for Google accounts: we deliberately don't store it.
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (userId: String, username: String, email: String, role: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: LoginViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(context) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.verificationState) {
        val state = uiState.verificationState
        if (state is LoginVerificationState.Verified) {
            val user = state.user
            onLoginSuccess(user.id, user.username, user.email.orEmpty(), user.role ?: "user")
        }
    }

    GoogleSignInScreen(
        onCancel = onBackClick,
        onSignIn = { consent -> viewModel.signInWithGoogle(context, consent) },
        isLoading = uiState.isLoading,
        error = uiState.error,
        modifier = modifier
    )
}
