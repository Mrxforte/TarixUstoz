package com.tarixustoz.app.presentation.mistakes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MistakesScreen(
    viewModel: MistakesViewModel = hiltViewModel()
) {
    val count by viewModel.activeCount.collectAsState()
    val entities by viewModel.wrongEntities.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Bilmagan savollarim",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "$count ta savol takrorlashni kutmoqda",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (count == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Hozircha xato savollar yo‘q!", style = MaterialTheme.typography.titleMedium)
                    Text("Testlarda xato qilsangiz, ular shu yerda paydo bo‘ladi.")
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entities) { entity ->
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Savol ID: ${entity.questionId}", style = MaterialTheme.typography.titleSmall)
                            Text("Sizning javobingiz: ${entity.userAnswer}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Xato soni: ${entity.incorrectCount} · Status: ${entity.masteryStatus}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { /* retry */ }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Qayta urinish")
                            }
                        }
                    }
                }
            }
        }
    }
}
