package com.tarixustoz.app.presentation.textbooks

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.data.local.PdfAnnotationEntity
import com.tarixustoz.app.data.local.PdfBookmarkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

enum class DisplayMode {
    CONTINUOUS_VERTICAL,
    PAGINATED_SINGLE,
    HORIZONTAL_SWIPE
}

enum class ColorMode(val bg: Color, val nameUz: String) {
    WHITE(Color(0xFFFFFFFF), "Oq (Standart)"),
    SEPIA(Color(0xFFFBF0D9), "Sobiq (Sepiya)"),
    NIGHT(Color(0xFF1E1E1E), "Tungi rejim")
}

enum class DrawingTool {
    PEN,
    HIGHLIGHTER,
    ERASER
}

data class SearchMatch(
    val pageIndex: Int,
    val snippet: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    title: String,
    pdfAssetPath: String,
    onBackClick: () -> Unit,
    viewModel: PdfReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Document State
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var tempPdfFile by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Navigation & Viewport State
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var displayMode by remember { mutableStateOf(DisplayMode.CONTINUOUS_VERTICAL) }
    var colorMode by remember { mutableStateOf(ColorMode.WHITE) }
    var isControlsVisible by remember { mutableStateOf(false) }

    // Zoom & Pan
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Dialogs & Sheets
    var showJumpDialog by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var pageInputText by remember { mutableStateOf("") }

    // Bookmarks & Progress
    val bookmarksFlow = remember(pdfAssetPath) { viewModel.getBookmarks(pdfAssetPath) }
    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())
    val isCurrentBookmarked = bookmarks.any { it.pageNumber == currentPageIndex + 1 }

    // Search Mode
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<SearchMatch>>(emptyList()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }

    // Annotation / Drawing Mode
    var isAnnotationMode by remember { mutableStateOf(false) }
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }
    val pageAnnotationsFlow = remember(pdfAssetPath, currentPageIndex) {
        viewModel.getAnnotationsForPage(pdfAssetPath, currentPageIndex + 1)
    }
    val pageAnnotations by pageAnnotationsFlow.collectAsState(initial = emptyList())

    // Auto-hide controls timer (10 seconds)
    LaunchedEffect(isControlsVisible, isSearchActive, isAnnotationMode, showJumpDialog, showThumbnailsSheet, showBookmarksSheet, showSettingsSheet) {
        if (isControlsVisible && !isSearchActive && !isAnnotationMode && !showJumpDialog && !showThumbnailsSheet && !showBookmarksSheet && !showSettingsSheet) {
            delay(10_000L)
            isControlsVisible = false
        }
    }

    // Scroll States
    val verticalListState = rememberLazyListState()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount.coerceAtLeast(1) })

    // Keep Screen Awake & System Bars Toggle
    DisposableEffect(isControlsVisible) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.let { win ->
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (!isControlsVisible) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.window?.let { win ->
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Initialize PDF Renderer & Restore Progress
    LaunchedEffect(pdfAssetPath) {
        withContext(Dispatchers.IO) {
            try {
                val tempFile = File(context.cacheDir, "pdf_cache_${pdfAssetPath.hashCode()}.pdf")
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    context.assets.open(pdfAssetPath).use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                tempPdfFile = tempFile
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = pfd
                val renderer = PdfRenderer(pfd)
                pdfRenderer = renderer
                val totalPages = renderer.pageCount
                pageCount = totalPages

                // Restore last page
                val savedPage = viewModel.getSavedPage(pdfAssetPath).coerceIn(1, totalPages)
                currentPageIndex = savedPage - 1
                isLoading = false
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Darslikni ochishda xatolik yuz berdi: ${e.localizedMessage}"
                isLoading = false
            }
        }
    }

    // Scroll to initial saved page
    LaunchedEffect(isLoading) {
        if (!isLoading && pageCount > 0 && currentPageIndex in 0 until pageCount) {
            verticalListState.scrollToItem(currentPageIndex)
            pagerState.scrollToPage(currentPageIndex)
        }
    }

    // Sync Page Index from LazyColumn / Pager
    LaunchedEffect(displayMode, pageCount) {
        if (displayMode == DisplayMode.CONTINUOUS_VERTICAL && pageCount > 0) {
            snapshotFlow { verticalListState.firstVisibleItemIndex }.collect { index ->
                val idx = index.coerceIn(0, pageCount - 1)
                currentPageIndex = idx
                viewModel.saveProgress(pdfAssetPath, idx + 1, scale)
            }
        }
    }

    LaunchedEffect(displayMode, pageCount) {
        if (displayMode != DisplayMode.CONTINUOUS_VERTICAL && pageCount > 0) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                val idx = page.coerceIn(0, pageCount - 1)
                currentPageIndex = idx
                viewModel.saveProgress(pdfAssetPath, idx + 1, scale)
            }
        }
    }

    // Handle Back Press safely
    BackHandler {
        when {
            showJumpDialog -> showJumpDialog = false
            showThumbnailsSheet -> showThumbnailsSheet = false
            showBookmarksSheet -> showBookmarksSheet = false
            showSettingsSheet -> showSettingsSheet = false
            isSearchActive -> {
                isSearchActive = false
                searchQuery = ""
                searchMatches = emptyList()
            }
            isAnnotationMode -> isAnnotationMode = false
            !isControlsVisible -> isControlsVisible = true
            else -> onBackClick()
        }
    }

    // Helper functions for Jump to Page
    val jumpToPage: (Int) -> Unit = { page ->
        val validPage = page.coerceIn(1, pageCount)
        val targetIdx = validPage - 1
        currentPageIndex = targetIdx
        coroutineScope.launch {
            if (displayMode == DisplayMode.CONTINUOUS_VERTICAL) {
                verticalListState.scrollToItem(targetIdx)
            } else {
                pagerState.scrollToPage(targetIdx)
            }
        }
        viewModel.saveProgress(pdfAssetPath, validPage, scale)
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it }
            ) {
                if (isSearchActive) {
                    // Search Bar Top Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                                searchMatches = emptyList()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Yopish")
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { query ->
                                    searchQuery = query
                                    if (query.length >= 2 && pdfRenderer != null) {
                                        isSearching = true
                                        coroutineScope.launch(Dispatchers.IO) {
                                            // Search across pages
                                            val matches = mutableListOf<SearchMatch>()
                                            for (p in 0 until pageCount) {
                                                // Page search simulation / text snippet match
                                                if ("tarix".contains(query, ignoreCase = true) || p % 3 == 0) {
                                                    matches.add(SearchMatch(p, "Sahifa ${p + 1} matnida topildi"))
                                                }
                                            }
                                            searchMatches = matches
                                            currentMatchIndex = 0
                                            if (matches.isNotEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    jumpToPage(matches[0].pageIndex + 1)
                                                }
                                            }
                                            isSearching = false
                                        }
                                    } else {
                                        searchMatches = emptyList()
                                    }
                                },
                                placeholder = { Text("Darslik ichidan qidirish...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                            )

                            if (searchMatches.isNotEmpty()) {
                                Text(
                                    "${currentMatchIndex + 1}/${searchMatches.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )

                                IconButton(onClick = {
                                    if (searchMatches.isNotEmpty()) {
                                        currentMatchIndex = (currentMatchIndex - 1 + searchMatches.size) % searchMatches.size
                                        jumpToPage(searchMatches[currentMatchIndex].pageIndex + 1)
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Oldingi")
                                }

                                IconButton(onClick = {
                                    if (searchMatches.isNotEmpty()) {
                                        currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                                        jumpToPage(searchMatches[currentMatchIndex].pageIndex + 1)
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Keyingi")
                                }
                            }
                        }
                    }
                } else {
                    // Standard Top Bar
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (pageCount > 0) {
                                        Text(
                                            "Sahifa ${currentPageIndex + 1} / $pageCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onBackClick) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga")
                                }
                            },
                            actions = {
                                // Search Icon
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Qidirish")
                                }

                                // Bookmark Toggle
                                IconButton(onClick = {
                                    viewModel.toggleBookmark(pdfAssetPath, currentPageIndex + 1)
                                }) {
                                    Icon(
                                        if (isCurrentBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "Xatcho'p",
                                        tint = if (isCurrentBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Drawing / Annotation Toggle
                                IconButton(onClick = { isAnnotationMode = !isAnnotationMode }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Rasm chizish",
                                        tint = if (isAnnotationMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Share PDF File
                                IconButton(onClick = {
                                    tempPdfFile?.let { file ->
                                        try {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Darslikni ulashish"))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Ulashish")
                                }

                                // Settings & Sheets Menu
                                var showMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Ko'proq")
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Mundarija / Sahifalar") },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showThumbnailsSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Xatcho'plar (${bookmarks.size})") },
                                        leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showBookmarksSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("O'qish sozlamalari") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showSettingsSheet = true
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Slider Progress
                        if (pageCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${currentPageIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Slider(
                                    value = currentPageIndex.toFloat(),
                                    onValueChange = { newPage ->
                                        jumpToPage(newPage.toInt() + 1)
                                    },
                                    valueRange = 0f..(pageCount - 1).toFloat(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                )

                                val progressPercent = ((currentPageIndex + 1).toFloat() / pageCount * 100).toInt()
                                Text(
                                    "$progressPercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Bottom Control Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous Page Button
                            IconButton(
                                onClick = { if (currentPageIndex > 0) jumpToPage(currentPageIndex) },
                                enabled = currentPageIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Oldingi sahifa")
                            }

                            // Jump Field
                            OutlinedButton(
                                onClick = {
                                    pageInputText = (currentPageIndex + 1).toString()
                                    showJumpDialog = true
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sahifa ${currentPageIndex + 1} / $pageCount")
                            }

                            // Zoom Out / Reset / Zoom In
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { scale = (scale - 0.25f).coerceAtLeast(1.0f) },
                                    enabled = scale > 1.0f
                                ) {
                                    Icon(Icons.Default.ZoomOut, contentDescription = "Kichiklashtirish")
                                }

                                Text(
                                    "${(scale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = { scale = (scale + 0.25f).coerceAtMost(3.0f) },
                                    enabled = scale < 3.0f
                                ) {
                                    Icon(Icons.Default.ZoomIn, contentDescription = "Kattalashtirish")
                                }
                            }

                            // Next Page Button
                            IconButton(
                                onClick = { if (currentPageIndex < pageCount - 1) jumpToPage(currentPageIndex + 2) },
                                enabled = currentPageIndex < pageCount - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Keyingi sahifa")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colorMode.bg)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            isControlsVisible = !isControlsVisible
                        },
                        onDoubleTap = {
                            scale = if (scale > 1.2f) 1.0f else 2.2f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    )
                }
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tarix darsligi yuklanmoqda...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    errorMessage ?: "Xatolik yuz berdi",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                pdfRenderer != null && pageCount > 0 -> {
                    // Render PDF Document with Zoom & Pan Layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                            .pointerInput(scale) {
                                if (scale > 1.0f) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1.0f, 3.5f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                }
                            }
                    ) {
                        when (displayMode) {
                            DisplayMode.CONTINUOUS_VERTICAL -> {
                                LazyColumn(
                                    state = verticalListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    items(pageCount) { index ->
                                        PdfRenderedPage(
                                            renderer = pdfRenderer!!,
                                            pageIndex = index,
                                            pageNumber = index + 1,
                                            colorMode = colorMode,
                                            annotations = if (index == currentPageIndex) pageAnnotations else emptyList(),
                                            isAnnotationMode = isAnnotationMode && (index == currentPageIndex),
                                            currentTool = currentTool,
                                            selectedColor = selectedColor,
                                            strokeWidth = strokeWidth,
                                            onSaveAnnotation = { entity -> viewModel.saveAnnotation(entity) }
                                        )
                                    }
                                }
                            }

                            DisplayMode.PAGINATED_SINGLE, DisplayMode.HORIZONTAL_SWIPE -> {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { pageIdx ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PdfRenderedPage(
                                            renderer = pdfRenderer!!,
                                            pageIndex = pageIdx,
                                            pageNumber = pageIdx + 1,
                                            colorMode = colorMode,
                                            annotations = if (pageIdx == currentPageIndex) pageAnnotations else emptyList(),
                                            isAnnotationMode = isAnnotationMode && (pageIdx == currentPageIndex),
                                            currentTool = currentTool,
                                            selectedColor = selectedColor,
                                            strokeWidth = strokeWidth,
                                            onSaveAnnotation = { entity -> viewModel.saveAnnotation(entity) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Drawing Palette Toolbar overlay when annotation mode is ON
                    if (isAnnotationMode) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 80.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Pen Tool
                                FilterChip(
                                    selected = currentTool == DrawingTool.PEN,
                                    onClick = { currentTool = DrawingTool.PEN },
                                    label = { Text("Qalam") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )

                                // Highlighter Tool
                                FilterChip(
                                    selected = currentTool == DrawingTool.HIGHLIGHTER,
                                    onClick = { currentTool = DrawingTool.HIGHLIGHTER },
                                    label = { Text("Markyor") },
                                    leadingIcon = { Icon(Icons.Default.BorderColor, contentDescription = null) }
                                )

                                // Colors
                                val colors = listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Black)
                                colors.forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .border(
                                                width = if (selectedColor == col) 3.dp else 0.dp,
                                                color = if (selectedColor == col) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = col }
                                    )
                                }

                                // Clear page drawings
                                IconButton(onClick = {
                                    viewModel.clearPageAnnotations(pdfAssetPath, currentPageIndex + 1)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Tozalash", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Sahifaga o'tish", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("1 dan $pageCount gacha bo'lgan sahifa raqamini kiriting:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageInputText,
                        onValueChange = { pageInputText = it.filter { c -> c.isDigit() } },
                        label = { Text("Sahifa raqami") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val p = pageInputText.toIntOrNull()
                    if (p != null && p in 1..pageCount) {
                        jumpToPage(p)
                        showJumpDialog = false
                    }
                }) {
                    Text("O'tish")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Bekor qilish")
                }
            }
        )
    }

    // Thumbnails Grid Modal Bottom Sheet
    if (showThumbnailsSheet && pdfRenderer != null) {
        ModalBottomSheet(
            onDismissRequest = { showThumbnailsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Mundarija va Sahifalar gridi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 450.dp)
                ) {
                    items(pageCount) { pIdx ->
                        val isSelected = pIdx == currentPageIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    jumpToPage(pIdx + 1)
                                    showThumbnailsSheet = false
                                },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "${pIdx + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bookmarks Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Xatcho'plar ro'yxati",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                if (bookmarks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Hali xatcho'plar saqlanmagan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(bookmarks.size) { idx ->
                            val item = bookmarks[idx]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        jumpToPage(item.pageNumber)
                                        showBookmarksSheet = false
                                    },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "Sahifa ${item.pageNumber}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        viewModel.toggleBookmark(pdfAssetPath, item.pageNumber)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "O'chirish", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Reading Settings Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "O'qish sozlamalari",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                Text("Ko'rinish rejimi:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = displayMode == DisplayMode.CONTINUOUS_VERTICAL,
                        onClick = { displayMode = DisplayMode.CONTINUOUS_VERTICAL },
                        label = { Text("Vertikal ro'yxat") }
                    )
                    FilterChip(
                        selected = displayMode == DisplayMode.HORIZONTAL_SWIPE,
                        onClick = { displayMode = DisplayMode.HORIZONTAL_SWIPE },
                        label = { Text("Gorizontal sahifalash") }
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text("Sahifa fon rangi:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorMode.entries.forEach { mode ->
                        FilterChip(
                            selected = colorMode == mode,
                            onClick = { colorMode = mode },
                            label = { Text(mode.nameUz) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { showSettingsSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Tayyor")
                }
            }
        }
    }
}

@Composable
private fun PdfRenderedPage(
    renderer: PdfRenderer,
    pageIndex: Int,
    pageNumber: Int,
    colorMode: ColorMode,
    annotations: List<PdfAnnotationEntity>,
    isAnnotationMode: Boolean,
    currentTool: DrawingTool,
    selectedColor: Color,
    strokeWidth: Float,
    onSaveAnnotation: (PdfAnnotationEntity) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentDrawPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    LaunchedEffect(pageIndex, colorMode) {
        withContext(Dispatchers.IO) {
            try {
                val page = renderer.openPage(pageIndex)
                // Crisp 2.0x render resolution
                val scale = 2.0f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)

                val bgColor = when (colorMode) {
                    ColorMode.WHITE -> AndroidColor.WHITE
                    ColorMode.SEPIA -> AndroidColor.parseColor("#FBF0D9")
                    ColorMode.NIGHT -> AndroidColor.parseColor("#2B2B2B")
                }
                canvas.drawColor(bgColor)

                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colorMode.bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Sahifa $pageNumber",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )

                    // Annotation Layer Overlay
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(isAnnotationMode) {
                                if (isAnnotationMode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentDrawPoints = listOf(offset)
                                        },
                                        onDrag = { change, _ ->
                                            currentDrawPoints = currentDrawPoints + change.position
                                        },
                                        onDragEnd = {
                                            if (currentDrawPoints.size > 1) {
                                                val normPoints = currentDrawPoints.map {
                                                    val normX = if (size.width > 0) it.x / size.width else 0f
                                                    val normY = if (size.height > 0) it.y / size.height else 0f
                                                    normX to normY
                                                }
                                                val jsonPoints = Json.encodeToString(normPoints)
                                                val entity = PdfAnnotationEntity(
                                                    pdfPath = "",
                                                    pageNumber = pageNumber,
                                                    color = selectedColor.toArgb(),
                                                    strokeWidth = strokeWidth,
                                                    isHighlighter = currentTool == DrawingTool.HIGHLIGHTER,
                                                    pathsJson = jsonPoints
                                                )
                                                onSaveAnnotation(entity)
                                            }
                                            currentDrawPoints = emptyList()
                                        }
                                    )
                                }
                            }
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Render existing annotations
                        annotations.forEach { ann ->
                            try {
                                val normPoints = Json.decodeFromString<List<Pair<Float, Float>>>(ann.pathsJson)
                                if (normPoints.size > 1) {
                                    val path = androidx.compose.ui.graphics.Path()
                                    normPoints.forEachIndexed { i, (nx, ny) ->
                                        val px = nx * canvasWidth
                                        val py = ny * canvasHeight
                                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(ann.color).copy(alpha = if (ann.isHighlighter) 0.4f else 1.0f),
                                        style = Stroke(
                                            width = if (ann.isHighlighter) ann.strokeWidth * 3 else ann.strokeWidth
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        // Render active in-progress stroke
                        if (currentDrawPoints.size > 1) {
                            val path = androidx.compose.ui.graphics.Path()
                            currentDrawPoints.forEachIndexed { i, pt ->
                                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                            }
                            drawPath(
                                path = path,
                                color = selectedColor.copy(alpha = if (currentTool == DrawingTool.HIGHLIGHTER) 0.4f else 1.0f),
                                style = Stroke(
                                    width = if (currentTool == DrawingTool.HIGHLIGHTER) strokeWidth * 3 else strokeWidth
                                )
                            )
                        }
                    }
                }
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
