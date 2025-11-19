package qa.deals.doha.feature.feed.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset

/**
 * ========================================
 * 🗳️ VOTE AUTHENTICATION DIALOG
 * ========================================
 *
 * Shows when anonymous user tries to vote.
 * Prompts user to:
 * 1. Log in (if they have account)
 * 2. Verify email (quick signup)
 *
 * Design: Matches UsernameDialog styling (Modern Material3)
 * Tone: Friendly, encouraging, non-blocking
 * UX Pattern: Instagram/YouTube 2025 - lightweight auth gate
 *
 * Created: 2025-11-19
 * Migration: Part of device_id → user_id voting migration
 *
 * @param voteType "hot" or "cold" - customizes icon/colors
 * @param onDismiss User clicks "Maybe Later" or outside dialog
 * @param onLoginClick User wants to log in (has account)
 * @param onVerifyEmailClick User wants quick email verification (new)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoteAuthDialog(
    voteType: String,
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit,
    onVerifyEmailClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // ========================================
                // 🎨 HEADER ICON (Fire/Ice based on vote type)
                // ========================================

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (voteType == "hot") {
                                    listOf(
                                        Color(0xFFFF6B00),  // Orange
                                        Color(0xFFFF0000)   // Red
                                    )
                                } else {
                                    listOf(
                                        Color(0xFF2196F3),  // Blue
                                        Color(0xFF00BCD4)   // Cyan
                                    )
                                },
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Emoji based on vote type
                    Text(
                        text = if (voteType == "hot") "🔥" else "❄️",
                        fontSize = 48.sp
                    )
                }

                // ========================================
                // 📝 FRIENDLY TITLE & MESSAGE
                // ========================================

                Text(
                    text = "Please verify you're human",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "To keep voting fair and prevent fraud, we need to verify your identity. It only takes a minute!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // ========================================
                // 🔘 ACTION BUTTONS
                // ========================================

                // Login button
                Button(
                    onClick = {
                        onDismiss()
                        onLoginClick()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Log In (Quick!)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Cancel option
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Maybe Later",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ========================================
                // ℹ️ INFO TEXT
                // ========================================

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "One vote per deal, per person. Your email stays private.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
