package com.tarixustoz.app.presentation.leaderboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.presentation.common.components.ShimmerListItem
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class LeaderboardUser(
    val rank: Int,
    val name: String,
    val initials: String,
    val score: Int,
    val accuracy: Int,
    val timeSpentMinutes: Int,
    val isCurrentUser: Boolean = false
)

data class StudentChartDataPoint(
    val dayLabel: String,
    val accuracyRatio: Float,     // 0.0f to 1.0f (Aniqlik foizi)
    val timeSpentMinutes: Float   // Sarflangan vaqt (daqiqa)
)

data class DetailedCategoryAnalytics(
    val title: String,
    val subtitle: String,
    val totalSolved: Int,
    val correctCount: Int,
    val accuracyPercent: Int,
    val timeSpentMinutes: Int,
    val growthPercent: Int,
    val averageSpeedSeconds: Int,
    val chartData: List<StudentChartDataPoint>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onStartQuiz: (String) -> Unit = {},
    onNavigateToAnalysis: (String) -> Unit = {},
    viewModel: LeaderboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var mainTab by remember { mutableIntStateOf(0) } // 0 = O'z natijam & Tahlil, 1 = Umumiy Reyting
    var periodFilter by remember { mutableIntStateOf(1) } // 0 = Bugun, 1 = Haftalik, 2 = Oylik, 3 = Barcha vaqt
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    var startAnimation by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "chartAnim"
    )

    LaunchedEffect(mainTab, periodFilter, uiState.isLoaded) {
        startAnimation = false
        delay(40)
        startAnimation = true
    }

    val activePeriod = uiState.periodSummaries[periodFilter]
    val dynamicChartPoints = activePeriod?.chartPoints ?: emptyList()

    val periodTimeText = remember(activePeriod?.timeSpentMinutes) {
        val mins = activePeriod?.timeSpentMinutes ?: 0
        when {
            mins <= 0 -> "0 daq"
            mins >= 60 -> "${mins / 60} soat ${mins % 60} daq"
            else -> "$mins daqiqa"
        }
    }

    val periodAccuracyText = "${activePeriod?.accuracyPct ?: 0}%"
    val periodSolvedCount = activePeriod?.solvedCount ?: 0
    val periodSolvedText = "$periodSolvedCount ta"

    val users = uiState.leaderboardUsers
    val categoryAnalytics = uiState.categoryAnalytics

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Reyting va Tahlil",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                    viewModel.refresh()
                    startAnimation = false
                    delay(400)
                    startAnimation = true
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Modern Split View Segmented Switch
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (mainTab == 0) BrandPurple else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { mainTab = 0 }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Analytics,
                                            contentDescription = null,
                                            tint = if (mainTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "O'z natijam",
                                            color = if (mainTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (mainTab == 1) BrandPurple else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { mainTab = 1 }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = if (mainTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Umumiy Reyting",
                                            color = if (mainTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Time Filter Chips (Bugun, Haftalik, Oylik, Barcha vaqt)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf("Bugun", "Haftalik", "Oylik", "Barchasi")
                        filters.forEachIndexed { index, label ->
                            val isSelected = periodFilter == index
                            FilterChip(
                                selected = isSelected,
                                onClick = { periodFilter = index },
                                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandPurple,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (mainTab == 0) {
                    // TAB 0: O'z natijam & Tahlil
                    item {
                        // User Personal Rank Card
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = BrandPurple,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.2f),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(uiState.avatar, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("${uiState.displayName} (Siz)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("${uiState.xp} XP • ${uiState.streak} kunlik seriya", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            uiState.currentUserRankLabel,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Sarflangan vaqt", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        Text(periodTimeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column {
                                        Text("To'g'rilik", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        Text(periodAccuracyText, color = Color(0xFF86EFAC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column {
                                        Text("Ishlangan savol", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        Text(periodSolvedText, color = Color(0xFFFDE047), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Dual-Line Animated Student Growth Chart (Dynamically updates with period filter)
                    item {
                        DualLineStudentChart(
                            points = dynamicChartPoints,
                            solvedCount = periodSolvedCount,
                            accuracyPct = activePeriod?.accuracyPct ?: 0,
                            animProgress = animProgress
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bo'limlar va darsliklar tahlili",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "(Tafsilotlar uchun bosing)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(categoryAnalytics) { _, cat ->
                        val animCorrect = (cat.correctCount * animProgress).roundToInt()
                        val animTotal = (cat.totalSolved * animProgress).roundToInt()
                        val animAccuracy = (cat.accuracyPercent * animProgress).roundToInt()
                        val animGrowth = (cat.growthPercent * animProgress).roundToInt()
                        val animTime = (cat.timeSpentMinutes * animProgress).roundToInt()

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToAnalysis(cat.title) }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cat.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(cat.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("$animCorrect/$animTotal ($animAccuracy%)", fontWeight = FontWeight.Bold, color = BrandPurple, fontSize = 12.sp)
                                }
                                LinearProgressIndicator(
                                    progress = { (cat.accuracyPercent / 100f * animProgress).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = BrandPurple,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (cat.totalSolved > 0) "Vaqt: $animTime daq" else "Hali test ishlanmagan",
                                        fontSize = 11.sp,
                                        color = if (cat.totalSolved > 0) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (cat.averageSpeedSeconds > 0) "Tezlik: ${cat.averageSpeedSeconds} sek/savol" else "Boshlash uchun bosing",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                } else {
                    // TAB 1: Umumiy Reyting List
                    item {
                        Text(
                            text = "Peshqadamlar Jadvali",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!uiState.isLoaded && users.isEmpty()) {
                        items(5) {
                            ShimmerListItem()
                        }
                    } else if (users.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.EmojiEvents,
                                        contentDescription = null,
                                        tint = BrandPurple,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Peshqadamlar jadvali bo‘sh",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Test ishlab XP to‘plang — natijangiz real vaqtda reytingda ko‘rinadi.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(users) { index, user ->
                        val rankColor = when (user.rank) {
                            1 -> Color(0xFFF59E0B) // Gold
                            2 -> Color(0xFF94A3B8) // Silver
                            3 -> Color(0xFFD97706) // Bronze
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (user.isCurrentUser) BrandPurple.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (user.isCurrentUser) 1.5.dp else 1.dp,
                                color = if (user.isCurrentUser) BrandPurple else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${user.rank}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = rankColor,
                                    modifier = Modifier.width(36.dp)
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = if (user.isCurrentUser) BrandPurple else rankColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user.initials,
                                            fontWeight = FontWeight.Bold,
                                            color = if (user.isCurrentUser) Color.White else rankColor,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (user.isCurrentUser) BrandPurple else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${user.accuracy}% aniqlik • ${user.timeSpentMinutes} daq",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BrandPurple.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "${user.score} XP",
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPurple,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * 2-Line Dual Animated Student Chart:
 * - Line 1 (Purple Color 0xFF818CF8): Correct Answers & Accuracy (%)
 * - Line 2 (Cyan Color 0xFF06B6D4): Time Spent & Activity (Minutes)
 */
@Composable
private fun DualLineStudentChart(
    points: List<StudentChartDataPoint>,
    solvedCount: Int,
    accuracyPct: Int,
    animProgress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Legend Explanation
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bilim va O'sish Dinamikasi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = if (solvedCount > 0) "$accuracyPct% Aniqlik" else "Hali boshlanmagan",
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Legend Indicators (Clear Explanation to User)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Line 1 Legend: Accuracy
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF818CF8),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "To'g'rilik (Aniqlik %)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Line 2 Legend: Time Spent
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF06B6D4),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Sarflangan vaqt (daq)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (solvedCount == 0 || points.size < 2) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Hali test ishlanmagan — birinchi testni boshlang va o'sish grafigingizni kuzating!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // Dual Line Canvas Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (points.size < 2) return@Canvas

                        val width = size.width
                        val height = size.height
                        val spacing = width / (points.size - 1)

                        // Line 1 Points: Accuracy (0.0 to 1.0)
                        val accuracyPoints = points.mapIndexed { index, item ->
                            val x = index * spacing
                            val normY = item.accuracyRatio.coerceIn(0f, 1f)
                            val y = height - (normY * height * 0.75f * animProgress) - (height * 0.12f)
                            Offset(x, y)
                        }

                        // Line 2 Points: Time Spent (0 to maxTime)
                        val maxTime = points.maxOfOrNull { it.timeSpentMinutes }?.coerceAtLeast(10f) ?: 30f
                        val timePoints = points.mapIndexed { index, item ->
                            val x = index * spacing
                            val normY = (item.timeSpentMinutes / maxTime).coerceIn(0f, 1f)
                            val y = height - (normY * height * 0.75f * animProgress) - (height * 0.12f)
                            Offset(x, y)
                        }

                    // Build Line 1 Path (Accuracy)
                    val accPath = Path().apply {
                        moveTo(accuracyPoints[0].x, accuracyPoints[0].y)
                        for (i in 0 until accuracyPoints.size - 1) {
                            val p1 = accuracyPoints[i]
                            val p2 = accuracyPoints[i + 1]
                            val controlX1 = p1.x + (p2.x - p1.x) / 2
                            val controlY1 = p1.y
                            val controlX2 = p1.x + (p2.x - p1.x) / 2
                            val controlY2 = p2.y
                            cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                        }
                    }

                    // Build Line 2 Path (Time Spent)
                    val timePath = Path().apply {
                        moveTo(timePoints[0].x, timePoints[0].y)
                        for (i in 0 until timePoints.size - 1) {
                            val p1 = timePoints[i]
                            val p2 = timePoints[i + 1]
                            val controlX1 = p1.x + (p2.x - p1.x) / 2
                            val controlY1 = p1.y
                            val controlX2 = p1.x + (p2.x - p1.x) / 2
                            val controlY2 = p2.y
                            cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
                        }
                    }

                    // Draw Line 1 Fill Gradient
                    val accFillPath = Path().apply {
                        addPath(accPath)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(
                        path = accFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF818CF8).copy(alpha = 0.35f * animProgress),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw Line 2 Fill Gradient
                    val timeFillPath = Path().apply {
                        addPath(timePath)
                        lineTo(width, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(
                        path = timeFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF06B6D4).copy(alpha = 0.25f * animProgress),
                                Color.Transparent
                            )
                        )
                    )

                    // Draw Line 1 (Accuracy - Purple)
                    drawPath(
                        path = accPath,
                        color = Color(0xFF818CF8),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Line 2 (Time - Cyan)
                    drawPath(
                        path = timePath,
                        color = Color(0xFF06B6D4),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Glowing Nodes for Line 1
                    accuracyPoints.forEach { point ->
                        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = point)
                        drawCircle(color = Color(0xFF818CF8), radius = 3.5.dp.toPx(), center = point)
                    }

                    // Draw Glowing Nodes for Line 2
                    timePoints.forEach { point ->
                        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = point)
                        drawCircle(color = Color(0xFF06B6D4), radius = 2.5.dp.toPx(), center = point)
                    }
                }
                }

                // X-Axis Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    points.forEach { item ->
                        Text(
                            text = item.dayLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
