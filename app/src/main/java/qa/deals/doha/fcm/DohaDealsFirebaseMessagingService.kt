package qa.deals.doha.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import qa.deals.doha.MainActivity
import qa.deals.doha.R
import java.net.URL

/**
 * ========================================
 * ✨ FIREBASE MESSAGING SERVICE
 * Handles incoming FCM notifications
 * ========================================
 *
 * Created: 2025-11-25 by @Magdyz
 * Location: app/src/main/java/qa/deals/doha/fcm/DohaDealsFirebaseMessagingService.kt
 *
 * Responsibilities:
 * 1. Receive push notifications from FCM
 * 2. Display notifications in system tray
 * 3. Handle notification clicks (open app)
 * 4. Log FCM token for debugging
 *
 * Topics Handled:
 * - all_deals - Global notifications
 * - cat_{categoryId} - Category-specific notifications
 */
class DohaDealsFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "deals_channel"
        private const val CHANNEL_NAME = "Deal Notifications"
        private const val NOTIFICATION_ID = 1001
    }

    // Coroutine scope for async image loading
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when a new FCM token is generated
     * This happens on first app install or token refresh
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "🔑 New FCM Token Generated")
        Log.d(TAG, "   Token: ${token.take(20)}...")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // TODO: Send token to your backend if needed for targeted notifications
        // For now, we're using topic-based messaging which doesn't need tokens
    }

    /**
     * Called when a message is received from FCM
     * This is where we display the notification
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "📨 FCM Message Received")
        Log.d(TAG, "   From: ${message.from}")
        Log.d(TAG, "   Data: ${message.data}")
        Log.d(TAG, "   Notification: ${message.notification?.title}")
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Extract notification data
        val title = message.notification?.title ?: "New Deal in Doha!"
        val body = message.notification?.body ?: "Check out the latest deals"
        val dealId = message.data["dealId"]
        val category = message.data["category"]
        val imageUrl = message.data["imageUrl"]?.takeIf { it.isNotEmpty() }

        // Display the notification
        showNotification(title, body, dealId, imageUrl)
    }

    /**
     * Display notification in system tray
     */
    private fun showNotification(title: String, body: String, dealId: String?, imageUrl: String?) {
        // Create notification channel (required for Android 8.0+)
        createNotificationChannel()

        // Create intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (dealId != null) {
                putExtra("dealId", dealId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // If image URL is provided, load it asynchronously
        if (imageUrl != null) {
            serviceScope.launch {
                val bitmap = loadImageBitmap(imageUrl)
                displayNotification(title, body, pendingIntent, bitmap)
            }
        } else {
            // No image, show notification immediately
            displayNotification(title, body, pendingIntent, null)
        }

        Log.d(TAG, "✅ Notification queued: $title")
    }

    /**
     * Load image bitmap from URL
     */
    private suspend fun loadImageBitmap(imageUrl: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(imageUrl)
                val connection = url.openConnection()
                connection.doInput = true
                connection.connect()
                val input = connection.getInputStream()
                BitmapFactory.decodeStream(input)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image: ${e.message}")
                null
            }
        }
    }

    /**
     * Display the actual notification
     */
    private fun displayNotification(title: String, body: String, pendingIntent: PendingIntent, imageBitmap: Bitmap?) {
        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // App icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Dismiss when tapped
            .setContentIntent(pendingIntent)

        // Add image if available
        if (imageBitmap != null) {
            notificationBuilder
                .setLargeIcon(imageBitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(imageBitmap)
                        .bigLargeIcon(null as Bitmap?) // Hide large icon when expanded
                )
        } else {
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        val notification = notificationBuilder.build()

        // Show the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "✅ Notification displayed: $title (with image: ${imageBitmap != null})")
    }

    /**
     * Create notification channel (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new deals in Doha"
                enableLights(true)
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "✅ Notification channel created: $CHANNEL_ID")
        }
    }
}
