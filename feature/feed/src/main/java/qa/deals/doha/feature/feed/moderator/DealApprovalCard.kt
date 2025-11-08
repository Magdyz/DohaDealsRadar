package qa.deals.doha.feature.feed.moderator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import qa.deals.doha.db.DealEntity
import qa.deals.doha.feature.feed.components.RoleBadge
import java.text.SimpleDateFormat
import java.util.*

/**
 * Card component for pending deal in moderator queue
 * Shows deal info with approve/reject/delete actions
 */
@Composable
fun DealApprovalCard(
    deal: DealEntity,
    onApprove: () -> Unit,
    onReject: (String?) -> Unit,  // ✅ Now accepts reason parameter
    onDelete: (String?) -> Unit,  // ✅ Now accepts reason parameter
    onClick: () -> Unit,
    actionInProgress: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            // Deal Image
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(deal.imageUrl)
                    .build(),
                contentDescription = deal.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    }
                }
            )

            // Deal Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Title
                Text(
                    text = deal.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF1F2937)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Posted by & Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Posted by ${deal.postedBy}",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )

                    deal.createdAt?.let { createdAt ->
                        Text(
                            text = formatTimeAgo(createdAt),
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                }

                // Category badge
                if (deal.category != "other") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEEF2FF)
                    ) {
                        Text(
                            text = formatCategory(deal.category),
                            fontSize = 11.sp,
                            color = Color(0xFF4F46E5),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Approve Button
                    Button(
                        onClick = onApprove,
                        enabled = !actionInProgress,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Approve",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve", fontSize = 14.sp)
                    }

                    // Reject Button
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        enabled = !actionInProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Reject",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reject", fontSize = 14.sp)
                    }

                    // Delete Button (Icon only)
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !actionInProgress
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFF6B7280)
                        )
                    }
                }
            }
        }
    }

    // Reject Dialog
    if (showRejectDialog) {
        RejectDealDialog(
            dealTitle = deal.title,
            onConfirm = { reason ->
                onReject(reason)  // ✅ Pass the reason!
            },
            onDismiss = { showRejectDialog = false }
        )
    }

    // Delete Dialog
    if (showDeleteDialog) {
        DeleteDealDialog(
            dealTitle = deal.title,
            onConfirm = { reason ->
                showDeleteDialog = false
                onDelete(reason)  // ✅ Pass the reason!
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * Dialog for rejecting a deal
 */
@Composable
private fun RejectDealDialog(
    dealTitle: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Deal") },
        text = {
            Column {
                Text("Are you sure you want to reject this deal?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dealTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444)
                )
            ) {
                Text("Reject")
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
 * Dialog for deleting a deal
 */
@Composable
private fun DeleteDealDialog(
    dealTitle: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Deal") },
        text = {
            Column {
                Text("This will permanently delete the deal. Are you sure?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dealTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.takeIf { it.isNotBlank() }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626)
                )
            ) {
                Text("Delete")
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
 * Format time ago string
 */
private fun formatTimeAgo(timestamp: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
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
