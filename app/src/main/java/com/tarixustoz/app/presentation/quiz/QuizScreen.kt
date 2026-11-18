package com.tarixustoz.app.presentation.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.ads.AdMobManager
import com.tarixustoz.app.domain.model.AnswerOption
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun QuizScreen(
    topicId: String?,
    customCount: Int? = null,
    customCategory: String? = null,
    customDifficulty: String? = null,
    timeLimitMinutes: Int? = null,
    selectedTopicIds: List<String>? = null,
    explicitGrade: Int? = null,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(topicId, customCount, customCategory, customDifficulty, explicitGrade) {
        viewModel.startQuiz(
            topicId = topicId,
            customCount = customCount,
            customCategory = customCategory,
            customDifficulty = customDifficulty,
            timeLimitMinutes = timeLimitMinutes,
            selectedTopicIds = selectedTopicIds,
            explicitGrade = explicitGrade
        )
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
                timeSpentSeconds = uiState.timeSpentSeconds,
                onRetry = {
                    viewModel.startQuiz(
                        topicId = topicId,
                        customCount = customCount,
                        customCategory = customCategory,
                        customDifficulty = customDifficulty,
                        timeLimitMinutes = timeLimitMinutes,
                        selectedTopicIds = selectedTopicIds,
                        explicitGrade = explicitGrade
                    )
                }
            )
        }
        uiState.questions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Savollar topilmadi. Avval darslikni tanlang.")
            }
        }
        else -> {
            val question = uiState.questions[uiState.currentIndex]
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Scrollable Question Content & Action Buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    uiState.answeredCorrectly.forEachIndexed { index, wasCorrect ->
                        val indicatorColor = when (wasCorrect) {
                            true -> Color(0xFF16A085)
                            false -> Color(0xFFDC2626)
                            null -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val indicatorBorder = if (index == uiState.currentIndex && wasCorrect == null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(indicatorColor)
                                .border(1.dp, indicatorBorder, RoundedCornerShape(4.dp))
                        )
                    }
                }
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
                            question.cleanText,
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

            // Responsive Bottom AdMob Banner Container filling space below button
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AdMobManager.BannerAdView(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
}
}

@Composable
private fun ResultScreen(
    correct: Int,
    total: Int,
    timeSpentSeconds: Int,
    onRetry: () -> Unit
) {
    val incorrect = (total - correct).coerceAtLeast(0)
    val accuracyPct = if (total > 0) (correct * 100f / total).roundToInt() else 0

    var startAnim by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "resultAnim"
    )

    LaunchedEffect(Unit) {
        startAnim = false
        delay(50)
        startAnim = true
    }

    val displayCorrect = (correct * animProgress).roundToInt()
    val displayIncorrect = (incorrect * animProgress).roundToInt()
    val displayTotal = (total * animProgress).roundToInt()
    val displayAccuracy = (accuracyPct * animProgress).roundToInt()
    val displaySeconds = (timeSpentSeconds * animProgress).roundToInt()

    val formattedTime = remember(displaySeconds) {
        val min = displaySeconds / 60
        val sec = displaySeconds % 60
        if (min > 0) "${min} daq ${sec} son" else "${sec} son"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Text(
            text = "Test Natijasi 🎯",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Ring Accuracy Chart
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(170.dp)
        ) {
            val primaryColor = BrandPurple
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            val successColor = Color(0xFF10B981)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
                val sweepAngle = 360f * (accuracyPct / 100f) * animProgress
                drawArc(
                    color = if (accuracyPct >= 70) successColor else primaryColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$displayAccuracy%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (accuracyPct >= 70) Color(0xFF10B981) else BrandPurple
                )
                Text(
                    text = "Aniqlik",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = if (accuracyPct >= 80) "Ajoyib natija! Barakalla! 🎉"
            else if (accuracyPct >= 60) "Yaxshi ko‘rsatkich, yana bir oz harakat qiling! 👍"
            else "Xatolar ustida ishlab bilimlarni oshiring! 💪",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 4 Metric Detail Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // To'g'ri javoblar
                ResultMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "To‘g‘ri javob",
                    value = "$displayCorrect ta",
                    subtitle = "$displayAccuracy%",
                    icon = Icons.Default.CheckCircle,
                    color = Color(0xFF10B981),
                    bgColor = Color(0xFFECFDF5)
                )

                // Xato javoblar
                ResultMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Xato javob",
                    value = "$displayIncorrect ta",
                    subtitle = "${(100 - accuracyPct).coerceAtLeast(0)}%",
                    icon = Icons.Default.Cancel,
                    color = Color(0xFFEF4444),
                    bgColor = Color(0xFFFEF2F2)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Sarflangan vaqt
                ResultMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Sarflangan vaqt",
                    value = formattedTime,
                    subtitle = "Vaqt tahlili",
                    icon = Icons.Default.Schedule,
                    color = Color(0xFF0284C7),
                    bgColor = Color(0xFFEFF6FF)
                )

                // Jami ishlangan testlar
                ResultMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Jami savol",
                    value = "$displayTotal ta",
                    subtitle = "Ishlangan test",
                    icon = Icons.Default.Quiz,
                    color = BrandPurple,
                    bgColor = Color(0xFFF5F3FF)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action Buttons
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Qayta urinish", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ResultMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    bgColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
                Text(subtitle, fontSize = 10.sp, color = color.copy(alpha = 0.8f))
            }
        }
    }
}
