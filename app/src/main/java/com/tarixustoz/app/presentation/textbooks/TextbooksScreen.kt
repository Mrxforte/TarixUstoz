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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.tarixustoz.app.domain.model.Textbook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextbooksScreen(
    onTopicSelected: (String) -> Unit,
    onOpenPdf: (title: String, pdfAsset: String) -> Unit,
    viewModel: TextbooksViewModel = hiltViewModel()
) {
    val textbooks by viewModel.textbooks.collectAsState()
    val selectedGrade by viewModel.selectedGrade.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Darsliklar",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "6-11 sinf tarix darsliklari (PDF va API)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showResetDialog = true }) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Yangilash")
                }
            }
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
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("API dan darsliklarni qayta yuklash")
                    }
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
                    val subjectFileName = if (textbook.gradeId == 6) "Qadimgi_dunyo_tarixi" else if (textbook.subjectId.contains("jahon", ignoreCase = true)) "Jahon_tarixi" else "Ozbekiston_tarixi"
                    val fallbackPdfUrl = "http://10.0.2.2:8000/media/pdfs/${textbook.gradeId}-sinf_$subjectFileName.pdf"
                    val pdfPath = textbook.pdfAsset?.takeIf { it.isNotBlank() } ?: fallbackPdfUrl

                    TextbookGridCard(
                        textbook = textbook,
                        onClick = {
                            onOpenPdf(textbook.title, pdfPath)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Darsliklarni API orqali yangilash") },
            text = { Text("Barcha darsliklar server API orqali qayta yuklanadi va yangilanadi. Davom ettirasizmi?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.load()
                    }
                ) {
                    Text("Ha, yangilash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Bekor qilish")
                }
            }
        )
    }
}

@Composable
private fun TextbookGridCard(
    textbook: Textbook,
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
                            color = if (textbook.subjectId == "uzbekiston") Color(0xFF2E7D32) else Color(0xFF1565C0),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Book Details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    textbook.title,
                    style = MaterialTheme.typography.titleMedium,
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
