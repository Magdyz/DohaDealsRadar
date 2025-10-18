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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✨ REDESIGNED: Post a Deal Screen - Vinted-Style Layout (2025)
 *
 * NEW CHANGES (Category + Image Picker):
 * 1. ✨ CATEGORY: Required dropdown selector after Description
 * 2. ✨ MOVED: Image picker to TOP of form (first section)
 * 3. ✨ COMPACT: Horizontal grid layout (Vinted style)
 * 4. ✨ CAMERA: Camera icon square (100x100dp)
 * 5. ✨ GALLERY: Gallery icon square (100x100dp)
 * 6. ✨ PREVIEW: Selected image thumbnail with remove button
 *
 * ✅ ALL VALIDATION LOGIC PRESERVED
 * ✅ ALL CAMERA PERMISSIONS PRESERVED
 * ✅ ALL UPLOAD FUNCTIONALITY PRESERVED
 * ✅ ALL LOGGING PRESERVED
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

    // ✅ PRESERVED: Camera setup (unchanged)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ========================================
            // ✨ NEW: Vinted-Style Image Picker (TOP OF FORM)
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Deal Photo",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        "*",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ImagePickerSquare(
                            type = "camera",
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
                                Log.d("PostScreen", "📷 Camera square clicked")
                            },
                            isDisabled = hasImage,
                            showError = !hasImage && (titleTouched || linkTouched || locationTouched)
                        )
                    }

                    item {
                        ImagePickerSquare(
                            type = "gallery",
                            onClick = {
                                galleryLauncher.launch("image/*")
                                Log.d("PostScreen", "🖼️ Gallery square clicked")
                            },
                            isDisabled = hasImage,
                            showError = !hasImage && (titleTouched || linkTouched || locationTouched)
                        )
                    }

                    if (state.selectedImageUri != null || state.imageUrl.isNotBlank()) {
                        item {
                            SelectedImageThumbnail(
                                uri = state.selectedImageUri ?: Uri.parse(state.imageUrl),
                                onRemove = {
                                    viewModel.clearImage()
                                    Log.d("PostScreen", "🗑️ Image removed from thumbnail")
                                }
                            )
                        }
                    }
                }

                if (permissionDenied) {
                    Text(
                        "⚠️ Camera permission required. Please enable it in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // ========================================
            // ✅ PRESERVED: Title Field
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
            // ✅ PRESERVED: Description (Optional) - SINGLE INSTANCE
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

            // ========================================
            // ✨ NEW: Category Selector (Required)
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Category",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        "*",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                CategoryDropdown(
                    selectedCategory = state.category,
                    onCategorySelected = { category ->
                        viewModel.updateCategory(category)
                    }
                )
            }

            Spacer(Modifier.height(4.dp))

            // ========================================
            // ✅ PRESERVED: Deal Type Segmented Control
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (state.dealType == DealType.ONLINE)
                                    Color(0xFF9046CF)
                                else
                                    Color(0xFF374151)
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
                                    Color(0xFF9CA3AF)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (state.dealType == DealType.PHYSICAL)
                                    Color(0xFF9046CF)
                                else
                                    Color(0xFF374151)
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
                                    Color(0xFF9CA3AF)
                            )
                        )
                    }
                }
            }

            // ========================================
            // ✅ PRESERVED: Conditional Online Deal Fields
            // ========================================
            if (state.dealType == DealType.ONLINE) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            // ✅ PRESERVED: Conditional Physical Store Field
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

            Spacer(Modifier.height(12.dp))

            // ========================================
            // ✅ PRESERVED: Post Deal Button
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
                    containerColor = Color(0xFF9046CF),
                    contentColor = Color(0xFFF3F3F4),
                    disabledContainerColor = Color(0xFF4B5563),
                    disabledContentColor = Color(0xFF9CA3AF)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
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

/**
 * ✨ NEW COMPONENT: Modern Category Dropdown (2025)
 * MOVED OUTSIDE PostScreen function
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: DealCategory,
    onCategorySelected: (DealCategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = "${selectedCategory.emoji}  ${selectedCategory.displayName}",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            enabled = false,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DealCategory.values().forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = category.emoji,
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (category == selectedCategory)
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal
                                )
                            )
                        }
                    },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                        Log.d("CategoryDropdown", "🏷️ Selected: ${category.displayName}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = MenuDefaults.itemColors(
                        textColor = if (category == selectedCategory)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

/**
 * ✨ NEW COMPONENT: Image Picker Square (Vinted Style)
 */
@Composable
private fun ImagePickerSquare(
    type: String,
    onClick: () -> Unit,
    isDisabled: Boolean = false,
    showError: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                when {
                    isDisabled -> Color(0xFFE5E7EB)
                    else -> Color(0xFF374151)
                }
            )
            .border(
                width = 2.dp,
                color = when {
                    showError -> Color(0xFFEF4444)
                    isDisabled -> Color(0xFF9CA3AF)
                    else -> Color(0xFF9046CF)
                },
                shape = MaterialTheme.shapes.medium
            )
            .clickable(enabled = !isDisabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                imageVector = if (type == "camera")
                    Icons.Default.PhotoCamera
                else
                    Icons.Default.Collections,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (isDisabled) Color(0xFF9CA3AF) else Color(0xFFD1D5DB)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (type == "camera") "Camera" else "Gallery",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (isDisabled) Color(0xFF9CA3AF) else Color(0xFFD1D5DB),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * ✨ NEW COMPONENT: Selected Image Thumbnail
 */
@Composable
private fun SelectedImageThumbnail(
    uri: Uri,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.size(100.dp)
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "Selected deal photo",
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .border(
                    width = 2.dp,
                    color = Color(0xFF10B981),
                    shape = MaterialTheme.shapes.medium
                ),
            contentScale = ContentScale.Crop
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.7f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove photo",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(20.dp)
                .background(Color(0xFF10B981), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}