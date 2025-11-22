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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    var showSuccessScreen by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-navigate back after success
    LaunchedEffect(showSuccessScreen) {
        if (showSuccessScreen) {
            delay(3000) // Show success for 3 seconds
            feedbackText = ""
            email = ""
            showSuccessScreen = false
            onBackClick()
        }
    }

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
                        IconButton(
                            onClick = onBackClick,
                            enabled = !isSubmitting  // Disable back button while submitting
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isSubmitting) Color.White.copy(alpha = 0.5f) else Color.White
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
            // Only show button when not submitting and not showing success
            if (!isSubmitting && !showSuccessScreen) {
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

                                // Sanitize text before submission
                                val sanitizedFeedback = sanitizeInput(feedbackText)

                                val result = repository.submitFeedback(
                                    deviceId = deviceId,
                                    feedbackText = sanitizedFeedback,
                                    userId = userId,
                                    email = if (email.isNotBlank()) email else null
                                )

                                result.fold(
                                    onSuccess = {
                                        isSubmitting = false
                                        showSuccessScreen = true
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
        }
    ) { paddingValues ->
        // Show sending, success, or form
        when {
            isSubmitting -> FeedbackSendingContent()
            showSuccessScreen -> FeedbackSuccessContent()
            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imeNestedScroll()  // Auto-scroll to keep focused field visible
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
            // Hero card - Clean, concise messaging
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF3E5F5) // Light purple background
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "We value your feedback! 💜",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = Color(0xFF7B1FA2)
                    )
                    Text(
                        text = "Help us improve DohaDealsRadar by sharing your thoughts or reporting issues.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = Color(0xFF4A148C),
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Email field (optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Email Address (Optional)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = Color(0xFFB0B0B0)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    placeholder = {
                        Text(
                            text = "name@example.com",
                            fontSize = 16.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp
                    ),
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

            Spacer(modifier = Modifier.height(24.dp))

            // Feedback text field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Your Feedback",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    ),
                    color = Color(0xFFB0B0B0)
                )

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { newText ->
                        // Basic filtering only during typing (remove dangerous characters)
                        val filtered = newText
                            .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
                            .replace(Regex("[\u200B-\u200D\uFEFF]"), "")

                        if (filtered.length <= maxCharacters) {
                            feedbackText = filtered
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 300.dp),
                    placeholder = {
                        Text(
                            text = "Tell us what you like or what we can improve...",
                            fontSize = 16.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp
                    ),
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

            // Error message (if any)
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(24.dp))

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
    }
}

/**
 * Feedback Sending Screen
 * Matches ReportScreen sending animation style with envelope icon
 */
@Composable
private fun FeedbackSendingContent() {
    // Pulsing animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "sending")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated envelope icon
        Icon(
            imageVector = Icons.Rounded.Email,
            contentDescription = "Sending feedback",
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .graphicsLayer { this.alpha = alpha },
            tint = Color(0xFF9C27B0)  // Purple brand color
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Sending Feedback...",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp
            ),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            modifier = Modifier.width(200.dp),
            color = Color(0xFF9C27B0)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Please wait",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp
            ),
            color = Color(0xFFB0B0B0)
        )
    }
}

/**
 * Feedback Success Screen
 * Matches ReportScreen success animation style
 */
@Composable
private fun FeedbackSuccessContent() {
    // Animation states
    var visible by remember { mutableStateOf(false) }
    var checkmarkScale by remember { mutableFloatStateOf(0f) }

    // Launch animations
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
        checkmarkScale = 1f
    }

    // Animated scale with bounce
    val scale by animateFloatAsState(
        targetValue = checkmarkScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmark_scale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Purple checkmark with concentric circles
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                // Outer circle (light purple)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            color = Color(0xFF9C27B0).copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                )

                // Middle circle (medium purple)
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            color = Color(0xFF9C27B0).copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )

                // Inner solid circle with checkmark
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = Color(0xFF9C27B0),
                    shadowElevation = 12.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Feedback sent",
                            modifier = Modifier.size(70.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Success messages
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main title
                Text(
                    text = "Thank You!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                // Subtitle
                Text(
                    text = "We appreciate your help in\nmaking DohaDealsRadar better!",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    ),
                    color = Color(0xFFB0B0B0),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Sanitize user input before submission (2025 best practices)
 *
 * Called only on submit, not during typing, to allow natural text entry.
 *
 * - Remove control characters
 * - Trim excessive whitespace
 * - Normalize spacing
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
