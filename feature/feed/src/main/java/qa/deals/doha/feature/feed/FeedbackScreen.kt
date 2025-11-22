package qa.deals.doha.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.Crossfade
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import kotlinx.coroutines.launch
import qa.deals.doha.repository.DealRepository

/**
 * Feedback Screen
 *
 * Allows users to submit feedback about DohaDealsRadar
 *
 * FEATURES:
 * - Friendly welcome message
 * - Text input with same behavior as post deal description
 * - Character limit (500 characters)
 * - Modern text sanitization and filtering
 * - Submit button with gradient style
 *
 * CREATED: 2025-11-22
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DealRepository() }
    val deviceIdManager = remember { qa.deals.doha.datastore.DeviceIdManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var feedbackText by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val maxCharacters = 500
    val scrollState = rememberScrollState()

    // Purple gradient matching app theme
    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF9C27B0),  // Purple
                Color(0xFFE91E63)   // Pink
            ),
            start = Offset(0f, 0f),
            end = Offset(1000f, 1000f)
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(gradientBrush)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Feedback",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        },
        // ✅ Modern 2025: Bottom bar with keyboard handling (matches ReportScreen)
        bottomBar = {
            // Stick button to keyboard (Snoonu-style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()  // CRITICAL: Makes the bar move up with keyboard
                    .padding(top = 16.dp)  // Space above button when keyboard is open
                    .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        // Validate email format if provided
                        if (email.isNotBlank()) {
                            val emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
                            if (!emailRegex.matches(email)) {
                                errorMessage = "Please enter a valid email address (e.g., name@example.com)"
                                return@Button
                            }
                        }

                        // Validate feedback text
                        if (feedbackText.isBlank()) {
                            errorMessage = "Please enter your feedback before submitting"
                            return@Button
                        }

                        if (feedbackText.length > maxCharacters) {
                            errorMessage = "Your feedback is too long. Please keep it under $maxCharacters characters"
                            return@Button
                        }

                        // All validations passed - submit feedback
                        isSubmitting = true
                        errorMessage = null

                        scope.launch {
                            try {
                                val deviceId = deviceIdManager.getDeviceId()
                                val userId = deviceIdManager.getUserId()

                                val result = repository.submitFeedback(
                                    deviceId = deviceId,
                                    feedbackText = feedbackText,
                                    userId = userId,
                                    email = if (email.isNotBlank()) email else null
                                )

                                result.fold(
                                    onSuccess = {
                                        isSubmitting = false
                                        showSuccessDialog = true
                                    },
                                    onFailure = { error ->
                                        isSubmitting = false
                                        errorMessage = when {
                                            error.message?.contains("email", ignoreCase = true) == true ->
                                                "There's an issue with the email address. Please check and try again"
                                            error.message?.contains("network", ignoreCase = true) == true ->
                                                "Network error. Please check your connection and try again"
                                            else ->
                                                "Unable to submit feedback. Please try again later"
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                isSubmitting = false
                                errorMessage = when {
                                    e.message?.contains("network", ignoreCase = true) == true ->
                                        "Network error. Please check your connection and try again"
                                    e.message?.contains("timeout", ignoreCase = true) == true ->
                                        "Request timed out. Please try again"
                                    else ->
                                        "Something went wrong. Please try again later"
                                }
                            }
                        }
                    },
                    enabled = feedbackText.isNotBlank() && !isSubmitting && feedbackText.length <= maxCharacters,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        disabledContainerColor = Color.Transparent,
                        disabledContentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        disabledElevation = 0.dp
                    ),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = if (feedbackText.isNotBlank() && !isSubmitting && feedbackText.length <= maxCharacters) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFE91E63),  // Pink
                                            Color(0xFF9C27B0)   // Purple
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF4B5563),
                                            Color(0xFF4B5563)
                                        )
                                    )
                                },
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = isSubmitting,
                            label = "submitButtonState"
                        ) { submitting ->
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Submitting...",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Submit Feedback",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imeNestedScroll()  // Auto-scroll to keep focused field visible
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Friendly welcome message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF3E5F5) // Light purple background
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "💜 We'd Love to Hear From You!",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color(0xFF7B1FA2)
                    )
                    Text(
                        text = "We are always working to improve DohaDealsRadar and add new features. Your feedback helps us make the app better for everyone!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4A148C),
                        lineHeight = 20.sp
                    )
                }
            }

            // Email field (optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Email (Optional)",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                )
                Text(
                    text = "If you'd like us to get back to you, please provide your email",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "your.email@example.com",
                            color = Color(0xFF9CA3AF)
                        )
                    },
                    singleLine = true,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Email,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF9C27B0),
                        cursorColor = Color(0xFF9C27B0)
                    )
                )
            }

            // Feedback text field (matches post deal description styling)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Your Feedback",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                )

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { newText ->
                        // Sanitize and filter input (2025 best practices)
                        val sanitized = sanitizeInput(newText)
                        if (sanitized.length <= maxCharacters) {
                            feedbackText = sanitized
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp),
                    placeholder = {
                        Text(
                            text = "Share your thoughts, suggestions, or report issues...",
                            color = Color(0xFF9CA3AF)
                        )
                    },
                    minLines = 5,
                    maxLines = Int.MAX_VALUE,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Default,
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = true
                    ),
                    supportingText = {
                        Text(
                            text = "${feedbackText.length} / $maxCharacters characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (feedbackText.length >= maxCharacters) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color(0xFF6B7280)
                            }
                        )
                    },
                    isError = feedbackText.length >= maxCharacters,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF9C27B0),
                        cursorColor = Color(0xFF9C27B0)
                    )
                )
            }

            // Privacy note
            Text(
                text = "Your feedback is valuable to us. We'll review it carefully and may reach out if we need more details.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Error message (if any)
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEE2E2)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "❌",
                            fontSize = 20.sp
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            // Space for keyboard (Snoonu-style)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                feedbackText = ""
                email = ""
                onBackClick()
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF10B981)  // Green success color
                )
            },
            title = {
                Text(
                    text = "Thank You!",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color(0xFF1F2937)
                )
            },
            text = {
                Text(
                    text = "Your feedback has been submitted successfully. We appreciate you taking the time to help us improve DohaDealsRadar!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp
                    ),
                    color = Color(0xFF4B5563),
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        feedbackText = ""
                        email = ""
                        onBackClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    ),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFE91E63),  // Pink
                                        Color(0xFF9C27B0)   // Purple
                                    )
                                ),
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        )
                    }
                }
            },
            containerColor = Color.White,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

/**
 * Sanitize user input (2025 best practices)
 *
 * - Remove control characters
 * - Trim excessive whitespace
 * - Filter malicious patterns
 * - Preserve emojis and international characters
 */
private fun sanitizeInput(input: String): String {
    return input
        // Remove control characters except newline, carriage return, and tab
        .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
        // Remove zero-width characters
        .replace(Regex("[\u200B-\u200D\uFEFF]"), "")
        // Normalize multiple spaces to single space (but preserve newlines)
        .replace(Regex(" {2,}"), " ")
        // Trim each line
        .lines()
        .joinToString("\n") { it.trim() }
        // Remove excessive newlines (more than 2 consecutive)
        .replace(Regex("\n{3,}"), "\n\n")
}
