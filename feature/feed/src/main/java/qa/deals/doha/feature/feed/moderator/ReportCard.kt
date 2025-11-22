package qa.deals.doha.feature.feed.moderator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import qa.deals.doha.network.ReportWithDetailsDto
import java.text.SimpleDateFormat
import java.util.*

/**
 * Card component for displaying a reported deal
 * Shows report info, deal info, and reporter info with action buttons
 *
 * CREATED: 2025-11-22
 */
@Composable
fun ReportCard(
    report: ReportWithDetailsDto,
    onViewDeal: () -> Unit,
    onDismiss: (String?) -> Unit,
    onTakeAction: () -> Unit,
    actionInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showDismissDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            // Report Header with Reason Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9FAFB))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reason Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getReasonColor(report.reason)
                ) {
                    Text(
                        text = formatReasonDisplay(report.reason),
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Time ago
                report.createdAt?.let { createdAt ->
                    Text(
                        text = formatTimeAgo(createdAt),
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }

            // Deal Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onViewDeal)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Deal Image (smaller, thumbnail style)
                report.dealImage?.let { imageUrl ->
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(imageUrl)
                            .build(),
                        contentDescription = report.dealTitle,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFF5F5F5)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    )
                }

                // Deal Info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Deal Title
                    Text(
                        text = report.dealTitle ?: "Unknown Deal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF111827),
                        lineHeight = 20.sp
                    )

                    // Posted by
                    Text(
                        text = "Posted by ${report.dealPostedBy ?: "Unknown"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7280)
                    )

                    // Category & Status
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        report.dealCategory?.let { category ->
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFFEEF2FF)
                            ) {
                                Text(
                                    text = formatCategory(category),
                                    fontSize = 11.sp,
                                    color = Color(0xFF4F46E5),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        report.dealStatus?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = getStatusColor(status).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, getStatusColor(status).copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = status.uppercase(),
                                    fontSize = 9.sp,
                                    color = getStatusColor(status),
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Divider(color = Color(0xFFE5E7EB))

            // Reporter Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFAFAFA))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reported by",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.reporterUsername ?: report.reporterEmail ?: "Anonymous",
                            fontSize = 14.sp,
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.Bold
                        )
                        report.deviceId?.let { deviceId ->
                            Text(
                                text = "ID: ${deviceId.take(16)}...",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Reporter Role Badge
                    report.reporterRole?.let { role ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = getRoleColor(role).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, getRoleColor(role).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = role.uppercase(),
                                fontSize = 11.sp,
                                color = getRoleColor(role),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Report Note (if present)
                report.note?.let { note ->
                    if (note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Additional Details",
                                    fontSize = 12.sp,
                                    color = Color(0xFF374151),
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFF9CA3AF)
                                )
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE5E7EB))
                                ) {
                                    Text(
                                        text = note,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1F2937),
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Divider(color = Color(0xFFE5E7EB))

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // View Deal Button
                OutlinedButton(
                    onClick = onViewDeal,
                    enabled = !actionInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF9C27B0)
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF9C27B0))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "View Deal",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "View",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dismiss Button
                OutlinedButton(
                    onClick = { showDismissDialog = true },
                    enabled = !actionInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF6B7280)
                    ),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF6B7280))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Dismiss",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Take Action Button
                Button(
                    onClick = onTakeAction,
                    enabled = !actionInProgress,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 4.dp
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Take Action",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Action",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Dismiss Dialog
    if (showDismissDialog) {
        DismissReportDialog(
            onConfirm = { reason ->
                showDismissDialog = false
                onDismiss(reason)
            },
            onDismiss = { showDismissDialog = false }
        )
    }
}

/**
 * Dialog for dismissing a report
 */
@Composable
private fun DismissReportDialog(
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dismiss Report") },
        text = {
            Column {
                Text("Mark this report as reviewed with no action needed?")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    placeholder = { Text("E.g., Not a violation, user error, etc.") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B7280)
                )
            ) {
                Text("Dismiss")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Get color for report reason badge
 */
private fun getReasonColor(reason: String?): Color {
    return when (reason?.lowercase()) {
        "scam" -> Color(0xFFEF4444)      // Red
        "spam" -> Color(0xFFF97316)      // Orange
        "expired" -> Color(0xFFFBBF24)   // Yellow
        "other" -> Color(0xFF6B7280)     // Gray
        else -> Color(0xFF9CA3AF)        // Light gray
    }
}

/**
 * Get color for deal status badge
 */
private fun getStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "approved" -> Color(0xFF10B981)  // Green
        "pending" -> Color(0xFFFBBF24)   // Yellow
        "rejected" -> Color(0xFFEF4444)  // Red
        else -> Color(0xFF6B7280)        // Gray
    }
}

/**
 * Get color for user role badge
 */
private fun getRoleColor(role: String): Color {
    return when (role.lowercase()) {
        "admin" -> Color(0xFF9C27B0)     // Purple
        "moderator" -> Color(0xFF3B82F6) // Blue
        "user" -> Color(0xFF10B981)      // Green
        else -> Color(0xFF6B7280)        // Gray
    }
}

/**
 * Format reason for display
 */
private fun formatReasonDisplay(reason: String?): String {
    return when (reason?.lowercase()) {
        "scam" -> "🚨 SCAM"
        "spam" -> "⚠️ SPAM"
        "expired" -> "⏰ EXPIRED"
        "other" -> "❓ OTHER"
        else -> "REPORT"
    }
}

/**
 * Format time ago string
 */
private fun formatTimeAgo(timestamp: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val date = format.parse(timestamp)
        val now = System.currentTimeMillis()
        val diff = now - (date?.time ?: now)

        when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> "${diff / 86400_000}d ago"
        }
    } catch (e: Exception) {
        "Recently"
    }
}

/**
 * Format category display name
 */
private fun formatCategory(category: String): String {
    return when (category) {
        "food_dining" -> "Food & Dining"
        "shopping_fashion" -> "Shopping & Fashion"
        "entertainment" -> "Entertainment"
        "home_services" -> "Home Services"
        else -> "Other"
    }
}
