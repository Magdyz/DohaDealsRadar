package eg.deals.onboarding

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import eg.deals.core.design.theme.LanguageToggleButton
import eg.deals.radar.datastore.DeviceIdManager
import eg.deals.radar.preload.ImagePreloader
import eg.deals.radar.repository.PreloadRepository
import eg.deals.radar.util.AppLanguage
import kotlinx.coroutines.delay

// Simple ViewModel to handle marking onboarding as "seen"
class OnboardingViewModel(context: Context) : ViewModel() {
    private val deviceIdManager = DeviceIdManager.getInstance(context)

    fun onOnboardingFinished() {
        viewModelScope.launch {
            Log.d("OnboardingViewModel", "Onboarding finished, setting flag.")
            deviceIdManager.setHasSeenOnboarding()
        }
    }
}

// Factory to create the ViewModel with a Context
class OnboardingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * One onboarding slide. Drawn in Compose (not a bitmap) so the text follows
 * the app language (EN / AR) and the layout mirrors in RTL.
 */
private data class OnboardingSlide(
    val emoji: String,
    val emojiRow: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int
)

private val slides = listOf(
    OnboardingSlide("📡", "🍔  🛒  📱  🛍️", R.string.onboarding_welcome_title, R.string.onboarding_welcome_subtitle),
    OnboardingSlide("🔥", "🔥  ❄️  🎟️", R.string.onboarding_engage_title, R.string.onboarding_engage_subtitle),
    OnboardingSlide("📸", "📸  ✍️  🚀", R.string.onboarding_share_title, R.string.onboarding_share_subtitle)
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = OnboardingViewModelFactory(context)
    )

    // ========================================
    // ✨ NEW: BACKGROUND PRELOAD
    // Preload feed data while user views slides
    // ========================================
    // ⚠️ SAFE: Cancels automatically when composable leaves
    // ⚠️ NON-BLOCKING: Runs in background, won't affect UI
    // ⚠️ GRACEFUL: If fails, normal feed loading continues
    LaunchedEffect(Unit) {
        try {
            // Wait 3 seconds before starting preload
            // (gives user time to view first slide)
            delay(3000)

            Log.d("OnboardingPreload", "🚀 Starting background preload...")

            // Preload deals data
            val preloadRepo = PreloadRepository.getInstance()
            val preloadSuccess = preloadRepo.preloadDeals()

            if (preloadSuccess) {
                Log.d("OnboardingPreload", "✅ Deals preloaded successfully")

                // Preload images for first few deals
                val cachedDeals = preloadRepo.getCachedDeals()
                if (cachedDeals != null) {
                    ImagePreloader.preloadImages(context, cachedDeals)
                    Log.d("OnboardingPreload", "✅ Images preloaded successfully")
                }
            } else {
                Log.d("OnboardingPreload", "⚠️ Preload failed (non-critical, normal load will continue)")
            }
        } catch (e: Exception) {
            // Catch any errors to prevent crash
            Log.e("OnboardingPreload", "💥 Preload error (non-critical)", e)
        }
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    // Dark background fading into the brand magenta (matches the old slide art)
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1F2937),
            Color(0xFF111827),
            Color(0xFF4A1A5C),
            Color(0xFF8E2C7A)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        // Sliding Pager for the slides
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            OnboardingSlideContent(slide = slides[page])
        }

        // 🌐 EN / AR switch - lets Arabic speakers switch before starting
        LanguageToggleButton(
            onClick = { AppLanguage.toggle(context) },
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
        )

        // Bottom controls (Indicator + Buttons)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Pager Indicator (Dots)
            Row(
                Modifier.wrapContentHeight(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pagerState.pageCount) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.Gray
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(10.dp)
                    )
                }
            }

            // 2. Buttons
            val isLastPage = pagerState.currentPage == slides.size - 1

            if (isLastPage) {
                // "Get Started" Button
                Button(
                    onClick = {
                        viewModel.onOnboardingFinished()
                        onFinished()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary // Use your theme's purple
                    )
                ) {
                    Text(
                        stringResource(R.string.onboarding_get_started),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // "Skip" and "Next" Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            viewModel.onOnboardingFinished()
                            onFinished()
                        }
                    ) {
                        Text(stringResource(R.string.onboarding_skip), color = Color.LightGray)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingSlideContent(slide: OnboardingSlide) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(top = 96.dp, bottom = 180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing emoji badge
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE91E63).copy(alpha = 0.55f),
                            Color(0xFF9C27B0).copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = slide.emoji, fontSize = 72.sp)
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = stringResource(slide.title),
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(slide.subtitle),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 17.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = slide.emojiRow, fontSize = 28.sp)
    }
}
