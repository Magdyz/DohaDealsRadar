package eg.deals.core.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eg.deals.radar.core.design.R

/**
 * Plain-language privacy policy & terms (EN / AR), shown inside the app.
 * Keep in sync with the Play Store Data Safety form and the web version.
 */
@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    val sections = listOf(
        R.string.privacy_collect_title to R.string.privacy_collect_body,
        R.string.privacy_not_collect_title to R.string.privacy_not_collect_body,
        R.string.privacy_why_title to R.string.privacy_why_body,
        R.string.privacy_where_title to R.string.privacy_where_body,
        R.string.privacy_retention_title to R.string.privacy_retention_body,
        R.string.privacy_rights_title to R.string.privacy_rights_body,
        R.string.privacy_rules_title to R.string.privacy_rules_body,
        R.string.privacy_contact_title to R.string.privacy_contact_body,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.privacy_intro),
                    style = MaterialTheme.typography.bodyMedium
                )
                sections.forEach { (title, body) ->
                    Text(
                        stringResource(title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(R.string.privacy_updated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.privacy_close)) }
        }
    )
}
