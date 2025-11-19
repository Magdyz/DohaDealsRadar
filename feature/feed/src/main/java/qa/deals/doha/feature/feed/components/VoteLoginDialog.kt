package qa.deals.doha.feature.feed.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ========================================
 * ✨ VOTE LOGIN DIALOG
 * Created: 2025-11-19
 * ========================================
 *
 * Friendly dialog shown to anonymous users when they attempt to vote.
 * Prompts them to log in or verify their email to enable voting.
 *
 * Design:
 * - Friendly, non-intrusive tone
 * - Matches app's Material3 theme
 * - Explains the reason (preventing fraud, keeping deals genuine)
 * - Provides clear call-to-action
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onLoginClick Callback when user chooses to log in
 */
@Composable
fun VoteLoginDialog(
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Vote on this deal?",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = "Please log in or verify your email to vote. " +
                        "This helps us keep deals genuine and prevent fraud!",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEA580C)  // Orange theme (matches app)
                )
            ) {
                Text("Log In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later")
            }
        }
    )
}
