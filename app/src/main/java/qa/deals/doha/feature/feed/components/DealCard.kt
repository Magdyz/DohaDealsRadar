// Code to reconnect all button functions in DealCard.kt

package qa.deals.doha.feature.feed.components

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*n
@Composable
fun DealCard(
    context: Context,
    onClick: () -> Unit,
    onVoteHot: () -> Unit,
    onVoteCold: () -> Unit
) {
    // Remove report button but keep styling commented out for future use
    // Button(onClick = { /* TODO: Implement report functionality */ }) {
    //     Text("Report")
    // }
    
    // Card layout
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = 4.dp,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Content of the deal
            Text("Deal Title")
            Spacer(modifier = Modifier.height(8.dp))

            // View button
            Button(onClick = onClick) {
                Text("View Deal")
            }

            // Hot vote chip
            Chip(onClick = onVoteHot) {
                Text("Vote Hot")
            }

            // Cold vote chip
            Chip(onClick = onVoteCold) {
                Text("Vote Cold")
            }
        }
    }
}