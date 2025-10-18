package qa.deals.doha.feature.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


/**
 * ========================================
 * ✨ USERNAME SELECTION DIALOG
 * First-time user onboarding for anonymous username
 * ========================================
 *
 * Created: 2025-10-18 19:23:26 UTC by @Magdyz
 * Location: feature/post/src/main/java/qa/deals/doha/feature/post/UsernameDialog.kt
 *
 * Features:
 * - Real-time validation
 * - Availability checking
 * - Loading states
 * - Error feedback
 * - Smooth animations
 * - Keyboard handling
 *
 * @param onDismiss Called when dialog is dismissed (not allowed here - must register)
 * @param onUsernameSelected Called when username is successfully validated
 * @param onCheckAvailability Called to check if username is available
 * @param isCheckingAvailability Loading state for availability check
 * @param availabilityResult Result of availability check (true=available, false=taken, null=not checked)
 * @param availabilityError Error message from availability check
 */
@Composable
fun UsernameDialog(
    onDismiss: () -> Unit,
    onUsernameSelected: (String) -> Unit,
    onCheckAvailability: (String) -> Unit,
    isCheckingAvailability: Boolean,
    availabilityResult: Boolean?,
    availabilityError: String?
) {
    // ========================================
    // ✨ STATE MANAGEMENT
    // ========================================

    var username by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ========================================
    // ✨ VALIDATION LOGIC
    // ========================================

    /**
     * Validate username format (client-side)
     * Returns error message or null if valid
     */
    fun validateUsername(input: String): String? {
        return when {
            input.isBlank() -> "Username cannot be empty"
            input.length < 3 -> "Username must be at least 3 characters"
            input.length > 20 -> "Username must be 20 characters or less"
            !input.matches(Regex("^[a-zA-Z0-9_]+$")) ->
                "Only letters, numbers, and underscore allowed"
            input.startsWith("_") || input.endsWith("_") ->
                "Username cannot start or end with underscore"
            input.contains("__") ->
                "Username cannot have consecutive underscores"
            else -> null
        }
    }

    // ========================================
    // ✨ DERIVED STATE
    // ========================================

    val isValid = username.isNotBlank() && validateUsername(username) == null
    val showCheckButton = isValid && availabilityResult != true && !isCheckingAvailability  // ✨ CHANGED: Show if not available yet
    val showContinueButton = isValid && availabilityResult == true && !isCheckingAvailability

    // ========================================
    // ✨ DIALOG UI
    // ========================================

    Dialog(
        onDismissRequest = { /* Prevent dismissal - user must choose username */ },
        properties = DialogProperties(
            dismissOnBackPress = false,  // Must register username
            dismissOnClickOutside = false,  // Must register username
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)  // ✨ CHANGED: Limit height to 85% of screen
                .imePadding(),  // ✨ NEW: Add IME padding to avoid keyboard overlap
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())  // ✨ NEW: Make scrollable
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ========================================
                // 👤 HEADER: Icon + Title
                // ========================================

                Text(
                    text = "👤",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 56.sp
                    )
                )

                Text(
                    text = "Choose Your Username",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "This will be shown when you post deals.\nStay anonymous — no email required!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ========================================
                // 📝 USERNAME INPUT FIELD
                // ========================================

// ========================================
// 📝 USERNAME INPUT FIELD
// ========================================

                OutlinedTextField(
                    value = username,
                    onValueChange = { newValue ->
                        // Only allow valid characters
                        if (newValue.isEmpty() || newValue.matches(Regex("^[a-zA-Z0-9_]*$"))) {
                            username = newValue
                            localError = validateUsername(newValue)
                            // ✨ CRITICAL FIX: Reset availability when user types
                            // This allows retrying with a different username
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    placeholder = { Text("e.g., DealHunter123") },
                    supportingText = {
                        // Show character count
                        Text(
                            text = "${username.length}/20",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    isError = localError != null || availabilityResult == false,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (isValid && availabilityResult != true) {  // ✨ CHANGED: Check if not already available
                                onCheckAvailability(username.trim())
                            }
                        }
                    ),
                    trailingIcon = {
                        // Show validation status icon
                        when {
                            isCheckingAvailability -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            availabilityResult == true -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Available",
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                            availabilityResult == false || localError != null -> {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = when {
                            availabilityResult == true -> Color(0xFF4CAF50)
                            localError != null || availabilityResult == false ->
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                    enabled = !isCheckingAvailability  // ✨ NEW: Disable only while checking, not after
                )

                // ========================================
                // ⚠️ ERROR/SUCCESS MESSAGES
                // ========================================

                AnimatedVisibility(
                    visible = localError != null || availabilityError != null || availabilityResult != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = when {
                                    availabilityResult == true -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = when {
                                availabilityResult == true -> "✅ Username is available!"
                                availabilityResult == false -> "❌ Username is already taken"
                                availabilityError != null -> "⚠️ $availabilityError"
                                localError != null -> "⚠️ $localError"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = when {
                                availabilityResult == true -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.error
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ========================================
// 🎨 ACTION BUTTONS
// ========================================

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // CHECK AVAILABILITY / TRY AGAIN BUTTON
                    AnimatedVisibility(
                        visible = showCheckButton,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                onCheckAvailability(username.trim())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = isValid && !isCheckingAvailability,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    availabilityResult == false -> MaterialTheme.colorScheme.error  // Red when retrying
                                    else -> MaterialTheme.colorScheme.primary  // Primary color for first check
                                }
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCheckingAvailability) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Checking...")
                            } else {
                                // ✨ DYNAMIC BUTTON TEXT based on state
                                val buttonText = when (availabilityResult) {
                                    false -> "Try Again"  // Username was taken
                                    null -> "Check Availability"  // First check
                                    true -> "Check Availability"  // Shouldn't happen (button hidden when true)
                                    else -> "Check Availability"
                                }

                                Text(
                                    buttonText,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // CONTINUE BUTTON (shown after availability confirmed)
                    AnimatedVisibility(
                        visible = showContinueButton,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                onUsernameSelected(username.trim())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Continue",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // ========================================
                // 💡 HELPER TEXT
                // ========================================

                Text(
                    text = "• 3-20 characters\n• Letters, numbers, underscore only\n• Cannot change later",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}