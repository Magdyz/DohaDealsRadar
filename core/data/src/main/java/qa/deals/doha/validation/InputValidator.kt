package qa.deals.doha.validation

/**
 * 🛡️ Input Validator - Prevents XSS, injection, and malicious content
 *
 * SECURITY FEATURES:
 * 1. HTML/Script tag stripping (XSS prevention)
 * 2. URL validation (phishing prevention)
 * 3. SQL injection character blocking
 * 4. Length limits (DoS prevention)
 *
 * USAGE:
 * - Call before saving user input to database
 * - Call before displaying user content
 * - Call before making API calls
 *
 * Created: 2025-11-14
 * Security Audit: Critical Fix #4
 */
object InputValidator {

    // Length limits (prevent DoS)
    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_DESCRIPTION_LENGTH = 2000
    private const val MAX_LOCATION_LENGTH = 300
    private const val MAX_PROMO_CODE_LENGTH = 50
    private const val MAX_USERNAME_LENGTH = 30

    /**
     * Sanitize title - Remove HTML, scripts, and dangerous characters
     */
    fun sanitizeTitle(input: String): String {
        return input
            .trim()
            .take(MAX_TITLE_LENGTH)
            .stripHtmlTags()
            .removeDangerousChars()
            .normalizeWhitespace()
    }

    /**
     * Sanitize description - Allow some formatting but prevent XSS
     */
    fun sanitizeDescription(input: String): String {
        return input
            .trim()
            .take(MAX_DESCRIPTION_LENGTH)
            .stripScriptTags()  // Remove <script> but allow basic formatting
            .removeDangerousChars()
            .normalizeWhitespace()
    }

    /**
     * Sanitize location - Remove all HTML, keep text only
     */
    fun sanitizeLocation(input: String): String {
        return input
            .trim()
            .take(MAX_LOCATION_LENGTH)
            .stripHtmlTags()
            .removeDangerousChars()
            .normalizeWhitespace()
    }

    /**
     * Sanitize promo code - Uppercase, alphanumeric only
     */
    fun sanitizePromoCode(input: String): String {
        return input
            .trim()
            .uppercase()
            .take(MAX_PROMO_CODE_LENGTH)
            .replace(Regex("[^A-Z0-9-]"), "")  // Only letters, numbers, hyphens
    }

    /**
     * Sanitize username - Alphanumeric + underscores only
     */
    fun sanitizeUsername(input: String): String {
        return input
            .trim()
            .lowercase()
            .take(MAX_USERNAME_LENGTH)
            .replace(Regex("[^a-z0-9_]"), "")  // Only lowercase letters, numbers, underscores
            .takeIf { it.isNotEmpty() } ?: "user${System.currentTimeMillis() % 10000}"
    }

    /**
     * Validate URL - Only allow HTTPS URLs, block dangerous schemes
     *
     * Returns:
     * - ValidationResult.Valid if URL is safe
     * - ValidationResult.Invalid with reason if URL is dangerous
     */
    fun validateURL(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Valid  // Empty is OK (optional field)
        }

        val trimmedUrl = url.trim().lowercase()

        // Block dangerous schemes
        val dangerousSchemes = listOf(
            "javascript:",
            "data:",
            "file:",
            "vbscript:",
            "about:",
            "chrome:",
            "res:",
            "content:"
        )

        for (scheme in dangerousSchemes) {
            if (trimmedUrl.startsWith(scheme)) {
                return ValidationResult.Invalid("URL scheme '$scheme' is not allowed")
            }
        }

        // Only allow HTTP/HTTPS
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return ValidationResult.Invalid("Only HTTP/HTTPS URLs are allowed")
        }

        // Validate URL structure
        val urlPattern = Regex(
            "^https?://[a-zA-Z0-9-.]+(:[0-9]{1,5})?(/[^\\s]*)?$",
            RegexOption.IGNORE_CASE
        )

        if (!urlPattern.matches(url)) {
            return ValidationResult.Invalid("Invalid URL format")
        }

        // Check for suspicious patterns
        val suspiciousPatterns = listOf(
            "..",          // Directory traversal
            "%00",         // Null byte injection
            "<script",     // XSS attempt
            "onclick=",    // Event handler injection
            "onerror=",
            "onload="
        )

        for (pattern in suspiciousPatterns) {
            if (trimmedUrl.contains(pattern)) {
                return ValidationResult.Invalid("URL contains suspicious pattern: $pattern")
            }
        }

        return ValidationResult.Valid
    }

    /**
     * Validate email - Basic RFC 5322 validation
     */
    fun validateEmail(email: String): ValidationResult {
        if (email.isBlank()) {
            return ValidationResult.Invalid("Email cannot be empty")
        }

        val emailPattern = Regex(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
        )

        if (!emailPattern.matches(email.trim())) {
            return ValidationResult.Invalid("Invalid email format")
        }

        return ValidationResult.Valid
    }

    // ============================================
    // Private Helper Functions
    // ============================================

    /**
     * Strip all HTML tags
     */
    private fun String.stripHtmlTags(): String {
        return this.replace(Regex("<[^>]*>"), "")
    }

    /**
     * Strip only script tags (but allow basic formatting)
     */
    private fun String.stripScriptTags(): String {
        return this
            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<iframe[^>]*>.*?</iframe>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<object[^>]*>.*?</object>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<embed[^>]*>.*?</embed>", RegexOption.IGNORE_CASE), "")
    }

    /**
     * Remove dangerous characters that could be used for injection
     */
    private fun String.removeDangerousChars(): String {
        return this
            .replace(Regex("[<>\"']"), "")  // Remove common XSS chars
            .replace(Regex("\\\\"), "")     // Remove backslash (escape char)
    }

    /**
     * Normalize whitespace - Convert multiple spaces to single space
     */
    private fun String.normalizeWhitespace(): String {
        return this
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Validation result sealed class
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()

        fun isValid(): Boolean = this is Valid
        fun getErrorMessage(): String? = (this as? Invalid)?.reason
    }
}
