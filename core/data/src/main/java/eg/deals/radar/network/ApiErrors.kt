package eg.deals.radar.network

import eg.deals.radar.core.data.R
import eg.deals.radar.util.AppLanguage
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * ========================================
 * 💬 ONE PLACE FOR ERROR MESSAGES
 * ========================================
 * Turns backend error codes and exceptions into short, human messages in the
 * app language (EN / AR). Screens never show raw exception text.
 */
object ApiErrors {
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val BANNED = "BANNED"
    const val NOT_FOUND = "NOT_FOUND"
    const val VALIDATION = "VALIDATION"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val DUPLICATE_DEAL = "DUPLICATE_DEAL"
    const val POSSIBLE_DUPLICATE = "POSSIBLE_DUPLICATE"
    const val LINK_BLOCKED = "LINK_BLOCKED"
    const val OWN_DEAL = "OWN_DEAL"
    const val ALREADY_DONE = "ALREADY_DONE"
    const val DEAL_UNAVAILABLE = "DEAL_UNAVAILABLE"
    const val INVALID_CODE = "INVALID_CODE"
    const val SERVER_ERROR = "SERVER_ERROR"
    const val NETWORK = "NETWORK"

    /** Message for an error envelope. `context` refines generic cases. */
    fun message(envelope: ApiEnvelope<*>?, context: Context = Context.GENERIC): String =
        message(envelope?.code, envelope?.error, envelope?.retryAfter, envelope?.field, envelope?.limit, context)

    fun message(
        code: String?,
        serverText: String? = null,
        retryAfter: Int? = null,
        field: String? = null,
        limit: Int? = null,
        context: Context = Context.GENERIC
    ): String = when (code) {
        UNAUTHORIZED -> s(R.string.err_unauthorized)
        BANNED -> s(R.string.err_banned)
        FORBIDDEN -> s(R.string.err_forbidden)
        NOT_FOUND -> if (context == Context.DEAL) s(R.string.err_deal_not_found) else s(R.string.err_not_found)
        RATE_LIMITED -> when {
            context == Context.POST && limit != null -> s(R.string.err_rate_post, limit, waitText(retryAfter))
            else -> s(R.string.err_rate_generic, waitText(retryAfter))
        }
        DUPLICATE_DEAL -> s(R.string.err_duplicate)
        POSSIBLE_DUPLICATE -> s(R.string.err_possible_duplicate)
        LINK_BLOCKED -> s(R.string.err_link_blocked)
        OWN_DEAL -> if (context == Context.REPORT) s(R.string.err_own_deal_report) else s(R.string.err_own_deal_vote)
        ALREADY_DONE -> when (context) {
            Context.REPORT -> s(R.string.err_already_reported)
            Context.EXPIRED -> s(R.string.err_already_marked_expired)
            else -> s(R.string.err_already_done)
        }
        DEAL_UNAVAILABLE -> s(R.string.err_deal_unavailable)
        INVALID_CODE -> s(R.string.err_invalid_code)
        VALIDATION -> validationMessage(field, serverText)
        NETWORK -> s(R.string.err_network)
        else -> s(R.string.err_server)
    }

    /** Message for a thrown exception (no connection, timeout, server down...). */
    fun message(t: Throwable): String = when (t) {
        is UnknownHostException, is java.net.ConnectException -> s(R.string.err_network)
        is SocketTimeoutException -> s(R.string.err_timeout)
        is IOException -> s(R.string.err_network)
        is HttpException -> if (t.code() == 401) s(R.string.err_unauthorized) else s(R.string.err_server)
        else -> s(R.string.err_server)
    }

    fun isNetwork(t: Throwable) = t is IOException

    private fun validationMessage(field: String?, serverText: String?): String = when (field) {
        "title" -> s(R.string.err_field_title)
        "description" -> s(R.string.err_field_description)
        "link" -> s(R.string.err_field_link)
        "location" -> s(R.string.err_field_location)
        "image" -> s(R.string.err_field_image)
        "original_price", "discounted_price" -> s(R.string.err_field_price)
        "email" -> s(R.string.err_field_email)
        "code" -> s(R.string.err_invalid_code)
        "consent" -> s(R.string.err_field_consent)
        "note" -> s(R.string.err_field_note)
        "reason" -> s(R.string.err_field_reason)
        "username" -> s(R.string.err_field_username)
        "feedback_text" -> s(R.string.err_field_feedback)
        // English users see the precise server text; Arabic users a translated generic line
        else -> if (!AppLanguage.isArabic() && !serverText.isNullOrBlank()) serverText else s(R.string.err_validation)
    }

    private fun waitText(seconds: Int?): String {
        val secs = seconds ?: 60
        return when {
            secs < 90 -> s(R.string.wait_minute)
            secs < 3600 -> s(R.string.wait_minutes, (secs + 59) / 60)
            else -> s(R.string.wait_hours, (secs + 3599) / 3600)
        }
    }

    private fun s(id: Int, vararg args: Any) = AppLanguage.string(id, *args)

    enum class Context { GENERIC, POST, VOTE, REPORT, DEAL, EXPIRED, LOGIN }
}
