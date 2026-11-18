package com.tarixustoz.app.presentation.home

import android.app.Activity
import android.widget.Toast
import com.tarixustoz.app.ads.findActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showNotificationSheet by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    val homeTopicCards by viewModel.homeTopicCards.collectAsState()
    val homeGradeCards by viewModel.homeGradeCards.collectAsState()
    val homeDirectionCards by viewModel.homeDirectionCards.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDirectionCounts()
        viewModel.load()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
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
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(42.dp)
                            .clickable { onNavigateToNotifications() }
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

            // 2. 100% Dedicated AdMob Banner Card (Hero Slot - 40% Screen Height)
            item {
                val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
                val heroBannerHeight = (screenHeightDp * 0.40f).coerceIn(240.dp, 320.dp)

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroBannerHeight)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AdMobManager.DedicatedHeroBannerAdView(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Yangi savol olmoq",
                                tint = BrandPurple,
                                modifier = Modifier.size(20.dp)
                            )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEEF2FF)
                            ) {
                                Text(
                                    text = "6-11 sinf • O'zbekiston & Jahon",
                                    color = BrandPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "+10 XP",
                                    color = Color(0xFFD97706),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = uiState.dailyQuestion?.text
                                ?: "Qadimgi Misrda Nil daryosining toshishi nimaga sabab bo‘lgan?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(14.dp))

                        val question = uiState.dailyQuestion
                        val options = question?.options ?: emptyList()
                        val optionLetters = listOf("A", "B", "C", "D")

                        if (options.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                options.forEachIndexed { idx, opt ->
                                    val letter = optionLetters.getOrElse(idx) { "" }
                                    val isSelected = uiState.selectedDailyOptionId == opt.id
                                    val isCorrect = opt.isCorrect

                                    val cardBg = when {
                                        uiState.isDailyAnswered && isCorrect -> Color(0xFFDCFCE7)
                                        uiState.isDailyAnswered && isSelected && !isCorrect -> Color(0xFFFEE2E2)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }

                                    val borderColor = when {
                                        uiState.isDailyAnswered && isCorrect -> Color(0xFF16A34A)
                                        uiState.isDailyAnswered && isSelected && !isCorrect -> Color(0xFFDC2626)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }

                                    val textColor = when {
                                        uiState.isDailyAnswered && isCorrect -> Color(0xFF15803D)
                                        uiState.isDailyAnswered && isSelected && !isCorrect -> Color(0xFFB91C1C)
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

                        // Feedback Banner
                        if (uiState.isDailyAnswered && !uiState.dailyAnswerFeedback.isNullOrBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.isDailyCorrect) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = uiState.dailyAnswerFeedback!!,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (uiState.isDailyCorrect) Color(0xFF15803D) else Color(0xFFB45309),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Action Button: Either Next/New Daily Question or Start Full Quiz
                        if (uiState.isDailyAnswered) {
                            Button(
                                onClick = { viewModel.loadNextDailyQuestion() },
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
                                    text = "Yangi kun savolini yechish 🔄",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.loadNextDailyQuestion() },
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

            // 3.5. Rewarded Ad Banner Card (Kuniga 2 marta max)
            item {
                val context = LocalContext.current
                val activity = context.findActivity()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFAF5FF),
                    border = BorderStroke(1.5.dp, BrandPurple.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.CardGiftcard,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mukofotli Reklama (+50 XP)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Reklama ko'rib +50 XP oling (Bugun: ${uiState.rewardedAdCountToday}/2)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (uiState.canShowRewardedAd) {
                                    if (activity != null) {
                                        AdMobManager.showRewardedAd(
                                            activity = activity,
                                            onRewardEarned = {
                                                viewModel.claimAdReward(
                                                    onSuccess = {
                                                        Toast.makeText(context, "Tabriklaymiz! +50 XP berildi! 🎉", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onLimitReached = {
                                                        Toast.makeText(context, "Siz bugungi 2 ta mukofotli reklamadan foydalanib bo'ldingiz!", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        )
                                    }
                                } else {
                                    Toast.makeText(context, "Bugungi 2 ta mukofotli reklama ko'rib bo'lingan (2/2). Ertaga yana keling!", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = uiState.canShowRewardedAd,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (uiState.canShowRewardedAd) "Ko'rish" else "2/2 Bajarildi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
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

    // Interactive Notification Bottom Sheet
    if (showNotificationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bildirishnomalar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Badge(containerColor = BrandPurple) {
                        Text("3 ta yangi")
                    }
                }
                Spacer(Modifier.height(16.dp))

                NotificationItem(
                    title = "Kunlik seriya davom etmoqda! 🔥",
                    desc = "Bugun ham kirib test yechdingiz. Seriyangiz 7 kunga yetdi!",
                    time = "10 daqiqa oldin"
                )
                NotificationItem(
                    title = "Yangi testlar qo‘shildi 📚",
                    desc = "Shahar nomlari va Tarixiy shaxslar bo‘yicha 40 ta yangi test yuklandi.",
                    time = "1 soat oldin"
                )
                NotificationItem(
                    title = "DTM imtihon sinovi tayyor 🏆",
                    desc = "30 savolli blok sinov imtihoni yangilandi. O‘z bilimingizni sinab ko‘ring.",
                    time = "Kecha"
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NotificationItem(
    title: String,
    desc: String,
    time: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = time,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = desc,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
