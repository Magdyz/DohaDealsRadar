package eg.deals.core.design.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** True when the UI is currently shown in Arabic (app language set to AR). */
@Composable
fun isArabicUi(): Boolean =
    LocalConfiguration.current.locales[0]?.language == "ar"

/**
 * EN / AR language switch button.
 * Shows the language you will switch TO ("ع" in English mode, "EN" in Arabic mode).
 */
@Composable
fun LanguageToggleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    val arabic = isArabicUi()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(36.dp)
            .semantics {
                contentDescription = if (arabic) "Switch to English" else "التبديل إلى العربية"
            },
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
    ) {
        Text(
            text = if (arabic) "EN" else "عربي",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Admin/moderator screens are English-only: keep them left-to-right even when
 * the rest of the app is in Arabic.
 */
@Composable
fun EnglishOnlyLayout(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        content()
    }
}
