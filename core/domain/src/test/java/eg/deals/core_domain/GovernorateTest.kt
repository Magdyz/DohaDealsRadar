package eg.deals.core_domain

import eg.deals.domain.DealCategory
import eg.deals.domain.Governorate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Governorate and category ids travel to the database and back, and the
 * database has CHECK constraints on them: a typo here means posts get rejected.
 */
class GovernorateTest {

    @Test
    fun everyGovernorateRoundTripsThroughItsId() {
        Governorate.values().forEach { g ->
            assertEquals(g, Governorate.fromId(g.id))
        }
    }

    @Test
    fun idsAreSnakeCaseAndUnique() {
        val ids = Governorate.values().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("bad id: $it", it.matches(Regex("^[a-z0-9_]+$"))) }
    }

    @Test
    fun unknownOrMissingIdIsNull() {
        assertNull(Governorate.fromId("qatar"))
        assertNull(Governorate.fromId(null))
        assertNull(Governorate.fromId(""))
    }

    @Test
    fun allEgyptIsTheDefaultAndCoversOnlineDeals() {
        assertEquals("all_egypt", Governorate.ALL_EGYPT.id)
        assertNotNull(Governorate.fromId("cairo"))
        assertNotNull(Governorate.fromId("giza"))
        assertNotNull(Governorate.fromId("alexandria"))
    }

    @Test
    fun everyGovernorateHasBothLanguages() {
        Governorate.values().forEach { g ->
            assertTrue(g.label(isArabic = false).isNotBlank())
            assertTrue(g.label(isArabic = true).isNotBlank())
        }
    }

    @Test
    fun categoriesRoundTripAndFallBackToOther() {
        DealCategory.values().forEach { c ->
            assertEquals(c, DealCategory.fromId(c.id))
            assertTrue(c.label(isArabic = false).isNotBlank())
            assertTrue(c.label(isArabic = true).isNotBlank())
        }
        // Deals stored before the Egypt categories must not crash the feed
        assertEquals(DealCategory.OTHER, DealCategory.fromId("majlis_qatar"))
        assertEquals(DealCategory.OTHER, DealCategory.fromId(""))
    }
}
