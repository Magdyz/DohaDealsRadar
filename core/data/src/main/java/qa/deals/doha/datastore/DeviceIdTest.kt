package qa.deals.doha.datastore

import android.content.Context
import android.util.Log

/**
 * ========================================
 * ✨ DEVICE ID TESTING UTILITIES
 * Enhanced with username testing
 * ========================================
 *
 * Updated: 2025-10-18 19:17:13 UTC by @Magdyz
 * Location: core/data/src/main/java/qa/deals/doha/datastore/DeviceIdTest.kt
 *
 * FIXED: Properly access singleton instance methods
 *
 * Usage:
 * - Call from any screen during development
 * - Check Logcat for output
 * - Remove before production release
 */
object DeviceIdTest {

    private const val TAG = "DeviceIdTest"

    /**
     * ========================================
     * ✨ COMPREHENSIVE TEST SUITE
     * Tests device ID and username functionality
     * ========================================
     */
    fun runFullTest(context: Context) {
        Log.d(TAG, "")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "🧪 DEVICE ID MANAGER - FULL TEST")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "")

        // ✅ FIX: Get singleton instance first
        val manager = DeviceIdManager.getInstance(context)

        // ========================================
        // TEST 1: Device ID Generation
        // ========================================
        Log.d(TAG, "📋 TEST 1: Device ID Generation")
        val startTime1 = System.currentTimeMillis()
        val deviceId1 = manager.getDeviceId()  // ✅ FIX: Call through instance
        val time1 = System.currentTimeMillis() - startTime1
        Log.d(TAG, "✅ Device ID: $deviceId1")
        Log.d(TAG, "⏱️  Time: ${time1}ms")
        Log.d(TAG, "")

        // ========================================
        // TEST 2: Cached Retrieval
        // ========================================
        Log.d(TAG, "📋 TEST 2: Cached Retrieval (should be fast)")
        val startTime2 = System.currentTimeMillis()
        val deviceId2 = manager.getDeviceId()  // ✅ FIX: Call through instance
        val time2 = System.currentTimeMillis() - startTime2
        Log.d(TAG, "✅ Device ID: $deviceId2")
        Log.d(TAG, "⏱️  Time: ${time2}ms")
        Log.d(TAG, "🔍 Same as first? ${deviceId1 == deviceId2}")
        Log.d(TAG, "")

        // ========================================
        // TEST 3: Device ID Info
        // ========================================
        Log.d(TAG, "📋 TEST 3: Device ID Info")
        val info = manager.getDeviceIdInfo()  // ✅ FIX: Call through instance
        for ((key, value) in info) {  // ✅ FIX: Explicit destructuring
            Log.d(TAG, "   $key: $value")
        }
        Log.d(TAG, "")

        // ========================================
        // TEST 4: Has Device ID Check
        // ========================================
        Log.d(TAG, "📋 TEST 4: Has Device ID Check")
        val hasId = manager.hasDeviceId()  // ✅ FIX: Call through instance
        Log.d(TAG, "✅ Has device ID: $hasId")
        Log.d(TAG, "")

        // ========================================
        // TEST 5: Username Check
        // ========================================
        Log.d(TAG, "📋 TEST 5: Username Management")
        val hasUsername = manager.hasUsername()
        val username = manager.getUsername()
        Log.d(TAG, "   Has username: $hasUsername")
        Log.d(TAG, "   Username: ${username ?: "null (not set)"}")
        Log.d(TAG, "")

        // ========================================
        // TEST 6: Voting State
        // ========================================
        Log.d(TAG, "📋 TEST 6: Voting State (example deal)")
        val testDealId = "test-deal-123"
        val hasVoted = manager.hasVoted(testDealId)
        val voteType = manager.getVoteType(testDealId)
        Log.d(TAG, "   Has voted on $testDealId: $hasVoted")
        Log.d(TAG, "   Vote type: ${voteType ?: "none"}")
        Log.d(TAG, "")

        // ========================================
        // TEST 7: Report State
        // ========================================
        Log.d(TAG, "📋 TEST 7: Report State")
        val hasReported = manager.hasReported(testDealId)
        val reportCount = manager.getTodayReportCount()
        Log.d(TAG, "   Has reported $testDealId: $hasReported")
        Log.d(TAG, "   Today's report count: $reportCount")
        Log.d(TAG, "")

        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "✅ FULL TEST COMPLETE")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "")
    }

    /**
     * ========================================
     * ✨ QUICK CHECK
     * Just print essential info
     * ========================================
     */
    fun quickCheck(context: Context) {
        val manager = DeviceIdManager.getInstance(context)
        val deviceId = manager.getDeviceId()
        val username = manager.getUsername()

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔍 QUICK CHECK")
        Log.d(TAG, "   Device ID: ${deviceId.take(12)}...${deviceId.takeLast(8)}")
        Log.d(TAG, "   Username: ${username ?: "not set"}")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * ========================================
     * ✨ TEST USERNAME FLOW
     * Simulates username registration
     * ========================================
     */
    fun testUsernameFlow(context: Context) {
        Log.d(TAG, "")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "👤 USERNAME FLOW TEST")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "")

        val manager = DeviceIdManager.getInstance(context)

        Log.d(TAG, "1️⃣ Check initial state...")
        Log.d(TAG, "   Has username: ${manager.hasUsername()}")
        Log.d(TAG, "   Username: ${manager.getUsername() ?: "null"}")
        Log.d(TAG, "")

        Log.d(TAG, "2️⃣ Simulating username registration...")
        manager.saveUsername("TestUser123")
        Log.d(TAG, "")

        Log.d(TAG, "3️⃣ Verify stored username...")
        Log.d(TAG, "   Has username: ${manager.hasUsername()}")
        Log.d(TAG, "   Username: ${manager.getUsername()}")
        Log.d(TAG, "")

        Log.d(TAG, "4️⃣ Clearing username (testing only)...")
        manager.clearUsername()
        Log.d(TAG, "")

        Log.d(TAG, "5️⃣ Verify cleared state...")
        Log.d(TAG, "   Has username: ${manager.hasUsername()}")
        Log.d(TAG, "   Username: ${manager.getUsername() ?: "null"}")
        Log.d(TAG, "")

        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "✅ USERNAME FLOW TEST COMPLETE")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "")
    }

    /**
     * ========================================
     * ✨ TEST DEVICE ID ONLY
     * Focused test for device ID generation
     * ========================================
     */
    fun testDeviceIdOnly(context: Context) {
        Log.d(TAG, "")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "🔑 DEVICE ID FOCUSED TEST")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "")

        val manager = DeviceIdManager.getInstance(context)

        // Test 1: Get ID
        Log.d(TAG, "1️⃣ Getting device ID...")
        val deviceId = manager.getDeviceId()
        Log.d(TAG, "   Full ID: $deviceId")
        Log.d(TAG, "   Preview: ${deviceId.take(12)}...${deviceId.takeLast(8)}")
        Log.d(TAG, "")

        // Test 2: Check if exists
        Log.d(TAG, "2️⃣ Checking if device ID exists...")
        val exists = manager.hasDeviceId()
        Log.d(TAG, "   Has device ID: $exists")
        Log.d(TAG, "")

        // Test 3: Get info
        Log.d(TAG, "3️⃣ Getting device ID info...")
        val info = manager.getDeviceIdInfo()
        for ((key, value) in info) {
            Log.d(TAG, "   $key: $value")
        }
        Log.d(TAG, "")

        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "✅ DEVICE ID TEST COMPLETE")
        Log.d(TAG, "════════════════════════════════════════")
        Log.d(TAG, "")
    }
}