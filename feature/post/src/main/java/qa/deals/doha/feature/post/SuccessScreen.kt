package qa.deals.doha.feature.post

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay

/**
 * ✨ SUCCESS CELEBRATION SCREEN
 *
 * Animated success screen shown after deal submission
 *
 * FEATURES:
 * 1. ✅ Animated checkmark with bounce effect
 * 2. 🎉 Celebration message with slide-in animation
 * 3. ⏱️ Auto-dismiss countdown (3 seconds)
 * 4. 🎨 Matches app color palette
 * 5. 📱 "Go to Feed" button for manual navigation
 * 6. 🎭 Smooth fade-in/fade-out transitions
 *
 * @param onDismiss Callback when user navigates to feed
 * @author Magdyz
 * @date 2025-10-16
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
    // ✨ Full-screen overlay with animations
    // ========================================
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A).copy(alpha = 0.95f)), // Dark overlay
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
                // ✨ Animated Checkmark Circle
                // ========================================
                Surface(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(scale), // Bounce animation
                    shape = CircleShape,
                    color = Color(0xFF10B981), // Success green
                    shadowElevation = 8.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            modifier = Modifier.size(80.dp),
                            tint = Color.White
                        )
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
                            containerColor = Color(0xFF10B981), // Success green
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

/**
 * ✨ OPTIONAL: Confetti Particle Animation
 *
 * Uncomment this section to add floating confetti particles
 */
/*
@Composable
private fun ConfettiParticle(
    modifier: Modifier = Modifier,
    color: Color
) {
    var offsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = tween(durationMillis = 2000)
        ) { value, _ ->
            offsetY = value
        }
    }

    Box(
        modifier = modifier
            .offset(y = offsetY.dp)
            .size(8.dp)
            .background(color, CircleShape)
    )
}
*/