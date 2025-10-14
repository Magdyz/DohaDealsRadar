package qa.deals.doha.util

import android.content.Context

/**
 * Holds an applicationContext reference for modules that don't own an Android Application.
 * Initialize from :app Application.onCreate().
 */
object AppContext {
    private var _appContext: Context? = null

    val appContext: Context
        get() = _appContext
            ?: error("AppContext not initialized. Call AppContext.init(appContext) in DohaDealsApp.onCreate().")

    fun init(context: Context) {
        _appContext = context.applicationContext
    }
}
