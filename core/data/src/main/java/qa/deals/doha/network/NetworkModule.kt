package qa.deals.doha.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import qa.deals.doha.core.data.BuildConfig // ✅ THIS IS THE FIX

object NetworkModule {

    // TODO: Move to BuildConfig or gradle.properties for security
    private const val SUPABASE_URL = BuildConfig.SUPABASE_URL
    internal const val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
      //  level = HttpLoggingInterceptor.Level.BODY
        // ✅ PRODUCTION: Only log in debug builds
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(SUPABASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: SupabaseApiService = retrofit.create(SupabaseApiService::class.java)
}