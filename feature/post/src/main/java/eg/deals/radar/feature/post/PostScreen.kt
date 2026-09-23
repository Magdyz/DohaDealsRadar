package eg.deals.radar.feature.post

import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import eg.deals.core.design.theme.isArabicUi
import eg.deals.domain.Money
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
import coil3.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import eg.deals.domain.DealCategory
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.FocusDirection
import androidx.core.net.toUri

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
 * ⚠️ EMAIL VERIFICATION INTEGRATION (2025-10-22):
 * 7. ✅ ADDED: Email verification screen integration (replaces old UsernameDialog)
 * 8. ✅ ADDED: Username display in TopBar when verified
 * 9. ✅ ADDED: Auto-approval success messages
 * 10. ⚠️ REMOVED: UsernameDialog (replaced by EmailVerificationScreen)
 *
 * ✅ ALL VALIDATION LOGIC PRESERVED
 * ✅ ALL CAMERA PERMISSIONS PRESERVED
 * ✅ ALL UPLOAD FUNCTIONALITY PRESERVED
 * ✅ ALL LOGGING PRESERVED
 * ✅ ALL EXISTING VIEWMODEL METHODS PRESERVED (updateTitle, updateLocation, etc.)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostScreen(
    onBackClick: () -> Unit = {},
    onSuccess: () -> Unit = {},
    onOpenDeal: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val isArabic = isArabicUi()
    // EGP label on price fields: "EGP 1,000" (English) / "1,000 ج.م" (Arabic)
    val egpPrefix: (@Composable () -> Unit)? =
        if (isArabic) null else ({ Text(Money.symbol(false) + " ", color = MaterialTheme.colorScheme.onSurfaceVariant) })
    val egpSuffix: (@Composable () -> Unit)? =
        if (isArabic) ({ Text(" " + Money.symbol(true), color = MaterialTheme.colorScheme.onSurfaceVariant) }) else null
    val viewModel: PostViewModel = viewModel(
        factory = PostViewModelFactory(context)
    )
    val state = viewModel.uiState
// 🔧 NEW: Snackbar for error display
    val snackbarHostState = remember { SnackbarHostState() }

    // 🔧 NEW: Auto-clear error when user makes changes
    LaunchedEffect(state.title, state.description, state.link, state.location,
        state.selectedImageUri, state.imageUrl, state.dealType) {
        if (state.error != null) {
            viewModel.clearError()
        }
    }

    // 🔧 NEW: Show error in Snackbar when validation fails
    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            Log.e("PostScreen", "❌ Validation Error: $errorMessage")
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Long
            )
        }
    }

    // ✨ Modern keyboard & focus management (PRESERVED)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // ✅ PRESERVED: Validation state tracking (unchanged)
    var titleTouched by remember { mutableStateOf(false) }
    var linkTouched by remember { mutableStateOf(false) }
    var locationTouched by remember { mutableStateOf(false) }

    // ✅ PRESERVED: Real-time validation (unchanged)
    val isTitleValid = state.title.isNotBlank()
    val isLinkValid = state.dealType == DealType.PHYSICAL ||
            (state.link.isNotBlank() &&
                    (state.link.startsWith("http://") || state.link.startsWith("https://")))
    val isLocationValid = state.dealType == DealType.ONLINE || state.location.isNotBlank()
    val hasImage = state.selectedImageUri != null || state.imageUrl.isNotBlank()
    val isFormValid = isTitleValid && isLinkValid && isLocationValid && hasImage


    // ✅ PRESERVED: Camera setup (completely unchanged)
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

    // System photo picker: no storage permission needed (privacy-friendly, Play policy compliant)
    // 🔔 Ask for notification permission in context: right after a deal goes to review,
    // so we can tell the poster when it's live (Android 13+ only, asked once).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Granting also turns on new-deal alerts (opt-out in notification settings)
        if (granted) {
            kotlinx.coroutines.MainScope().launch {
                eg.deals.radar.manager.NotificationManager.getInstance(context).applyDefaultSubscriptions(context)
            }
        }
    }
    LaunchedEffect(state.submitted) {
        if (state.submitted && !state.submittedLive && android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
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

    // Wrap main content and overlays in a Box for proper layering
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) {
                    Snackbar(
                        snackbarData = it,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        actionColor = MaterialTheme.colorScheme.error
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        // ✅ NEW: Show username when verified, but preserve existing title format
                        Column {
                            Text(
                                stringResource(R.string.post_title),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            // ⚠️ NEW FEATURE: Display verified username (non-breaking addition)
                            // Only shows if user has verified email, otherwise invisible
                            state.username?.let { username ->
                                Text(
                                    stringResource(R.string.post_posting_as, username),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.post_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
        // ✅ NEW 2025: Use Scaffold bottomBar - the proper, standard approach
        bottomBar = {
            // This container sticks the button to the keyboard (Snoonu-style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()  // CRITICAL: Makes the bar move up with keyboard
                    .padding(top = 16.dp)  // ✅ Snoonu gap: space above button when keyboard is open
                    .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        viewModel.submitDeal()
                    },
                    modifier = Modifier
                        .width(280.dp)
                        .height(56.dp),
                    enabled = isFormValid && !state.loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF4B5563),
                        disabledContentColor = Color(0xFF9CA3AF)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp,
                        disabledElevation = 0.dp
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = if (isFormValid && !state.loading) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFE91E63),  // Pink
                                            Color(0xFF9C27B0)   // Purple
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF4B5563),
                                            Color(0xFF4B5563)
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.loading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.post_posting),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.post_submit),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // No imeNestedScroll(): it opened the keyboard when scrolling past the end. Focused
                    // fields are still scrolled into view automatically.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ========================================
                // ✅ PRESERVED: ALL EXISTING FORM FIELDS
                // (Image picker, title, description, category, etc.)
                // Keep everything exactly as it is now
                // ========================================

                // ✅ PRESERVED: Image Picker
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.post_photo),
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
                                },
                                isDisabled = hasImage,
                                showError = !hasImage && (titleTouched || linkTouched || locationTouched)
                            )
                        }

                        item {
                            ImagePickerSquare(
                                type = "gallery",
                                onClick = {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                isDisabled = hasImage,
                                showError = !hasImage && (titleTouched || linkTouched || locationTouched)
                            )
                        }

                        if (state.selectedImageUri != null || state.imageUrl.isNotBlank()) {
                            item {
                                SelectedImageThumbnail(
                                    uri = state.selectedImageUri ?: state.imageUrl.toUri(),                                    onRemove = {
                                        viewModel.clearImage()
                                    }
                                )
                            }
                        }
                    }

                    if (permissionDenied) {
                        Text(
                            stringResource(R.string.post_camera_permission),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // ✅ PRESERVED: Title Field (using existing method: updateTitle)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.post_field_title),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = {
                            viewModel.updateTitle(it)  // ✅ PRESERVED: Existing method name
                            titleTouched = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.post_title_placeholder)) },
                        singleLine = true,
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            autoCorrectEnabled = true
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        ),
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

                // ✨ NEW: Price Fields (2025-11-16) - Optional original and discounted prices
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.post_price_optional),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Original Price Field
                        OutlinedTextField(
                            value = state.originalPrice,
                            onValueChange = {
                                viewModel.updateOriginalPrice(it)
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("1000") },
                            label = { Text(stringResource(R.string.post_price_original)) },
                            // EGP: "EGP 1,000" in English, "1,000 ج.م" in Arabic
                            prefix = egpPrefix,
                            suffix = egpSuffix,
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Decimal
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Next) }
                            )
                        )

                        // Discounted Price Field
                        OutlinedTextField(
                            value = state.discountedPrice,
                            onValueChange = {
                                viewModel.updateDiscountedPrice(it)
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("800") },
                            label = { Text(stringResource(R.string.post_price_discounted)) },
                            prefix = egpPrefix,
                            suffix = egpSuffix,
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next,
                                keyboardType = KeyboardType.Decimal
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )
                    }
                }

                // ✅ PRESERVED: Description Field (using existing method: updateDescription)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.post_description_optional),
                        style = MaterialTheme.typography.labelLarge
                    )

                    OutlinedTextField(
                        value = state.description,
                        onValueChange = {
                            viewModel.updateDescription(it)  // ✅ PRESERVED: Existing method name
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp),
                        placeholder = { Text(stringResource(R.string.post_description_placeholder)) },
                        minLines = 3,
                        maxLines = Int.MAX_VALUE,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Default,
                            capitalization = KeyboardCapitalization.Sentences,
                            autoCorrectEnabled = true
                        )
                    )
                }

                // ✅ PRESERVED: Category Selector (using existing method: updateCategory)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.post_category),
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
                            viewModel.updateCategory(category)  // ✅ PRESERVED: Existing method name
                        },
                        keyboardController = keyboardController,
                        focusManager = focusManager
                    )
                }

                // ✨ Where is the deal? (governorate; "All Egypt" for online deals)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.post_governorate),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    GovernorateDropdown(
                        selected = state.governorate,
                        onSelected = viewModel::updateGovernorate
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ✅ PRESERVED: Deal Type Segmented Control
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.post_type),
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
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.post_type_online),
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
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.post_type_physical),
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

                // ✅ PRESERVED: Conditional Online Deal Fields (using existing methods)
                if (state.dealType == DealType.ONLINE) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.post_link),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            OutlinedTextField(
                                value = state.link,
                                onValueChange = {
                                    viewModel.updateLink(it)  // ✅ PRESERVED: Existing method name
                                    linkTouched = true
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
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    keyboardType = KeyboardType.Uri,
                                    autoCorrectEnabled = false
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (linkTouched && !isLinkValid)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (linkTouched && !isLinkValid)
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.outline
                                ),
                                isError = (linkTouched && !isLinkValid) || state.errorField == "link"
                            )
                            // ✨ Link lookup: store recognised / details filled in
                            when {
                                state.linkPreviewLoading -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Text(stringResource(R.string.post_link_checking), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                state.linkPreview?.store != null -> Text(
                                    stringResource(R.string.post_link_store, state.linkPreview!!.store!!),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.post_promo_optional),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = state.promoCode ?: "",
                                onValueChange = {
                                    viewModel.updatePromoCode(it)  // ✅ PRESERVED: Existing method name
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.post_promo_placeholder)) },
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done,
                                    keyboardType = KeyboardType.Text,
                                    capitalization = KeyboardCapitalization.Characters,
                                    autoCorrectEnabled = false
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                    }
                                )
                            )
                        }
                    }
                }

                // ✨ NEW: Expiration Duration Slider

                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.post_expires_in),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            pluralStringResource(R.plurals.post_days, state.expiresInDays, state.expiresInDays),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Slider(
                        value = state.expiresInDays.toFloat(),
                        onValueChange = { newValue ->
                            viewModel.updateExpiresInDays(newValue.toInt())
                        },
                        valueRange = 1f..30f,
                        steps = 28, // 30 steps minus the min and max = 28
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            pluralStringResource(R.plurals.post_days, 1, 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            pluralStringResource(R.plurals.post_days, 30, 30),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ✅ PRESERVED: Conditional Physical Store Field (using existing method)
                if (state.dealType == DealType.PHYSICAL) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.post_location),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        OutlinedTextField(
                            value = state.location,
                            onValueChange = {
                                viewModel.updateLocation(it)  // ✅ PRESERVED: Existing method name
                                locationTouched = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.post_location_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                capitalization = KeyboardCapitalization.Words,
                                autoCorrectEnabled = true
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ),
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

                // ✅ CRITICAL: Large bottom spacer ensures text fields scroll high enough above floating button
                // This creates actual scrollable content (not just padding) so focused fields
                // scroll into a visible position above the 56dp button
                Spacer(Modifier.height(100.dp))
            }
        }

        // 🔧 NEW: Error display above button
        if (state.error != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 88.dp),  // Above the button
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = stringResource(R.string.post_error),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        state.error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.post_dismiss),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ========================================
        // ✅ PRESERVED: FLOATING BUTTON OVERLAY
        // Positioned at bottom center, floats over content
        // ========================================
        Button(
            onClick = {
                viewModel.submitDeal()  // ✅ PRESERVED: Existing method
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // ========================================
                // This makes the button "float" above the keyboard
                // when a text field is focused.
                // ========================================
                .imePadding()
                .padding(bottom = 24.dp)
                .width(280.dp)
                .height(56.dp),
            enabled = isFormValid && !state.loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF4B5563),
                disabledContentColor = Color(0xFF9CA3AF)
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp,
                disabledElevation = 0.dp
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (isFormValid && !state.loading) {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE91E63),  // Pink
                                    Color(0xFF9C27B0)   // Purple
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF4B5563),
                                    Color(0xFF4B5563)
                                )
                            )
                        },
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state.loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.post_posting),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.post_submit),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    )
                }
            }
        }

        // ✅ PRESERVED: Loading overlay
        if (state.loading) {
            UploadLoadingOverlay(message = state.message)
        }

        // ✅ PRESERVED: Success animation
        if (state.submitted) {
            SuccessScreen(
                live = state.submittedLive,
                onDismiss = {
                    Log.d("PostScreen", "✅ Success, navigating to feed")
                    onSuccess()
                }
            )
        }

        // ========================================
        // 🔐 SIGN IN (Google) - shown when posting needs an account
        // ========================================
        if (state.showEmailVerification) {
            LaunchedEffect(state.emailVerificationState) {
                val verified = state.emailVerificationState
                if (verified is EmailVerificationState.Verified) {
                    // Updates state and continues the post that triggered sign-in
                    viewModel.onSignedIn(verified.user.id, verified.user.username)
                }
            }

            GoogleSignInScreen(
                onCancel = viewModel::hideEmailVerification,
                onSignIn = { consent -> viewModel.signInWithGoogle(context, consent) },
                isLoading = state.emailVerificationState is EmailVerificationState.Loading,
                error = (state.emailVerificationState as? EmailVerificationState.Error)?.message
            )
        }

        // ✨ Duplicate warnings
        when (val warning = state.duplicateWarning) {
            is DuplicateWarning.Exact -> AlertDialog(
                onDismissRequest = viewModel::dismissDuplicateWarning,
                title = { Text(stringResource(R.string.post_dup_exact_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.post_dup_exact_message))
                        warning.existing.title?.let { Text("• $it", fontWeight = FontWeight.SemiBold) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissDuplicateWarning()
                        onOpenDeal(warning.existing.id)
                    }) { Text(stringResource(R.string.post_dup_open)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDuplicateWarning) { Text(stringResource(R.string.post_dup_edit)) }
                }
            )
            is DuplicateWarning.Similar -> AlertDialog(
                onDismissRequest = viewModel::dismissDuplicateWarning,
                title = { Text(stringResource(R.string.post_dup_similar_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.post_dup_similar_message))
                        warning.deals.take(3).forEach { d ->
                            Text(
                                "• ${d.title ?: ""}",
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    viewModel.dismissDuplicateWarning()
                                    onOpenDeal(d.id)
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmNotDuplicate) { Text(stringResource(R.string.post_dup_mine_different)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDuplicateWarning) { Text(stringResource(R.string.post_dup_cancel)) }
                }
            )
            null -> Unit
        }
    }  // Close Box wrapper
}

/** Governorate picker (EN/AR names). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GovernorateDropdown(
    selected: eg.deals.domain.Governorate,
    onSelected: (eg.deals.domain.Governorate) -> Unit
) {
    val isArabic = isArabicUi()
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label(isArabic),
            onValueChange = {},
            readOnly = true,
            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            eg.deals.domain.Governorate.values().forEach { g ->
                DropdownMenuItem(
                    text = { Text(g.label(isArabic), fontWeight = if (g == selected) FontWeight.Bold else FontWeight.Normal) },
                    onClick = {
                        onSelected(g)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * ✨ PRESERVED: Modern Category Dropdown (2025)
 * MOVED OUTSIDE PostScreen function
 * ✅ NO CHANGES - Completely preserved
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selectedCategory: DealCategory,
    onCategorySelected: (DealCategory) -> Unit,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            // ✨ Dismiss keyboard when opening dropdown
            if (it) {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
            expanded = it
        }
    ) {
        OutlinedTextField(
            value = "${selectedCategory.emoji}  ${selectedCategory.label(isArabicUi())}",
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
                                text = category.label(isArabicUi()),
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
 * ✨ PRESERVED: Image Picker Square (Vinted Style)
 * ✅ NO CHANGES - Completely preserved
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
                text = if (type == "camera") stringResource(R.string.post_camera) else stringResource(R.string.post_gallery),
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
 * ✨ PRESERVED: Selected Image Thumbnail
 * ✅ NO CHANGES - Completely preserved
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
            contentDescription = stringResource(R.string.post_selected_photo),
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
                contentDescription = stringResource(R.string.post_remove_photo),
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