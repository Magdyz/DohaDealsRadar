package qa.deals.doha.feature.post

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * ========================================
 * ✨ EMAIL VERIFICATION SCREEN
 * Two-step email verification for user identity
 * ========================================
 *
 * Step 1: Enter email → Send verification code
 * Step 2: Enter code → Get auto-generated username
 *
 * @param onVerified Callback with (userId, username, email)
 * @param onCancel User canceled verification
 * @param onSkip User wants to skip (fallback to manual username)
 * @param onSendCode Callback to send verification code
 * @param onVerifyCode Callback to verify code
 */
@Composable
fun EmailVerificationScreen(
    onVerified: (userId: String, username: String, email: String) -> Unit,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
    onSendCode: (email: String) -> Unit,
    onVerifyCode: (email: String, code: String) -> Unit,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1 = email, 2 = code

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (step == 2) step = 1 else onCancel()
            }) {
                Icon(Icons.Default.ArrowBack, "Back")
            }

            TextButton(onClick = onSkip) {
                Text("Skip", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 1: Enter email
        if (step == 1) {
            Text(
                text = "📧 Verify your email",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Get a unique username and faster posting",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text("Email address") },
                placeholder = { Text("you@example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    onSendCode(email)
                    step = 2
                },
                enabled = email.contains("@") && email.length > 5 && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Send Code")
                }
            }

            // Benefits card
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "✨ Why verify?",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "• Get a cool username (DealHunter247)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Keep your username after reinstall",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Faster deal approval (auto-approve)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "⚠️ Check spam folder for code",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Step 2: Enter code
        else {
            Text(
                text = "Enter verification code",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Sent to $email",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = code,
                onValueChange = {
                    if (it.length <= 6) code = it
                },
                label = { Text("6-digit code") },
                placeholder = { Text("123456") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onVerifyCode(email, code) },
                enabled = code.length == 6 && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Verify & Continue")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { step = 1 },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Didn't receive code? Send again")
            }
        }
    }
}