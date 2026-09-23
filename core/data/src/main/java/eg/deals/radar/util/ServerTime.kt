package eg.deals.radar.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * ========================================
 * 🕒 SERVER TIMESTAMPS
 * ========================================
 * Postgres/PostgREST hands us timestamps in a few shapes:
 *   2026-09-22T15:21:48.647+00:00   (REST JSON)
 *   2026-09-22 15:21:48.647+00      (SQL text)
 *   2026-09-22T15:21:48             (no offset -> treated as UTC)
 * Everything is stored in UTC, so parsing must never fall back to the device
 * timezone: Egypt is UTC+2/+3 and a fresh deal would otherwise look hours old.
 */
object ServerTime {

    /** Epoch millis for a server timestamp, or null if it can't be read. */
    fun toEpochMillis(timestamp: String?): Long? {
        val raw = timestamp?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // "…+00" -> "…+00:00" so the ISO parser accepts short SQL offsets
        val normalized = raw.replace(' ', 'T').replace(SHORT_OFFSET) { "${it.groupValues[1]}:00" }
        return runCatching { OffsetDateTime.parse(normalized).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(normalized).toEpochMilli() }
            .recoverCatching {
                // No offset at all: the server means UTC
                LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC).toEpochMilli()
            }
            .getOrNull()
    }

    /** Milliseconds since the timestamp (negative if it is in the future). */
    fun millisSince(timestamp: String?): Long? =
        toEpochMillis(timestamp)?.let { System.currentTimeMillis() - it }

    private val SHORT_OFFSET = Regex("([+-]\\d{2})$")
}
