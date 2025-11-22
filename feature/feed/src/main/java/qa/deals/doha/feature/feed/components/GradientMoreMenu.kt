package qa.deals.doha.feature.feed.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

// ========================================
// 🎨 ARTIST UI: GRADIENT MENU
// ========================================

@Composable
fun GradientMoreMenu(
    onFeedbackClick: () -> Unit,
    onRateAppClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // The Signature Gradient (Matches your Post FAB)
    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF9C27B0),  // Purple
                Color(0xFFE91E63)   // Pink
            ),
            start = Offset(0f, 0f),
            end = Offset(100f, 100f) // Diagonal gradient
        )
    }

    // Rotation animation for the dots container when clicked
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rotation"
    )

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        // 1. THE TRIGGER: Three Clean Gradient Circles
        Row(
            modifier = Modifier
                .padding(end = 8.dp) // Slight padding from edge
                .size(40.dp) // Touch target size
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null // Disable default ripple for cleaner look
                ) { expanded = !expanded }
                .rotate(rotation) // Rotates vertically when open
                .padding(8.dp), // Inner padding
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Render 3 dots
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(6.dp) // Size of each dot
                        .background(gradientBrush, CircleShape)
                )
            }
        }

        // 2. THE POPUP MENU (The "Big Gradient Circle")
        // We use a Popup so it floats above everything else
        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
                alignment = Alignment.TopEnd,
                offset = androidx.compose.ui.unit.IntOffset(x = -20, y = 110), // Offset to align below dots
                properties = PopupProperties(focusable = true)
            ) {
                // Animate entrance
                AnimatedVisibility(
                    visible = true, // Always true inside the if(expanded) block effectively, but needed for transition
                    enter = scaleIn(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        transformOrigin = TransformOrigin(1f, 0f) // Expands from Top Right
                    ) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    // The Container "Bubble"
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .shadow(16.dp, RoundedCornerShape(20.dp)) // Soft drop shadow
                            .background(gradientBrush, RoundedCornerShape(20.dp)) // The Gradient Background
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        // Option 1: Feedback
                        MenuOptionItem(
                            icon = Icons.Rounded.Email,
                            text = "Feedback",
                            onClick = {
                                expanded = false
                                onFeedbackClick()
                            },
                            delayMillis = 50 // Staggered animation
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Divider (White with low opacity)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            thickness = 1.dp,
                            color = Color.White.copy(alpha = 0.2f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Option 2: Rate App
                        MenuOptionItem(
                            icon = Icons.Rounded.Star,
                            text = "Rate App",
                            onClick = {
                                expanded = false
                                onRateAppClick()
                            },
                            delayMillis = 100 // Staggered animation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuOptionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    delayMillis: Long
) {
    var isVisible by remember { mutableStateOf(false) }

    // Trigger entrance animation
    LaunchedEffect(Unit) {
        delay(delayMillis)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + androidx.compose.animation.slideInHorizontally { it / 2 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Icon with semi-transparent bubble
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Text (same size and weight as View Deal button)
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
