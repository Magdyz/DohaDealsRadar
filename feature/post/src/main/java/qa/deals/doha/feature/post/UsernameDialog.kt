package qa.deals.doha.feature.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.geometry.Offset

/**
 * ========================================
 * ✨ USERNAME DIALOG - 2025 MODERN DESIGN
 * ========================================
 *
 * Created: Initial implementation
 * Updated: 2025-10-19 17:30:00 UTC by @Magdyz
 *
 * DESIGN UPDATES:
 * - ✨ Modern Material3 Person icon (replaced emoji)
 * - ✨ Gradient accent header (purple-pink brand colors)
 * - ✨ Consistent with app theme
 * - ✨ Smooth animations
 * - ✨ Scrollable for keyboard compatibility
 * - ✅ All functionality preserved
 *
 * FEATURES:
 * - Username validation (3-20 chars, alphanumeric + underscore)
 * - Real-time availability checking
 * - Error states with retry
 * - Cannot dismiss until username selected
 * - Optimistic UI updates
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameDialog(
    onDismiss: () -> Unit,
    onUsernameSelected: (String) -> Unit,
    onCheckAvailability: (String) -> Unit,
    isCheckingAvailability: Boolean,
    availabilityResult: Boolean?,
    availabilityError: String?
) {
    var username by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ✅ PRESERVED: Validation logic
    fun validateUsername(input: String): String? {
        return when {
            input.length < 3 -> "Username must be at least 3 characters"
            input.length > 20 -> "Username cannot exceed 20 characters"
            !input.matches(Regex("^[a-zA-Z0-9_]+$")) -> "Only letters, numbers, and underscores allowed"
            else -> null
        }
    }

    // ✅ PRESERVED: Derived states
    val isValid = username.isNotBlank() && validateUsername(username) == null
    val showCheckButton = isValid && availabilityResult != true && !isCheckingAvailability
    val showContinueButton = isValid && availabilityResult == true && !isCheckingAvailability

    Dialog(
        onDismissRequest = { /* ✅ PRESERVED: Prevent dismissal */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .imePadding(),  // ✅ Keyboard padding
            shape = MaterialTheme.shapes.extraLarge,  // ✨ Modern rounded corners
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())  // ✅ Scrollable
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                Spacer(modifier = Modifier.height(8.dp))

                // ========================================
                // ✨ MODERN HEADER with Icon
                // Replaced emoji with Material3 icon
                // ========================================

                // ✨ NEW: Icon container with gradient background
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF9C27B0),  // Purple (brand color)
                                    Color(0xFFE91E63)   // Pink (brand color)
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // ✨ NEW: Modern Material3 Person icon (not emoji!)
                    Icon(
                        imageVector = Icons.Default.Person,  // ✨ Clean vector icon
                        contentDescription = "User profile",
                        modifier = Modifier.size(56.dp),  // Large, visible
                        tint = Color.White  // High contrast
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ========================================
                // ✨ TITLE & SUBTITLE
                // ========================================

                Text(
                    text = "Choose Your Username",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "This will be visible on all deals you post",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ========================================
                // ✅ PRESERVED: Username Input Field
                // (No changes to functionality)
                // ========================================

                OutlinedTextField(
                    value = username,
                    onValueChange = { newValue ->
                        // ✅ PRESERVED: Validation logic
                        if (newValue.isEmpty() || newValue.matches(Regex("^[a-zA-Z0-9_]*$"))) {
                            username = newValue
                            localError = validateUsername(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    placeholder = { Text("e.g., DealHunter123") },
                    supportingText = {
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
                            if (isValid && availabilityResult != true) {
                                onCheckAvailability(username.trim())
                            }
                        }
                    ),
                    trailingIcon = {
                        // ✅ PRESERVED: Status icons
                        when {
                            isCheckingAvailability -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            availabilityResult == true -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Available",
                                    tint = Color(0xFF4CAF50)  // Green
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
                    enabled = !isCheckingAvailability,
                    shape = MaterialTheme.shapes.medium  // ✨ Modern rounded shape
                )

                // ========================================
                // ✅ PRESERVED: Error/Success Messages
                // ========================================

                AnimatedVisibility(
                    visible = localError != null || availabilityError != null || availabilityResult != null,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = when {
                            availabilityResult == true -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        }
                    ) {
                        Text(
                            text = when {
                                availabilityResult == true -> "✅ Username is available!"
                                availabilityResult == false -> "❌ Username is already taken. Try another!"
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ========================================
                // ✅ PRESERVED: Action Buttons
                // Updated styling only
                // ========================================

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ✅ Check Availability / Try Again Button
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
                                containerColor = if (availabilityResult == false)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            ),
                            shape = MaterialTheme.shapes.medium
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
                                Text(
                                    if (availabilityResult == false) "Try Again" else "Check Availability",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    // ✅ Continue Button
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
                                containerColor = Color(0xFF4CAF50)  // Green for success
                            ),
                            shape = MaterialTheme.shapes.medium
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

                Spacer(modifier = Modifier.height(8.dp))

                // ========================================
                // ✨ INFO TEXT (helpful hints)
                // ========================================

                Text(
                    text = "• 3-20 characters\n• Letters, numbers, and underscores only\n• Unique across DohaDeals",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
    }
}