package qa.deals.doha.feature.report

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qa.deals.doha.network.ReportReason
// ========================================
// ✅ IMPORTS for modern 2025 keyboard handling
// ========================================
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableFloatStateOf

/**
 * Report Screen - Clean, minimal Vinted-inspired design
 * ✅ ENHANCED: Now validates high-severity reports require details
 * ✅ ENHANCED: Animated sending/sent graphics for better UX
 * ✅ FIXED: Modern Snoonu-style keyboard handling with Scaffold bottomBar (2025)
 * ✅ UPDATED: Increased minimum character requirement to 30
 * ✅ UPDATED: Modern purple checkmark success screen (2025)
 * ✅ UPDATED: Matches PostScreen keyboard behavior exactly
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(
    dealId: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember(dealId) {
        ReportViewModel(dealId = dealId, context = context)
    }
    val uiState = viewModel.uiState

    // Auto-dismiss on success
    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            delay(2000)
            onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Report Deal",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Text(
                            "←",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        // ✅ NEW 2025: Use Scaffold bottomBar - proper standard approach (matches PostScreen)
        bottomBar = {
            // Only show button when form is visible (not loading/success/error states)
            if (!uiState.loading && !uiState.success && !uiState.alreadyReported && !uiState.dailyLimitReached) {
                // This container sticks the button to the keyboard (Snoonu-style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()  // CRITICAL: Makes the bar move up with keyboard
                        .padding(top = 16.dp)  // ✅ Snoonu gap: space above button when keyboard is open
                        .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { viewModel.submitReport() },
                        modifier = Modifier
                            .width(280.dp)
                            .height(56.dp),
                        enabled = uiState.selectedReason != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF4B5563),
                            disabledContentColor = Color(0xFF9CA3AF)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 12.dp,
                            disabledElevation = 0.dp
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = if (uiState.selectedReason != null) {
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
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Submit Report",
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
    ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Removed .padding(padding) - we apply it manually via Spacer at bottom
                    .imeNestedScroll()  // ✅ Modern 2025: Auto-scroll to keep focused field visible
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when {
                    // ✅ NEW: Show animated sending screen while loading
                    uiState.loading -> SendingContent()
                    // ✅ Enhanced: Animated success screen
                    uiState.success -> SuccessContent()
                    // ✅ Unchanged: Already reported screen
                    uiState.alreadyReported -> AlreadyReportedContent(onClose)
                    // ✅ Unchanged: Daily limit screen
                    uiState.dailyLimitReached -> DailyLimitContent(onClose)
                    // ✅ Enhanced: Form with validation (without inline button)
                    else -> ReportFormContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        onReasonSelected = { viewModel.selectReason(it) },
                        onNoteChanged = { viewModel.updateNote(it) },
                        onSubmit = { viewModel.submitReport() }
                    )
                }

                // ✅ Dynamic spacer: bottomBar height + visual gap
                // This creates the perfect Snoonu-style spacing and works with imeNestedScroll()
                Spacer(Modifier.height(padding.calculateBottomPadding() + 16.dp))
            }
        }
}

/**
 * ✅ NEW: Animated "Sending..." screen
 * Shows while report is being submitted with pulsing animation
 */
@Composable
private fun SendingContent() {
    // Pulsing animation for the emoji
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
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Animated paper plane emoji
        Text(
            text = "✈️",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier
                .scale(scale)
                .graphicsLayer { this.alpha = alpha }
        )

        Text(
            text = "Sending Report...",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        LinearProgressIndicator(
            modifier = Modifier.width(200.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Please wait",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * ========================================
 * ✨ SUCCESS SCREEN - 2025 MODERN UPDATE
 * ========================================
 *
 * Updated: 2025-10-19 18:46:12 UTC by @Magdyz
 *
 * CHANGES:
 * - ✅ Sharp vector checkmark (not emoji)
 * - ✅ Purple brand color (not green)
 * - ✅ Concentric circles design (matches post success)
 * - ✅ Smooth bounce animation
 * - ✅ FIXED: Removed problematic Shield icon (simplified design)
 */
@Composable
private fun SuccessContent() {
    // ========================================
    // ✨ Animation States
    // ========================================
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
                .fillMaxWidth()
                .padding(vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ========================================
            // ✨ MODERN PURPLE CHECKMARK
            // Sharp vector icon, not emoji
            // ========================================
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

                // Inner solid circle
                Surface(
                    modifier = Modifier.size(100.dp),
                    shape = CircleShape,
                    color = Color(0xFF9C27B0),  // ✅ PURPLE (brand color)
                    shadowElevation = 12.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ✨ SHARP VECTOR CHECKMARK
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Report sent",
                            modifier = Modifier.size(70.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            // ========================================
            // ✨ Success Messages
            // ========================================
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main title
                Text(
                    text = "Report Sent! 🛡️",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                // Subtitle
                Text(
                    text = "Thank you for helping keep\nour community safe.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        lineHeight = 26.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ✨ Info box with purple background
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = Color(0xFF9C27B0).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Our team will review this report shortly",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ✨ Auto-close message
            Text(
                text = "Closing automatically...",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * ✅ UNCHANGED: Already reported message
 */
@Composable
private fun AlreadyReportedContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "ℹ️",
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = "Already Reported",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "You've already reported this deal.\nThank you for helping keep our community safe.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "Close",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

/**
 * ✅ UNCHANGED: Daily limit reached message
 */
@Composable
private fun DailyLimitContent(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "⏱️",
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = "Daily Limit Reached",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "You've reached the maximum number of reports for today (5).\nPlease try again tomorrow.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "Close",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

/**
 * ✅ CHANGE 4: Report form WITHOUT inline button
 * Button removed - now shown as floating button in main ReportScreen
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportFormContent(
    viewModel: ReportViewModel,
    uiState: ReportUiState,
    onReasonSelected: (ReportReason) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Instructions
        Text(
            text = "Help us understand what's wrong",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Select a reason *",
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Reason options - Clean radio buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReportReason.values().forEach { reason ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = uiState.selectedReason == reason,
                            onClick = { onReasonSelected(reason) },
                            role = Role.RadioButton
                        ),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (uiState.selectedReason == reason) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                    ),
                    color = if (uiState.selectedReason == reason) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = uiState.selectedReason == reason,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reason.displayName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (uiState.selectedReason == reason) {
                                        FontWeight.Medium
                                    } else {
                                        FontWeight.Normal
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (reason == ReportReason.SCAM || reason == ReportReason.SPAM) {
                                Text(
                                    text = "Details required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Additional details",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (viewModel.isDetailsRequired()) {
                Text(
                    text = " *",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = " (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        if (viewModel.isDetailsRequired()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "⚠️ Please provide specific details about why this is ${uiState.selectedReason?.displayName}. Include evidence or context (minimum 30 characters).",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        OutlinedTextField(
            value = uiState.note,
            onValueChange = onNoteChanged,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            placeholder = {
                Text(
                    when (uiState.selectedReason) {
                        ReportReason.SCAM -> "Example: This seller requested payment outside the platform, refused to provide tracking info, or the product description is completely false..."
                        ReportReason.SPAM -> "Example: This is a duplicate post, contains promotional links to other sites, or is irrelevant advertising..."
                        ReportReason.EXPIRED -> "Optional: When did this expire? Is there a replacement deal?"
                        else -> "Provide any additional context..."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            maxLines = 6,
            supportingText = if (viewModel.isDetailsRequired()) {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.note.trim().length < 30) {
                                "Minimum 30 characters required"
                            } else {
                                "✓ Details provided"
                            },
                            color = if (uiState.note.trim().length >= 30) {
                                Color(0xFF10B981)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = viewModel.getDetailCharacterCount(),
                            color = if (uiState.note.trim().length >= 30) {
                                Color(0xFF10B981)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            } else null,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (viewModel.isDetailsRequired() && uiState.note.trim().length < 30) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                unfocusedBorderColor = if (viewModel.isDetailsRequired() && uiState.note.trim().length < 30) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            )
        )

        if (uiState.error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = uiState.error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ✅ CHANGE 5: REMOVED inline button - now shown as floating button in main ReportScreen
        // Old code removed:
        // Button(
        //     onClick = onSubmit,
        //     modifier = Modifier
        //         .fillMaxWidth()
        //         .height(52.dp),
        //     ...
        // )

        Text(
            text = "* Required field",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}