package qa.deals.doha

import android.app.Application
import qa.deals.doha.util.AppContext

class DohaDealsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Make appContext available to core modules (e.g., DataStore)
        AppContext.init(this)
    }
}
