package qa.deals.doha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import qa.deals.doha.navigation.AppNavHost
import qa.deals.doha.design.theme.DohaDealsTheme

/**
 * Main activity - Entry point of the app.
 * Sets up Compose with navigation and theming.
 * ✅ OPTIMIZED: Edge-to-edge, predictive back ready
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ========================================
        // ✅ NEW: Enable edge-to-edge for modern UI
        // ========================================
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        setContent {
            DohaDealsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavHost(navController = navController)
                }
            }
        }
    }
}