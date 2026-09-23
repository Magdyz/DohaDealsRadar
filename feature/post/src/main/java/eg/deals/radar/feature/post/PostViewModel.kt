package eg.deals.radar.feature.post

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eg.deals.domain.DealCategory
import eg.deals.domain.Governorate
import eg.deals.domain.Money
import eg.deals.radar.auth.AuthManager
import eg.deals.radar.datastore.DeviceIdManager
import eg.deals.radar.network.ApiErrors
import eg.deals.radar.network.LinkPreviewDto
import eg.deals.radar.network.SimilarDealDto
import eg.deals.radar.network.UserInfo
import eg.deals.radar.repository.DealRepository
import eg.deals.radar.repository.UserRepository
import eg.deals.radar.util.AppLanguage
import eg.deals.radar.util.ImageCompressor
import eg.deals.radar.util.ImageHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Online = link to a store; Physical = in-store deal with a location. */
enum class DealType { ONLINE, PHYSICAL }

sealed interface EmailVerificationState {
    object Idle : EmailVerificationState
    data class Loading(val message: String) : EmailVerificationState
    data class Verified(val user: UserInfo) : EmailVerificationState
    data class Error(val message: String) : EmailVerificationState
}

/** Duplicate warnings shown as dialogs on the post screen. */
sealed interface DuplicateWarning {
    /** Same link or photo is already live: can't post, open the existing deal instead. */
    data class Exact(val existing: SimilarDealDto) : DuplicateWarning
    /** Similar title: user can confirm their deal is different. */
    data class Similar(val deals: List<SimilarDealDto>) : DuplicateWarning
}

data class PostUiState(
    val title: String = "",
    val description: String = "",
    val dealType: DealType = DealType.ONLINE,
    val link: String = "",
    val location: String = "",
    val promoCode: String? = null,
    val category: DealCategory = DealCategory.FOOD_DINING,
    val governorate: Governorate = Governorate.ALL_EGYPT,
    val imageUrl: String = "",
    val selectedImageUri: Uri? = null,
    val originalPrice: String = "",
    val discountedPrice: String = "",
    val expiresInDays: Int = 10,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val submitted: Boolean = false,
    val submittedLive: Boolean = false,
    val username: String? = null,
    val verifiedUserId: String? = null,
    val showEmailVerification: Boolean = false,
    val emailVerificationState: EmailVerificationState = EmailVerificationState.Idle,
    // ✨ Egypt 2.0
    val linkPreview: LinkPreviewDto? = null,
    val linkPreviewLoading: Boolean = false,
    val duplicateWarning: DuplicateWarning? = null,
    val errorField: String? = null
)

/**
 * ========================================
 * ✍️ POST A DEAL
 * ========================================
 * - Requires a logged-in account (secure session)
 * - Pasted links are looked up to pre-fill title/price/store
 * - Photo: compressed on-device (EXIF/location stripped), fingerprinted for
 *   duplicate detection, uploaded through a signed URL
 * - Duplicates: exact -> "already posted"; similar -> "is yours different?"
 * - Every backend error is shown as a clear, translated message
 */
class PostViewModel(
    private val context: Context,
    private val repo: DealRepository = DealRepository(),
    private val userRepo: UserRepository = UserRepository()
) : ViewModel() {

    var uiState by mutableStateOf(PostUiState())
        private set

    private val deviceIdManager = DeviceIdManager.getInstance(context)

    // Upload results kept across a "Mine is different" re-submit (no second upload)
    private var uploadedThumbUrl: String? = null
    private var uploadedForUri: Uri? = null
    private var pendingFullImage: File? = null
    private var imageHash: String? = null
    private var previewJob: Job? = null

    init {
        val cachedUsername = deviceIdManager.getUsername()
        val cachedUserId = deviceIdManager.getUserId()
        if (AuthManager.isLoggedIn && cachedUsername != null && cachedUserId != null) {
            uiState = uiState.copy(username = cachedUsername, verifiedUserId = cachedUserId)
            viewModelScope.launch {
                runCatching { if (userRepo.getCachedUser(cachedUserId) == null) userRepo.fetchUserProfile(cachedUserId) }
            }
        }
    }

    // ========================================
    // 🔐 Login (email code)
    // ========================================

    fun showEmailVerification() {
        uiState = uiState.copy(showEmailVerification = true, emailVerificationState = EmailVerificationState.Idle)
    }

    fun hideEmailVerification() {
        uiState = uiState.copy(showEmailVerification = false, emailVerificationState = EmailVerificationState.Idle)
    }

    /** Called by the screen once verification succeeded: continue posting. */
    fun onSignedIn(userId: String, username: String) {
        AuthManager.onLoggedIn(context, userId, username)
        uiState = uiState.copy(
            username = username,
            verifiedUserId = userId,
            showEmailVerification = false,
            emailVerificationState = EmailVerificationState.Idle
        )
        viewModelScope.launch { runCatching { userRepo.fetchUserProfile(userId) } }
        submitDeal()
    }

    /** [activityContext] must be an Activity: Credential Manager shows a system sheet. */
    fun signInWithGoogle(activityContext: Context, consent: Boolean) {
        viewModelScope.launch {
            uiState = uiState.copy(
                emailVerificationState = EmailVerificationState.Loading(AppLanguage.string(R.string.post_verifying_code))
            )
            when (val result = eg.deals.radar.auth.GoogleAuth.signIn(activityContext)) {
                is eg.deals.radar.auth.GoogleAuth.Result.Cancelled ->
                    uiState = uiState.copy(emailVerificationState = EmailVerificationState.Idle)
                is eg.deals.radar.auth.GoogleAuth.Result.NoAccount ->
                    signInFailed(AppLanguage.string(R.string.signin_no_account))
                is eg.deals.radar.auth.GoogleAuth.Result.Failed -> signInFailed(
                    if (eg.deals.radar.auth.GoogleAuth.isConfigured) AppLanguage.string(R.string.signin_failed)
                    else AppLanguage.string(R.string.signin_not_configured)
                )
                is eg.deals.radar.auth.GoogleAuth.Result.Success -> {
                    val response = repo.signInWithGoogle(
                        idToken = result.idToken,
                        nonce = result.rawNonce,
                        deviceId = deviceIdManager.getDeviceId(),
                        consent = consent
                    )
                    val user = response.user
                    uiState = if (response.success && user != null) {
                        uiState.copy(emailVerificationState = EmailVerificationState.Verified(user))
                    } else {
                        uiState.copy(emailVerificationState = EmailVerificationState.Error(
                            response.error ?: AppLanguage.string(R.string.signin_failed)
                        ))
                    }
                }
            }
        }
    }

    private fun signInFailed(message: String) {
        uiState = uiState.copy(emailVerificationState = EmailVerificationState.Error(message))
    }

    // ========================================
    // ✏️ Form fields
    // ========================================

    fun updateTitle(title: String) { uiState = uiState.copy(title = title, error = null, errorField = null) }
    fun updateCategory(category: DealCategory) { uiState = uiState.copy(category = category) }
    fun updateGovernorate(governorate: Governorate) { uiState = uiState.copy(governorate = governorate) }
    fun updateDescription(description: String) { uiState = uiState.copy(description = description, error = null) }
    fun updateLocation(location: String) { uiState = uiState.copy(location = location, error = null, errorField = null) }
    fun updatePromoCode(promoCode: String) { uiState = uiState.copy(promoCode = promoCode.trim().ifBlank { null }, error = null) }
    fun updateOriginalPrice(price: String) { uiState = uiState.copy(originalPrice = Money.sanitizeInput(price), error = null) }
    fun updateDiscountedPrice(price: String) { uiState = uiState.copy(discountedPrice = Money.sanitizeInput(price), error = null) }
    fun updateExpiresInDays(days: Int) { uiState = uiState.copy(expiresInDays = days.coerceIn(1, 30), error = null) }
    fun updateImageUrl(imageUrl: String) { uiState = uiState.copy(imageUrl = imageUrl, error = null, selectedImageUri = null) }

    fun setDealType(type: DealType) {
        val gov = if (type == DealType.ONLINE) Governorate.ALL_EGYPT
        else if (uiState.governorate == Governorate.ALL_EGYPT) Governorate.CAIRO else uiState.governorate
        uiState = uiState.copy(dealType = type, governorate = gov, error = null, errorField = null)
    }

    fun setSelectedImage(uri: Uri) {
        resetUpload()
        uiState = uiState.copy(selectedImageUri = uri, imageUrl = "", error = null)
    }

    fun clearImage() {
        resetUpload()
        uiState = uiState.copy(selectedImageUri = null, error = null)
    }

    fun clearError() {
        if (uiState.error != null) uiState = uiState.copy(error = null, errorField = null)
    }

    fun dismissDuplicateWarning() { uiState = uiState.copy(duplicateWarning = null) }

    /** The user confirmed their deal is different from the similar ones. */
    fun confirmNotDuplicate() {
        uiState = uiState.copy(duplicateWarning = null)
        submitDeal(confirmNotDuplicate = true)
    }

    /** Link typed/pasted: look it up (debounced) to pre-fill empty fields. */
    fun updateLink(link: String) {
        uiState = uiState.copy(link = link, error = null, errorField = null)
        previewJob?.cancel()
        val trimmed = link.trim()
        if (!trimmed.startsWith("https://") || trimmed.length < 12 || !AuthManager.isLoggedIn) {
            uiState = uiState.copy(linkPreview = null, linkPreviewLoading = false)
            return
        }
        previewJob = viewModelScope.launch {
            delay(700)
            uiState = uiState.copy(linkPreviewLoading = true)
            val preview = repo.linkPreview(trimmed)
            if (uiState.link.trim() != trimmed) return@launch
            val previewTitle = preview?.title
            val previewPrice = preview?.price
            uiState = uiState.copy(
                linkPreview = preview,
                linkPreviewLoading = false,
                title = if (uiState.title.isBlank() && !previewTitle.isNullOrBlank()) previewTitle.take(150) else uiState.title,
                // The page price is what you pay now -> pre-fill "discounted" when both are empty
                discountedPrice = if (uiState.discountedPrice.isBlank() && uiState.originalPrice.isBlank() && previewPrice != null)
                    Money.sanitizeInput(if (previewPrice % 1.0 == 0.0) previewPrice.toLong().toString() else previewPrice.toString())
                else uiState.discountedPrice
            )
        }
    }

    private fun resetUpload() {
        uploadedThumbUrl = null
        uploadedForUri = null
        pendingFullImage?.delete()
        pendingFullImage = null
        imageHash = null
    }

    private fun fail(message: String, field: String? = null) {
        uiState = uiState.copy(loading = false, message = null, error = message, errorField = field)
    }

    // ========================================
    // 🚀 Submit
    // ========================================

    fun submitDeal(confirmNotDuplicate: Boolean = false) {
        if (!AuthManager.isLoggedIn || uiState.verifiedUserId == null) {
            showEmailVerification()
            return
        }
        val s = uiState

        // ---- client-side validation (same rules as the server) ----
        val title = s.title.trim()
        when {
            title.isBlank() -> return fail(AppLanguage.string(R.string.post_error_title_required), "title")
            !isValidTitle(title) -> return fail(AppLanguage.string(R.string.post_error_title_invalid), "title")
            s.description.trim().length > 2000 -> return fail(AppLanguage.string(R.string.post_error_description_long), "description")
        }
        when (s.dealType) {
            DealType.ONLINE -> when {
                s.link.isBlank() -> return fail(AppLanguage.string(R.string.post_error_link_required), "link")
                !s.link.trim().startsWith("https://") -> return fail(AppLanguage.string(R.string.post_error_link_scheme), "link")
            }
            DealType.PHYSICAL -> when {
                s.location.isBlank() -> return fail(AppLanguage.string(R.string.post_error_location_required), "location")
                !isValidPlaceName(s.location) -> return fail(AppLanguage.string(R.string.post_error_location_invalid), "location")
            }
        }
        if (s.selectedImageUri == null && uploadedThumbUrl == null) return fail(AppLanguage.string(R.string.post_error_image_required), "image")

        val original = Money.parse(s.originalPrice)
        val discounted = Money.parse(s.discountedPrice)
        if (original != null && discounted != null && discounted >= original) {
            return fail(AppLanguage.string(R.string.post_error_price_order), "discounted_price")
        }

        viewModelScope.launch {
            try {
                uiState = uiState.copy(loading = true, error = null, errorField = null, message = null)

                // ---- photo: compress + fingerprint + signed upload (once) ----
                val uri = uiState.selectedImageUri
                if (uri != null && (uploadedThumbUrl == null || uploadedForUri != uri)) {
                    uiState = uiState.copy(message = AppLanguage.string(R.string.post_progress_compressing))
                    val images = ImageCompressor.compressImageTwoStage(context = context, uri = uri)
                    imageHash = withContext(Dispatchers.Default) { ImageHasher.dHash(images.thumbnail) }

                    // Duplicate pre-check before uploading, so a blocked post leaves no orphan photo
                    if (!confirmNotDuplicate) {
                        val check = repo.checkDuplicate(
                            link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                            title = title,
                            imageHash = imageHash
                        )
                        val dups = check?.duplicates.orEmpty()
                        val warning = when {
                            check?.blocking == true -> dups.firstOrNull { it.matchType == "url" || it.matchType == "image" }
                                ?.let { DuplicateWarning.Exact(it) }
                            dups.isNotEmpty() -> DuplicateWarning.Similar(dups)
                            else -> null
                        }
                        if (warning != null) {
                            images.thumbnail.delete()
                            images.fullImage.delete()
                            uiState = uiState.copy(loading = false, message = null, duplicateWarning = warning)
                            return@launch
                        }
                    }

                    uiState = uiState.copy(message = AppLanguage.string(R.string.post_progress_uploading))
                    uploadedThumbUrl = repo.uploadImage(images.thumbnail, "image/webp") // compressor outputs WebP
                    uploadedForUri = uri
                    images.thumbnail.delete()
                    pendingFullImage?.delete()
                    pendingFullImage = images.fullImage
                }
                val thumbUrl = uploadedThumbUrl ?: return@launch fail(AppLanguage.string(R.string.post_error_image_required), "image")

                // ---- submit ----
                uiState = uiState.copy(message = AppLanguage.string(R.string.post_progress_posting))
                val result = repo.submitDeal(
                    title = title,
                    description = uiState.description.trim().ifBlank { null },
                    link = if (uiState.dealType == DealType.ONLINE) uiState.link.trim() else null,
                    imageUrl = thumbUrl,
                    location = if (uiState.dealType == DealType.PHYSICAL) uiState.location.trim() else null,
                    category = uiState.category.id,
                    promoCode = uiState.promoCode?.trim()?.ifBlank { null },
                    expiresInDays = uiState.expiresInDays,
                    originalPrice = original,
                    discountedPrice = discounted,
                    governorate = uiState.governorate.id,
                    imageHash = imageHash,
                    confirmNotDuplicate = confirmNotDuplicate
                )

                val deal = result.data?.firstOrNull()
                if (result.success == true && deal != null) {
                    val live = result.status == "approved" || deal.status == "approved"
                    uiState = uiState.copy(
                        loading = false,
                        submitted = true,
                        submittedLive = live,
                        message = AppLanguage.string(if (live) R.string.post_result_published else R.string.post_result_review)
                    )
                    // Upgrade to the full-quality photo in the background
                    val full = pendingFullImage
                    val dealId = deal.id
                    if (full != null && dealId != null) {
                        pendingFullImage = null
                        viewModelScope.launch(Dispatchers.IO) {
                            runCatching {
                                val fullUrl = repo.uploadImage(full, "image/webp")
                                repo.updateDealImage(dealId, fullUrl)
                            }.onFailure { Log.w("PostViewModel", "Full image upgrade failed: ${it.javaClass.simpleName}") }
                            full.delete()
                        }
                    }
                    return@launch
                }

                when (result.code) {
                    ApiErrors.DUPLICATE_DEAL -> {
                        uiState = uiState.copy(loading = false, message = null,
                            duplicateWarning = result.existing?.let { DuplicateWarning.Exact(it) })
                        if (result.existing == null) fail(ApiErrors.message(result))
                    }
                    ApiErrors.POSSIBLE_DUPLICATE -> uiState = uiState.copy(
                        loading = false, message = null,
                        duplicateWarning = DuplicateWarning.Similar(result.similar.orEmpty())
                    )
                    ApiErrors.UNAUTHORIZED -> {
                        uiState = uiState.copy(loading = false, message = null, verifiedUserId = null)
                        showEmailVerification()
                    }
                    ApiErrors.LINK_BLOCKED -> fail(ApiErrors.message(result), "link")
                    else -> fail(ApiErrors.message(result, ApiErrors.Context.POST), result.field)
                }
            } catch (e: Exception) {
                Log.w("PostViewModel", "Submit failed: ${e.javaClass.simpleName}")
                fail(e.message?.takeIf { it.isNotBlank() && e !is java.io.IOException } ?: ApiErrors.message(e))
            }
        }
    }

    override fun onCleared() {
        pendingFullImage?.delete()
        super.onCleared()
    }

    // ========================================
    // ✅ Validation (mirrors the server rules)
    // ========================================

    private fun isValidPlaceName(place: String): Boolean {
        val trimmed = place.trim()
        if (trimmed.length < 3 || trimmed.length > 200) return false
        val urlPatterns = listOf("http://", "https://", "www.", ".com", ".eg", ".net", ".org")
        if (urlPatterns.any { trimmed.lowercase().contains(it) }) return false
        if (trimmed.matches(Regex("^[0-9]+$"))) return false
        return true
    }

    private fun isValidTitle(title: String): Boolean {
        if (title.length < 5 || title.length > 150) return false
        if (listOf("http://", "https://", "www.").any { title.lowercase().contains(it) }) return false
        return !title.all { it.isWhitespace() || it.isISOControl() }
    }
}

class PostViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PostViewModel::class.java)) return PostViewModel(context) as T
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
