package qa.deals.doha.network

/**
 * Generic API envelope used by Supabase Edge Functions.
 * Use this instead of the old ApiResponse to avoid type conflicts.
 */
data class ApiEnvelope<T>(
    val success: Boolean? = null,
    val message: String? = null,
    val error: String? = null,
    val data: T? = null
)
