package qa.deals.doha.feature.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import qa.deals.doha.network.ReportReason

/**
 * Report Screen - Clean, minimal Vinted-inspired design
 * ✅ ENHANCED: Now validates high-severity reports require details
 */
@OptIn(ExperimentalMaterial3Api::class)
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
            kotlinx.coroutines.delay(2000)
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
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when {
                uiState.success -> SuccessContent()
                uiState.alreadyReported -> AlreadyReportedContent(onClose)
                uiState.dailyLimitReached -> DailyLimitContent(onClose)
                else -> ReportFormContent(
                    viewModel = viewModel, // ✅ NEW: Pass viewModel for validation
                    uiState = uiState,
                    onReasonSelected = { viewModel.selectReason(it) },
                    onNoteChanged = { viewModel.updateNote(it) },
                    onSubmit = { viewModel.submitReport() }
                )
            }
        }
    }
}

/**
 * Success message - Clean and encouraging
 */
@Composable
private fun SuccessContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "✅",
            style = MaterialTheme.typography.displayLarge
        )
        Text(
            text = "Thank You!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Your report has been submitted.\nWe'll review it shortly.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Already reported message
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
 * Daily limit reached message
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
 * Report form - Clean, minimal design
 * ✅ ENHANCED: Shows validation requirements for high-severity reasons
 */
@Composable
private fun ReportFormContent(
    viewModel: ReportViewModel, // ✅ NEW: Added for validation checks
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
                        // ✅ ENHANCED: Show validation requirement indicator
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
                            // ✅ NEW: Show "Details required" badge for high-severity reasons
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

        // ✅ ENHANCED: Dynamic label based on requirement
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
            // ✅ NEW: Show asterisk if required, otherwise "(optional)"
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

        // ✅ NEW: Info box explaining requirement for high-severity reasons
        if (viewModel.isDetailsRequired()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "⚠️ Please provide specific details about why this is ${uiState.selectedReason?.displayName}. Include evidence or context (minimum 20 characters).",
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
                .heightIn(min = 120.dp), // Keep original height
            placeholder = {
                // ✅ ENHANCED: Context-aware placeholder based on selected reason
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
            maxLines = 5,
            // ✅ NEW: Show character count and validation status for required fields
            supportingText = if (viewModel.isDetailsRequired()) {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.note.trim().length < 20) {
                                "Minimum 20 characters required"
                            } else {
                                "✓ Details provided"
                            },
                            color = if (uiState.note.trim().length >= 20) {
                                Color(0xFF10B981)
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = viewModel.getDetailCharacterCount(),
                            color = if (uiState.note.trim().length >= 20) {
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
                // ✅ ENHANCED: Error state for required but incomplete details
                focusedBorderColor = if (viewModel.isDetailsRequired() && uiState.note.trim().length < 20) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                unfocusedBorderColor = if (viewModel.isDetailsRequired() && uiState.note.trim().length < 20) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            )
        )

        // Error message
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

        // Submit button (unchanged)
        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !uiState.loading && uiState.selectedReason != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            if (uiState.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onError,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Submitting...")
            } else {
                Text(
                    "Submit Report",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Helper text (unchanged)
        Text(
            text = "* Required field",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}