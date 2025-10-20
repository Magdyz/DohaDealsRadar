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
import qa.deals.doha.repository.UsernameRepository
import qa.deals.domain.DealCategory  // ✅ ADD THIS IMPORT


/**
 * Deal type enum
 */
enum class DealType {
    ONLINE,
    PHYSICAL
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
    val category: DealCategory = DealCategory.FOOD_DINING, // ✨ CATEGORY CHANGE 1: Added category field
    val imageUrl: String = "",
    val selectedImageUri: Uri? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val submitted: Boolean = false,
    // ✨ NEW: Username management state
    val username: String? = null,  // Current username (null if not set)
    val showUsernameDialog: Boolean = false,  // Whether to show dialog
    val isCheckingUsername: Boolean = false,  // Loading state for availability check
    val usernameAvailable: Boolean? = null,  // true=available, false=taken, null=not checked
    val usernameError: String? = null  // Error message from username operations

)

/**
 * ViewModel for Post Screen
 * ✅ ENHANCED: Comprehensive logging for two-stage upload debugging
 */
class PostViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository()
) : ViewModel() {

    var uiState by mutableStateOf(PostUiState())
        private set

    // ✨ NEW: Repository for username operations
    private val usernameRepo = UsernameRepository()

    // ✨ NEW: Device ID manager for device identification
    private val deviceIdManager = DeviceIdManager.getInstance(context)

    init {
        // ✨ NEW: Check if user has username on initialization
        checkUserIdentity()
    }

    // ========================================
    // ✨ NEW: USERNAME MANAGEMENT
    // ========================================

    /**
     * Check if user has username on screen load
     * If not, will trigger username dialog on first post attempt
     */
    private fun checkUserIdentity() {
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("PostViewModel", "🔍 Checking user identity...")

                val deviceId = deviceIdManager.getDeviceId()
                Log.d("PostViewModel", "   Device ID: ${deviceId.take(8)}...${deviceId.takeLast(4)}")

                // Check local cache first (fast)
                val cachedUsername = deviceIdManager.getUsername()
                if (cachedUsername != null) {
                    Log.d("PostViewModel", "✅ Found cached username: $cachedUsername")
                    uiState = uiState.copy(username = cachedUsername)
                    Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    return@launch
                }

                // Check backend (authoritative)
                Log.d("PostViewModel", "🌐 Checking backend for username...")
                val result = usernameRepo.getUsernameForDevice(deviceId)

                result.onSuccess { username ->
                    if (username != null) {
                        Log.d("PostViewModel", "✅ Found backend username: $username")
                        // Cache it locally
                        deviceIdManager.saveUsername(username)
                        uiState = uiState.copy(username = username)
                    } else {
                        Log.d("PostViewModel", "ℹ️  No username found (first-time user)")
                        uiState = uiState.copy(username = null)
                    }
                }.onFailure { error ->
                    Log.e("PostViewModel", "❌ Error checking username: ${error.message}")
                    // Don't block user, they can still post (will show dialog)
                    uiState = uiState.copy(username = null)
                }

                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            } catch (e: Exception) {
                Log.e("PostViewModel", "💥 Error in checkUserIdentity", e)
                // Don't block user
                uiState = uiState.copy(username = null)
            }
        }
    }

    /**
     * ✨ Show username dialog
     * Called when user tries to post without username
     */
    fun showUsernameDialog() {
        Log.d("PostViewModel", "📋 Showing username dialog")
        uiState = uiState.copy(
            showUsernameDialog = true,
            usernameAvailable = null,  // Reset availability
            usernameError = null  // Reset errors
        )
    }

    /**
     * ✨ Hide username dialog
     * Called after successful registration or cancel
     */
    fun hideUsernameDialog() {
        Log.d("PostViewModel", "📋 Hiding username dialog")
        uiState = uiState.copy(
            showUsernameDialog = false,
            usernameAvailable = null,
            usernameError = null
        )
    }

    /**
     * ✨ Check username availability
     * Called when user clicks "Check Availability" in dialog
     */
    fun checkUsernameAvailability(username: String) {
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("PostViewModel", "🔍 Checking availability: \"$username\"")

                uiState = uiState.copy(
                    isCheckingUsername = true,
                    usernameAvailable = null,
                    usernameError = null
                )

                val result = usernameRepo.checkUsernameAvailability(username)

                result.onSuccess { available ->
                    Log.d("PostViewModel", if (available) "✅ Available!" else "❌ Taken")
                    uiState = uiState.copy(
                        isCheckingUsername = false,
                        usernameAvailable = available,
                        usernameError = if (!available) "Username is already taken" else null
                    )
                }.onFailure { error ->
                    Log.e("PostViewModel", "❌ Error: ${error.message}")
                    uiState = uiState.copy(
                        isCheckingUsername = false,
                        usernameAvailable = false,
                        usernameError = error.message ?: "Failed to check availability"
                    )
                }

                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            } catch (e: Exception) {
                Log.e("PostViewModel", "💥 Error checking availability", e)
                uiState = uiState.copy(
                    isCheckingUsername = false,
                    usernameAvailable = false,
                    usernameError = "Network error. Please try again."
                )
            }
        }
    }

    /**
     * ✨ Register username
     * Called when user clicks "Continue" after availability confirmed
     */
    fun registerUsername(username: String) {
        viewModelScope.launch {
            try {
                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("PostViewModel", "📝 Registering username: \"$username\"")

                uiState = uiState.copy(isCheckingUsername = true)

                val deviceId = deviceIdManager.getDeviceId()
                val result = usernameRepo.registerUsername(deviceId, username)

                result.onSuccess { registeredUsername ->
                    Log.d("PostViewModel", "✅ Registration successful!")

                    // Save to local cache
                    deviceIdManager.saveUsername(registeredUsername)

                    // Update UI state
                    uiState = uiState.copy(
                        username = registeredUsername,
                        isCheckingUsername = false,
                        showUsernameDialog = false,
                        usernameAvailable = null,
                        usernameError = null
                    )

                    Log.d("PostViewModel", "   Username saved: $registeredUsername")
                    Log.d("PostViewModel", "   Proceeding with deal submission...")

                    // Now submit the deal
                    submitDealWithUsername()

                }.onFailure { error ->
                    Log.e("PostViewModel", "❌ Registration failed: ${error.message}")
                    uiState = uiState.copy(
                        isCheckingUsername = false,
                        usernameError = error.message ?: "Failed to register username"
                    )
                }

                Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            } catch (e: Exception) {
                Log.e("PostViewModel", "💥 Error registering username", e)
                uiState = uiState.copy(
                    isCheckingUsername = false,
                    usernameError = "Network error. Please try again."
                )
            }
        }
    }

    // ========================================
    // ✨ EXISTING: Update methods (keep all existing code)
    // ========================================


    fun updateTitle(title: String) {
        uiState = uiState.copy(title = title, error = null)
    }

    // ✨ CATEGORY CHANGE 2: Added updateCategory function
    /**
     * ✨ NEW: Update selected category
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

    /**
     * ✨ Submit deal with username
     * Called after username is successfully registered
     * This is the actual deal submission that happens after username dialog
     */

    private fun submitDealWithUsername() {
        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d("PostViewModel", "🚀 Proceeding with deal submission...")
        Log.d("PostViewModel", "   Username: ${uiState.username}")
        Log.d("PostViewModel", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Call the existing submitDeal() function
        // This will handle all the image compression and upload logic
        submitDeal()
    }

    /**
     * ✅ ENHANCED: Submit deal with TWO-STAGE UPLOAD + comprehensive logging
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
        // ✨ STEP 0: Check for username FIRST
        // ========================================
        if (uiState.username == null) {
            Log.d("PostViewModel", "⚠️  No username found - showing dialog")
            showUsernameDialog()
            return  // Stop here - will resume after username registered
        }

        Log.d("PostViewModel", "✅ Username confirmed: ${uiState.username}")

        // Validation
        if (uiState.title.isBlank()) {
            uiState = uiState.copy(error = "Please enter a title")
            return
        }

// ✅ NEW: Validate title format
        if (!isValidTitle(uiState.title)) {
            uiState = uiState.copy(error = "Title contains invalid characters or URLs")
            return
        }

// ✅ NEW: Validate description if provided
        if (uiState.description.isNotBlank() && !isValidDescription(uiState.description)) {
            uiState = uiState.copy(error = "Description is too long (max 2000 characters)")
            return
        }

        // Validate based on deal type
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

        // Must have either selected image OR image URL
        if (uiState.selectedImageUri == null && uiState.imageUrl.isBlank()) {
            uiState = uiState.copy(error = "Please select an image or provide an image URL")
            return
        }

        // URL validation for image URL (if provided)
        if (uiState.imageUrl.isNotBlank() &&
            !uiState.imageUrl.startsWith("http://") &&
            !uiState.imageUrl.startsWith("https://")
        ) {
            uiState = uiState.copy(error = "Image URL must start with http:// or https://")
            return
        }

        // ✅ Use SupervisorJob + Dispatchers.IO to prevent cancellation
        viewModelScope.launch(Dispatchers.IO + SupervisorJob()) {
            try {
                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.d("Post", "🚀 TWO-STAGE UPLOAD STARTED")
                Log.d("Post", "   Deal: ${uiState.title}")
                Log.d("Post", "   Type: ${uiState.dealType}")
                Log.d("Post", "   Category: ${uiState.category.displayName}") // ✨ CATEGORY CHANGE 3: Added category to log
                Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                val startTime = System.currentTimeMillis()

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(loading = true, error = null, message = null)
                }

                var finalImageUrl = uiState.imageUrl
                var dealId: String? = null

                uiState.selectedImageUri?.let { uri ->
                    // ========================================
                    // ⚡ STAGE 1: Compress to thumbnail + full image
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
                    Log.d("Post", "      File: ${images.thumbnail.name}")
                    Log.d("Post", "      Path: ${images.thumbnail.absolutePath}")
                    Log.d("Post", "   → Full image: ${images.fullImage.length() / 1024}KB")
                    Log.d("Post", "      File: ${images.fullImage.name}")
                    Log.d("Post", "      Path: ${images.fullImage.absolutePath}")
                    Log.d("Post", "   → Size ratio: ${String.format("%.1f", images.fullImage.length().toFloat() / images.thumbnail.length().toFloat())}x")

                    // ========================================
                    // ⚡ STAGE 2: Upload TINY thumbnail only
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
                    Log.d("Post", "   → Thumbnail file deleted from cache")

                    // ========================================
                    // ⚡ STAGE 3: Submit deal with thumbnail
                    // ========================================
                    val stage3Start = System.currentTimeMillis()
                    Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.d("Post", "📤 STAGE 3: Submitting deal with THUMBNAIL...")
                    Log.d("Post", "   Title: ${uiState.title.trim()}")
                    Log.d("Post", "   Category: ${uiState.category.id}") // ✨ CATEGORY CHANGE 4: Added category to log
                    Log.d("Post", "   Image URL: $thumbnailUrl")

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Posting deal...")
                    }
                    Log.d("Post", "📤 Submitting with category: ${uiState.category.id}")
                    Log.d("Post", "   Category display name: ${uiState.category.displayName}")

                    // ✨ CATEGORY CHANGE 5: Added category parameter to submitDeal call
                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = thumbnailUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null,
                        category = uiState.category.id, // ✨ CATEGORY CHANGE: Category added here
                        promoCode = uiState.promoCode?.trim()?.ifBlank { null },
                        postedBy = uiState.username ?: "Anonymous"  // ✨ NEW: Include username
                    )

                    Log.d("Post", "📥 API Response success: ${result.success}")
                    Log.d("Post", "   API Response data: ${result.data}")

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
                                message = "✅ Deal posted! Uploading full image...",
                                submitted = true
                            )
                        }

                        // ========================================
                        // ⚡ STAGE 4: Upload full image in BACKGROUND
                        // (User has already navigated away!)
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
                                Log.d("Post", "   Old URL (thumbnail): $thumbnailUrl")
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
                                Log.d("Post", "   → Feed should now display FULL IMAGE for deal $id")

                                val totalBackgroundTime = System.currentTimeMillis() - stage4Start
                                Log.d("Post", "✅ STAGE 4 COMPLETE (${totalBackgroundTime}ms)")
                                Log.d("Post", "   Upload: ${uploadTime}ms")
                                Log.d("Post", "   DB update: ${updateTime}ms")
                                Log.d("Post", "   Cache refresh: ${cacheTime}ms")

                            } ?: run {
                                Log.e("Post", "❌ ERROR: dealId was null, cannot update image!")
                            }

                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.d("Post", "🎉 TWO-STAGE UPLOAD COMPLETE")
                            Log.d("Post", "   Total time: ${totalTime}ms (~${totalTime/1000}s)")
                            Log.d("Post", "   User waited: ${userWaitTime}ms (~${userWaitTime/1000}s)")
                            Log.d("Post", "   Background: ${totalTime - userWaitTime}ms")
                            Log.d("Post", "   Compression: ${stage1Time}ms")
                            Log.d("Post", "   Thumbnail upload: ${stage2Time}ms")
                            Log.d("Post", "   Deal submit: ${stage3Time}ms")
                            Log.d("Post", "   Full upload: ${totalTime - userWaitTime}ms")
                            Log.d("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                        } catch (e: Exception) {
                            Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            Log.e("Post", "💥 STAGE 4 FAILED - Background upload error")
                            Log.e("Post", "   Error type: ${e.javaClass.simpleName}")
                            Log.e("Post", "   Error message: ${e.message}")
                            Log.e("Post", "   Deal $dealId will keep THUMBNAIL URL")
                            Log.e("Post", "   (Deal still visible with lower quality image)")
                            Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", e)
                        }
                    } else {
                        // Deal submission failed
                        images.fullImage.delete()
                        Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.e("Post", "❌ STAGE 3 FAILED - Deal submission error")
                        Log.e("Post", "   API success: ${result.success}")
                        Log.e("Post", "   API error: ${result.error}")
                        Log.e("Post", "   Data: $dealData")
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
                    // No image selected, use URL directly
                    // ========================================
                    Log.d("Post", "📤 Submitting deal with IMAGE URL (no compression)...")
                    Log.d("Post", "   Image URL: $finalImageUrl")
                    Log.d("Post", "   Category: ${uiState.category.id}") // ✨ CATEGORY CHANGE 6: Added category to log

                    // ✨ CATEGORY CHANGE 7: Added category parameter to submitDeal call
                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = finalImageUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null,
                        category = uiState.category.id, // ✨ CATEGORY CHANGE: Category added here
                        promoCode = uiState.promoCode?.trim()?.ifBlank { null },
                        postedBy = uiState.username ?: "Anonymous"  // ✨ NEW: Include username
                    )

                    withContext(Dispatchers.Main) {
                        if (result.success == true) {
                            val totalTime = System.currentTimeMillis() - startTime
                            Log.d("Post", "✅ Deal submitted successfully (${totalTime}ms)")
                            Log.d("Post", "   No image compression needed (using URL)")

                            uiState = uiState.copy(
                                loading = false,
                                message = "✅ Deal submitted successfully! It will appear after review.",
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
                Log.e("Post", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.e("Post", "💥 FATAL ERROR - Submit deal crashed")
                Log.e("Post", "   Error type: ${e.javaClass.simpleName}")
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

    /**
     * Validate place name - block URLs, spam, etc.
     */
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
        // Allow: letters (any language), numbers, spaces, and common punctuation
        // Arabic range: \u0600-\u06FF
        // Latin with diacritics: \u00C0-\u017F (includes ö, é, ñ, etc.)
        // Basic Latin: a-zA-Z
        if (!trimmed.matches(Regex("^[\\p{L}\\p{N}\\s.,''&()\\-/]+$"))) return false

        return true
    }
    /**
     * Validate title - allow multilingual input
     */
    private fun isValidTitle(title: String): Boolean {
        val trimmed = title.trim()

        // Length check (3-200 characters)
        if (trimmed.length < 3 || trimmed.length > 200) return false

        // Block URLs in title
        val urlPatterns = listOf("http://", "https://", "www.")
        if (urlPatterns.any { trimmed.lowercase().contains(it) }) return false

        // Allow Unicode letters, numbers, spaces, and common punctuation
        // More permissive than location (allows %, emojis for deal titles)
        return trimmed.matches(Regex("^[\\p{L}\\p{N}\\p{P}\\p{S}\\s]+$"))
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