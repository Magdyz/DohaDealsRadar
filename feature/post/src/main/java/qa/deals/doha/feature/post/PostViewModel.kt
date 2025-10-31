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
import qa.deals.doha.util.ImageCompressor
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.domain.DealCategory
// ✅ NEW IMPORTS for email verification
// ❌ REMOVED: No longer need direct API access
// import qa.deals.doha.network.NetworkModule
// import qa.deals.doha.network.SupabaseApiService
import qa.deals.doha.network.SendCodeRequest
import qa.deals.doha.network.UserInfo
import qa.deals.doha.network.VerifyCodeRequest
// ❌ REMOVED: UsernameRepository is no longer used
// import qa.deals.doha.repository.UsernameRepository


/**
 * Deal type enum
 */
enum class DealType {
    ONLINE,
    PHYSICAL
}

// ========================================
// ✅ NEW: State for Email Verification Flow
// (as described in handover brief)
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
 */
data class PostUiState(
    val title: String = "",
    val description: String = "",
    val dealType: DealType = DealType.ONLINE,
    val link: String = "",
    val location: String = "",
    val promoCode: String? = null,
    val category: DealCategory = DealCategory.FOOD_DINING, // ✨ PRESERVED: Category field
    val imageUrl: String = "",
    val selectedImageUri: Uri? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val submitted: Boolean = false,

    // ========================================
    // ✅ MODIFIED: Username state replaced with Email Verification state
    // ========================================
    val username: String? = null,  // Current username (loaded from cache, set by verification)
    val verifiedUserId: String? = null, // ✨ NEW: User ID from verification (null if not verified this session)
    val showEmailVerification: Boolean = false,  // ✅ RENAMED: Replaces showUsernameDialog
    val emailVerificationState: EmailVerificationState = EmailVerificationState.Idle // ✨ NEW: Manages dialog UI
    // ❌ REMOVED: isCheckingUsername, usernameAvailable, usernameError
    // ========================================
)

/**
 * ViewModel for Post Screen
 * ✅ ENHANCED: Comprehensive logging for two-stage upload debugging
 * ✅ UPDATED: Integrated new email verification flow
 */
class PostViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository()
) : ViewModel() {

    var uiState by mutableStateOf(PostUiState())
        private set

    // ❌ REMOVED: Old username repository
    // private val usernameRepo = UsernameRepository()

    // ❌ REMOVED: Direct API access
    // private val api: SupabaseApiService = NetworkModule.api

    // ✨ PRESERVED: Device ID manager for device identification
    private val deviceIdManager = DeviceIdManager.getInstance(context)

    init {
        // ✅ FIXED: Load both username AND user_id from persistent storage
        viewModelScope.launch {
            val cachedUsername = deviceIdManager.getUsername()
            val cachedUserId = deviceIdManager.getUserId()  // ✨ NEW: Load persistent user ID

            if (cachedUsername != null && cachedUserId != null) {
                Log.d("PostViewModel", "✅ Loaded cached user: $cachedUsername (ID: ${cachedUserId.take(8)}...)")
                uiState = uiState.copy(
                    username = cachedUsername,
                    verifiedUserId = cachedUserId  // ✨ CRITICAL: Restore user ID from storage
                )
            } else {
                Log.d("PostViewModel", "ℹ️ No cached user. Will prompt for verification on post.")
            }
        }
    }

    // ========================================
    // ❌ REMOVED: OLD USERNAME MANAGEMENT
    // All functions (checkUserIdentity, showUsernameDialog,
    // hideUsernameDialog, checkUsernameAvailability, registerUsername)
    // have been removed.
    // ========================================


    // ========================================
    // ✅ NEW: EMAIL VERIFICATION MANAGEMENT
    // Replaces old username dialog logic
    // ========================================

    /**
     * ✨ Show email verification screen
     * Called when user tries to post without a verified User ID in session
     */
    fun showEmailVerification() {
        Log.d("PostViewModel", "📧 Showing email verification screen")
        uiState = uiState.copy(
            showEmailVerification = true,
            emailVerificationState = EmailVerificationState.Idle
        )
    }

    /**
     * ✨ Hide email verification screen
     * Called on 'Cancel' or 'Dismiss'
     */
    fun hideEmailVerification() {
        Log.d("PostViewModel", "📧 Hiding email verification screen")
        uiState = uiState.copy(
            showEmailVerification = false,
            emailVerificationState = EmailVerificationState.Idle
        )
    }

    /**
     * ✨ Called from EmailVerificationScreen when user data is successfully retrieved
     * This is the new entry point from `PostScreen.kt`.
     */
    fun onEmailVerified(userId: String, username: String, email: String, isNew: Boolean) {
        Log.d("PostViewModel", "✅ Email Verified. User: $username, ID: $userId")

        // 1. Cache BOTH username AND user_id for future sessions
        deviceIdManager.saveUsername(username)
        deviceIdManager.saveUserId(userId)  // ✨ NEW: Persist user ID to skip verification next time

        // 2. Update UI state for *this session*
        uiState = uiState.copy(
            username = username,          // For display in TopBar
            verifiedUserId = userId,      // CRITICAL: For deal submission
            showEmailVerification = false,
            emailVerificationState = EmailVerificationState.Idle
        )

        // 3. Auto-submit the deal (as requested in PostScreen.kt logic)
        Log.d("PostViewModel", "   Proceeding with auto-submission...")
        submitDeal()
    }

    /**
     * ✨ Send verification code (Called from EmailVerificationScreen)
     */
    fun sendVerificationCode(email: String) {
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "📧 Sending verification code to: $email")
                uiState = uiState.copy(emailVerificationState = EmailVerificationState.Loading("Sending code..."))

                // ========================================
                // ✅ MODIFIED: Use Repository
                // ========================================
                val response = repo.sendVerificationCode(email)
                // ❌ REMOVED: api.sendVerificationCode(SendCodeRequest(email))

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
     * ✨ Verify code and get user (Called from EmailVerificationScreen)
     */
    fun verifyCode(email: String, code: String) {
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "🔒 Verifying code: $code for email: $email")
                uiState = uiState.copy(emailVerificationState = EmailVerificationState.Loading("Verifying code..."))
                val deviceId = deviceIdManager.getDeviceId()

                // ========================================
                // ✅ MODIFIED: Use Repository
                // ========================================
                val response = repo.verifyCodeAndGetUser(
                    email = email,
                    code = code,
                    deviceId = deviceId
                )
                // ❌ REMOVED: api.verifyCodeAndGetUser(...)

                // ========================================
                // ✅ FIX: "Smart cast... impossible"
                // Assign to a local val before checking null
                // ========================================
                val user = response.user //

                if (response.success && user != null) {
                    Log.d("PostViewModel", "✅ Verification successful! User: ${user.username}") //
                    // This state triggers the callback in PostScreen
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
    // ✨ PRESERVED: Update methods (All existing code unchanged)
    // ========================================

    fun updateTitle(title: String) {
        uiState = uiState.copy(title = title, error = null)
    }

    /**
     * ✨ PRESERVED: Update selected category
     */
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

    fun updateImageUrl(imageUrl: String) {
        uiState = uiState.copy(imageUrl = imageUrl, error = null, selectedImageUri = null)
    }

    fun setSelectedImage(uri: Uri) {
        uiState = uiState.copy(selectedImageUri = uri, imageUrl = "", error = null)
    }

    fun clearImage() {
        uiState = uiState.copy(selectedImageUri = null, error = null)
    }

    // ========================================
// 🔧 NEW (2025-10-29): Clear error method
// This is called when user makes changes to form fields
// ========================================
    fun clearError() {
        if (uiState.error != null) {
            Log.d("PostViewModel", "🧹 Clearing error state")
            uiState = uiState.copy(error = null)
        }
    }

    // ========================================
    // ❌ REMOVED: `submitDealWithUsername`
    // This function was part of the old dialog flow and is no longer needed.
    // The new flow calls `submitDeal()` directly from `onEmailVerified`.
    // ========================================

    /**
     * ✅ ENHANCED: Submit deal with TWO-STAGE UPLOAD + comprehensive logging
     * ✅ UPDATED: Now checks for verification and includes user/device IDs
     *
     * Process:
     * 1. Compress to thumbnail + full image (~700ms)
     * 2. Upload thumbnail ONLY (~1 second)
     * 3. Submit deal with thumbnail → User sees "Posted!" immediately
     * 4. Upload full image in background → Auto-updates deal
     *
     * Total user wait time: ~1.5 seconds (was 12-14 seconds)
     */
    fun submitDeal() {
        // ========================================
        // ✅ STEP 0: Check for user verification ID FIRST
        // ========================================
        if (uiState.verifiedUserId == null) {
            // This session is not verified. We must show the email verification screen.
            // Even if username is cached, we need the user_id for the API call.
            // The backend will handle returning the correct user via device_id.
            Log.d("PostViewModel", "⚠️ Verified User ID not in session. Showing email verification.")
            showEmailVerification() // ✅ MODIFIED: Call new function
            return  // Stop here - will resume after verification
        }

        // ✅ PRESERVED: Logged in and verified
        Log.d("PostViewModel", "✅ User ID confirmed: ${uiState.verifiedUserId}")
        Log.d("PostViewModel", "✅ Username confirmed: ${uiState.username}")


        // ✅ PRESERVED: All existing validation logic (lines 379-445)
        if (uiState.title.isBlank()) {
            uiState = uiState.copy(error = "Please enter a title")
            return
        }

// AFTER (with detailed logging):
        Log.d("PostViewModel", "📝 Step 1: Validating title...")
        Log.d("PostViewModel", "   Title: '${uiState.title}'")
        Log.d("PostViewModel", "   Title length: ${uiState.title.length}")
        Log.d("PostViewModel", "   Is blank: ${uiState.title.isBlank()}")

        if (!isValidTitle(uiState.title)) {
            Log.e("PostViewModel", "❌ VALIDATION FAILED: Title contains invalid characters")
            Log.e("PostViewModel", "   Title: '${uiState.title}'")
            Log.e("PostViewModel", "   Title bytes: ${uiState.title.toByteArray().joinToString(" ") { "%02x".format(it) }}")
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

        // ✅ PRESERVED: SupervisorJob + Dispatchers.IO
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            try {
                // ✅ PRESERVED: All logging
                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("Post", "🚀 TWO-STAGE UPLOAD STARTED")
                Log.d("Post", "   Deal: ${uiState.title}")
                Log.d("Post", "   Type: ${uiState.dealType}")
                Log.d("Post", "   Category: ${uiState.category.displayName}")
                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                val startTime = System.currentTimeMillis()

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(loading = true, error = null, message = null)
                }

                var finalImageUrl = uiState.imageUrl
                var dealId: String? = null

                uiState.selectedImageUri?.let { uri ->
                    // ========================================
                    // ⚡ PRESERVED: STAGE 1: Compress to thumbnail + full image
                    // ========================================
                    val stage1Start = System.currentTimeMillis()
                    Log.d("Post", "📦 STAGE 1: Starting image compression...")
                    Log.d("Post", "   Input URI: $uri")

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

                    // ========================================
                    // ⚡ PRESERVED: STAGE 2: Upload TINY thumbnail only
                    // ========================================
                    val stage2Start = System.currentTimeMillis()
                    Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d("Post", "📤 STAGE 2: Uploading thumbnail (${images.thumbnail.length() / 1024}KB)...")

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Uploading preview...")
                    }

                    val thumbnailUrl = repo.uploadImage(images.thumbnail)
                    images.thumbnail.delete()
                    finalImageUrl = thumbnailUrl

                    val stage2Time = System.currentTimeMillis() - stage2Start
                    Log.d("Post", "✅ STAGE 2 COMPLETE (${stage2Time}ms)")
                    Log.d("Post", "   → Thumbnail uploaded successfully")
                    Log.d("Post", "   → URL: $thumbnailUrl")

                    // ========================================
                    // ⚡ STAGE 3: Submit deal with thumbnail
                    // ========================================
                    val stage3Start = System.currentTimeMillis()
                    Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d("Post", "📤 STAGE 3: Submitting deal with THUMBNAIL...")
                    Log.d("Post", "   Title: ${uiState.title.trim()}")
                    Log.d("Post", "   Category: ${uiState.category.id}")
                    Log.d("Post", "   Image URL: $thumbnailUrl")

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Posting deal...")
                    }
                    Log.d("Post", "📤 Submitting with category: ${uiState.category.id}")

                    // ========================================
                    // ⚠️ MODIFIED: `repo.submitDeal` call
                    // Added `userId` and `deviceId` parameters.
                    // This REQUIRES `DealRepository.kt` to be updated.
                    // ========================================
                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = thumbnailUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null,
                        category = uiState.category.id, // ✨ PRESERVED: Category
                        promoCode = uiState.promoCode?.trim()?.ifBlank { null },
                        postedBy = uiState.username ?: "Anonymous", // ✨ MODIFIED: Uses verified username
                        userId = uiState.verifiedUserId,           // ✅ NEW: Pass verified User ID
                        deviceId = deviceIdManager.getDeviceId()   // ✅ NEW: Pass Device ID
                    )

                    // ✅ PRESERVED: All post-submission logic (lines 523-600)
                    Log.d("Post", "📥 API Response success: ${result.success}")
                    Log.d("Post", "   API Response data: ${result.data}")

                    // ========================================
                    // ✅ FIX: "Smart cast... impossible"
                    // Assign to a local val before checking null
                    // ========================================
                    val dealData = result.data

                    if (result.success == true && dealData != null && dealData.isNotEmpty()) {
                        dealId = dealData[0].id
                        val stage3Time = System.currentTimeMillis() - stage3Start
                        val userWaitTime = System.currentTimeMillis() - startTime

                        Log.d("Post", "✅ STAGE 3 COMPLETE (${stage3Time}ms)")
                        Log.d("Post", "   → Deal ID: $dealId")
                        Log.d("Post", "   → Deal created with THUMBNAIL URL in database")
                        Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d("Post", "⏱️  USER WAIT TIME: ${userWaitTime}ms (~${userWaitTime/1000}s)")
                        Log.d("Post", "🎉 USER SEES 'POSTED!' - Navigating away now...")
                        Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(
                                loading = false,
                                // ✅ NEW: Updated success message based on auto-approval
                                message = if (dealData[0].autoApproved == true) {
                                    "✅ Deal posted immediately!"
                                } else {
                                    "⏳ Deal submitted for review"
                                },
                                submitted = true
                            )
                        }

                        // ========================================
                        // ⚡ PRESERVED: STAGE 4: Upload full image in BACKGROUND
                        // ========================================
                        Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d("Post", "🔄 STAGE 4: Background full image upload...")
                        Log.d("Post", "   (User already left screen)")
                        Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                        try {
                            val stage4Start = System.currentTimeMillis()

                            Log.d("Post", "📤 Uploading full image (${images.fullImage.length() / 1024}KB)...")
                            val fullImageUrl = repo.uploadImage(images.fullImage)

                            val uploadTime = System.currentTimeMillis() - stage4Start
                            Log.d("Post", "✅ Full image uploaded (${uploadTime}ms)")
                            Log.d("Post", "   → URL: $fullImageUrl")

                            images.fullImage.delete()
                            Log.d("Post", "   → Full image file deleted from cache")

                            // Update deal with full image URL
                            dealId?.let { id ->
                                val updateStart = System.currentTimeMillis()

                                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                Log.d("Post", "🔄 Updating database with FULL IMAGE...")
                                Log.d("Post", "   Deal ID: $id")
                                Log.d("Post", "   New URL (full): $fullImageUrl")

                                repo.updateDealImage(id, fullImageUrl)

                                val updateTime = System.currentTimeMillis() - updateStart
                                Log.d("Post", "✅ Database updated (${updateTime}ms)")
                                Log.d("Post", "   → Deal $id now has FULL IMAGE in Supabase")

                                // ✅ Force cache refresh
                                val cacheStart = System.currentTimeMillis()
                                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                                Log.d("Post", "🔄 Refreshing local Room cache from backend...")

                                repo.refreshDeals()

                                val cacheTime = System.currentTimeMillis() - cacheStart
                                Log.d("Post", "✅ Cache refreshed (${cacheTime}ms)")

                                val totalBackgroundTime = System.currentTimeMillis() - stage4Start
                                Log.d("Post", "✅ STAGE 4 COMPLETE (${totalBackgroundTime}ms)")

                            } ?: run {
                                Log.e("Post", "❌ ERROR: dealId was null, cannot update image!")
                            }

                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.d("Post", "🎉 TWO-STAGE UPLOAD COMPLETE")
                            Log.d("Post", "   Total time: ${totalTime}ms (~${totalTime/1000}s)")
                            Log.d("Post", "   User waited: ${userWaitTime}ms (~${userWaitTime/1000}s)")
                            Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                        } catch (e: Exception) {
                            Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.e("Post", "💥 STAGE 4 FAILED - Background upload error")
                            Log.e("Post", "   Error: ${e.message}")
                            Log.e("Post", "   Deal $dealId will keep THUMBNAIL URL")
                            Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", e)
                        }
                    } else {
                        // ✅ PRESERVED: Deal submission failed logic
                        images.fullImage.delete()
                        Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.e("Post", "❌ STAGE 3 FAILED - Deal submission error")
                        Log.e("Post", "   API error: ${result.error}")
                        Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(
                                loading = false,
                                error = result.error ?: "Failed to submit deal"
                            )
                        }
                    }
                } ?: run {
                    // ========================================
                    // ✅ PRESERVED: No image selected, use URL directly
                    // ========================================
                    Log.d("Post", "📤 Submitting deal with IMAGE URL (no compression)...")
                    Log.d("Post", "   Image URL: $finalImageUrl")
                    Log.d("Post", "   Category: ${uiState.category.id}")

                    // ========================================
                    // ⚠️ MODIFIED: `repo.submitDeal` call
                    // Added `userId` and `deviceId` parameters.
                    // This REQUIRES `DealRepository.kt` to be updated.
                    // ========================================
                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = finalImageUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null,
                        category = uiState.category.id, // ✨ PRESERVED: Category
                        promoCode = uiState.promoCode?.trim()?.ifBlank { null },
                        postedBy = uiState.username ?: "Anonymous", // ✨ MODIFIED: Uses verified username
                        userId = uiState.verifiedUserId,           // ✅ NEW: Pass verified User ID
                        deviceId = deviceIdManager.getDeviceId()   // ✅ NEW: Pass Device ID
                    )

                    // ========================================
                    // ✅ FIX: "Smart cast... impossible"
                    // Assign to a local val before checking null
                    // ========================================
                    val dealData = result.data //

                    withContext(Dispatchers.Main) {
                        if (result.success == true && dealData != null) {
                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d("Post", "✅ Deal submitted successfully (${totalTime}ms)")

                            uiState = uiState.copy(
                                loading = false,
                                // ✅ NEW: Updated success message based on auto-approval
                                message = if (dealData.firstOrNull()?.autoApproved == true) {
                                    "✅ Deal posted immediately!"
                                } else {
                                    "⏳ Deal submitted for review"
                                },
                                submitted = true
                            )
                        } else {
                            Log.e("Post", "❌ Deal submission failed: ${result.error}")
                            uiState = uiState.copy(
                                loading = false,
                                error = result.error ?: "Failed to submit deal"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // ✅ PRESERVED: Fatal error handling
                Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.e("Post", "💥 FATAL ERROR - Submit deal crashed")
                Log.e("Post", "   Error message: ${e.message}")
                Log.e("Post", "   Stack trace:", e)
                Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

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
    // ✨ PRESERVED: Validation methods (All existing code unchanged)
    // ========================================

    /**
     * Validate place name - block URLs, spam, etc.
     * ✅ UPDATED: Now supports English, Arabic, and international characters (ö, é, etc.)
     */
    private fun isValidPlaceName(place: String): Boolean {
        val trimmed = place.trim()

        // Length check (3-100 characters)
        if (trimmed.length < 3 || trimmed.length > 100) return false

        // Block URLs
        val urlPatterns = listOf("http://", "https://", "www.", ".com", ".qa", ".net", ".org")
        if (urlPatterns.any { trimmed.lowercase().contains(it) }) return false

        // Block excessive dots
        if (trimmed.contains("...")) return false

        // Block excessive exclamation/question marks
        if (Regex("[!?]{3,}").containsMatchIn(trimmed)) return false

        // Block numbers only
        if (trimmed.matches(Regex("^[0-9]+$"))) return false

        // ✅ FIXED: Allow Unicode letters (Arabic, English, accented characters)
        if (!trimmed.matches(Regex("^[\\p{L}\\p{N}\\s.,''&()\\-/]+$"))) return false

        return true
    }
    /**
     * Validate title - allow multilingual input
     */
    /**
     * Validate title - allow multilingual input, emojis, and common symbols
     * 🔧 FIXED (2025-10-29): More permissive regex to allow emojis and Arabic
     */
    private fun isValidTitle(title: String): Boolean {
        val trimmed = title.trim()

        // Length check (3-200 characters)
        if (trimmed.length < 3 || trimmed.length > 200) {
            Log.d("PostViewModel", "❌ Title length invalid: ${trimmed.length}")
            return false
        }

        // Block URLs in title
        val urlPatterns = listOf("http://", "https://", "www.")
        if (urlPatterns.any { trimmed.lowercase().contains(it) }) {
            Log.d("PostViewModel", "❌ Title contains URL")
            return false
        }

        // 🔧 FIXED: More permissive validation
        // Allow: Letters, Numbers, Punctuation, Symbols, Whitespace, Emojis
        // Block: Only control characters and invisible formatting
        val hasOnlyControlChars = trimmed.all { it.isWhitespace() || it.isISOControl() }
        if (hasOnlyControlChars) {
            Log.d("PostViewModel", "❌ Title has only control characters")
            return false
        }

        // 🔧 NEW: Allow almost anything except pure control characters
        // This is more user-friendly and allows emojis, Arabic, special symbols
        return true
    }

    /**
     * Validate description - most permissive (multiline, emojis, etc.)
     */
    private fun isValidDescription(description: String): Boolean {
        val trimmed = description.trim()

        // Length check (0-2000 characters, optional field)
        if (trimmed.length > 2000) return false

        // Allow almost anything (Unicode letters, numbers, punctuation, symbols, whitespace)
        return true  // Descriptions can be very free-form
    }
}

/**
 * Factory for creating PostViewModel with Context dependency
 * ✅ PRESERVED: No changes needed as constructor signature was maintained.
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