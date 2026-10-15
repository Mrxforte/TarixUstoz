package com.tarixustoz.app.presentation.textbooks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.Textbook

@Composable
fun TextbooksScreen(
    onTopicSelected: (String) -> Unit,
    viewModel: TextbooksViewModel = hiltViewModel()
) {
    val textbooks by viewModel.textbooks.collectAsState()
    val selectedGrade by viewModel.selectedGrade.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Darsliklar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        // Grade filter
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (6..11).forEach { grade ->
                FilterChip(
                    selected = selectedGrade == grade,
                    onClick = { viewModel.setGrade(grade) },
                    label = { Text("$grade-sinf") }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(textbooks) { textbook ->
                TextbookCard(textbook) {
                    // For demo, navigate to first topic of this book
                    onTopicSelected("uz7_t1")
                }
            }
        }
    }
}

@Composable
private fun TextbookCard(textbook: Textbook, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(textbook.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${textbook.authors} · ${textbook.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${textbook.totalQuestions} ta savol · ${textbook.edition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
