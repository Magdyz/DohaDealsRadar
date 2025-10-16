package qa.deals.doha.feature.post

import android.Manifest
import androidx.compose.runtime.getValue
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✨ ENHANCED: Post a Deal Screen with Improved Form Validation
 *
 * NEW FEATURES:
 * 1. Bold required field labels with colored asterisks (16sp)
 * 2. Inline validation with real-time feedback
 * 3. Visual field states (normal/focused/error/success)
 * 4. Smart submit button (disabled when invalid with tooltip)
 * 5. Clear error messages for each field
 * 6. Success checkmarks for valid fields
 * 7. Helper text explaining field requirements
 * 8. Missing fields list when form is invalid
 *
 * ✅ ALL EXISTING FUNCTIONALITY PRESERVED
 *
 * @author Magdyz
 * @date 2025-10-16
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

    // ========================================
    // ✨ NEW: Validation state tracking
    // Tracks whether user has interacted with each field
    // ========================================
    var titleTouched by remember { mutableStateOf(false) }
    var linkTouched by remember { mutableStateOf(false) }
    var locationTouched by remember { mutableStateOf(false) }

    // ========================================
    // ✨ NEW: Real-time validation results
    // These update automatically as user types
    // ========================================
    val isTitleValid = state.title.isNotBlank()
    val isLinkValid = state.dealType == DealType.PHYSICAL ||
            (state.link.isNotBlank() &&
                    (state.link.startsWith("http://") || state.link.startsWith("https://")))
    val isLocationValid = state.dealType == DealType.ONLINE || state.location.isNotBlank()
    val hasImage = state.selectedImageUri != null || state.imageUrl.isNotBlank()

    // ✨ NEW: Overall form validity check
    val isFormValid = isTitleValid && isLinkValid && isLocationValid && hasImage

    // ✨ NEW: Validation logging for debugging
    Log.d("PostScreen", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    Log.d("PostScreen", "📝 Form validation state:")
    Log.d("PostScreen", "   Title valid: $isTitleValid")
    Log.d("PostScreen", "   Link valid: $isLinkValid")
    Log.d("PostScreen", "   Location valid: $isLocationValid")
    Log.d("PostScreen", "   Has image: $hasImage")
    Log.d("PostScreen", "   Form valid: $isFormValid")
    Log.d("PostScreen", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    // Camera URI state
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("PostScreen", "✅ Camera permission granted")
        } else {
            Log.e("PostScreen", "❌ Camera permission denied")
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            viewModel.setSelectedImage(cameraImageUri!!)
            Log.d("PostScreen", "✅ Camera image captured")
        }
    }

    // Gallery launcher
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========================================
            // ✨ ENHANCED: Title Field with Validation
            // Shows bold label, asterisk, helper text, and validation state
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // ✨ NEW: Bold label with prominent red asterisk
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Deal Title",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold // Bold for required fields
                        )
                    )
                    Text(
                        " *",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp // Larger, more visible asterisk
                        )
                    )
                    // ✨ NEW: Green checkmark when valid
                    if (isTitleValid && titleTouched) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Valid",
                            tint = Color(0xFF10B981),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(16.dp)
                        )
                    }
                }

                // ✨ NEW: Helper text explaining the field
                Text(
                    "A clear, descriptive title for your deal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = state.title,
                    onValueChange = {
                        viewModel.updateTitle(it)
                        titleTouched = true
                        Log.d("PostScreen", "📝 Title updated: ${it.take(20)}...")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g., 50% off smartphones at Carrefour") },
                    // ✨ NEW: Dynamic border color based on validation state
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (titleTouched && !isTitleValid)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (titleTouched && !isTitleValid)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outline
                    ),
                    // ✨ NEW: Error state triggers red border
                    isError = titleTouched && !isTitleValid,
                    // ✨ NEW: Error message shown below field
                    supportingText = if (titleTouched && !isTitleValid) {
                        { Text("Title is required", color = MaterialTheme.colorScheme.error) }
                    } else null
                )
            }

            // ========================================
            // Description Field (Optional - no validation needed)
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Description (Optional)",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "Add more details about the deal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = {
                        viewModel.updateDescription(it)
                        Log.d("PostScreen", "📄 Description updated")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Details, terms, restrictions...") },
                    minLines = 3
                )
            }

            // ========================================
            // ✨ ENHANCED: Deal Type Selector
            // Required field with visual selection state
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Deal Type",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        " *",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Online Deal Chip
                    FilterChip(
                        selected = state.dealType == DealType.ONLINE,
                        onClick = {
                            viewModel.setDealType(DealType.ONLINE)
                            Log.d("PostScreen", "🌐 Deal type: ONLINE")
                        },
                        label = { Text("Online") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (state.dealType == DealType.ONLINE) {
                            { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null
                    )
                    // Physical Store Chip
                    FilterChip(
                        selected = state.dealType == DealType.PHYSICAL,
                        onClick = {
                            viewModel.setDealType(DealType.PHYSICAL)
                            Log.d("PostScreen", "🏪 Deal type: PHYSICAL")
                        },
                        label = { Text("Physical Store") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = if (state.dealType == DealType.PHYSICAL) {
                            { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                        } else null
                    )
                }
            }

            // ========================================
            // ✨ ENHANCED: Conditional Fields with Validation
            // Shows either Link (for online) or Location (for physical)
            // ========================================
            if (state.dealType == DealType.ONLINE) {
                // ONLINE DEAL: Link Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Deal Link",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            " *",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp
                            )
                        )
                        // ✨ NEW: Success indicator
                        if (isLinkValid && linkTouched) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Valid",
                                tint = Color(0xFF10B981),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(16.dp)
                            )
                        }
                    }

                    // ✨ NEW: Specific helper text for URL format
                    Text(
                        "Must start with http:// or https://",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = state.link,
                        onValueChange = {
                            viewModel.updateLink(it)
                            linkTouched = true
                            Log.d("PostScreen", "🔗 Link updated: ${it.take(30)}...")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://example.com/deal") },
                        // ✨ FIXED: InsertLink icon instead of Link
                        leadingIcon = { Icon(Icons.Default.InsertLink, contentDescription = null) },
                        // ✨ NEW: Visual validation state
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (linkTouched && !isLinkValid)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (linkTouched && !isLinkValid)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline
                        ),
                        isError = linkTouched && !isLinkValid,
                        // ✨ NEW: Specific error messages
                        supportingText = if (linkTouched && !isLinkValid) {
                            {
                                Text(
                                    if (state.link.isBlank()) "Link is required"
                                    else "Link must start with http:// or https://",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null
                    )
                }
            } else {
                // PHYSICAL DEAL: Location Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Location",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            " *",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 16.sp
                            )
                        )
                        // ✨ NEW: Success indicator
                        if (isLocationValid && locationTouched) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Valid",
                                tint = Color(0xFF10B981),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(16.dp)
                            )
                        }
                    }

                    Text(
                        "Store name or address in Doha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = state.location,
                        onValueChange = {
                            viewModel.updateLocation(it)
                            locationTouched = true
                            Log.d("PostScreen", "📍 Location updated: $it")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Carrefour City Center Mall") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (locationTouched && !isLocationValid)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (locationTouched && !isLocationValid)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline
                        ),
                        isError = locationTouched && !isLocationValid,
                        supportingText = if (locationTouched && !isLocationValid) {
                            { Text("Location is required", color = MaterialTheme.colorScheme.error) }
                        } else null
                    )
                }
            }

            // ========================================
            // ✨ ENHANCED: Image Selection with Validation
            // Required field with visual feedback
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Deal Image",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        " *",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        )
                    )
                    // ✨ NEW: Success indicator when image is selected
                    if (hasImage) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Valid",
                            tint = Color(0xFF10B981),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(16.dp)
                        )
                    }
                }

                Text(
                    "Upload a clear photo of the deal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Image preview or placeholder
                if (state.selectedImageUri != null || state.imageUrl.isNotBlank()) {
                    // ✨ Image Preview with Remove Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.LightGray, MaterialTheme.shapes.medium)
                    ) {
                        AsyncImage(
                            model = state.selectedImageUri ?: state.imageUrl,
                            contentDescription = "Selected image",
                            modifier = Modifier.fillMaxSize()
                        )
                        // Remove button
                        IconButton(
                            onClick = {
                                viewModel.clearImage()
                                Log.d("PostScreen", "🗑️ Image removed")
                            },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, "Remove image", tint = Color.White)
                        }
                    }
                } else {
                    // ✨ NEW: Empty state with error border if form submitted
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .border(
                                width = 2.dp,
                                color = if (!hasImage && state.error != null)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ✨ FIXED: PhotoCamera icon instead of AddPhotoAlternate
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No image selected",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Camera and Gallery Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                            cameraImageUri = createImageFile()
                            cameraLauncher.launch(cameraImageUri!!)
                            Log.d("PostScreen", "📷 Camera launched")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        // ✨ FIXED: PhotoCamera icon instead of Camera
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, Modifier.size(18.dp))
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
                        // ✨ FIXED: Collections icon instead of Image
                        Icon(Icons.Default.Collections, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gallery")
                    }
                }
            }

            // ========================================
            // ✨ ENHANCED: Error Display
            // Shows validation errors from ViewModel
            // ========================================
            if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ✨ FIXED: Warning icon instead of Error
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Success message display
            if (state.message != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981)
                        )
                        Text(
                            state.message!!,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ========================================
            // ✨ NEW: Smart Submit Button with Tooltip
            // Disabled when form is invalid, shows what's missing
            // ========================================
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ✨ NEW: Tooltip card showing missing fields
                if (!isFormValid) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Please complete required fields:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            // ✨ NEW: Bullet list of missing fields
                            Column(modifier = Modifier.padding(start = 28.dp)) {
                                if (!isTitleValid) {
                                    Text("• Deal title", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!isLinkValid && state.dealType == DealType.ONLINE) {
                                    Text("• Valid deal link (http:// or https://)", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!isLocationValid && state.dealType == DealType.PHYSICAL) {
                                    Text("• Store location", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!hasImage) {
                                    Text("• Deal image", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // ✨ ENHANCED: Submit button with dynamic state
                Button(
                    onClick = {
                        Log.d("PostScreen", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        Log.d("PostScreen", "🚀 Submit button clicked")
                        Log.d("PostScreen", "   Form valid: $isFormValid")
                        Log.d("PostScreen", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        viewModel.submitDeal()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    // ✨ NEW: Disabled when form is invalid or loading
                    enabled = isFormValid && !state.loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (state.loading) {
                        // Loading state
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Posting...", style = MaterialTheme.typography.labelLarge)
                    } else {
                        // Normal state
                        Icon(Icons.Default.Send, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Post Deal",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
    // ========================================
    // ✨ NEW: Success Celebration Screen Overlay
    // Shows animated success screen when deal is posted
    // ========================================
    if (state.submitted) {
        SuccessScreen(
            onDismiss = {
                Log.d("PostScreen", "✅ Success screen dismissed, navigating to feed")
                onSuccess()
            }
        )
    }

}