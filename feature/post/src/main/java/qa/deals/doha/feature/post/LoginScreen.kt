package qa.deals.doha.feature.post



import android.util.Log

import androidx.compose.foundation.layout.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp

import androidx.lifecycle.ViewModelProvider

import androidx.lifecycle.viewmodel.compose.viewModel



/**

 * Login/Authentication Screen

 * Uses email verification flow to authenticate users

 *

 * Flow:

 * 1. User enters email → sends verification code

 * 2. User enters code → verifies and logs in

 * 3. On success → navigate based on user role

 *    - Admin/Moderator → Moderator Dashboard

 *    - Regular User → User Account Screen

 *

 * @param onLoginSuccess Callback with (userId, username, email, role) when login succeeds

 * @param onBackClick User clicks back button

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



    // Handle verification success

    LaunchedEffect(uiState.verificationState) {

        if (uiState.verificationState is LoginVerificationState.Verified) {

            val user = (uiState.verificationState as LoginVerificationState.Verified).user

            Log.d("LoginScreen", "✅ Login successful! User: ${user.username}, Role: ${user.id}")



            // Get user role from backend

            // For now, we'll use the user's role from the verification response

            // In a production app, you'd fetch this from the user profile

            val role = "user" // Default role, will be updated by FeedViewModel when it loads



            onLoginSuccess(user.id, user.username, user.email, role)

        }

    }



    // Show email verification screen

    EmailVerificationScreen(

        onVerified = { userId, username, email ->

            // This is called from the EmailVerificationScreen

            // but we handle success in the LaunchedEffect above

            Log.d("LoginScreen", "Verification complete: userId=$userId, username=$username")

        },

        onCancel = {

            Log.d("LoginScreen", "User canceled login")

            onBackClick()

        },

        onSkip = {

            // For login, we don't allow skipping

            // User must verify to continue

            Log.d("LoginScreen", "Skip pressed, redirecting to back")

            onBackClick()

        },

        onSendCode = { email ->

            Log.d("LoginScreen", "Sending code to: $email")

            viewModel.sendVerificationCode(email)

        },

        onVerifyCode = { email, code ->

            Log.d("LoginScreen", "Verifying code for: $email")

            viewModel.verifyCode(email, code)

        },

        isLoading = uiState.isLoading,

        error = uiState.error,

        modifier = modifier

    )



    // Show loading overlay if needed

    if (uiState.verificationState is LoginVerificationState.Loading) {

        Box(

            modifier = Modifier

                .fillMaxSize()

                .padding(16.dp),

            contentAlignment = Alignment.Center

        ) {

            CircularProgressIndicator()

        }

    }

}

