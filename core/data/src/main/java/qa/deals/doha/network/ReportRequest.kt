package qa.deals.doha.network

/**
 * Report reasons - EXACTLY matching database enum values
 * Database has: expired, other, scam, spam
 */
enum class ReportReason(val displayName: String, val value: String) {
    SPAM("Spam or Advertising", "spam"),
    SCAM("Scam or Fraud", "scam"),
    EXPIRED("Deal Expired or Invalid", "expired"),
    OTHER("Other", "other")
}

/**
 * Request body for reporting a deal
 */
data class ReportRequest(
    val deal_id: String,
    val device_id: String,
    val reason: String,
    val note: String? = null
)