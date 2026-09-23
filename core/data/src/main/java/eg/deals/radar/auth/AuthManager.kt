package eg.deals.radar.auth

import android.content.Context
import android.util.Log
import eg.deals.radar.datastore.DeviceIdManager
import eg.deals.radar.manager.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ========================================
 * 🔑 AUTH MANAGER - one place for login state
 * ========================================
 * - onLoggedIn(): save user + subscribe to personal notifications
 *   ("your deal is live / wasn't approved")
 * - logout(): clear session, user and personal notification topic
 * - init(): runs at app start
 *     * old installs (before secure sessions) had only a saved user id; those
 *       users are logged out once so they sign in with the new secure flow
 *     * when a session can't be refreshed any more, local login state is cleared
 */
object AuthManager {
    private const val TAG = "AuthManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        val device = DeviceIdManager.getInstance(app)

        if (device.hasUserId() && !SessionStore.isLoggedIn) {
            Log.i(TAG, "Legacy login without secure session - asking user to log in again")
            clearLocalUser(app)
        }

        scope.launch {
            SessionStore.events.collect { event ->
                if (event is AuthEvent.SessionExpired) clearLocalUser(app)
            }
        }
    }

    val isLoggedIn: Boolean get() = SessionStore.isLoggedIn

    fun onLoggedIn(context: Context, userId: String, username: String) {
        val app = context.applicationContext
        val device = DeviceIdManager.getInstance(app)
        device.saveUserId(userId)
        device.saveUsername(username)
        NotificationManager.getInstance(app).subscribeUserTopic(userId)
    }

    fun logout(context: Context) {
        SessionStore.clear()
        clearLocalUser(context.applicationContext)
    }

    private fun clearLocalUser(app: Context) {
        val device = DeviceIdManager.getInstance(app)
        NotificationManager.getInstance(app).unsubscribeUserTopic()
        // Let the system picker ask again next time instead of silently reusing the account
        scope.launch { GoogleAuth.clearSelection(app) }
        device.clearUserId()
        device.clearUsername()
    }
}
