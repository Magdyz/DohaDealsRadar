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
    val imageUrl: String = "",
    val selectedImageUri: Uri? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val submitted: Boolean = false
)

/**
 * ViewModel for Post Screen
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
    // ✅ REPLACE THE ENTIRE submitDeal() FUNCTION WITH THIS:
    // ========================================
    /**
     * Submit the deal with TWO-STAGE UPLOAD
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
                Log.d("Post", "📤 Submitting deal: ${uiState.title}")
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(loading = true, error = null, message = null)
                }

                var finalImageUrl = uiState.imageUrl
                var dealId: String? = null

                uiState.selectedImageUri?.let { uri ->
                    // ⚡ STAGE 1: Compress to thumbnail + full image (~700ms)
                    Log.d("Post", "🖼️ Compressing images...")
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📦 Compressing image...")
                    }

                    val images = ImageCompressor.compressImageTwoStage(
                        context = context,
                        uri = uri
                    )

                    Log.d("Post", "✅ Thumbnail: ${images.thumbnail.length() / 1024}KB")
                    Log.d("Post", "✅ Full image: ${images.fullImage.length() / 1024}KB")

                    // ⚡ STAGE 2: Upload TINY thumbnail only (~1 second)
                    Log.d("Post", "📤 Uploading thumbnail...")
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Uploading preview...")
                    }

                    val thumbnailUrl = repo.uploadImage(images.thumbnail)
                    images.thumbnail.delete()
                    finalImageUrl = thumbnailUrl

                    Log.d("Post", "✅ Thumbnail uploaded: $thumbnailUrl")

                    // ⚡ STAGE 3: Submit deal with thumbnail (user can navigate away after this!)
                    Log.d("Post", "📤 Submitting deal...")
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

                    val dealData = result.data // ✅ Create a stable local variable
                    if (result.success == true && dealData != null && dealData.isNotEmpty()) {
                        dealId = dealData[0].id

                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(
                                loading = false,
                                message = "✅ Deal posted! Uploading full image...",
                                submitted = true
                            )
                        }

                        Log.d("Post", "✅ Deal submitted successfully with ID: $dealId")

                        // ⚡ STAGE 4: Upload full image in BACKGROUND (user already navigated away!)
                        try {
                            Log.d("Post", "📤 Uploading full image in background...")
                            val fullImageUrl = repo.uploadImage(images.fullImage)
                            images.fullImage.delete()

                            Log.d("Post", "✅ Full image uploaded: $fullImageUrl")

                            // Update deal with full image URL
                            dealId?.let { id -> // ✅ Only execute if dealId is not null
                                repo.updateDealImage(id, fullImageUrl)
                                Log.d("Post", "✅ Deal image updated to full resolution for ID: $id")
                            } ?: Log.e("Post", "❌ Could not update image, dealId was null.")

                        } catch (e: Exception) {
                            Log.e("Post", "⚠️ Full image upload failed (thumbnail still works)", e)
                            // Thumbnail is already live, so deal is still visible!
                        }
                    } else {
                        // Deal submission failed
                        images.fullImage.delete()
                        withContext(Dispatchers.Main) {
                            uiState = uiState.copy(
                                loading = false,
                                error = result.error ?: "Failed to submit deal"
                            )
                        }
                        Log.e("Post", "❌ Deal submission failed: ${result.error}")
                    }
                } ?: run {
                    // No image selected, use URL
                    val result = repo.submitDeal(
                        title = uiState.title.trim(),
                        description = uiState.description.trim().ifBlank { null },
                        link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                        imageUrl = finalImageUrl,
                        location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null
                    )

                    withContext(Dispatchers.Main) {
                        if (result.success == true) {
                            uiState = uiState.copy(
                                loading = false,
                                message = "✅ Deal submitted successfully! It will appear after review.",
                                submitted = true
                            )
                            Log.d("Post", "✅ Deal submitted successfully")
                        } else {
                            uiState = uiState.copy(
                                loading = false,
                                error = result.error ?: "Failed to submit deal"
                            )
                            Log.e("Post", "❌ Submit failed: ${result.error}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Post", "💥 Error submitting deal", e)
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
    // ✅ END OF UPDATED submitDeal()
    // ========================================

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