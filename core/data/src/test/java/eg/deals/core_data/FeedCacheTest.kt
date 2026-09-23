package eg.deals.core_data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import eg.deals.radar.db.DealDao
import eg.deals.radar.db.DealDatabase
import eg.deals.radar.db.DealEntity
import eg.deals.radar.db.FeedMetaEntity
import eg.deals.radar.db.feedKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every feed tab shows exactly what the backend returned for it, in its order, and stays cached. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedCacheTest {

    private lateinit var db: DealDatabase
    private lateinit var dao: DealDao

    private val all = feedKey("hottest", null, null, null)
    private val food = feedKey("hottest", "food_dining", null, null)

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

    private fun meta(key: String, at: Long = 1000, cursor: String? = null, hasMore: Boolean = false) =
        FeedMetaEntity(key, at, cursor, hasMore, 1)

    private suspend fun ids(key: String) = dao.getFeed(key).first().map { it.id }

    @Test
    fun deals_cached_by_other_screens_do_not_leak_into_a_tab() = runTest {
        dao.replaceFeed(food, listOf(deal("food2", category = "food_dining"), deal("food1", category = "food_dining")), meta(food))
        dao.insertAll(listOf(deal("mine", category = "electronics", by = "u1")))
        dao.insertDeal(deal("opened", category = "groceries"))

        assertEquals(listOf("food2", "food1"), ids(food))
    }

    @Test
    fun each_tab_keeps_its_own_list_so_switching_back_is_instant() = runTest {
        dao.replaceFeed(all, listOf(deal("a"), deal("f1", category = "food_dining")), meta(all))
        dao.replaceFeed(food, listOf(deal("f1", category = "food_dining")), meta(food))

        assertEquals(listOf("a", "f1"), ids(all))
        assertEquals(listOf("f1"), ids(food))
    }

    @Test
    fun reloading_a_tab_keeps_deals_that_another_tab_still_lists() = runTest {
        dao.replaceFeed(all, listOf(deal("a"), deal("shared")), meta(all))
        dao.replaceFeed(food, listOf(deal("shared")), meta(food))
        dao.replaceFeed(all, listOf(deal("b")), meta(all, at = 2000))

        assertEquals(listOf("b"), ids(all))
        assertEquals(listOf("shared"), ids(food))
    }

    @Test
    fun feed_keeps_backend_order_even_when_a_deal_is_rewritten() = runTest {
        dao.replaceFeed(all, listOf(deal("a"), deal("b"), deal("c")), meta(all))
        dao.insertDeal(deal("a")) // REPLACE gives "a" a new rowid (e.g. opened in details)

        assertEquals(listOf("a", "b", "c"), ids(all))
    }

    @Test
    fun load_more_appends_after_the_current_page_without_duplicates() = runTest {
        dao.replaceFeed(all, listOf(deal("a"), deal("b")), meta(all, cursor = "c1", hasMore = true))
        val written = dao.appendFeed(all, "c1", listOf(deal("b"), deal("c")), meta(all, cursor = null))

        assertTrue(written)
        assertEquals(listOf("a", "b", "c"), ids(all))
        assertEquals(2, dao.getFeedMeta(all)!!.pagesLoaded)
    }

    @Test
    fun load_more_is_dropped_if_the_tab_was_reloaded_meanwhile() = runTest {
        dao.replaceFeed(all, listOf(deal("a")), meta(all, cursor = "old", hasMore = true))
        dao.replaceFeed(all, listOf(deal("x")), meta(all, at = 2000, cursor = "new", hasMore = true))
        val written = dao.appendFeed(all, "old", listOf(deal("late")), meta(all))

        assertFalse(written)
        assertEquals(listOf("x"), ids(all))
    }

    @Test
    fun prefetch_never_overwrites_a_tab_loaded_after_it_started() = runTest {
        dao.replaceFeed(food, listOf(deal("fresh", category = "food_dining")), meta(food, at = 5000))
        dao.replaceFeedIfOlder(food, listOf(deal("prefetched", category = "food_dining")), meta(food, at = 4000))

        assertEquals(listOf("fresh"), ids(food))
    }

    @Test
    fun switching_tabs_keeps_deals_cached_by_other_screens() = runTest {
        dao.insertAll(listOf(deal("my-pending", status = "pending", by = "u1")))
        dao.replaceFeed(all, listOf(deal("all1"), deal("all2")), meta(all))
        dao.replaceFeed(all, listOf(deal("all3")), meta(all, at = 2000))

        assertEquals(listOf("my-pending"), dao.getDealsByUser("u1").first().map { it.id })
    }

    @Test
    fun only_the_most_recent_tabs_stay_cached() = runTest {
        val max = DealDao.MAX_CACHED_FEEDS
        for (i in 0..max) {
            val key = feedKey("hottest", "c$i", null, null)
            dao.replaceFeed(key, listOf(deal("d$i")), meta(key, at = 1000L + i))
        }
        assertNull(dao.getFeedMeta(feedKey("hottest", "c0", null, null)))
        assertEquals(emptyList<String>(), ids(feedKey("hottest", "c0", null, null)))
        assertEquals(listOf("d$max"), ids(feedKey("hottest", "c$max", null, null)))
    }

    @Test
    fun archived_deal_disappears_from_the_feed() = runTest {
        dao.replaceFeed(all, listOf(deal("a"), deal("b")), meta(all))
        dao.markArchived("a")

        assertEquals(listOf("b"), ids(all))
    }
}
