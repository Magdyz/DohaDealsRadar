package eg.deals.domain

/**
 * ========================================
 * ✨ DEAL CATEGORIES - SHARED DOMAIN MODEL
 * ========================================
 *
 * Created: 2025-10-19 20:00:15 UTC by @Magdyz
 *
 * Moved from feature/post to core/domain for shared access.
 * Used by both feed filtering and deal posting.
 *
 * `id` is stored in the database and used for FCM topics (cat_{id}),
 * so it must never change. Keep in sync with:
 * - supabase/functions/submit_deal (validCategories)
 * - supabase/functions/send_notification (category names/emojis)
 *
 * CATEGORIES:
 * - Food & Dining (🍔)
 * - Groceries & Supermarkets (🛒)
 * - Electronics & Mobiles (📱)
 * - Shopping & Fashion (🛍️)
 * - Telecom & Internet (📶)
 * - Entertainment & Outings (🎮)
 * - Home & Services (🏠)
 * - Other (⭐)
 */
enum class DealCategory(
    val id: String,
    val emoji: String,
    val displayName: String,
    val arabicName: String
) {
    FOOD_DINING("food_dining", "🍔", "Food & Dining", "أكل ومطاعم"),
    GROCERIES("groceries", "🛒", "Groceries & Supermarkets", "بقالة وسوبر ماركت"),
    ELECTRONICS("electronics", "📱", "Electronics & Mobiles", "إلكترونيات وموبايلات"),
    SHOPPING_FASHION("shopping_fashion", "🛍️", "Shopping & Fashion", "تسوق وموضة"),
    TELECOM("telecom", "📶", "Telecom & Internet", "اتصالات وإنترنت"),
    ENTERTAINMENT("entertainment", "🎮", "Entertainment & Outings", "ترفيه وخروجات"),
    HOME_SERVICES("home_services", "🏠", "Home & Services", "البيت والخدمات"),
    OTHER("other", "⭐", "Other", "أخرى");

    /** Name in the current app language. */
    fun label(isArabic: Boolean): String = if (isArabic) arabicName else displayName

    companion object {
        fun fromId(id: String): DealCategory {
            return values().find { it.id == id } ?: OTHER
        }
    }
}
