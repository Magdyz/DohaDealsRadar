package eg.deals.domain

/**
 * Egypt's 27 governorates + "All Egypt / Online".
 * `id` is stored in the database; keep in sync with the backend
 * (supabase/functions/_shared/deals.ts GOVERNORATES and the DB constraint).
 */
enum class Governorate(val id: String, val displayName: String, val arabicName: String) {
    ALL_EGYPT("all_egypt", "All Egypt / Online", "كل مصر / أونلاين"),
    CAIRO("cairo", "Cairo", "القاهرة"),
    GIZA("giza", "Giza", "الجيزة"),
    ALEXANDRIA("alexandria", "Alexandria", "الإسكندرية"),
    QALYUBIA("qalyubia", "Qalyubia", "القليوبية"),
    SHARQIA("sharqia", "Sharqia", "الشرقية"),
    DAKAHLIA("dakahlia", "Dakahlia", "الدقهلية"),
    GHARBIA("gharbia", "Gharbia", "الغربية"),
    MONUFIA("monufia", "Monufia", "المنوفية"),
    BEHEIRA("beheira", "Beheira", "البحيرة"),
    KAFR_EL_SHEIKH("kafr_el_sheikh", "Kafr El Sheikh", "كفر الشيخ"),
    DAMIETTA("damietta", "Damietta", "دمياط"),
    PORT_SAID("port_said", "Port Said", "بورسعيد"),
    ISMAILIA("ismailia", "Ismailia", "الإسماعيلية"),
    SUEZ("suez", "Suez", "السويس"),
    FAYOUM("fayoum", "Fayoum", "الفيوم"),
    BENI_SUEF("beni_suef", "Beni Suef", "بني سويف"),
    MINYA("minya", "Minya", "المنيا"),
    ASYUT("asyut", "Asyut", "أسيوط"),
    SOHAG("sohag", "Sohag", "سوهاج"),
    QENA("qena", "Qena", "قنا"),
    LUXOR("luxor", "Luxor", "الأقصر"),
    ASWAN("aswan", "Aswan", "أسوان"),
    RED_SEA("red_sea", "Red Sea", "البحر الأحمر"),
    NEW_VALLEY("new_valley", "New Valley", "الوادي الجديد"),
    MATROUH("matrouh", "Matrouh / North Coast", "مطروح / الساحل الشمالي"),
    NORTH_SINAI("north_sinai", "North Sinai", "شمال سيناء"),
    SOUTH_SINAI("south_sinai", "South Sinai", "جنوب سيناء");

    fun label(isArabic: Boolean): String = if (isArabic) arabicName else displayName

    companion object {
        fun fromId(id: String?): Governorate? = values().firstOrNull { it.id == id }
    }
}
