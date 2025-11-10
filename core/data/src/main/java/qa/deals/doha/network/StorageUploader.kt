package qa.deals.doha.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import qa.deals.doha.core.data.BuildConfig
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object StorageUploader {

    // ✅ SECURITY IMPROVEMENT: Credentials now loaded from BuildConfig (configured in build.gradle.kts)
    // Previously these were hardcoded here - now they come from local.properties (not committed to git)
    // NO LOGIC CHANGE: Same values, just different source for better security

    private val STORAGE_URL = BuildConfig.SUPABASE_STORAGE_URL
    private val ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
    private val PUBLIC_BASE_URL = BuildConfig.SUPABASE_PUBLIC_URL
    // ✅ NO LOGGING - Clean logs
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun uploadImage(file: File): String = withContext(Dispatchers.IO) {
        val fileName = "${UUID.randomUUID()}.jpg"
        val url = "$STORAGE_URL/$fileName"

        Log.d("StorageUploader", "📤 START: Uploading ${file.length() / 1024}KB to Supabase")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                file.asRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer $ANON_KEY")
            .build()

        try {
            Log.d("StorageUploader", "⏳ Sending request...")
            val response = client.newCall(request).execute()

            Log.d("StorageUploader", "📥 RESPONSE CODE: ${response.code}")
            Log.d("StorageUploader", "📥 RESPONSE MESSAGE: ${response.message}")
            Log.d("StorageUploader", "📥 IS SUCCESSFUL: ${response.isSuccessful}")

            if (response.isSuccessful) {
                // ✅ SECURITY IMPROVEMENT: Using configurable PUBLIC_BASE_URL instead of hardcoded value
                val publicUrl = "$PUBLIC_BASE_URL/$fileName"
                Log.d("StorageUploader", "✅ SUCCESS! URL: $publicUrl")
                publicUrl
            } else {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e("StorageUploader", "❌ FAILED! Code: ${response.code}")
                Log.e("StorageUploader", "❌ Error: $errorBody")
                throw Exception("Upload failed: ${response.code} - $errorBody")
            }
        } catch (e: Exception) {
            Log.e("StorageUploader", "💥 EXCEPTION!", e)
            throw e
        }
    }
}