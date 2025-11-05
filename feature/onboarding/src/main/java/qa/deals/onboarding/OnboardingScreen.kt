package qa.deals.onboarding

import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import qa.deals.doha.core.design.R // This import provides R.drawable.*
import qa.deals.doha.datastore.DeviceIdManager
import qa.deals.doha.preload.ImagePreloader
import qa.deals.doha.repository.PreloadRepository
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

    val slides = listOf(
        R.drawable.onboarding_slide_1,
        R.drawable.onboarding_slide_2,
        R.drawable.onboarding_slide_3
    )
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    // Use a gradient matching your app's dark theme
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1F2937), // Dark Grey (from your screenshots)
            Color(0xFF111827)  // Darker Grey (from your screenshots)
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
            Image(
                painter = painterResource(id = slides[page]),
                contentDescription = "Onboarding Slide ${page + 1}",
                contentScale = ContentScale.Fit, // Use Fit to show the whole 16:9 image
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 80.dp) // Add padding so it doesn't touch edges
            )
        }

        // Bottom controls (Indicator + Buttons)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
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
                        "Get Started",
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
                        Text("Skip", color = Color.Gray)
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
                        Text("Next")
                    }
                }
            }
        }
    }
}