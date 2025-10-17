package qa.deals.doha.feature.post

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.compose.runtime.getValue
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Canvas
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✨ REDESIGNED: Post a Deal Screen - Visual Design Update 2025
 *
 * VISUAL CHANGES:
 * 1. Removed helper text above title input (kept in placeholder)
 * 2. Red borders for validation (no error messages below)
 * 3. Modern segmented control for Deal Type (dark blue/white active, grey inactive)
 * 4. Removed helper text below Deal Link field
 * 5. NEW: Promo/Coupon Code field for online deals
 * 6. Enhanced image upload area: "Upload a photo or screenshot" + red + icon
 * 7. Dashed border (grey/red) for image upload area
 * 8. Removed "Please complete required fields" error box
 * 9. Increased spacing around key sections
 * 10. White/black Post Deal button (grey when disabled)
 *
 * ✅ ALL VALIDATION LOGIC PRESERVED
 * ✅ ALL FUNCTIONALITY PRESERVED
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    onBackClick: () -> Unit = {},
    onSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: PostViewModel = viewModel(
        factory = PostViewModelFactory(context)
    )
    val state = viewModel.uiState

    // Validation state tracking (unchanged)
    var titleTouched by remember { mutableStateOf(false) }
    var linkTouched by remember { mutableStateOf(false) }
    var locationTouched by remember { mutableStateOf(false) }

    // Real-time validation (unchanged)
    val isTitleValid = state.title.isNotBlank()
    val isLinkValid = state.dealType == DealType.PHYSICAL ||
            (state.link.isNotBlank() &&
                    (state.link.startsWith("http://") || state.link.startsWith("https://")))
    val isLocationValid = state.dealType == DealType.ONLINE || state.location.isNotBlank()
    val hasImage = state.selectedImageUri != null || state.imageUrl.isNotBlank()
    val isFormValid = isTitleValid && isLinkValid && isLocationValid && hasImage

    // Logging (unchanged)
    Log.d("PostScreen", "📝 Validation: Title=$isTitleValid Link=$isLinkValid Location=$isLocationValid Image=$hasImage Valid=$isFormValid")

    // Camera setup (unchanged)
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    var shouldLaunchCamera by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            hasCameraPermission = true
            shouldLaunchCamera = true
            permissionDenied = false
            Log.d("PostScreen", "✅ Camera permission granted")
        } else {
            permissionDenied = true
            Log.e("PostScreen", "❌ Camera permission denied")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            viewModel.setSelectedImage(cameraImageUri!!)
            Log.d("PostScreen", "✅ Camera image captured")
        }
        shouldLaunchCamera = false
    }

    LaunchedEffect(shouldLaunchCamera) {
        if (shouldLaunchCamera && hasCameraPermission && cameraImageUri != null) {
            cameraLauncher.launch(cameraImageUri!!)
            shouldLaunchCamera = false
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setSelectedImage(it)
            Log.d("PostScreen", "✅ Gallery image selected")
        }
    }

    fun createImageFile(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFile = File(context.cacheDir, "JPEG_${timeStamp}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Post a Deal",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp) // Increased from 16dp
        ) {
            // ========================================
            // ✨ REDESIGNED: Title Field
            // - Removed helper text above
            // - Red border validation only
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Deal Title",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

                OutlinedTextField(
                    value = state.title,
                    onValueChange = {
                        viewModel.updateTitle(it)
                        titleTouched = true
                        Log.d("PostScreen", "📝 Title updated")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., 50% off smartphones at Carrefour") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (titleTouched && !isTitleValid)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (titleTouched && !isTitleValid)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.outline
                    ),
                    isError = titleTouched && !isTitleValid
                )
            }

            // ========================================
            // Description (Optional) - Unchanged
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Description (Optional)",
                    style = MaterialTheme.typography.labelLarge
                )

                OutlinedTextField(
                    value = state.description,
                    onValueChange = {
                        viewModel.updateDescription(it)
                        Log.d("PostScreen", "📄 Description updated")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Details, terms, restrictions...") },
                    minLines = 3,
                    maxLines = 5
                )
            }

            Spacer(Modifier.height(4.dp)) // Extra spacing before Deal Type

            // ========================================
            // ✨ REDESIGNED: Deal Type - Modern Segmented Control
            // Dark blue (0xFF1E40AF) bg + white text when active
            // Dark grey (0xFF374151) bg + light grey (0xFF9CA3AF) text when inactive
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Deal Type",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.medium
                        )
                        .clip(MaterialTheme.shapes.medium),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Online Segment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (state.dealType == DealType.ONLINE)
                                    Color(0xFF9046CF) // Purple when active
                                else
                                    Color(0xFF374151) // Dark grey when inactive
                            )
                            .clickable {
                                viewModel.setDealType(DealType.ONLINE)
                                Log.d("PostScreen", "🌐 Deal type: ONLINE")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Online",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.dealType == DealType.ONLINE)
                                    Color(0xFFF3F3F4)
                                else
                                    Color(0xFF9CA3AF) // Light grey
                            )
                        )
                    }

                    // Physical Store Segment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (state.dealType == DealType.PHYSICAL)
                                    Color(0xFF9046CF) // Dark blue when active
                                else
                                    Color(0xFF374151) // Dark grey when inactive
                            )
                            .clickable {
                                viewModel.setDealType(DealType.PHYSICAL)
                                Log.d("PostScreen", "🏪 Deal type: PHYSICAL")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Physical Store",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.dealType == DealType.PHYSICAL)
                                    Color.White
                                else
                                    Color(0xFF9CA3AF) // Light grey
                            )
                        )
                    }
                }
            }

            // ========================================
            // ✨ CONDITIONAL: Online Deal Fields
            // - Deal Link (removed helper text below)
            // - NEW: Promo/Coupon Code field
            // ========================================
            if (state.dealType == DealType.ONLINE) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Deal Link
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Deal Link",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        OutlinedTextField(
                            value = state.link,
                            onValueChange = {
                                viewModel.updateLink(it)
                                linkTouched = true
                                Log.d("PostScreen", "🔗 Link updated")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("https://example.com/deal") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.InsertLink,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (linkTouched && !isLinkValid)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = if (linkTouched && !isLinkValid)
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.outline
                            ),
                            isError = linkTouched && !isLinkValid
                        )
                    }

                    // ✨ NEW: Promo/Coupon Code Field
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Promo/Coupon Code (Optional)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.promoCode ?: "",
                            onValueChange = {
                                viewModel.updatePromoCode(it)
                                Log.d("PostScreen", "🎟️ Promo code updated")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter code here") },
                            singleLine = true
                        )
                    }
                }
            }

            // ========================================
            // ✨ CONDITIONAL: Physical Store Field
            // ========================================
            if (state.dealType == DealType.PHYSICAL) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Location",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    OutlinedTextField(
                        value = state.location,
                        onValueChange = {
                            viewModel.updateLocation(it)
                            locationTouched = true
                            Log.d("PostScreen", "📍 Location updated")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Carrefour City Center Mall") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (locationTouched && !isLocationValid)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (locationTouched && !isLocationValid)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outline
                        ),
                        isError = locationTouched && !isLocationValid
                    )
                }
            }

            Spacer(Modifier.height(4.dp)) // Extra spacing before image section

            // ========================================
            // ✨ REDESIGNED: Deal Image Section
            // - "Upload a photo or screenshot" text
            // - Dashed border (grey/red)
            // - Red circular + icon at bottom-right
            // - Thumbnail preview when uploaded
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Deal Image",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )

                // Image Preview or Upload Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    if (state.selectedImageUri != null || state.imageUrl.isNotBlank()) {
                        // ✨ Image thumbnail preview
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.medium)
                                .background(Color(0xFF1F2937))
                        ) {
                            AsyncImage(
                                model = state.selectedImageUri ?: state.imageUrl,
                                contentDescription = "Deal image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Remove button
                            IconButton(
                                onClick = {
                                    viewModel.clearImage()
                                    Log.d("PostScreen", "🗑️ Image removed")
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Remove",
                                    tint = Color.White
                                )
                            }
                        }
                    } else {
                        // ✨ REDESIGNED: Upload area with dashed border
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color(0xFF1F2937).copy(alpha = 0.3f),
                                    MaterialTheme.shapes.medium
                                )
                        ) {
                            val borderColor = if (!hasImage && (titleTouched || linkTouched || locationTouched))
                                androidx.compose.ui.graphics.Color(0xFFEF4444) // Red
                            else
                                androidx.compose.ui.graphics.Color(0xFF4B5563) // Grey

                            drawRoundRect(
                                color = borderColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(10f, 10f)
                                    )
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                            )
                        }

                        // Content inside upload area
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF9CA3AF)
                                )
                                Text(
                                    "Upload a photo or screenshot",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF9CA3AF),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // ✨ Red circular + icon at bottom-right
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.error,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add image",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Camera and Gallery Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            cameraImageUri = createImageFile()
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                hasCameraPermission = true
                                shouldLaunchCamera = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                            Log.d("PostScreen", "📷 Camera button clicked")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Camera")
                    }

                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch("image/*")
                            Log.d("PostScreen", "🖼️ Gallery launched")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Collections,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery")
                    }
                }

                // Permission denied message
                if (permissionDenied) {
                    Text(
                        "Camera permission required. Please enable it in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ✨ REMOVED: "Please complete required fields" error box

            Spacer(Modifier.height(12.dp)) // Extra spacing before button

            // ========================================
            // ✨ REDESIGNED: Post Deal Button
            // - White bg + black text when enabled
            // - Grey bg + light text when disabled
            // - Increased spacing above
            // ========================================
            Button(
                onClick = {
                    Log.d("PostScreen", "🚀 Submit: Valid=$isFormValid")
                    viewModel.submitDeal()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = isFormValid && !state.loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9046CF), // Purple
                    contentColor = Color(0xFFF3F3F4),
                    disabledContainerColor = Color(0xFF4B5563),
                    disabledContentColor = Color(0xFF9CA3AF)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Black,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Posting...",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    Text(
                        "Post Deal",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ✅ PRESERVED: Loading overlay
    if (state.loading) {
        UploadLoadingOverlay(message = state.message)
    }

    // ✅ PRESERVED: Success animation
    if (state.submitted) {
        SuccessScreen(
            onDismiss = {
                Log.d("PostScreen", "✅ Success, navigating to feed")
                onSuccess()
            }
        )
    }
}