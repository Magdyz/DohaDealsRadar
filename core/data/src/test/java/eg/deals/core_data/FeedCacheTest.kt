package eg.deals.core_data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eg.deals.radar.db.DealDao
import eg.deals.radar.db.DealDatabase
import eg.deals.radar.db.DealEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The feed shows exactly what the backend returned for the current tab, in its order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedCacheTest {

    private lateinit var db: DealDatabase
    private lateinit var dao: DealDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DealDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dealDao()
    }

    @After
    fun tearDown() = db.close()

    private fun deal(id: String, status: String = "approved", category: String = "other", by: String? = null) =
        DealEntity(
            id = id, title = id, link = "", imageUrl = null, status = status, createdAt = null,
            hotCount = 0, coldCount = 0, category = category, submittedByUserId = by
        )

    private suspend fun feedIds() = dao.getApprovedActiveDeals().first().map { it.id }

    @Test
    fun deals_cached_by_other_screens_do_not_leak_into_the_feed() = runTest {
        dao.replaceFeed(listOf(deal("food2", category = "food_dining"), deal("food1", category = "food_dining")))
        // Account / profile / details cache approved deals of any category
        dao.insertAll(listOf(deal("mine", category = "electronics", by = "u1")))
        dao.insertDeal(deal("opened", category = "groceries"))

        assertEquals(listOf("food2", "food1"), feedIds())
    }

    @Test
    fun feed_keeps_backend_order_even_when_a_deal_is_rewritten() = runTest {
        dao.replaceFeed(listOf(deal("a"), deal("b"), deal("c")))
        dao.insertDeal(deal("a")) // REPLACE gives "a" a new rowid (e.g. opened in details)

        assertEquals(listOf("a", "b", "c"), feedIds())
    }

    @Test
    fun load_more_appends_after_the_current_page_without_duplicates() = runTest {
        dao.replaceFeed(listOf(deal("a"), deal("b")))
        dao.appendFeed(listOf(deal("b"), deal("c")))

        assertEquals(listOf("a", "b", "c"), feedIds())
    }

    @Test
    fun switching_tabs_keeps_deals_cached_by_other_screens() = runTest {
        dao.insertAll(listOf(deal("my-pending", status = "pending", by = "u1")))
        dao.replaceFeed(listOf(deal("all1"), deal("all2")))
        dao.replaceFeed(listOf(deal("food1", category = "food_dining")))

        assertEquals(listOf("food1"), feedIds())
        assertEquals(listOf("my-pending"), dao.getDealsByUser("u1").first().map { it.id })
    }

    @Test
    fun archived_deal_disappears_from_the_feed() = runTest {
        dao.replaceFeed(listOf(deal("a"), deal("b")))
        dao.markArchived("a")

        assertEquals(listOf("b"), feedIds())
    }
}
