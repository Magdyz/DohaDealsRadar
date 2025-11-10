package qa.deals.doha.network

import com.google.gson.annotations.SerializedName

/**
 * Request to permanently delete a deal and its image from database
 * Admin-only endpoint that:
 * - Deletes the deal record from database
 * - Deletes the image file from storage
 * - Cannot be undone
 */

data class PermanentDeleteDealRequest(
    @SerializedName("admin_user_id")
    val userId: String,

    @SerializedName("deal_id")
    val dealId: String
)