package eg.deals.radar.network

/**
 * Report reasons - EXACTLY matching database enum values
 * Database has: expired, other, scam, spam
 * `value` is sent to the backend; names are UI only (admin UI uses displayName).
 */
enum class ReportReason(val displayName: String, val value: String, val arabicName: String) {
    SPAM("Spam or Advertising", "spam", "سبام أو إعلان"),
    SCAM("Scam or Fraud", "scam", "نصب أو احتيال"),
    EXPIRED("Deal Expired or Invalid", "expired", "العرض خلص أو مش صحيح"),
    OTHER("Other", "other", "سبب تاني");

    /** Name in the current app language. */
    fun label(isArabic: Boolean): String = if (isArabic) arabicName else displayName
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
