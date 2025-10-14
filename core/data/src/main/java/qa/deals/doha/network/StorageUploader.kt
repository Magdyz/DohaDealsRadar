package qa.deals.doha.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

object StorageUploader {

    private const val STORAGE_URL = "https://nzchbnshkrkdqpcawohu.supabase.co/storage/v1/object/deals/images"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56Y2hibnNoa3JrZHFwY2F3b2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAxODE3ODMsImV4cCI6MjA3NTc1Nzc4M30.rBl_9k6kd3ICQCD0Th8ysUu6YGozYGC12Pjl_Ra01l0"

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
                val publicUrl = "https://nzchbnshkrkdqpcawohu.supabase.co/storage/v1/object/public/deals/images/$fileName"
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