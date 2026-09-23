package eg.deals.radar.feature.post

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eg.deals.core.design.components.PrivacyPolicyDialog

/**
 * ========================================
 * 🔐 SIGN IN WITH GOOGLE
 * ========================================
 * One button, no password, no email typing, no codes to wait for.
 *
 * Privacy promise shown here is real: the backend keeps only an internal id
 * and the random username it generates — the Google address is never stored
 * in our own tables and is never shown to anyone.
 */
@Composable
fun GoogleSignInScreen(
    onCancel: () -> Unit,
    onSignIn: (consent: Boolean) -> Unit,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    var consent by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onCancel, enabled = !isLoading) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.post_dup_cancel))
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFF9C27B0))),
                        shape = RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("🔥", fontSize = 40.sp)
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.signin_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.signin_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // What signing in does and doesn't mean
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrivacyLine(stringResource(R.string.signin_promise_username))
                    PrivacyLine(stringResource(R.string.signin_promise_no_email))
                    PrivacyLine(stringResource(R.string.signin_promise_no_password))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Consent (required to create an account)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isLoading) { consent = !consent },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = !isLoading)
                Column(Modifier.padding(start = 4.dp)) {
                    Text(stringResource(R.string.signin_consent), style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = stringResource(R.string.signin_read_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { showPrivacy = true }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onSignIn(consent) },
                enabled = !isLoading && consent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F1F1F),
                    disabledContainerColor = Color.White.copy(alpha = 0.5f),
                    disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDADCE0))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFF9C27B0))
                } else {
                    GoogleGlyph()
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.signin_with_google),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!consent && !isLoading) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.signin_consent_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.signin_browse_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PrivacyLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("✓ ", color = Color(0xFF059669), fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Google's four-colour "G", drawn simply so no extra asset is needed. */
@Composable
private fun GoogleGlyph() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(Color.White, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "G",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4285F4)
        )
    }
}
