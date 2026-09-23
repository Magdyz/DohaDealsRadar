package eg.deals.core_data

import eg.deals.radar.util.ServerTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Timestamps arrive in several shapes and are always UTC. Reading them as local
 * time would make a fresh deal look 2-3 hours old in Egypt, so every shape the
 * backend can send must land on the same instant.
 */
class ServerTimeTest {

    private val expected = 1_790_000_000_000L // fixed instant used by the cases below

    @Test
    fun restJsonWithOffset() {
        assertEquals(expected, ServerTime.toEpochMillis(iso(expected)))
    }

    @Test
    fun sqlTextWithSpaceAndShortOffset() {
        // "2026-09-22 15:33:20+00"
        val sql = iso(expected).replace('T', ' ').replace("+00:00", "+00").substringBefore('.')
        assertEquals(expected / 1000, ServerTime.toEpochMillis(sql)!! / 1000)
    }

    @Test
    fun zuluForm() {
        val zulu = iso(expected).replace("+00:00", "Z")
        assertEquals(expected, ServerTime.toEpochMillis(zulu))
    }

    @Test
    fun noOffsetIsTreatedAsUtcNotLocalTime() {
        val naive = iso(expected).substringBefore('+').substringBefore('.')
        assertEquals(expected / 1000, ServerTime.toEpochMillis(naive)!! / 1000)
    }

    @Test
    fun nonOffsetTimezoneOfTheDeviceIsIrrelevant() {
        val before = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))
            val cairo = ServerTime.toEpochMillis(iso(expected))
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"))
            val utc = ServerTime.toEpochMillis(iso(expected))
            assertEquals(utc, cairo)
        } finally {
            java.util.TimeZone.setDefault(before)
        }
    }

    @Test
    fun garbageIsNullRatherThanNow() {
        assertNull(ServerTime.toEpochMillis(null))
        assertNull(ServerTime.toEpochMillis(""))
        assertNull(ServerTime.toEpochMillis("   "))
        assertNull(ServerTime.toEpochMillis("yesterday"))
    }

    @Test
    fun millisSinceIsPositiveForThePast() {
        val anHourAgo = System.currentTimeMillis() - 3_600_000
        val diff = ServerTime.millisSince(iso(anHourAgo))!!
        assertTrue(diff in 3_500_000..3_700_000)
    }

    private fun iso(epochMillis: Long): String =
        java.time.OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(epochMillis),
            java.time.ZoneOffset.UTC,
        ).toString().let { if (it.endsWith("Z")) it.dropLast(1) + "+00:00" else it }
}
