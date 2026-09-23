package eg.deals.radar.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.annotation.StringRes
import eg.deals.radar.manager.NotificationManager
import java.util.Locale

/**
 * ========================================
 * 🌐 APP LANGUAGE (EN / AR)
 * ========================================
 *
 * In-app language switch, independent of the device language.
 * - Default: English
 * - Arabic: Egyptian Arabic strings + RTL layout
 *
 * How it works:
 * - The choice is stored in SharedPreferences.
 * - MainActivity wraps its base context with [wrap], so Compose `stringResource`
 *   and layout direction follow the chosen language.
 * - Non-UI code (ViewModels, helpers) resolves strings with [string].
 * - Switching calls [toggle], which also moves FCM topic subscriptions to the
 *   matching language and recreates the activity.
 */
object AppLanguage {
    const val ENGLISH = "en"
    const val ARABIC = "ar"

    private const val PREFS_FILE = "app_language_prefs"
    private const val KEY_LANGUAGE = "language"

    @Volatile
    private var cachedContext: Pair<String, Context>? = null

    /** Current app language code ("en" or "ar"). */
    fun current(context: Context = AppContext.appContext): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, ENGLISH)
        return if (stored == ARABIC) ARABIC else ENGLISH
    }

    fun isArabic(context: Context = AppContext.appContext): Boolean = current(context) == ARABIC

    /** Locale used for resources: Egyptian Arabic or English. */
    fun locale(language: String): Locale =
        if (language == ARABIC) Locale("ar", "EG") else Locale.ENGLISH

    /** Returns [base] configured for the chosen app language (locale + layout direction). */
    fun wrap(base: Context): Context {
        val locale = locale(current(base))
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Resolve a string resource in the app language from non-UI code (e.g. ViewModels). */
    fun string(@StringRes resId: Int, vararg formatArgs: Any): String =
        localizedContext().getString(resId, *formatArgs)

    private fun localizedContext(): Context {
        val language = current()
        cachedContext?.let { (lang, ctx) -> if (lang == language) return ctx }
        return wrap(AppContext.appContext).also { cachedContext = language to it }
    }

    /**
     * Switch EN <-> AR and restart the current screen so every string and the
     * layout direction update.
     */
    fun toggle(context: Context) {
        val newLanguage = if (isArabic(context)) ENGLISH else ARABIC
        setLanguage(context, newLanguage)
        context.findActivity()?.recreate()
    }

    fun setLanguage(context: Context, language: String) {
        val oldLanguage = current(context)
        if (oldLanguage == language) return
        context.applicationContext
            .getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .commit() // Synchronous: the activity is recreated right after
        cachedContext = null
        // Push notifications are sent per language, so follow the new language
        NotificationManager.getInstance(context).onLanguageChanged(oldLanguage, language)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
