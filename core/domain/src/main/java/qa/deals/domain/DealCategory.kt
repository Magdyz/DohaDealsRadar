package qa.deals.domain  // ✅ CORRECT

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
 * CATEGORIES:
 * - Food & Dining (🍔)
 * - Shopping & Fashion (🛍️)
 * - Entertainment & Leisure (🎮)
 * - Home & Services (🏠)
 * - Other (⭐)
 */
enum class DealCategory(
    val id: String,
    val emoji: String,
    val displayName: String
) {
    FOOD_DINING("food_dining", "🍔", "Food & Dining"),
    SHOPPING_FASHION("shopping_fashion", "🛍️", "Shopping & Fashion"),
    ENTERTAINMENT("entertainment", "🎮", "Entertainment & Leisure"),
    HOME_SERVICES("home_services", "🏠", "Home & Services"),
    OTHER("other", "⭐", "Other");

    companion object {
        fun fromId(id: String): DealCategory {
            return values().find { it.id == id } ?: OTHER
        }
    }
}