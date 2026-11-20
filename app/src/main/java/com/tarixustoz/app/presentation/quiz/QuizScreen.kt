package com.tarixustoz.app.presentation.quiz

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.app.Activity
import com.tarixustoz.app.ads.AdMobManager
import com.tarixustoz.app.ads.findActivity
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    forceShuffle: Boolean = false,
    viewModel: QuizViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val activity: Activity? = remember(context) { context.findActivity() }
    var hasShownCompletionAd by remember(uiState.isFinished) { mutableStateOf(false) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished && !hasShownCompletionAd && activity != null) {
            hasShownCompletionAd = true
            AdMobManager.showCompletionAd(
                activity = activity,
                completedCount = uiState.ticketAdCounter
            )
        }
    }

    LaunchedEffect(topicId, customCount, customCategory, customDifficulty, explicitGrade, forceShuffle) {
        viewModel.startQuiz(
            topicId = topicId,
            customCount = customCount,
            customCategory = customCategory,
            customDifficulty = customDifficulty,
            timeLimitMinutes = timeLimitMinutes,
            selectedTopicIds = selectedTopicIds,
            explicitGrade = explicitGrade,
            forceShuffle = forceShuffle
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
                isTimedOut = uiState.isTimedOut,
                earnedStars = uiState.earnedStars,
                isTicketQuiz = uiState.isTicketQuiz,
                onRetry = {
                    viewModel.restartWithShuffle()
                },
                onNextQuiz = {
                    viewModel.restartWithNextQuiz()
                }
            )
        }
        uiState.questions.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Savollar topilmadi. Avval darslikni tanlang.")
            }
        }
        else -> {
            val question = uiState.questions.getOrNull(uiState.currentIndex)
            if (question == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@QuizScreen
            }
            val isQuestionFav = question.id in uiState.favoriteQuestionIds
            val remSec = uiState.remainingSeconds ?: 0
            val formattedCountdown = remember(remSec) {
                val mins = remSec / 60
                val secs = remSec % 60
                "%02d:%02d".format(mins, secs)
            }
            val isUrgentTime = remSec in 1..60

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val totalHeight = maxHeight
                    val isCompactHeight = totalHeight < 520.dp
                    val minAdaptiveBannerHeight = if (isCompactHeight) {
                        64.dp
                    } else {
                        (totalHeight * 0.22f).coerceIn(110.dp, 260.dp)
                    }
                    val actionButtonAllowance = 62.dp
                    val maxTopContentHeight = (totalHeight - minAdaptiveBannerHeight - actionButtonAllowance)
                        .coerceAtLeast(180.dp)

                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxSize()
                    ) {
                        // Top quiz content (timer, favorites, progress, question, options, feedback) bounded so button & adaptive ad never clip
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxTopContentHeight)
                                .verticalScroll(rememberScrollState())
                                .testTag("quiz_top_content_container")
                        ) {
                            // Top Row: Prominent Countdown Timer + Ticket Favorite + Question Favorite
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Countdown Timer Badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isUrgentTime) Color(0xFFFEE2E2) else BrandPurple.copy(alpha = 0.12f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isUrgentTime) Color(0xFFEF4444) else BrandPurple.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.testTag("quiz_top_timer_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Timer,
                                            contentDescription = "Qolgan vaqt",
                                            tint = if (isUrgentTime) Color(0xFFDC2626) else BrandPurple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = formattedCountdown,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = if (isUrgentTime) Color(0xFFDC2626) else BrandPurple
                                        )
                                    }
                                }

                                // Separate Favorites Actions: Ticket Favorite & Question Favorite
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (uiState.isTicketQuiz) {
                                        Surface(
                                            onClick = { viewModel.toggleCurrentTicketFavorite() },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (uiState.isCurrentTicketFavorite) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (uiState.isCurrentTicketFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant
                                            ),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 36.dp)
                                                .testTag("quiz_fav_ticket_btn")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (uiState.isCurrentTicketFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                    contentDescription = "Biletni sevimlilarga qo'shish",
                                                    tint = if (uiState.isCurrentTicketFavorite) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (uiState.isCurrentTicketFavorite) "Bilet ★" else "Bilet",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (uiState.isCurrentTicketFavorite) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        onClick = { viewModel.toggleCurrentQuestionFavorite() },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isQuestionFav) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isQuestionFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 36.dp)
                                            .testTag("quiz_fav_question_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isQuestionFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Savolni sevimlilarga qo'shish",
                                                tint = if (isQuestionFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isQuestionFav) "Savol ♥" else "Savol",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isQuestionFav) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Savol ${uiState.currentIndex + 1} / ${uiState.questions.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${((uiState.currentIndex + 1) * 100f / uiState.questions.size).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (uiState.currentIndex + 1).toFloat() / uiState.questions.size },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.height(6.dp))
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
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(indicatorColor)
                                            .border(1.dp, indicatorBorder, RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // Question Card
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    if (question.historicalDate != null) {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(question.historicalDate, fontSize = 11.sp) },
                                            modifier = Modifier.height(28.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    Text(
                                        question.cleanText,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            lineHeight = 21.sp
                                        ),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // Answer Options (each >= 48.dp touch target) with A/B/C/D badges
                            question.options.forEachIndexed { idx, option ->
                                val isSelected = uiState.selectedOptionId == option.id
                                val showResult = uiState.showFeedback
                                val isCorrect = option.isCorrect
                                val letterBadge = listOf("A", "B", "C", "D").getOrElse(idx) { option.id.uppercase() }

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
                                        .padding(vertical = 3.dp)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                                        .clickable(enabled = !uiState.showFeedback) {
                                            viewModel.selectOption(option.id)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = bgColor)
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = letterBadge,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            option.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 14.sp,
                                                lineHeight = 18.sp
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (showResult && isCorrect) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "To‘g‘ri javob",
                                                tint = Color(0xFF16A085)
                                            )
                                        } else if (showResult && isSelected && !isCorrect) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Noto‘g‘ri javob",
                                                tint = Color(0xFFDC2626)
                                            )
                                        }
                                    }
                                }
                            }

                            // Feedback Explanation Card
                            AnimatedVisibility(visible = uiState.showFeedback) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (uiState.wasCorrect) Color(0xFFD5F5E3)
                                        else MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            if (uiState.wasCorrect) "To‘g‘ri!" else "Noto‘g‘ri",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            question.explanation,
                                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp)
                                        )
                                        if (question.sourceReference != null) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "Manba: ${question.sourceReference}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Pinned Primary Action Button ("Javobni tekshirish" / "Keyingi savol") — never hidden inside scroll
                        if (!uiState.showFeedback) {
                            Button(
                                onClick = { viewModel.submitAnswer() },
                                enabled = uiState.selectedOptionId != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("quiz_check_answer_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Javobni tekshirish", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.nextQuestion() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("quiz_next_question_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    if (uiState.currentIndex < uiState.questions.lastIndex) "Keyingi savol"
                                    else "Natijani ko‘rish",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Adaptive AdMob Banner dynamically resizing to fill all remaining vertical space
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .heightIn(min = minAdaptiveBannerHeight)
                                .clip(RoundedCornerShape(14.dp))
                                .testTag("quiz_responsive_ad_container")
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AdMobManager.ResponsiveQuizBannerAdView(
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
    isTimedOut: Boolean,
    earnedStars: Int,
    isTicketQuiz: Boolean,
    onRetry: () -> Unit,
    onNextQuiz: () -> Unit
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isTimedOut) "Vaqt tugadi! ⏰" else "Test Natijasi 🎯",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isTimedOut) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
            )

            // 3-Star Rating Card (10 correct = 1 star within time limit; 0 stars if timed out)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isTimedOut) Color(0xFFFEF2F2) else Color(0xFFFFFBEB),
                border = BorderStroke(
                    1.5.dp,
                    if (isTimedOut) Color(0xFFFCA5A5) else Color(0xFFFDE68A)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiz_result_star_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (starIdx in 1..3) {
                            val isEarned = !isTimedOut && starIdx <= earnedStars
                            Icon(
                                imageVector = if (isEarned) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (isEarned) Color(0xFFF59E0B) else Color(0xFFD1D5DB),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (isTimedOut) {
                            "Belgilangan vaqtda tugallanmadi — 0 Yulduz! Qayta boshlang."
                        } else {
                            "$earnedStars / 3 Yulduz qo'lga kiritildi (Har 10 ta to'g'ri javob = 1 ⭐)"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTimedOut) Color(0xFFDC2626) else Color(0xFFB45309)
                    )
                }
            }

            // Ring Accuracy Chart
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
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
                        color = if (isTimedOut) Color(0xFFEF4444) else if (accuracyPct >= 70) successColor else primaryColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$displayAccuracy%",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isTimedOut) Color(0xFFEF4444) else if (accuracyPct >= 70) Color(0xFF10B981) else BrandPurple
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
                text = when {
                    isTimedOut -> "Vaqt tugagani sababli bilet hisoblanmadi. Savollar va A/B/C/D variantlar aralashtirilib qayta boshlanadi!"
                    accuracyPct >= 80 -> "Ajoyib natija! Barakalla! 🎉"
                    accuracyPct >= 60 -> "Yaxshi ko‘rsatkich, yana bir oz harakat qiling! 👍"
                    else -> "Xatolar ustida ishlab bilimlarni oshiring! 💪"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isTimedOut) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
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
                    ResultMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "To‘g‘ri javob",
                        value = "$displayCorrect ta",
                        subtitle = "$displayAccuracy%",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF10B981),
                        bgColor = Color(0xFFECFDF5)
                    )

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
                    ResultMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Sarflangan vaqt",
                        value = formattedTime,
                        subtitle = "Vaqt tahlili",
                        icon = Icons.Default.Schedule,
                        color = Color(0xFF0284C7),
                        bgColor = Color(0xFFEFF6FF)
                    )

                    ResultMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Yulduz reytingi",
                        value = "$earnedStars ⭐",
                        subtitle = if (isTimedOut) "Vaqt tugadi" else "10 to'g'ri = 1 ⭐",
                        icon = Icons.Default.Star,
                        color = Color(0xFFD97706),
                        bgColor = Color(0xFFFFFBEB)
                    )
                }
            }

            // Reklama va ilovani bepul saqlash haqida tushuntirish kartasi
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quiz_result_ad_explanation_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandPurple,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reklama loyihani bepul saqlashga yordam beradi 💙",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Qisqa reklamalar ilovadagi barcha 6-11 sinf darsliklari va 1.1 mln+ (1 100 000+) tarixiy testlarni har doim bepul ushlab turish hamda yangilab borish uchun xizmat qiladi. Qo‘llab-quvvatlaganingiz uchun rahmat! 🙏",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Action Buttons: Keyingi testga o'tish + Qayta ishlash
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("quiz_retry_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, BrandPurple)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = BrandPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isTimedOut) "Qayta boshlash" else "Qayta ishlash",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BrandPurple
                    )
                }

                Button(
                    onClick = onNextQuiz,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("quiz_next_test_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
                ) {
                    Text(
                        text = "Keyingi test",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }
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
