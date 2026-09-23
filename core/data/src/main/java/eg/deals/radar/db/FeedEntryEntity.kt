package eg.deals.radar.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Which cached deals belong to which feed tab, and in what order.
 * A tab is identified by its filters (see [feedKey]); every tab keeps its own
 * list so switching back to a tab shows it instantly. Other screens (account,
 * profile, details, moderation) also cache deals in the `deals` table; only
 * deals listed here are shown in the feed, in the exact backend order.
 */
@Entity(
    tableName = "feed_entries",
    primaryKeys = ["feedKey", "dealId"],
    indices = [Index(value = ["feedKey", "position"])]
)
data class FeedEntryEntity(
    val feedKey: String,
    val dealId: String,
    val position: Int
)

/** Per-tab state: when it was loaded and where "load more" continues. */
@Entity(tableName = "feed_meta")
data class FeedMetaEntity(
    @PrimaryKey val feedKey: String,
    val updatedAt: Long,
    val nextCursor: String?,
    val hasMore: Boolean,
    val pagesLoaded: Int
)

/** The cache key of a feed tab. Search results use their query as part of the key. */
fun feedKey(sortBy: String, category: String?, governorate: String?, query: String?): String =
    "$sortBy|${category ?: ""}|${governorate ?: ""}|${query ?: ""}"
