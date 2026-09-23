package eg.deals.radar.network

import android.util.Log
import eg.deals.radar.auth.Session
import eg.deals.radar.auth.SessionStore
import eg.deals.radar.core.data.BuildConfig
import eg.deals.radar.datastore.DeviceIdManager
import eg.deals.radar.util.AppContext
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ========================================
 * 🌐 NETWORK MODULE
 * ========================================
 * - Sends the user's session token when logged in (anon key otherwise);
 *   the backend identifies callers ONLY from this token.
 * - Refreshes the session before it expires and again on HTTP 401.
 *   If refreshing fails the user is logged out (SessionStore.expire()).
 * - Backend errors come back as JSON { success:false, code, error, ... } with
 *   4xx/5xx status. They're passed to Retrofit as normal bodies so every
 *   screen can read `code` and show a clear, translated message.
 */
object NetworkModule {
    private const val TAG = "NetworkModule"
    private const val SUPABASE_URL = BuildConfig.SUPABASE_URL
    internal const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
    private const val PROJECT_URL = BuildConfig.SUPABASE_PROJECT_URL

    private val refreshLock = Any()

    private val plainClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun deviceId(): String? = runCatching {
        DeviceIdManager.getInstance(AppContext.appContext).getDeviceId()
    }.getOrNull()

    /**
     * Exchanges the refresh token for a new session. Returns null when the
     * refresh token is no longer valid (user must log in again).
     */
    fun refreshSession(old: Session): Session? = synchronized(refreshLock) {
        // Another thread may already have refreshed
        SessionStore.current()?.let { if (it.accessToken != old.accessToken && !it.isExpiringSoon) return it }
        val body = JSONObject().put("refresh_token", old.refreshToken).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$PROJECT_URL/auth/v1/token?grant_type=refresh_token")
            .header("apikey", SUPABASE_ANON_KEY)
            .post(body)
            .build()
        return try {
            plainClient.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    if (res.code in 400..499) SessionStore.expire() // refresh token revoked / expired
                    null
                } else {
                    val json = JSONObject(text)
                    Session(
                        accessToken = json.getString("access_token"),
                        refreshToken = json.getString("refresh_token"),
                        expiresAt = json.optLong("expires_at", System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
                    ).also { SessionStore.save(it) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Session refresh failed (network): ${e.javaClass.simpleName}")
            null // keep the session; we'll try again on the next request
        }
    }

    private val authInterceptor = Interceptor { chain ->
        var session = SessionStore.current()
        if (session != null && session.isExpiringSoon) session = refreshSession(session) ?: SessionStore.current()
        val token = session?.accessToken ?: SUPABASE_ANON_KEY
        val builder = chain.request().newBuilder()
            .header("apikey", SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
        deviceId()?.let { builder.header("x-device-id", it) }
        chain.proceed(builder.build())
    }

    /** On 401 with a user token: refresh once and retry. */
    private val tokenAuthenticator = Authenticator { _, response ->
        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val session = SessionStore.current() ?: return@Authenticator null
        if (sentToken == SUPABASE_ANON_KEY) return@Authenticator null
        if (response.priorResponse != null) {            // already retried once
            SessionStore.expire()
            return@Authenticator null
        }
        val fresh = if (sentToken != session.accessToken) session else refreshSession(session)
        fresh?.let {
            response.request.newBuilder().header("Authorization", "Bearer ${it.accessToken}").build()
        }
    }

    /**
     * Lets Retrofit parse error bodies: backend JSON errors are returned with
     * HTTP 200 to the converter (the original status is kept in `http_status`).
     */
    private val errorBodyInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) return@Interceptor response
        val contentType = response.body?.contentType()
        val text = response.peekBody(256 * 1024).string()
        val isEnvelope = contentType?.subtype == "json" && text.contains("\"success\"")
        if (!isEnvelope) return@Interceptor response
        val patched = runCatching { JSONObject(text).put("http_status", response.code).toString() }.getOrDefault(text)
        response.newBuilder()
            .code(200)
            .message("OK")
            .body(patched.toResponseBody(contentType))
            .build()
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Headers (tokens) are never logged; bodies only in debug builds
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
        redactHeader("apikey")
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(errorBodyInterceptor)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(SUPABASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: SupabaseApiService = retrofit.create(SupabaseApiService::class.java)
}
