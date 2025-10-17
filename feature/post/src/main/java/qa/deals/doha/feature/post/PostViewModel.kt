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
    val promoCode: String? = null, // ✅ NEW: Add promo code field
    val imageUrl: String = "",
    val selectedImageUri: Uri? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val submitted: Boolean = false
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

    init {
        Log.d("Post", "📝 PostViewModel created")
    }

    fun updateTitle(title: String) {
        uiState = uiState.copy(title = title, error = null)
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
    // ✅ NEW: Add promo code update function
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
        // Validation
        if (uiState.title.isBlank()) {
            uiState = uiState.copy(error = "Please enter a title")
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
                    Log.d("Post", "   Image URL: $thumbnailUrl")

                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Posting deal...")
                    }

                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = thumbnailUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null
                    )

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

                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = finalImageUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null
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

        // Allow only letters, numbers, spaces, and basic punctuation
        if (!trimmed.matches(Regex("^[a-zA-Z0-9\\s.,''&-]+$"))) return false

        return true
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