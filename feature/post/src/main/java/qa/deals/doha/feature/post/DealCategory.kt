package qa.deals.doha.feature.post

/**
 * ✨ Deal Categories (2025)
 * Required field for all deals
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