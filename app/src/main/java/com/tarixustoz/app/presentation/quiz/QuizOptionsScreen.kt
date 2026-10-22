package com.tarixustoz.app.presentation.quiz

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.Difficulty
import com.tarixustoz.app.domain.model.QuestionCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizOptionsScreen(
    viewModel: QuizOptionsViewModel = hiltViewModel(),
    onStartQuiz: (Int, QuestionCategory, Difficulty, Int, List<String>) -> Unit
) {
    var selectedCount by remember { mutableStateOf(10) }
    var selectedCategory by remember { mutableStateOf(QuestionCategory.ALL) }
    var selectedDifficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var selectedTimeLimit by remember { mutableStateOf(0) } // 0 = no limit
    val selectedTopics = remember { mutableStateListOf<String>() }

    val allTopics by viewModel.topics.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                "Test sozlamalari",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Savollar soni
            Text("Savollar soni", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 20, 36).forEach { count ->
                    FilterChip(
                        selected = selectedCount == count,
                        onClick = { selectedCount = count },
                        label = { Text("$count ta") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Kategoriya
            Text("Turkum", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            QuestionCategory.entries.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                    Text("${cat.icon} ${cat.titleUz}", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Qiyinchilik
            Text("Qiyinchilik darajasi", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Difficulty.entries.forEach { diff ->
                    FilterChip(
                        selected = selectedDifficulty == diff,
                        onClick = { selectedDifficulty = diff },
                        label = { Text(diff.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Vaqt chegarasi
            Text("Vaqt (daqiqa)", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "Cheksiz", 10 to "10 daq", 20 to "20 daq", 36 to "36 daq").forEach { (min, label) ->
                    FilterChip(
                        selected = selectedTimeLimit == min,
                        onClick = { selectedTimeLimit = min },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Mavzularni tanlang (Hech biri tanlanmasa barchasidan)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(allTopics) { topic ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = selectedTopics.contains(topic.id),
                    onCheckedChange = { isChecked ->
                        if (isChecked) selectedTopics.add(topic.id)
                        else selectedTopics.remove(topic.id)
                    }
                )
                Text(topic.title, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    onStartQuiz(selectedCount, selectedCategory, selectedDifficulty, selectedTimeLimit, selectedTopics)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Testni boshlash", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
