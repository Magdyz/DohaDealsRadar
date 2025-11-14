package qa.deals.doha.util

import android.util.Log
import qa.deals.doha.core.data.BuildConfig

/**
 * 🔐 Secure Logger - Prevents sensitive data leaks in production
 *
 * SECURITY FEATURES:
 * 1. Automatically strips logs in release builds
 * 2. Sanitizes PII (Personally Identifiable Information)
 * 3. Provides safe alternatives for production debugging
 *
 * USAGE:
 * Replace all `Log.d()` calls with `SecureLogger.d()`
 *
 * Created: 2025-11-14
 * Security Audit: Critical Fix #2
 */
object SecureLogger {

    private const val MAX_LOG_LENGTH = 4000  // Android's log limit

    /**
     * Debug log - Only in debug builds
     * Completely stripped from release builds by ProGuard
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /**
     * Info log - Only in debug builds
     */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    /**
     * Warning log - Allowed in production (for crash reporting)
     * But sanitizes PII
     */
    fun w(tag: String, message: String) {
        Log.w(tag, sanitize(message))
    }

    /**
     * Error log - Allowed in production (for crash reporting)
     * But sanitizes PII
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, sanitize(message), throwable)
        } else {
            Log.e(tag, sanitize(message))
        }
    }

    /**
     * PII-safe log - Redacts sensitive information
     * Use for user IDs, emails, device IDs, etc.
     *
     * Example:
     * - Input: "User ID: abc123def456"
     * - Debug: "User ID: abc123def456"
     * - Release: "User ID: abc***f456"
     */
    fun pii(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            // Full logging in debug
            Log.d(tag, "[PII] $message")
        } else {
            // Redacted in production
            Log.i(tag, "[PII] ${redact(message)}")
        }
    }

    /**
     * Sanitize message - Remove common PII patterns
     */
    private fun sanitize(message: String): String {
        if (BuildConfig.DEBUG) return message

        var sanitized = message

        // Redact email addresses
        sanitized = sanitized.replace(
            Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            "***@***.***"
        )

        // Redact phone numbers
        sanitized = sanitized.replace(
            Regex("\\+?[0-9]{10,15}"),
            "+***********"
        )

        // Redact UUIDs
        sanitized = sanitized.replace(
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "********-****-****-****-************"
        )

        // Redact JWT tokens
        sanitized = sanitized.replace(
            Regex("eyJ[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*"),
            "eyJ***TOKEN_REDACTED***"
        )

        return sanitized
    }

    /**
     * Redact PII - Show only first/last chars
     * Example: "abc123def456" -> "abc***456"
     */
    private fun redact(message: String): String {
        if (message.length <= 8) return "***"

        val visible = 3
        return "${message.take(visible)}***${message.takeLast(visible)}"
    }

    /**
     * Safe network log - Redacts auth headers and sensitive params
     */
    fun network(tag: String, url: String, method: String = "GET", statusCode: Int? = null) {
        if (!BuildConfig.DEBUG) return

        val sanitizedUrl = url.replace(
            Regex("(apikey|token|key|secret)=[^&]+"),
            "$1=***"
        )

        val message = buildString {
            append("🌐 $method $sanitizedUrl")
            if (statusCode != null) {
                append(" → $statusCode")
            }
        }

        Log.d(tag, message)
    }
}
