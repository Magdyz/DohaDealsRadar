package eg.deals.domain

import java.util.Locale

/**
 * ========================================
 * 💷 MONEY - Egyptian Pound (EGP) helpers
 * ========================================
 *
 * All prices in the app are Egyptian Pounds.
 *
 * Display:
 * - English: "EGP 1,995"      (currency first)
 * - Arabic:  "1,995 ج.م"      (currency after the amount, as written in Egypt)
 * Whole pounds are shown without decimals; piasters only when present.
 *
 * Input:
 * - Accepts Western (0-9) and Arabic-Indic (٠-٩ / ۰-۹) digits
 * - Accepts "," / "٬" as thousands separators and "." / "٫" as decimal point
 */
object Money {
    const val CURRENCY_CODE = "EGP"
    const val SYMBOL_EN = "EGP"
    const val SYMBOL_AR = "ج.م"

    /** Currency label for price input fields. */
    fun symbol(isArabic: Boolean): String = if (isArabic) SYMBOL_AR else SYMBOL_EN

    /**
     * Format a price for display.
     * - 1995.0  -> "EGP 1,995" / "1,995 ج.م"
     * - 19.5    -> "EGP 19.50" / "19.50 ج.م"
     */
    fun format(price: Double, isArabic: Boolean): String {
        val amount = if (price % 1.0 == 0.0) {
            String.format(Locale.US, "%,.0f", price)
        } else {
            String.format(Locale.US, "%,.2f", price)
        }
        // U+200F (RLM) keeps "number + ج.م" in the right order inside RTL text
        return if (isArabic) "$amount $SYMBOL_AR‏" else "$SYMBOL_EN $amount"
    }

    /**
     * Convert Arabic-Indic / Persian digits and Arabic separators to their
     * Western equivalents so the value can be parsed.
     * "١٬٩٩٥٫٥٠" -> "1,995.50"
     */
    fun normalizeDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    in '٠'..'٩' -> '0' + (ch - '٠') // Arabic-Indic
                    in '۰'..'۹' -> '0' + (ch - '۰') // Persian
                    '٫' -> '.' // Arabic decimal separator
                    '٬', '،' -> ',' // Arabic thousands separator / Arabic comma
                    else -> ch
                }
            )
        }
    }

    /**
     * Keep only characters valid in a price while typing (after digit normalization).
     */
    fun sanitizeInput(input: String): String =
        normalizeDigits(input).filter { it in '0'..'9' || it == '.' || it == ',' }

    /** Parse a user-entered price; null when blank or invalid. */
    fun parse(input: String): Double? {
        if (input.isBlank()) return null
        return normalizeDigits(input).replace(",", "").trim().toDoubleOrNull()
    }
}
