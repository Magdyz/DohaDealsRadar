package qa.deals.doha.feature.post

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import qa.deals.doha.repository.DealRepository
import qa.deals.doha.repository.UserRepository  // ✅ SPRINT 5: NEW IMPORT
import qa.deals.doha.util.ImageCompressor
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.domain.DealCategory
import qa.deals.doha.network.SendCodeRequest
import qa.deals.doha.network.UserInfo
import qa.deals.doha.network.VerifyCodeRequest

/**
 * Deal type enum
 * ✅ PRESERVED: No changes
 */
enum class DealType {
    ONLINE,
    PHYSICAL
}

// ========================================
// ✅ PRESERVED: Email Verification State
// ========================================
sealed interface EmailVerificationState {
    object Idle : EmailVerificationState
    data class Loading(val message: String) : EmailVerificationState
    data class CodeSent(val email: String) : EmailVerificationState
    data class Verified(val user: UserInfo) : EmailVerificationState
    data class Error(val message: String) : EmailVerificationState
}

/**
 * UI State for Post Screen
 * ✅ UPDATED: Added expiresInDays field for deal expiration
 * ✅ UPDATED: 2025-11-16 Added originalPrice and discountedPrice fields
 */

data class PostUiState(
    val title: String = "",
    val description: String = "",
    val dealType: DealType = DealType.ONLINE,
    val link: String = "",
    val location: String = "",
    val promoCode: String? = null,
    val category: DealCategory = DealCategory.FOOD_DINING,
    val imageUrl: String = "",
    val selectedImageUri: Uri? = null,
    val originalPrice: String = "",        // ✨ NEW: Original price input (user enters as text)
    val discountedPrice: String = "",      // ✨ NEW: Discounted price input (user enters as text)
    val expiresInDays: Int = 10,  // ✨ NEW: Expiration duration (default: 10 days)
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val submitted: Boolean = false,
    val username: String? = null,
    val verifiedUserId: String? = null,
    val showEmailVerification: Boolean = false,
    val emailVerificationState: EmailVerificationState = EmailVerificationState.Idle
)

/**
 * ViewModel for Post Screen
 * ✅ SPRINT 5 ENHANCED: Added UserRepository and improved userId handling
 * ⚠️ NO BREAKING CHANGES: All existing functionality preserved
 */
class PostViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val userRepo: UserRepository = UserRepository()  // ✅ SPRINT 5: NEW - For role caching
) : ViewModel() {

    var uiState by mutableStateOf(PostUiState())
        private set

    private val deviceIdManager = DeviceIdManager.getInstance(context)

    init {
        // ✅ SPRINT 5 ENHANCED: Load user data with additional validation
        viewModelScope.launch {
            val cachedUsername = deviceIdManager.getUsername()
            val cachedUserId = deviceIdManager.getUserId()

            Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d("PostViewModel", "🔍 INIT: Loading cached user data")
            Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d("PostViewModel", "👤 Cached Username: ${cachedUsername ?: "null"}")
            Log.d("PostViewModel", "🆔 Cached UserId: ${cachedUserId?.take(8) ?: "null"}...")

            if (cachedUsername != null && cachedUserId != null) {
                Log.d("PostViewModel", "✅ User data found in cache")
                uiState = uiState.copy(
                    username = cachedUsername,
                    verifiedUserId = cachedUserId
                )

                // ✅ SPRINT 5: Ensure user profile is cached in Room database
                // This is critical for moderator detection
                try {
                    val cachedUser = userRepo.getCachedUser(cachedUserId)
                    if (cachedUser == null) {
                        Log.d("PostViewModel", "⚠️ User not in Room cache, fetching profile...")
                        val result = userRepo.fetchUserProfile(cachedUserId)
                        if (result.isSuccess) {
                            Log.d("PostViewModel", "✅ User profile cached: ${result.getOrNull()?.role}")
                        } else {
                            Log.w("PostViewModel", "⚠️ Failed to cache profile (non-critical): ${result.exceptionOrNull()?.message}")
                        }
                    } else {
                        Log.d("PostViewModel", "✅ User already in Room cache: ${cachedUser.role}")
                    }
                } catch (e: Exception) {
                    Log.w("PostViewModel", "⚠️ Error caching user profile (non-critical)", e)
                }

                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            } else {
                Log.d("PostViewModel", "ℹ️ No cached user. Will prompt for verification on post.")
                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            }
        }
    }

    // ========================================
    // ✅ PRESERVED: Email Verification Management
    // ⚠️ NO CHANGES: All existing methods kept intact
    // ========================================

    fun showEmailVerification() {
        Log.d("PostViewModel", "📧 Showing email verification screen")
        uiState = uiState.copy(
            showEmailVerification = true,
            emailVerificationState = EmailVerificationState.Idle
        )
    }

    fun hideEmailVerification() {
        Log.d("PostViewModel", "📧 Hiding email verification screen")
        uiState = uiState.copy(
            showEmailVerification = false,
            emailVerificationState = EmailVerificationState.Idle
        )
    }

    /**
     * ✅ SPRINT 5 ENHANCED: Cache user profile in Room after verification
     * ⚠️ SAFETY: Non-breaking enhancement, original logic preserved
     */
    fun onEmailVerified(userId: String, username: String, email: String, isNew: Boolean) {
        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("PostViewModel", "✅ EMAIL VERIFIED")
        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("PostViewModel", "👤 Username: $username")
        Log.d("PostViewModel", "🆔 UserId: ${userId.take(8)}...")
        Log.d("PostViewModel", "📧 Email: $email")
        Log.d("PostViewModel", "🆕 Is New User: $isNew")

        // 1. Cache username and userId in SharedPreferences
        deviceIdManager.saveUsername(username)
        deviceIdManager.saveUserId(userId)
        Log.d("PostViewModel", "💾 Saved to DeviceIdManager")

        // 2. Update UI state
        uiState = uiState.copy(
            username = username,
            verifiedUserId = userId,
            showEmailVerification = false,
            emailVerificationState = EmailVerificationState.Idle
        )

        // ✅ SPRINT 5: Cache user profile in Room database for moderator detection
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "🔄 Fetching user profile to cache in Room...")
                val result = userRepo.fetchUserProfile(userId)
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    Log.d("PostViewModel", "✅ User profile cached: Role=${user?.role}, AutoApprove=${user?.autoApprove}")
                } else {
                    Log.w("PostViewModel", "⚠️ Failed to cache user profile (non-critical)")
                }
            } catch (e: Exception) {
                Log.w("PostViewModel", "⚠️ Error caching user profile (non-critical)", e)
            }
        }

        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("PostViewModel", "🔄 Proceeding with auto-submission...")

        // 3. Auto-submit the deal
        submitDeal()
    }

    /**
     * ✅ PRESERVED: Send verification code
     * ⚠️ NO CHANGES
     */
    fun sendVerificationCode(email: String) {
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "📧 Sending verification code to: $email")
                uiState = uiState.copy(emailVerificationState = EmailVerificationState.Loading("Sending code..."))

                val response = repo.sendVerificationCode(email)

                if (response.success) {
                    Log.d("PostViewModel", "✅ Code sent successfully")
                    uiState = uiState.copy(emailVerificationState = EmailVerificationState.CodeSent(email))
                } else {
                    Log.e("PostViewModel", "❌ Failed to send code: ${response.error}")
                    uiState = uiState.copy(
                        emailVerificationState = EmailVerificationState.Error(
                            response.error ?: "Failed to send code. Please try again."
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("PostViewModel", "💥 Error sending code", e)
                uiState = uiState.copy(
                    emailVerificationState = EmailVerificationState.Error(
                        e.message ?: "Network error. Please check your connection."
                    )
                )
            }
        }
    }

    /**
     * ✅ PRESERVED: Verify code and get user
     * ⚠️ NO CHANGES
     */
    fun verifyCode(email: String, code: String) {
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "🔒 Verifying code: $code for email: $email")
                uiState = uiState.copy(emailVerificationState = EmailVerificationState.Loading("Verifying code..."))
                val deviceId = deviceIdManager.getDeviceId()

                val response = repo.verifyCodeAndGetUser(
                    email = email,
                    code = code,
                    deviceId = deviceId
                )

                val user = response.user

                if (response.success && user != null) {
                    Log.d("PostViewModel", "✅ Verification successful! User: ${user.username}")
                    uiState = uiState.copy(
                        emailVerificationState = EmailVerificationState.Verified(user)
                    )
                } else {
                    Log.e("PostViewModel", "❌ Invalid code: ${response.error}")
                    uiState = uiState.copy(
                        emailVerificationState = EmailVerificationState.Error(
                            response.error ?: "Invalid code. Please try again."
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("PostViewModel", "💥 Error verifying code", e)
                uiState = uiState.copy(
                    emailVerificationState = EmailVerificationState.Error(
                        e.message ?: "Network error. Please check your connection."
                    )
                )
            }
        }
    }

    // ========================================
    // ✅ PRESERVED: Update methods
    // ⚠️ NO CHANGES: All existing methods kept intact
    // ========================================

    fun updateTitle(title: String) {
        uiState = uiState.copy(title = title, error = null)
    }

    fun updateCategory(category: DealCategory) {
        uiState = uiState.copy(category = category)
        Log.d("PostViewModel", "🏷️ Category updated: ${category.displayName}")
    }

    fun updateDescription(description: String) {
        uiState = uiState.copy(description = description, error = null)
    }

    fun updateLink(link: String) {
        uiState = uiState.copy(link = link, error = null)
    }

    fun updateLocation(location: String) {
        uiState = uiState.copy(location = location, error = null)
    }

    fun setDealType(type: DealType) {
        uiState = uiState.copy(dealType = type, error = null)
    }

    fun updatePromoCode(promoCode: String) {
        uiState = uiState.copy(promoCode = promoCode.trim().ifBlank { null }, error = null)
    }

    // ========================================
    // ✨ NEW: Price update methods (2025-11-16)
    // ========================================
    fun updateOriginalPrice(price: String) {
        // Allow only numbers, comma, and decimal point
        val filtered = price.filter { it.isDigit() || it == '.' || it == ',' }
        uiState = uiState.copy(originalPrice = filtered, error = null)
    }

    fun updateDiscountedPrice(price: String) {
        // Allow only numbers, comma, and decimal point
        val filtered = price.filter { it.isDigit() || it == '.' || it == ',' }
        uiState = uiState.copy(discountedPrice = filtered, error = null)
    }

    fun updateExpiresInDays(days: Int) {
        uiState = uiState.copy(expiresInDays = days.coerceIn(1, 30), error = null)
        Log.d("PostViewModel", "⏰ Expiration updated: $days days")
    }

    fun updateImageUrl(imageUrl: String) {
        uiState = uiState.copy(imageUrl = imageUrl, error = null, selectedImageUri = null)
    }

    fun setSelectedImage(uri: Uri) {
        uiState = uiState.copy(selectedImageUri = uri, imageUrl = "", error = null)
    }

    fun clearImage() {
        uiState = uiState.copy(selectedImageUri = null, error = null)
    }

    fun clearError() {
        if (uiState.error != null) {
            Log.d("PostViewModel", "🧹 Clearing error state")
            uiState = uiState.copy(error = null)
        }
    }

    /**
     * ✅ SPRINT 5 ENHANCED: Submit deal with comprehensive userId debugging
     * ⚠️ SAFETY NOTES:
     * - All existing validation logic preserved
     * - All existing two-stage upload logic preserved
     * - Enhanced logging for troubleshooting userId issues
     * - No breaking changes to submission flow
     */
    fun submitDeal() {
        // ========================================
        // ✅ SPRINT 5: Enhanced userId validation with detailed logging
        // ========================================
        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("PostViewModel", "🚀 SUBMIT DEAL - SPRINT 5 VALIDATION")
        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("PostViewModel", "👤 Username: ${uiState.username ?: "null"}")
        Log.d("PostViewModel", "🆔 VerifiedUserId: ${uiState.verifiedUserId?.take(8) ?: "NULL"}...")
        Log.d("PostViewModel", "📱 DeviceId: ${deviceIdManager.getDeviceId().take(8)}...")

        if (uiState.verifiedUserId == null) {
            Log.d("PostViewModel", "❌ BLOCKED: Verified User ID not in session")
            Log.d("PostViewModel", "   Showing email verification screen...")
            Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            showEmailVerification()
            return
        }

        Log.d("PostViewModel", "✅ User verification passed")
        Log.d("PostViewModel", "   UserId will be sent: ${uiState.verifiedUserId}")
        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // ✅ PRESERVED: All existing validation logic (NO CHANGES)
        if (uiState.title.isBlank()) {
            uiState = uiState.copy(error = "Please enter a title")
            return
        }

        Log.d("PostViewModel", "📝 Step 1: Validating title...")
        Log.d("PostViewModel", "   Title: '${uiState.title}'")
        Log.d("PostViewModel", "   Title length: ${uiState.title.length}")

        if (!isValidTitle(uiState.title)) {
            Log.e("PostViewModel", "❌ VALIDATION FAILED: Title contains invalid characters")
            uiState = uiState.copy(error = "Title contains invalid characters or URLs")
            return
        }

        if (uiState.description.isNotBlank() && !isValidDescription(uiState.description)) {
            uiState = uiState.copy(error = "Description is too long (max 2000 characters)")
            return
        }

        when (uiState.dealType) {
            DealType.ONLINE -> {
                if (uiState.link.isBlank()) {
                    uiState = uiState.copy(error = "Please enter a link")
                    return
                }
                if (!uiState.link.startsWith("http://") && !uiState.link.startsWith("https://")) {
                    uiState = uiState.copy(error = "Link must start with http:// or https://")
                    return
                }
            }
            DealType.PHYSICAL -> {
                if (uiState.location.isBlank()) {
                    uiState = uiState.copy(error = "Please enter a location")
                    return
                }
                if (!isValidPlaceName(uiState.location)) {
                    uiState = uiState.copy(error = "Location contains invalid characters or looks like a URL")
                    return
                }
            }
        }

        if (uiState.selectedImageUri == null && uiState.imageUrl.isBlank()) {
            uiState = uiState.copy(error = "Please select an image or provide an image URL")
            return
        }

        if (uiState.imageUrl.isNotBlank() &&
            !uiState.imageUrl.startsWith("http://") &&
            !uiState.imageUrl.startsWith("https://")
        ) {
            uiState = uiState.copy(error = "Image URL must start with http:// or https://")
            return
        }

        // ========================================
        // ✨ NEW: Price validation (2025-11-16)
        // ========================================
        val originalPriceValue = parsePrice(uiState.originalPrice)
        val discountedPriceValue = parsePrice(uiState.discountedPrice)

        // Validate: if both prices exist, discounted must be less than original
        if (originalPriceValue != null && discountedPriceValue != null) {
            if (discountedPriceValue >= originalPriceValue) {
                uiState = uiState.copy(error = "Discounted price must be less than original price")
                return
            }
        }

        // ✅ PRESERVED: All existing submission logic with two-stage upload
        // ⚠️ NO CHANGES to the core upload flow below
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            try {
                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("Post", "🚀 TWO-STAGE UPLOAD STARTED")
                Log.d("Post", "   Deal: ${uiState.title}")
                Log.d("Post", "   Type: ${uiState.dealType}")
                Log.d("Post", "   Category: ${uiState.category.displayName}")
                // ✅ SPRINT 5: Log userId being sent
                Log.d("Post", "   UserId: ${uiState.verifiedUserId?.take(8)}...")
                Log.d("Post", "   DeviceId: ${deviceIdManager.getDeviceId().take(8)}...")
                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                val startTime = System.currentTimeMillis()

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(loading = true, error = null, message = null)
                }

                var finalImageUrl = uiState.imageUrl
                var dealId: String? = null

                uiState.selectedImageUri?.let { uri ->
                    // ========================================
                    // ⚡ PRESERVED: STAGE 1-4 (Two-stage upload)
                    // ⚠️ NO CHANGES to compression/upload logic
                    // ========================================
                    val stage1Start = System.currentTimeMillis()
                    Log.d("Post", "📦 STAGE 1: Starting image compression...")

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📦 Compressing image...")
                    }

                    val images = ImageCompressor.compressImageTwoStage(
                        context = context,
                        uri = uri
                    )

                    val stage1Time = System.currentTimeMillis() - stage1Start
                    Log.d("Post", "✅ STAGE 1 COMPLETE (${stage1Time}ms)")
                    Log.d("Post", "   → Thumbnail: ${images.thumbnail.length() / 1024}KB")
                    Log.d("Post", "   → Full image: ${images.fullImage.length() / 1024}KB")

                    val stage2Start = System.currentTimeMillis()
                    Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d("Post", "📤 STAGE 2: Uploading thumbnail...")

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Uploading preview...")
                    }

                    val thumbnailUrl = repo.uploadImage(images.thumbnail)
                    images.thumbnail.delete()
                    finalImageUrl = thumbnailUrl

                    val stage2Time = System.currentTimeMillis() - stage2Start
                    Log.d("Post", "✅ STAGE 2 COMPLETE (${stage2Time}ms)")

                    val stage3Start = System.currentTimeMillis()
                    Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d("Post", "📤 STAGE 3: Submitting deal with THUMBNAIL...")
                    // ✅ SPRINT 5: Log userId in submission
                    Log.d("Post", "   UserId: ${uiState.verifiedUserId?.take(8)}...")

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Posting deal...")
                    }

                    // ✅ SPRINT 5: userId and deviceId already included
                    // ✅ NEW: expiresInDays parameter added

                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = thumbnailUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null,
                        category = uiState.category.id,
                        promoCode = uiState.promoCode?.trim()?.ifBlank { null },
                        postedBy = uiState.username ?: "Anonymous",
                        userId = uiState.verifiedUserId,           // ✅ SPRINT 5: Already included
                        deviceId = deviceIdManager.getDeviceId(),  // ✅ SPRINT 5: Already included
                        expiresInDays = uiState.expiresInDays,     // ✨ NEW: Expiration duration
                        originalPrice = originalPriceValue,        // ✨ NEW: Original price (2025-11-16)
                        discountedPrice = discountedPriceValue     // ✨ NEW: Discounted price (2025-11-16)
                    )

                    Log.d("Post", "📥 API Response success: ${result.success}")

                    val dealData = result.data

                    if (result.success == true && dealData != null && dealData.isNotEmpty()) {
                        dealId = dealData[0].id
                        val stage3Time = System.currentTimeMillis() - stage3Start
                        val userWaitTime = System.currentTimeMillis() - startTime

                        Log.d("Post", "✅ STAGE 3 COMPLETE (${stage3Time}ms)")
                        Log.d("Post", "   → Deal ID: $dealId")
                        // ✅ SPRINT 5: Log if deal was auto-approved
                        Log.d("Post", "   → Auto-approved: ${dealData[0].autoApproved}")
                        Log.d("Post", "⏱️  USER WAIT TIME: ${userWaitTime}ms")

                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(
                                loading = false,
                                message = if (dealData[0].autoApproved == true) {
                                    "✅ Deal posted immediately!"
                                } else {
                                    "⏳ Deal submitted for review"
                                },
                                submitted = true
                            )
                        }

                        // ========================================
                        // ⚡ PRESERVED: STAGE 4 (Background upload)
                        // ⚠️ NO CHANGES
                        // ========================================
                        Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d("Post", "🔄 STAGE 4: Background full image upload...")

                        try {
                            val stage4Start = System.currentTimeMillis()
                            val fullImageUrl = repo.uploadImage(images.fullImage)
                            val uploadTime = System.currentTimeMillis() - stage4Start
                            Log.d("Post", "✅ Full image uploaded (${uploadTime}ms)")

                            images.fullImage.delete()

                            dealId?.let { id ->
                                repo.updateDealImage(id, fullImageUrl)
                                Log.d("Post", "✅ Deal $id updated with full image")
                                repo.refreshDeals()
                            }
                        } catch (e: Exception) {
                            Log.e("Post", "💥 STAGE 4 FAILED", e)
                        }
                    } else {
                        images.fullImage.delete()
                        Log.e("Post", "❌ STAGE 3 FAILED: ${result.error}")

                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(
                                loading = false,
                                error = result.error ?: "Failed to submit deal"
                            )
                        }
                    }
                } ?: run {
                    // ========================================
                    // ✅ PRESERVED: Image URL path (no compression)
                    // ⚠️ NO CHANGES
                    // ========================================
                    Log.d("Post", "📤 Submitting with IMAGE URL (no compression)...")
                    // ✅ SPRINT 5: Log userId in submission
                    Log.d("Post", "   UserId: ${uiState.verifiedUserId?.take(8)}...")

                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = finalImageUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null,
                        category = uiState.category.id,
                        promoCode = uiState.promoCode?.trim()?.ifBlank { null },
                        postedBy = uiState.username ?: "Anonymous",
                        userId = uiState.verifiedUserId,           // ✅ SPRINT 5: Already included
                        deviceId = deviceIdManager.getDeviceId(),  // ✅ SPRINT 5: Already included
                        expiresInDays = uiState.expiresInDays,     // ✨ NEW: Expiration duration
                        originalPrice = originalPriceValue,        // ✨ NEW: Original price (2025-11-16)
                        discountedPrice = discountedPriceValue     // ✨ NEW: Discounted price (2025-11-16)
                    )

                    val dealData = result.data

                    withContext(Dispatchers.Main) {
                        if (result.success == true && dealData != null) {
                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d("Post", "✅ Deal submitted (${totalTime}ms)")
                            // ✅ SPRINT 5: Log auto-approval status
                            Log.d("Post", "   Auto-approved: ${dealData.firstOrNull()?.autoApproved}")

                            uiState = uiState.copy(
                                loading = false,
                                message = if (dealData.firstOrNull()?.autoApproved == true) {
                                    "✅ Deal posted immediately!"
                                } else {
                                    "⏳ Deal submitted for review"
                                },
                                submitted = true
                            )
                        } else {
                            Log.e("Post", "❌ Submission failed: ${result.error}")
                            uiState = uiState.copy(
                                loading = false,
                                error = result.error ?: "Failed to submit deal"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Post", "💥 FATAL ERROR", e)
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        loading = false,
                        error = e.message ?: "Network error"
                    )
                }
            }
        }
    }

    // ========================================
    // ✅ PRESERVED: Validation methods
    // ⚠️ NO CHANGES: All existing validation logic kept intact
    // ========================================

    private fun isValidPlaceName(place: String): Boolean {
        val trimmed = place.trim()
        if (trimmed.length < 3 || trimmed.length > 100) return false
        val urlPatterns = listOf("http://", "https://", "www.", ".com", ".qa", ".net", ".org")
        if (urlPatterns.any { trimmed.lowercase().contains(it) }) return false
        if (trimmed.contains("...")) return false
        if (Regex("[!?]{3,}").containsMatchIn(trimmed)) return false
        if (trimmed.matches(Regex("^[0-9]+$"))) return false
        if (!trimmed.matches(Regex("^[\\p{L}\\p{N}\\s.,''&()\\-/]+$"))) return false
        return true
    }

    private fun isValidTitle(title: String): Boolean {
        val trimmed = title.trim()
        if (trimmed.length < 3 || trimmed.length > 200) {
            Log.d("PostViewModel", "❌ Title length invalid: ${trimmed.length}")
            return false
        }
        val urlPatterns = listOf("http://", "https://", "www.")
        if (urlPatterns.any { trimmed.lowercase().contains(it) }) {
            Log.d("PostViewModel", "❌ Title contains URL")
            return false
        }
        val hasOnlyControlChars = trimmed.all { it.isWhitespace() || it.isISOControl() }
        if (hasOnlyControlChars) {
            Log.d("PostViewModel", "❌ Title has only control characters")
            return false
        }
        return true
    }

    private fun isValidDescription(description: String): Boolean {
        val trimmed = description.trim()
        if (trimmed.length > 2000) return false
        return true
    }

    // ========================================
    // ✨ NEW: Price parsing helper (2025-11-16)
    // ========================================
    /**
     * Parses a price string to a Double value.
     * Removes commas and converts to Double.
     * Returns null if the string is empty or invalid.
     *
     * Examples:
     * - "1,995" -> 1995.0
     * - "19.99" -> 19.99
     * - "1,995.50" -> 1995.50
     * - "" -> null
     * - "abc" -> null
     */
    private fun parsePrice(priceString: String): Double? {
        if (priceString.isBlank()) return null
        return try {
            // Remove commas and parse as double
            priceString.replace(",", "").toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Factory for creating PostViewModel
 * ✅ SPRINT 5: NO CHANGES NEEDED
 * ⚠️ Constructor signature unchanged, factory remains compatible
 */
class PostViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PostViewModel::class.java)) {
            return PostViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
