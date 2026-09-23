package eg.deals.radar.feature.feed.moderator

import eg.deals.radar.feature.feed.R

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eg.deals.domain.DealCategory
import eg.deals.domain.Governorate
import eg.deals.radar.network.AppHealthDto
import eg.deals.radar.network.StatsDto
import eg.deals.radar.repository.DealRepository
import kotlinx.coroutines.launch

/**
 * ========================================
 * 📈 COMMUNITY STATS (moderators/admins, English-only)
 * ========================================
 * Anonymous aggregate numbers computed on our own backend (get_stats).
 * No analytics SDK, no per-user tracking (crash reports: Crashlytics, no user ids).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = remember { DealRepository() }
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<StatsDto?>(null) }
    var appHealth by remember { mutableStateOf<AppHealthDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            repo.getStats()
                .onSuccess { stats = it; appHealth = it.appHealth } // app_health: admins only
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderator_stats_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { load() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading && stats == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null && stats == null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { load() }) { Text(stringResource(R.string.common_retry)) }
                }
                else -> stats?.let { s -> StatsContent(s, appHealth) }
            }
        }
    }
}

@Composable
private fun StatsContent(s: StatsDto, appHealth: AppHealthDto?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Anonymous totals from our own database. No tracking of individual users.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("Deals")
        TileRow(
            "Live now" to s.liveDeals,
            "Waiting review" to s.pendingReview,
            "Hidden by reports" to s.hiddenByReports
        )
        TileRow(
            "Posted 24h" to s.posted24h,
            "Posted 7 days" to s.posted7d,
            "Approved 7 days" to s.approved7d
        )
        TileRow(
            "Rejected 7 days" to s.rejected7d,
            "Open reports" to s.openReports,
            "Votes 7 days" to s.votes7d
        )

        SectionTitle("People")
        TileRow("Accounts" to s.usersTotal, "New 7 days" to s.newUsers7d)

        if (s.topCategories7d.isNotEmpty()) {
            SectionTitle("Top categories (7 days)")
            BarList(s.topCategories7d.map { DealCategory.fromId(it.name).let { c -> "${c.emoji} ${c.displayName}" } to it.count })
        }
        if (s.topGovernorates7d.isNotEmpty()) {
            SectionTitle("Top places (7 days)")
            BarList(s.topGovernorates7d.map { (Governorate.fromId(it.name)?.displayName ?: it.name) to it.count })
        }
        s.generatedAt?.let {
            Text("Updated $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Admins only: the backend omits app_health entirely for moderators.
        appHealth?.let { health -> AppHealthSection(health) }
    }
}

@Composable
private fun AppHealthSection(health: AppHealthDto) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("App health")
        TileRow(
            "Crashes 24h" to health.crashes24h,
            "Crashes 7 days" to health.crashes7d
        )
        TileRow(
            "Errors 24h" to health.errors24h,
            "Errors 7 days" to health.errors7d
        )
        if (health.topErrorAreas7d.isNotEmpty()) {
            Text("Top error areas (7 days)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            BarList(health.topErrorAreas7d.map { it.name to it.count })
        }
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://console.firebase.google.com/project/dohadeals-3d6b1/crashlytics")
                )
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open crash reports")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun TileRow(vararg tiles: Pair<String, Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        tiles.forEach { (label, value) ->
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(value.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9C27B0))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BarList(items: List<Pair<String, Int>>) {
    val max = (items.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, count) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(count.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / max)
                            .fillMaxHeight()
                            .background(Color(0xFF9C27B0))
                    )
                }
            }
        }
    }
}
