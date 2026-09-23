package eg.deals.core_domain

import eg.deals.domain.DealCategory
import eg.deals.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun format_english_wholePounds() {
        assertEquals("EGP 1,995", Money.format(1995.0, isArabic = false))
    }

    @Test
    fun format_english_withPiasters() {
        assertEquals("EGP 19.50", Money.format(19.5, isArabic = false))
    }

    @Test
    fun format_arabic_currencyAfterAmount() {
        assertEquals("24,999 ج.م‏", Money.format(24999.0, isArabic = true))
    }

    @Test
    fun parse_westernDigits() {
        assertEquals(1995.5, Money.parse("1,995.50")!!, 0.0001)
    }

    @Test
    fun parse_arabicIndicDigitsAndSeparators() {
        assertEquals(1995.5, Money.parse("١٬٩٩٥٫٥٠")!!, 0.0001)
    }

    @Test
    fun parse_blankOrInvalid_returnsNull() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
    }

    @Test
    fun sanitizeInput_keepsDigitsAndSeparatorsOnly() {
        assertEquals("1500", Money.sanitizeInput("١٥٠٠ جنيه"))
    }

    @Test
    fun category_fromId_knowsEgyptCategories() {
        assertEquals(DealCategory.GROCERIES, DealCategory.fromId("groceries"))
        assertEquals(DealCategory.OTHER, DealCategory.fromId("unknown"))
        assertEquals("إلكترونيات وموبايلات", DealCategory.ELECTRONICS.label(isArabic = true))
    }
}
