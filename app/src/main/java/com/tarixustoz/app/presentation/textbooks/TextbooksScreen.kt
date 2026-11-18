package com.tarixustoz.app.presentation.textbooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tarixustoz.app.data.remote.DownloadState
import com.tarixustoz.app.data.remote.MediaUrls
import com.tarixustoz.app.data.remote.PdfDownloader
import com.tarixustoz.app.domain.model.Textbook
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextbooksScreen(
    onTopicSelected: (String) -> Unit,
    onOpenPdf: (title: String, pdfAsset: String) -> Unit,
    viewModel: TextbooksViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val textbooks by viewModel.textbooks.collectAsState()
    val selectedGrade by viewModel.selectedGrade.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val downloadProgressMap = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                // Header
                Column {
                    Text(
                        "Darsliklar",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "6-11 sinf tarix darsliklari (Ekranni pastga tortib yangilang)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Grade Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedGrade == 0,
                            onClick = { viewModel.setGrade(0) },
                            label = { Text("Barchasi") }
                        )
                    }
                    items((6..11).toList()) { grade ->
                        FilterChip(
                            selected = selectedGrade == grade,
                            onClick = { viewModel.setGrade(grade) },
                            label = { Text("$grade-sinf") }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                val sortedTextbooks = remember(textbooks) {
                    textbooks.sortedWith(
                        compareBy<Textbook> { it.gradeId }
                            .thenBy { if (it.subjectId.contains("jahon")) 1 else 0 }
                            .thenBy { it.title }
                    )
                }

                // 2-Column Grid
                if (sortedTextbooks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (selectedGrade == 0) "Darsliklar topilmadi" else "$selectedGrade-sinf bo'yicha darsliklar topilmadi",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ekranni pastga tortib darsliklarni qayta yuklang 🔄",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(sortedTextbooks) { textbook ->
                            val fallbackPdfUrl = MediaUrls.textbook(textbook.gradeId, textbook.subjectId)
                            val pdfPath = textbook.pdfAsset?.takeIf { it.isNotBlank() } ?: fallbackPdfUrl
                            val currentProgress = downloadProgressMap[textbook.id]

                            TextbookGridCard(
                                textbook = textbook,
                                downloadProgress = currentProgress,
                                onClick = {
                                    if (currentProgress != null) {
                                        // Already downloading
                                        return@TextbookGridCard
                                    }

                                    if (PdfDownloader.isPdfDownloaded(context, pdfPath)) {
                                        val localFile = PdfDownloader.getLocalPdfFile(context, pdfPath)
                                        val finalPath = if (localFile.exists() && localFile.length() > 50000L) {
                                            localFile.absolutePath
                                        } else {
                                            pdfPath
                                        }
                                        onOpenPdf(textbook.title, finalPath)
                                    } else {
                                        // Download with progress - DO NOT open PDF screen until 100% completed
                                        downloadProgressMap[textbook.id] = 0
                                        coroutineScope.launch {
                                            PdfDownloader.downloadPdf(context, pdfPath).collect { state ->
                                                when (state) {
                                                    is DownloadState.Downloading -> {
                                                        downloadProgressMap[textbook.id] = state.progress
                                                        snackbarHostState.showSnackbar(
                                                            message = "«${textbook.title}» darsligi yuklanmoqda: ${state.progress}%",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                    }
                                                    is DownloadState.Success -> {
                                                        downloadProgressMap.remove(textbook.id)
                                                        snackbarHostState.showSnackbar(
                                                            message = "«${textbook.title}» darsligi muvaffaqiyatli yuklandi! Ochilmoqda...",
                                                            duration = SnackbarDuration.Short
                                                        )
                                                        onOpenPdf(textbook.title, state.file.absolutePath)
                                                    }
                                                    is DownloadState.Error -> {
                                                        downloadProgressMap.remove(textbook.id)
                                                        snackbarHostState.showSnackbar(
                                                            message = state.message,
                                                            duration = SnackbarDuration.Long
                                                        )
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TextbookGridCard(
    textbook: Textbook,
    downloadProgress: Int? = null,
    onClick: () -> Unit
) {
    val coverUrl = textbook.coverAsset ?: "file:///android_asset/images/${textbook.id}_cover.png"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Cover Image Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = textbook.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Book,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Radial Progress Overlay when Downloading
                if (downloadProgress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.70f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.3f),
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(46.dp)
                                )
                                Text(
                                    text = "$downloadProgress%",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Yuklanmoqda...",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Top badges over cover
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ) {
                        Text(
                            "${textbook.gradeId}-SINF",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (textbook.subjectId == "uzbekiston") Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                    ) {
                        Text(
                            if (textbook.subjectId == "uzbekiston") "O‘zbekiston" else "Jahon",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (textbook.subjectId == "uzbekiston") Color(0xFF2E7D32) else Color(0xFF1565C0)
                        )
                    }
                }
            }

            // Book Information
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = textbook.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${textbook.totalTopics} mavzu",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.QuestionAnswer,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${textbook.totalQuestions}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
