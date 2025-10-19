package qa.deals.doha.feature.post

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * ========================================
 * ✨ SUCCESS CELEBRATION SCREEN - 2025
 * ========================================
 *
 * Updated: 2025-10-19 18:05:44 UTC by @Magdyz
 *
 * FIXES APPLIED:
 * - ✅ Blocks ALL background touches (prevents double submission)
 * - ✅ Purple checkmark (brand color, not green)
 * - ✅ Vector icon (sharp, high quality)
 * - ✅ Cannot dismiss dialog
 * - ✅ Full-screen modal overlay
 *
 * FEATURES:
 * 1. ✅ Animated checkmark with bounce effect
 * 2. 🎉 Celebration message with slide-in animation
 * 3. ⏱️ Auto-dismiss countdown (3 seconds)
 * 4. 🎨 Purple brand colors throughout
 * 5. 📱 "Go to Feed" button for manual navigation
 * 6. 🎭 Smooth fade-in/fade-out transitions
 * 7. 🔒 Blocks all user interaction
 */
@Composable
fun SuccessScreen(
    onDismiss: () -> Unit
) {
    // ========================================
    // ✨ Animation States
    // ========================================
    var visible by remember { mutableStateOf(false) }
    var checkmarkScale by remember { mutableStateOf(0f) }
    var messageAlpha by remember { mutableStateOf(0f) }
    var buttonVisible by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(3) }

    // ✨ Animated values
    val scale by animateFloatAsState(
        targetValue = checkmarkScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmark_scale"
    )

    val alpha by animateFloatAsState(
        targetValue = messageAlpha,
        animationSpec = tween(durationMillis = 600),
        label = "message_alpha"
    )

    // ========================================
    // ✨ Launch animations on mount
    // ========================================
    LaunchedEffect(Unit) {
        Log.d("SuccessScreen", "🎉 Success screen launched")

        // Fade in background
        visible = true
        delay(100)

        // Animate checkmark (bounce in)
        checkmarkScale = 1f
        delay(300)

        // Fade in message
        messageAlpha = 1f
        delay(400)

        // Show button
        buttonVisible = true

        // Countdown timer
        for (i in 3 downTo 1) {
            countdown = i
            delay(1000)
        }

        // Auto-dismiss
        Log.d("SuccessScreen", "⏱️ Auto-dismiss after countdown")
        onDismiss()
    }

    // ========================================
    // ✨ FIX: Use Dialog to block ALL touches
    // ========================================
    Dialog(
        onDismissRequest = { /* ✅ Prevent dismissal */ },
        properties = DialogProperties(
            dismissOnBackPress = false,      // ✅ Cannot back out
            dismissOnClickOutside = false,   // ✅ Cannot tap outside
            usePlatformDefaultWidth = false  // ✅ Full screen
        )
    ) {
        // ✨ Full-screen overlay that blocks touches
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))  // ✅ Darker overlay
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // ✅ Consume all clicks (do nothing)
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // ========================================
                    // ✨ PURPLE CHECKMARK with concentric circles
                    // Changed from green to purple (brand color)
                    // ========================================
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(scale), // ✅ Bounce animation
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer circle (light purple)
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(
                                    color = Color(0xFF9C27B0).copy(alpha = 0.15f),  // ✅ PURPLE
                                    shape = CircleShape
                                )
                        )

                        // Middle circle (medium purple)
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(
                                    color = Color(0xFF9C27B0).copy(alpha = 0.3f),  // ✅ PURPLE
                                    shape = CircleShape
                                )
                        )

                        // Inner solid circle
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = Color(0xFF9C27B0),  // ✅ PURPLE (not green!)
                            shadowElevation = 8.dp
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // ✅ VECTOR ICON (sharp, high quality)
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    modifier = Modifier.size(60.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // ========================================
                    // ✨ Success Messages with Fade Animation
                    // ========================================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { this.alpha = alpha }, // Fade in
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main success message
                        Text(
                            text = "Deal Posted! 🎉",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        // Subtitle
                        Text(
                            text = "Your deal is being reviewed and will appear in the feed soon!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ========================================
                    // ✨ "Go to Feed" Button with Slide Animation
                    // Changed to purple theme
                    // ========================================
                    AnimatedVisibility(
                        visible = buttonVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn()
                    ) {
                        Button(
                            onClick = {
                                Log.d("SuccessScreen", "👆 User clicked 'Go to Feed'")
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9C27B0),  // ✅ PURPLE (not green!)
                                contentColor = Color.White
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                "Go to Feed",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // ========================================
                    // ✨ Auto-dismiss Countdown
                    // ========================================
                    AnimatedVisibility(
                        visible = countdown > 0,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "Auto-closing in ${countdown}s...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}