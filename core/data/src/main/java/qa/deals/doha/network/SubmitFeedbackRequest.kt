package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request DTO for submitting user feedback
 *
 * CREATED: 2025-11-22
 *
 * @param deviceId Device ID of the user submitting feedback
 * @param feedbackText The actual feedback content (max 500 chars)
 * @param userId Optional user ID if authenticated
 * @param email Optional email if user wants a response
 */
data class SubmitFeedbackRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("feedback_text") val feedbackText: String,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("email") val email: String? = null
)

/**
 * Response DTO for feedback submission
 *
 * @param id Feedback ID
 * @param createdAt Timestamp when feedback was submitted
 */
data class FeedbackData(
    @SerializedName("id") val id: String,
    @SerializedName("created_at") val createdAt: String
)
