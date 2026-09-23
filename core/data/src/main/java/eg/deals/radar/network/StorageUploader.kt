package eg.deals.radar.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ========================================
 * 📤 SECURE PHOTO UPLOAD
 * ========================================
 * 1. Ask the backend for a short-lived signed upload URL (requires login).
 *    The file lands in the user's own folder: deals/images/<user>/<uuid>.jpg
 * 2. PUT the file to that URL.
 * The public key can no longer upload anything by itself.
 */
object StorageUploader {

    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Uploads [file] and returns its public URL. Throws with a user-friendly message on failure. */
    suspend fun uploadImage(file: File, contentType: String = "image/jpeg"): String = withContext(Dispatchers.IO) {
        val ticket = try {
            NetworkModule.api.createUploadUrl(UploadUrlRequest(contentType))
        } catch (e: Exception) {
            throw Exception(ApiErrors.message(e), e)
        }
        val uploadUrl = ticket.uploadUrl
        val publicUrl = ticket.publicUrl
        if (ticket.success != true || uploadUrl == null || publicUrl == null) {
            throw Exception(ApiErrors.message(ticket.code, ticket.error, ticket.retryAfter))
        }

        val request = Request.Builder()
            .url(uploadUrl)
            .put(file.asRequestBody((ticket.contentType ?: contentType).toMediaType()))
            .build()
        try {
            uploadClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) throw Exception(ApiErrors.message(ApiErrors.SERVER_ERROR))
            }
        } catch (e: java.io.IOException) {
            throw Exception(ApiErrors.message(e), e)
        }
        publicUrl
    }
}
