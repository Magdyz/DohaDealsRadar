package eg.deals.radar.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Which cached deals belong to the main feed, and in what order.
 * Other screens (account, profile, details, moderation) also cache deals in
 * the `deals` table; only deals listed here are shown in the feed, in the
 * exact order the backend returned them for the current filters.
 */
@Entity(tableName = "feed_entries")
data class FeedEntryEntity(
    @PrimaryKey val dealId: String,
    val position: Int
)
