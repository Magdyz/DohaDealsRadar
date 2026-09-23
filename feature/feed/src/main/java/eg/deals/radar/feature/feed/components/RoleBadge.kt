package eg.deals.radar.feature.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import eg.deals.radar.feature.feed.R

/**
 * Role badge component
 * Displays user's role with appropriate color coding
 */
@Composable
fun RoleBadge(
    role: String,
    autoApprove: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (badgeText, backgroundColor) = when (role) {
        "admin" -> stringResource(R.string.role_admin) to Color(0xFFDC2626) // Red
        "moderator" -> stringResource(R.string.role_moderator) to Color(0xFF2563EB) // Blue
        "user" -> {
            if (autoApprove) {
                stringResource(R.string.role_trusted) to Color(0xFF10B981) // Green
            } else {
                stringResource(R.string.role_user) to Color(0xFF6B7280) // Gray
            }
        }
        else -> stringResource(R.string.role_user) to Color(0xFF6B7280)
    }

    Text(
        text = badgeText,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
