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

    /**
     * Submit the deal
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

                // Step 1: Upload image if selected
                var finalImageUrl = uiState.imageUrl

                uiState.selectedImageUri?.let { uri ->
                    Log.d("Post", "🖼️ Compressing image...")
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📦 Compressing image...")
                    }

                    // Compress image
                    val compressedFile = ImageCompressor.compressImage(
                        context = context,
                        uri = uri,
                        maxDimension = 1280,
                        maxSizeBytes = 500 * 1024 // 500KB
                    )

                    Log.d("Post", "📤 Uploading image... Size: ${compressedFile.length() / 1024}KB")
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = "📤 Uploading image (may take 30 seconds)...")
                    }

                    // Upload to Supabase Storage
                    finalImageUrl = repo.uploadImage(compressedFile)

                    // Clean up temp file
                    compressedFile.delete()

                    Log.d("Post", "✅ Image uploaded: $finalImageUrl")
                    withContext(Dispatchers.Main) {
                        uiState = uiState.copy(message = null)
                    }
                }

                // Step 2: Submit deal with image URL
                Log.d("Post", "📤 Submitting deal to backend...")
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