package qa.deals.doha.feature.feed.moderator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ========================================
 * ✨ ANALYTICS DASHBOARD SCREEN
 * PostHog Analytics Integration for Moderators
 * ========================================
 *
 * Purpose:
 * - Provide moderators access to PostHog analytics dashboard
 * - Display key metrics and tracking information
 * - Show what analytics are being tracked
 *
 * Tracks Core Value Actions:
 * 1. Happy Path (Funnel): App_Opened → Deal_Viewed → Deal_Link_Clicked → Deal_Shared
 * 2. User Focus: Scroll depth, rage clicks, heatmaps
 * 3. Feature Adoption: Voting system usage, retention correlation
 * 4. Session Replay: Watch real user sessions
 *
 * Created: 2025-11-24
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2563EB),  // Blue
                                Color(0xFF1E40AF)   // Darker blue
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(1000f, 1000f)
                        )
                    )
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "📊 Analytics Dashboard",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "PostHog Analytics",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF9FAFB))
                .verticalScroll(scrollState)
        ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Quick Access Card
        QuickAccessCard(
            onOpenPostHog = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://app.posthog.com"))
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // What We Track Section
        Text(
            text = "📈 What We're Tracking",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F2937),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Core Value Actions
        TrackingCategoryCard(
            title = "1. Happy Path (Funnel)",
            description = "Track user journey from viewing to conversion",
            events = listOf(
                "App_Opened" to "User launches the app",
                "Deal_Viewed" to "User views a deal (with Category, Price)",
                "Deal_Link_Clicked" to "🎯 CONVERSION - User clicks deal link",
                "Deal_Shared" to "🚀 VIRAL LOOP - User shares deal"
            ),
            iconColor = Color(0xFF10B981)
        )

        Spacer(modifier = Modifier.height(12.dp))

        TrackingCategoryCard(
            title = "2. User Focus (Heatmaps)",
            description = "Understand where users pay attention",
            events = listOf(
                "Scroll Depth" to "Do users scroll to see 'Cold' deals?",
                "Rage Clicks" to "Detect frustrated users (5+ rapid taps)",
                "Time on Screen" to "Which screens keep users engaged?",
                "Click Heatmaps" to "Where do users tap most?"
            ),
            iconColor = Color(0xFFF59E0B)
        )

        Spacer(modifier = Modifier.height(12.dp))

        TrackingCategoryCard(
            title = "3. Feature Adoption (Voting)",
            description = "Measure engagement with Fire/Ice voting",
            events = listOf(
                "Vote Cast" to "% of users who voted at least once",
                "Retention Correlation" to "Do voters come back more?",
                "Vote Type" to "Fire vs Ice distribution",
                "Vote Changes" to "How often users change votes"
            ),
            iconColor = Color(0xFFEF4444)
        )

        Spacer(modifier = Modifier.height(12.dp))

        TrackingCategoryCard(
            title = "4. Session Replay",
            description = "Watch real user sessions to understand behavior",
            events = listOf(
                "Full Sessions" to "Video-like recreation of user actions",
                "Search Behavior" to "What users search for and don't find",
                "Navigation Patterns" to "How users move through the app",
                "Drop-off Points" to "Where users abandon actions"
            ),
            iconColor = Color(0xFF8B5CF6)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Key Questions Card
        KeyQuestionsCard()

        Spacer(modifier = Modifier.height(16.dp))

            // How to Use Card
            HowToUseCard()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Quick access button to PostHog dashboard
 */
@Composable
private fun QuickAccessCard(
    onOpenPostHog: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onOpenPostHog),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2563EB)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Open PostHog Dashboard",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "View live analytics and session replays",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Open",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Display a category of tracked events
 */
@Composable
private fun TrackingCategoryCard(
    title: String,
    description: String,
    events: List<Pair<String, String>>,
    iconColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    Text(
                        text = description,
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = Color(0xFFE5E7EB))

            Spacer(modifier = Modifier.height(12.dp))

            // Events list
            events.forEach { (eventName, eventDescription) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "•",
                        fontSize = 14.sp,
                        color = iconColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(16.dp)
                    )
                    Column {
                        Text(
                            text = eventName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F2937)
                        )
                        Text(
                            text = eventDescription,
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Key questions this analytics can answer
 */
@Composable
private fun KeyQuestionsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "🎯 Key Questions We Can Answer",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val questions = listOf(
                "If 100 people view a deal, how many click the link?",
                "Are users scrolling to see 'Cold' deals or only viewing 'Hot' ones?",
                "Do users who vote come back more often than those who don't?",
                "What category gets the most engagement?",
                "Where do users rage-click (frustration points)?",
                "What do users search for but can't find?",
                "How long do users spend on the app per session?",
                "What's the drop-off rate in the deal submission funnel?"
            )

            questions.forEach { question ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "❓",
                        fontSize = 14.sp,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = question,
                        fontSize = 13.sp,
                        color = Color(0xFF374151)
                    )
                }
            }
        }
    }
}

/**
 * Instructions on how to use the analytics
 */
@Composable
private fun HowToUseCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How to Use PostHog",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF92400E)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val steps = listOf(
                "Open PostHog Dashboard" to "Tap the blue button above",
                "View Live Events" to "Activity → Live Events (see events in real-time)",
                "Check Trends" to "Product Analytics → Trends (see charts)",
                "Watch Sessions" to "Session Replay (watch actual user sessions)",
                "Analyze Funnels" to "Funnels → Create (track drop-off rates)",
                "Check Retention" to "Retention → See who comes back"
            )

            steps.forEachIndexed { index, (title, description) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = Color(0xFFA16207)
                        )
                    }
                }
            }
        }
    }
}

