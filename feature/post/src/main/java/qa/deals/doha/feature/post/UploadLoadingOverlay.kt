package qa.deals.doha.feature.post

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * ========================================
 * ✨ UPLOAD LOADING OVERLAY - 2025
 * ========================================
 *
 * Updated: 2025-10-19 18:05:44 UTC by @Magdyz
 *
 * FIXES APPLIED:
 * - ✅ Blocks ALL background touches (prevents double submission)
 * - ✅ Cannot dismiss dialog
 * - ✅ Full-screen modal overlay
 * - ✅ Purple brand color for spinner
 *
 * FEATURES:
 * 1. 🔒 Prevents all user interaction with form
 * 2. 🎨 Modern glassmorphism design
 * 3. 📊 Shows upload progress stages
 * 4. ✅ Animated stage completion
 * 5. 💫 Smooth fade transitions
 * 6. 🎭 Purple brand colors
 *
 * STAGES:
 * 1. 📦 Compressing image...
 * 2. ☁️ Uploading preview...
 * 3. 📤 Posting deal...
 * 4. 🖼️ Uploading full image...
 */
@Composable
fun UploadLoadingOverlay(
    message: String?
) {
    // ========================================
    // ✨ Determine current upload stage
    // ========================================
    val currentStage = when {
        message?.contains("Compressing", ignoreCase = true) == true -> UploadStage.COMPRESSING
        message?.contains("Uploading preview", ignoreCase = true) == true -> UploadStage.UPLOADING_PREVIEW
        message?.contains("Posting", ignoreCase = true) == true -> UploadStage.POSTING
        message?.contains("full image", ignoreCase = true) == true -> UploadStage.UPLOADING_FULL
        else -> UploadStage.COMPRESSING
    }

    // ========================================
    // ✨ FIX: Use Dialog to block ALL touches
    // ========================================
    Dialog(
        onDismissRequest = { /* ✅ Cannot dismiss during upload */ },
        properties = DialogProperties(
            dismissOnBackPress = false,      // ✅ Blocks back button
            dismissOnClickOutside = false,   // ✅ Blocks outside clicks
            usePlatformDefaultWidth = false  // ✅ Full screen
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))  // ✅ Dark overlay
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // ✅ Consume all clicks (do nothing)
                },
            contentAlignment = Alignment.Center
        ) {
            // ========================================
            // ✨ Modern glassmorphism card
            // ========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // ========================================
                    // ✨ Main loading indicator (PURPLE)
                    // ========================================
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp,
                        color = Color(0xFF9C27B0)  // ✅ PURPLE (brand color)
                    )

                    // ========================================
                    // ✨ Upload title
                    // ========================================
                    Text(
                        text = "Posting Your Deal",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    // ========================================
                    // ✨ Upload stages with progress
                    // ========================================
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UploadStageItem(
                            stage = UploadStage.COMPRESSING,
                            currentStage = currentStage,
                            icon = Icons.Default.Image,
                            label = "Compressing image"
                        )

                        UploadStageItem(
                            stage = UploadStage.UPLOADING_PREVIEW,
                            currentStage = currentStage,
                            icon = Icons.Default.CloudUpload,
                            label = "Uploading preview"
                        )

                        UploadStageItem(
                            stage = UploadStage.POSTING,
                            currentStage = currentStage,
                            icon = Icons.Default.Send,
                            label = "Posting deal"
                        )

                        UploadStageItem(
                            stage = UploadStage.UPLOADING_FULL,
                            currentStage = currentStage,
                            icon = Icons.Default.CloudUpload,
                            label = "Uploading full image"
                        )
                    }

                    // ========================================
                    // ✨ Current status message
                    // ========================================
                    if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF9C27B0),  // ✅ PURPLE
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }

                    // ========================================
                    // ✨ Info message
                    // ========================================
                    Text(
                        text = "Please don't close the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * ✨ Upload Stage Item
 * Shows individual stage with icon, label, and completion state
 */
@Composable
private fun UploadStageItem(
    stage: UploadStage,
    currentStage: UploadStage,
    icon: ImageVector,
    label: String
) {
    // Determine state
    val isCompleted = stage.ordinal < currentStage.ordinal
    val isActive = stage == currentStage

    // Animated alpha for fade effect
    val alpha by animateFloatAsState(
        targetValue = when {
            isCompleted -> 1f
            isActive -> 1f
            else -> 0.4f
        },
        label = "stage_alpha"
    )

    // Animated scale for active stage
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "stage_scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✨ Icon or checkmark
        if (isCompleted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Completed",
                tint = Color(0xFF9C27B0),  // ✅ PURPLE (not green!)
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF9C27B0) else Color.Gray,  // ✅ PURPLE
                modifier = Modifier.size(24.dp)
            )
        }

        // ✨ Label
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            ),
            color = when {
                isCompleted -> Color(0xFF9C27B0)  // ✅ PURPLE
                isActive -> Color(0xFF9C27B0)     // ✅ PURPLE
                else -> Color.Gray
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // ✨ Loading indicator for active stage (PURPLE)
        if (isActive) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF9C27B0)  // ✅ PURPLE
            )
        }
    }
}

/**
 * ✨ Upload Stages Enum
 * Defines the order of upload operations
 */
private enum class UploadStage {
    COMPRESSING,
    UPLOADING_PREVIEW,
    POSTING,
    UPLOADING_FULL
}