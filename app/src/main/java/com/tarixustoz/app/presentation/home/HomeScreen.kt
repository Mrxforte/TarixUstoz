package com.tarixustoz.app.presentation.home

import android.app.Activity
import android.widget.Toast
import com.tarixustoz.app.R
import com.tarixustoz.app.ads.findActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.ads.AdMobManager
import com.tarixustoz.app.domain.model.Topic
import com.tarixustoz.app.domain.model.gradeId
import com.tarixustoz.app.presentation.common.components.TestSectionRow
import com.tarixustoz.app.presentation.tests.TestCardItem
import com.tarixustoz.app.presentation.tests.toTestCardItem
import com.tarixustoz.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToQuiz: (category: String?) -> Unit,
    onStartDirectionQuiz: (directionCode: String) -> Unit,
    onNavigateToMistakes: () -> Unit,
    onNavigateToTextbooks: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToTestsHub: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToTopicSelection: () -> Unit,
    onNavigateToVerticalCards: (title: String, categoryType: String) -> Unit = { _, _ -> },
    onNavigateToTickets: (categoryKey: String, categoryTitle: String) -> Unit = { _, _ -> },
    onStartQuizForBookOrTopic: (id: String) -> Unit = {},
    onContinueLastQuiz: (route: String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    val homeTopicCards by viewModel.homeTopicCards.collectAsState()
    val homeGradeCards by viewModel.homeGradeCards.collectAsState()
    val homeDirectionCards by viewModel.homeDirectionCards.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val targetRoute = uiState.globalContinueRoute.ifBlank {
                        "quiz/ticket_EVENT_1?time=20&grade=0&shuffle=false"
                    }
                    onContinueLastQuiz(targetRoute)
                },
                shape = CircleShape,
                containerColor = BrandPurple,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                ),
                modifier = Modifier
                    .size(58.dp)
                    .testTag("home_global_continue_play_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Oxirgi kelgan joydan davom ettirish",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple,
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("home_user_avatar")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = uiState.userAvatar,
                                    color = Color.White,
                                    fontSize = if (uiState.userAvatar.length > 2) 18.sp else 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = uiState.greeting,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    // To'plangan jami yulduzlar ko'rsatkichi (Home screen tepasida)
                    Surface(
                        onClick = onNavigateToLeaderboard,
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        border = BorderStroke(1.2.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(38.dp)
                            .testTag("home_total_stars_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "To'plangan yulduzlar",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = formatCompactStarsCount(uiState.totalStars),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }

                    Surface(
                        onClick = onNavigateToNotifications,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .minimumInteractiveComponentSize()
                            .size(44.dp)
                            .testTag("home_notifications_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BadgedBox(
                                badge = {
                                    if (uiState.unreadNotifications > 0) {
                                        Badge(
                                            containerColor = Color(0xFFEF4444),
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                "${uiState.unreadNotifications}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = "Bildirishnomalar",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    viewModel.loadDirectionCounts(forceRefresh = true)
                    viewModel.loadNextDailyQuestion()
                    isRefreshing = false
                }
            },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = isRefreshing,
                    color = BrandPurple,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

            // 2. Hero Carousel: Har doim 1-slaydda AdMob reklama (~350x250dp, ochiq joysiz) + 20 ta HD tarixiy slayd (Qadimgi dunyodan bugungi kungacha)
            item {
                HeroAdAndHistoryCarousel()
            }

            // 3. Kun savoli (Daily Question)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Kun savoli",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Har kuni yangi bilim va savol",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.loadNextDailyQuestion() },
                            enabled = !uiState.isDailyLoading,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_daily_refresh_button")
                        ) {
                            if (uiState.isDailyLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = BrandPurple,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Yangi savol olmoq",
                                    tint = BrandPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Countdown Timer Pill
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = uiState.dailyTimeRemaining,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Kun savoli Card
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_daily_question_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val question = uiState.dailyQuestion
                        val dailyBadge = if (question != null) {
                            val subjLabel = when {
                                question.gradeId == 6 -> "Qadimgi dunyo"
                                question.subjectId.contains("jahon", ignoreCase = true) -> "Jahon tarixi"
                                else -> "O'zbekiston tarixi"
                            }
                            "${question.gradeId}-sinf • $subjLabel"
                        } else {
                            "6-11 sinf • O'zbekiston & Jahon"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandPurple.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = dailyBadge,
                                    color = BrandPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "+10 XP",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (uiState.isDailyLoading && question != null) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = BrandPurple,
                                trackColor = BrandPurple.copy(alpha = 0.14f)
                            )
                            Spacer(Modifier.height(8.dp))
                        } else {
                            Spacer(Modifier.height(2.dp))
                        }

                        val options = question?.options ?: emptyList()
                        val optionLetters = listOf("A", "B", "C", "D")

                        if (uiState.isDailyLoading && question == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = BrandPurple,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Kun savoli yuklanmoqda...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (question != null) {
                            Text(
                                text = question.cleanText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )

                            Spacer(Modifier.height(14.dp))
                        }

                        if (options.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEachIndexed { idx, opt ->
                                    val letter = optionLetters.getOrElse(idx) { "" }
                                    val isSelected = uiState.selectedDailyOptionId == opt.id
                                    val isCorrect = opt.isCorrect

                                    val cardBg = when {
                                        uiState.isDailyAnswered && isCorrect -> Color(0xFF16A34A).copy(alpha = 0.15f)
                                        uiState.isDailyAnswered && isSelected && !isCorrect -> Color(0xFFDC2626).copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }

                                    val borderColor = when {
                                        uiState.isDailyAnswered && isCorrect -> Color(0xFF16A34A)
                                        uiState.isDailyAnswered && isSelected && !isCorrect -> Color(0xFFDC2626)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }

                                    val textColor = when {
                                        uiState.isDailyAnswered && isCorrect -> Color(0xFF16A34A)
                                        uiState.isDailyAnswered && isSelected && !isCorrect -> Color(0xFFEF4444)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardBg,
                                        border = BorderStroke(1.dp, borderColor),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !uiState.isDailyAnswered) {
                                                viewModel.answerDailyQuestion(opt.id)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (uiState.isDailyAnswered && isCorrect) Color(0xFF16A34A) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, borderColor),
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = letter,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (uiState.isDailyAnswered && isCorrect) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = opt.text,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || (uiState.isDailyAnswered && isCorrect)) FontWeight.Bold else FontWeight.Normal,
                                                color = textColor,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (uiState.isDailyAnswered && isCorrect) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "To'g'ri",
                                                    tint = Color(0xFF16A34A),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else if (uiState.isDailyAnswered && isSelected && !isCorrect) {
                                                Icon(
                                                    Icons.Default.Cancel,
                                                    contentDescription = "Noto'g'ri",
                                                    tint = Color(0xFFDC2626),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Feedback Banner & Explanation
                        if (uiState.isDailyAnswered && !uiState.dailyAnswerFeedback.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.isDailyCorrect) Color(0xFF16A34A).copy(alpha = 0.14f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = uiState.dailyAnswerFeedback!!,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (uiState.isDailyCorrect) Color(0xFF16A34A) else Color(0xFFD97706)
                                    )
                                    val expText = question?.explanation.orEmpty()
                                    if (expText.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = expText,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Action Button: Either Next/New Daily Question or Start Full Quiz
                        if (uiState.isDailyAnswered) {
                            Button(
                                onClick = { viewModel.loadNextDailyQuestion() },
                                enabled = !uiState.isDailyLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("home_next_daily_button")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Yangi kun savolini yechish",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.loadNextDailyQuestion() },
                                enabled = !uiState.isDailyLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("home_daily_answer_button")
                            ) {
                                Text(
                                    text = "Tasodifiy savol / Yangi test",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Tarixiy Yo'nalishlar Horizontal Row (Cards like Image #2)
            item {
                TestSectionRow(
                    sectionTitle = "Tarixiy yo'nalishli testlar",
                    items = homeDirectionCards,
                    onSeeAllClick = { onNavigateToVerticalCards("Tarixiy Yo'nalishlar", "directions") },
                    onCardClick = { code, _, title -> onNavigateToTickets(code, title) }
                )
            }

            // 5. Mavzulashgan testlar Horizontal Row (Cards like Image #2)
            item {
                TestSectionRow(
                    sectionTitle = "Mavzulashgan testlar",
                    items = homeTopicCards,
                    onSeeAllClick = { onNavigateToVerticalCards("Mavzulashgan Testlar", "topics") },
                    onCardClick = { topicId, _, title ->
                        onNavigateToTickets(topicId, title)
                    }
                )
            }

            // 6. Sinf testlari Horizontal Row (Cards like Image #2)
            item {
                TestSectionRow(
                    sectionTitle = "Sinf testlari (6-11 sinflar)",
                    items = homeGradeCards,
                    onSeeAllClick = { onNavigateToVerticalCards("Sinf Testlari", "grades") },
                    onCardClick = { gradeKey, _, gradeTitle ->
                        onNavigateToTickets(gradeKey, gradeTitle)
                    }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * Yulduzlar soni mingga borsa `k` bilan (masalan: `1k`, `1.2k`),
 * millionga borsa `m` bilan (masalan: `1m`, `1.5m`) ixcham formatlaydi.
 */
fun formatCompactStarsCount(count: Int): String {
    val safe = count.coerceAtLeast(0)
    return when {
        safe >= 1_000_000 -> {
            val whole = safe / 1_000_000
            val tenth = (safe % 1_000_000) / 100_000
            if (tenth == 0) "${whole}m" else "${whole}.${tenth}m"
        }
        safe >= 1_000 -> {
            val whole = safe / 1_000
            val tenth = (safe % 1_000) / 100
            if (tenth == 0) "${whole}k" else "${whole}.${tenth}k"
        }
        else -> "$safe"
    }
}

/**
 * Foydalanuvchi tomonidan taqdim etilgan 11 ta tarixiy rasm (sarlavhasiz va tavsifsiz faqat to'liq rasm).
 */
private val HERO_USER_SLIDE_IMAGES: List<Int> = listOf(
    R.drawable.hero_user_slide_1,
    R.drawable.hero_user_slide_2,
    R.drawable.hero_user_slide_3,
    R.drawable.hero_user_slide_4,
    R.drawable.hero_user_slide_5,
    R.drawable.hero_user_slide_6,
    R.drawable.hero_user_slide_7,
    R.drawable.hero_user_slide_8,
    R.drawable.hero_user_slide_9,
    R.drawable.hero_user_slide_10,
    R.drawable.hero_user_slide_11
)

@Composable
private fun HeroAdAndHistoryCarousel() {
    // 0-sahifa = Har doim birinchi ochiladigan AdMob Reklama (~350x250dp, kamida 10 soniya turadi),
    // 1..11-sahifalar = Foydalanuvchi bergan 11 ta tarixiy rasm har safar va har aylanada RANDOM (shuffled) tartibda chiqadi.
    var shuffledImages by remember { mutableStateOf(HERO_USER_SLIDE_IMAGES.shuffled()) }
    val totalPages = 1 + shuffledImages.size
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalPages })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val coroutineScope = rememberCoroutineScope()
    val wrapTransitionAlpha = remember { Animatable(1f) }
    val smoothSlideEasing = remember { CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f) }

    LaunchedEffect(pagerState.settledPage, isDragged) {
        if (!isDragged) {
            val current = pagerState.settledPage
            val waitMs = if (current == 0) 11_000L else 5_500L
            delay(waitMs)
            val nextPage = (current + 1) % totalPages
            if (nextPage == 0) {
                // Oxirgi slayddan 0-slaydga qaytganda orqaga 11 ta rasmni miltillatib aylantirmasdan,
                // mayin cross-fade animatsiyasi bilan o'tib, rasmlar tartibini yangidan random qilamiz.
                wrapTransitionAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                )
                shuffledImages = HERO_USER_SLIDE_IMAGES.shuffled()
                pagerState.scrollToPage(0)
                wrapTransitionAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 480, easing = LinearOutSlowInEasing)
                )
            } else {
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(durationMillis = 900, easing = smoothSlideEasing)
                )
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(18.dp))
                .testTag("home_hero_carousel")
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = wrapTransitionAlpha.value
                    }
            ) { page ->
                val rawPageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = rawPageOffset.absoluteValue.coerceIn(0f, 1f)
                val pageAlpha = lerp(0.35f, 1f, 1f - absOffset)
                val pageScale = lerp(0.92f, 1f, 1f - absOffset)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = pageAlpha
                            scaleX = pageScale
                            scaleY = pageScale
                        }
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    if (page == 0) {
                        // 0-slayd: Har doim birinchi ochiladigan ~350x250dp AdMob Hero Banner (kamida 10 soniya)
                        AdMobManager.DedicatedHeroBannerAdView(
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val imageRes = shuffledImages[(page - 1).coerceIn(0, shuffledImages.lastIndex)]
                        val isCurrentSlide = pagerState.currentPage == page
                        val subtleZoom by animateFloatAsState(
                            targetValue = if (isCurrentSlide) 1.045f else 1.0f,
                            animationSpec = tween(durationMillis = 5200, easing = LinearOutSlowInEasing),
                            label = "heroImageCinematicZoom"
                        )
                        Image(
                            painter = painterResource(id = imageRes),
                            contentDescription = "Tarixiy slayd $page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = subtleZoom
                                    scaleY = subtleZoom
                                }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Dots Indicator — rasmdan tashqarida, bannerning pastki qismida
        Row(
            modifier = Modifier
                .testTag("home_hero_dots_indicator")
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (dotIndex in 0 until totalPages) {
                val isSelected = pagerState.currentPage == dotIndex
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 20.dp else 7.dp,
                    animationSpec = tween(durationMillis = 300),
                    label = "heroDotWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = when {
                        isSelected && dotIndex == 0 -> Color(0xFFF59E0B)
                        isSelected -> BrandPurple
                        dotIndex == 0 -> Color(0xFFF59E0B).copy(alpha = 0.35f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    animationSpec = tween(durationMillis = 300),
                    label = "heroDotColor"
                )
                Box(
                    modifier = Modifier
                        .height(7.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(dotColor)
                        .clickable {
                            coroutineScope.launch {
                                val distance = (pagerState.currentPage - dotIndex).absoluteValue
                                if (distance > 2) {
                                    wrapTransitionAlpha.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                                    )
                                    pagerState.scrollToPage(dotIndex)
                                    wrapTransitionAlpha.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
                                    )
                                } else {
                                    pagerState.animateScrollToPage(
                                        page = dotIndex,
                                        animationSpec = tween(durationMillis = 650, easing = smoothSlideEasing)
                                    )
                                }
                            }
                        }
                )
            }
        }
    }
}


