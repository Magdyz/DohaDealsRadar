package qa.deals.doha.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkModule {

    // TODO: Move to BuildConfig or gradle.properties for security
    private const val SUPABASE_URL = "https://nzchbnshkrkdqpcawohu.functions.supabase.co/"
    internal const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56Y2hibnNoa3JrZHFwY2F3b2h1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAxODE3ODMsImV4cCI6MjA3NTc1Nzc4M30.rBl_9k6kd3ICQCD0Th8ysUu6YGozYGC12Pjl_Ra01l0"

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
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