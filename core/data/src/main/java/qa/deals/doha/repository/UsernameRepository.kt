package qa.deals.doha.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qa.deals.doha.network.NetworkModule
import qa.deals.doha.network.SupabaseApiService
import qa.deals.doha.network.UsernameRequest
import qa.deals.doha.network.UsernameResponse

/**
 * ========================================
 * ✨ USERNAME REPOSITORY
 * Manages username operations with backend
 * ========================================
 *
 * Created: 2025-10-18 19:04:07 UTC by @Magdyz
 *
 * Responsibilities:
 * - Check if device has username
 * - Validate username availability
 * - Register new usernames
 * - Handle API errors gracefully
 *
 * All operations run on IO dispatcher for performance.
 */
class UsernameRepository {

    // ========================================
    // ✨ DEPENDENCY: API Service
    // ========================================
    private val api: SupabaseApiService = NetworkModule.api

    /**
     * ========================================
     * ✨ GET USERNAME FOR DEVICE
     * Check if device already has a registered username
     * ========================================
     *
     * @param deviceId Unique device identifier
     * @return Result with username if exists, or null if not registered
     *
     * Success: Result.success(username)
     * Not Found: Result.success(null)
     * Error: Result.failure(exception)
     */
    suspend fun getUsernameForDevice(deviceId: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            Log.d("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d("UsernameRepo", "🔍 Checking username for device")
            Log.d("UsernameRepo", "   Device ID: ${deviceId.take(8)}...${deviceId.takeLast(4)}")

            val request = UsernameRequest(
                action = "get_username",
                deviceId = deviceId
            )

            val response = api.getUsernameForDevice(request)

            if (response.success) {
                if (response.exists == true && response.username != null) {
                    Log.d("UsernameRepo", "✅ Found username: ${response.username}")
                    Log.d("UsernameRepo", "   Created: ${response.data?.createdAt ?: "unknown"}")
                    Log.d("UsernameRepo", "   Deal count: ${response.data?.dealCount ?: 0}")
                    Log.d("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Result.success(response.username)
                } else {
                    Log.d("UsernameRepo", "ℹ️  No username found for device (first time user)")
                    Log.d("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Result.success(null)
                }
            } else {
                Log.e("UsernameRepo", "❌ API returned success=false")
                Log.e("UsernameRepo", "   Error: ${response.error}")
                Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Result.failure(Exception(response.error ?: "Failed to check username"))
            }
        } catch (e: Exception) {
            Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.e("UsernameRepo", "💥 Error checking username")
            Log.e("UsernameRepo", "   Error type: ${e.javaClass.simpleName}")
            Log.e("UsernameRepo", "   Error message: ${e.message}")
            Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", e)
            Result.failure(e)
        }
    }

    /**
     * ========================================
     * ✨ CHECK USERNAME AVAILABILITY
     * Validate if username can be registered
     * ========================================
     *
     * @param username Username to check (will be validated)
     * @return Result with true if available, false if taken
     *
     * Available: Result.success(true)
     * Taken: Result.success(false)
     * Invalid: Result.failure(exception with validation error)
     * Network Error: Result.failure(exception)
     */
    suspend fun checkUsernameAvailability(username: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d("UsernameRepo", "🔍 Checking availability: \"$username\"")

            // ✅ Client-side validation (fail fast)
            if (username.length < 3 || username.length > 20) {
                Log.e("UsernameRepo", "❌ Invalid length: ${username.length} (must be 3-20)")
                Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return@withContext Result.failure(Exception("Username must be 3-20 characters"))
            }

            val usernameRegex = Regex("^[a-zA-Z0-9_]+$")
            if (!usernameRegex.matches(username)) {
                Log.e("UsernameRepo", "❌ Invalid format: contains special characters")
                Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                return@withContext Result.failure(Exception("Username can only contain letters, numbers, and underscore"))
            }

            val request = UsernameRequest(
                action = "check_availability",
                username = username
            )

            val response = api.checkUsernameAvailability(request)

            if (response.success) {
                val available = response.available == true
                if (available) {
                    Log.d("UsernameRepo", "✅ Username is available!")
                } else {
                    Log.d("UsernameRepo", "❌ Username is taken")
                }
                Log.d("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Result.success(available)
            } else {
                Log.e("UsernameRepo", "❌ API returned success=false")
                Log.e("UsernameRepo", "   Error: ${response.error}")
                Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Result.failure(Exception(response.error ?: "Failed to check availability"))
            }
        } catch (e: Exception) {
            Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.e("UsernameRepo", "💥 Error checking availability")
            Log.e("UsernameRepo", "   Error type: ${e.javaClass.simpleName}")
            Log.e("UsernameRepo", "   Error message: ${e.message}")
            Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", e)
            Result.failure(e)
        }
    }

    /**
     * ========================================
     * ✨ REGISTER USERNAME
     * Associate username with device ID
     * ========================================
     *
     * @param deviceId Unique device identifier
     * @param username Username to register
     * @return Result with registered username on success
     *
     * Success: Result.success(username)
     * Already Exists: Result.failure(exception)
     * Invalid: Result.failure(exception)
     * Network Error: Result.failure(exception)
     */
    suspend fun registerUsername(deviceId: String, username: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d("UsernameRepo", "📝 Registering username")
            Log.d("UsernameRepo", "   Username: $username")
            Log.d("UsernameRepo", "   Device ID: ${deviceId.take(8)}...${deviceId.takeLast(4)}")

            val request = UsernameRequest(
                action = "register_username",
                deviceId = deviceId,
                username = username
            )

            val response = api.registerUsername(request)

            if (response.success && response.username != null) {
                Log.d("UsernameRepo", "✅ Username registered successfully!")
                Log.d("UsernameRepo", "   Username: ${response.username}")
                Log.d("UsernameRepo", "   Created: ${response.data?.createdAt ?: "now"}")
                Log.d("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Result.success(response.username)
            } else {
                Log.e("UsernameRepo", "❌ Registration failed")
                Log.e("UsernameRepo", "   Error: ${response.error}")
                Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Result.failure(Exception(response.error ?: "Failed to register username"))
            }
        } catch (e: Exception) {
            Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.e("UsernameRepo", "💥 Error registering username")
            Log.e("UsernameRepo", "   Error type: ${e.javaClass.simpleName}")
            Log.e("UsernameRepo", "   Error message: ${e.message}")
            Log.e("UsernameRepo", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", e)
            Result.failure(e)
        }
    }
}