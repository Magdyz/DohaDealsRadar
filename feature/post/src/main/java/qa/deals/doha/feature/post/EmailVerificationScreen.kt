package qa.deals.doha.feature.post

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ========================================
 * âœ¨ EMAIL VERIFICATION SCREEN
 * Two-step email verification for user identity
 * ========================================
 *
 * Step 1: Enter email â†’ Send verification code
 * Step 2: Enter code â†’ Get auto-generated username
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

    // âœ¨ NEW: Modern 2025 styled surface with proper background
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
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
                    text = "Verify your email",
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

                // âœ¨ STYLED: Modern 2025 button matching Post Deal/Report button style (Pink-Purple gradient)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(
                            enabled = email.contains("@") && email.length > 5 && !isLoading,
                            onClick = {
                                onSendCode(email)
                                step = 2
                            }
                        )
                        .background(
                            brush = if (email.contains("@") && email.length > 5 && !isLoading) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFE91E63),  // âœ… Pink (matches Post Deal button)
                                        Color(0xFF9C27B0)   // âœ… Purple (matches Post Deal button)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF9CA3AF),  // Disabled gray
                                        Color(0xFF9CA3AF)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Sending...",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            )
                        }
                    } else {
                        Text(
                            "Send Code",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        )
                    }
                }

                // Benefits card
                Spacer(modifier = Modifier.height(16.dp))

                // âœ¨ STYLED: Modern benefits card with consistent theme colors
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Why verify?",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Get a cool username (DealHunter247)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Keep your username after reinstall",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Faster deal approval (auto-approve)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Check spam folder for code",
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

                // âœ¨ STYLED: Modern 2025 Verify button matching Post Deal/Report style (Pink-Purple gradient)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable(
                            enabled = code.length == 6 && !isLoading,
                            onClick = { onVerifyCode(email, code) }
                        )
                        .background(
                            brush = if (code.length == 6 && !isLoading) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFE91E63),  // âœ… Pink (matches Post Deal button)
                                        Color(0xFF9C27B0)   // âœ… Purple (matches Post Deal button)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF9CA3AF),  // Disabled gray
                                        Color(0xFF9CA3AF)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Verifying...",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            )
                        }
                    } else {
                        Text(
                            "Verify & Continue",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        )
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
}