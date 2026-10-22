package com.tarixustoz.app.presentation.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.domain.model.AnswerOption

@Composable
fun QuizScreen(
    topicId: String?,
    customCount: Int? = null,
    customCategory: String? = null,
    customDifficulty: String? = null,
    timeLimitMinutes: Int? = null,
    selectedTopicIds: List<String>? = null,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(topicId, customCount, customCategory, customDifficulty) {
        viewModel.startQuiz(topicId, customCount, customCategory, customDifficulty, timeLimitMinutes, selectedTopicIds)
    }

    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.isFinished -> {
            ResultScreen(
                correct = uiState.correctCount,
                total = uiState.questions.size,
                onRetry = { viewModel.startQuiz(topicId) }
            )
        }
        uiState.questions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Savollar topilmadi. Avval darslikni tanlang.")
            }
        }
        else -> {
            val question = uiState.questions[uiState.currentIndex]
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Progress
                LinearProgressIndicator(
                    progress = { (uiState.currentIndex + 1).toFloat() / uiState.questions.size },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Savol ${uiState.currentIndex + 1} / ${uiState.questions.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                // Question
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        if (question.historicalDate != null) {
                            AssistChip(
                                onClick = {},
                                label = { Text(question.historicalDate) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            question.text,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // Options
                question.options.forEach { option ->
                    val isSelected = uiState.selectedOptionId == option.id
                    val showResult = uiState.showFeedback
                    val isCorrect = option.isCorrect

                    val borderColor = when {
                        showResult && isCorrect -> Color(0xFF16A085)
                        showResult && isSelected && !isCorrect -> Color(0xFFDC2626)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val bgColor = when {
                        showResult && isCorrect -> Color(0xFFD5F5E3)
                        showResult && isSelected && !isCorrect -> Color(0xFFFEE2E2)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable(enabled = !uiState.showFeedback) {
                                viewModel.selectOption(option.id)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = bgColor)
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                option.text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (showResult && isCorrect) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A085))
                            } else if (showResult && isSelected && !isCorrect) {
                                Icon(Icons.Default.Close, null, tint = Color(0xFFDC2626))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Feedback
                AnimatedVisibility(visible = uiState.showFeedback) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.wasCorrect) Color(0xFFD5F5E3)
                            else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (uiState.wasCorrect) "To‘g‘ri!" else "Noto‘g‘ri",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(question.explanation, style = MaterialTheme.typography.bodyMedium)
                            if (question.sourceReference != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Manba: ${question.sourceReference}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Buttons
                if (!uiState.showFeedback) {
                    Button(
                        onClick = { viewModel.submitAnswer() },
                        enabled = uiState.selectedOptionId != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Javobni tekshirish")
                    }
                } else {
                    Button(
                        onClick = { viewModel.nextQuestion() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (uiState.currentIndex < uiState.questions.lastIndex) "Keyingi savol" else "Natijani ko‘rish")
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultScreen(correct: Int, total: Int, onRetry: () -> Unit) {
    val accuracy = if (total > 0) correct.toFloat() / total else 0f
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Natija",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "$correct / $total",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Aniqlik: ${(accuracy * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Qayta urinish")
        }
    }
}
