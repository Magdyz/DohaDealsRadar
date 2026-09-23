package eg.deals.radar.network

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Client-side crash/error beacon (report_client_error edge function).
 * Kept separate from [SupabaseApiService] so ErrorReporter has its own,
 * intentionally tiny, fire-and-forget surface.
 */
interface ClientErrorApi {
    @POST("report_client_error")
    suspend fun reportClientError(@Body body: ClientErrorReportRequest): ApiEnvelope<Unit>
}

data class ClientErrorReportRequest(
    val kind: String, // "crash" | "error"
    val area: String,
    val code: String?,
    @com.google.gson.annotations.SerializedName("app_version") val appVersion: String?,
    @com.google.gson.annotations.SerializedName("os_version") val osVersion: String?
)
