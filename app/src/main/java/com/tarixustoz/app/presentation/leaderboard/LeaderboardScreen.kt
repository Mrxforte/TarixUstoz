package com.tarixustoz.app.presentation.leaderboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import com.tarixustoz.app.presentation.theme.BrandPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val growthPercent: Int,
    val averageSpeedSeconds: Int,
    val chartData: List<StudentChartDataPoint>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onStartQuiz: (String) -> Unit = {},
    onNavigateToAnalysis: (String) -> Unit = {}
) {
    var mainTab by remember { mutableIntStateOf(0) } // 0 = O'z natijam & Tahlil, 1 = Umumiy Reyting
    var periodFilter by remember { mutableIntStateOf(1) } // 0 = Bugun, 1 = Haftalik, 2 = Oylik, 3 = Barcha vaqt
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedCategoryDetail by remember { mutableStateOf<DetailedCategoryAnalytics?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

    // Trigger animation filling on every tab or filter entry
    var startAnimation by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "chartAnim"
    )

    LaunchedEffect(mainTab, periodFilter) {
        startAnimation = false
        delay(40)
        startAnimation = true
    }

    // Dynamic Chart Data based on selected period filter (Bugun, Haftalik, Oylik, Barchasi)
    val dynamicChartPoints = remember(periodFilter) {
        when (periodFilter) {
            0 -> listOf( // Bugun (Hourly Timeline)
                StudentChartDataPoint("09:00", 0.60f, 5f),
                StudentChartDataPoint("12:00", 0.72f, 10f),
                StudentChartDataPoint("15:00", 0.80f, 12f),
                StudentChartDataPoint("18:00", 0.85f, 18f),
                StudentChartDataPoint("21:00", 0.89f, 25f)
            )
            1 -> listOf( // Haftalik (Days of Week)
                StudentChartDataPoint("Du", 0.45f, 15f),
                StudentChartDataPoint("Se", 0.58f, 22f),
                StudentChartDataPoint("Ch", 0.64f, 28f),
                StudentChartDataPoint("Pa", 0.72f, 35f),
                StudentChartDataPoint("Ju", 0.79f, 42f),
                StudentChartDataPoint("Sh", 0.84f, 48f),
                StudentChartDataPoint("Ya", 0.86f, 52f)
            )
            2 -> listOf( // Oylik (Weeks of Month)
                StudentChartDataPoint("1-hafta", 0.55f, 80f),
                StudentChartDataPoint("2-hafta", 0.68f, 110f),
                StudentChartDataPoint("3-hafta", 0.78f, 145f),
                StudentChartDataPoint("4-hafta", 0.86f, 180f)
            )
            else -> listOf( // Barcha vaqt (Months Trend)
                StudentChartDataPoint("Yan", 0.40f, 220f),
                StudentChartDataPoint("Fev", 0.52f, 310f),
                StudentChartDataPoint("Mar", 0.65f, 420f),
                StudentChartDataPoint("Apr", 0.78f, 540f),
                StudentChartDataPoint("May", 0.86f, 680f)
            )
        }
    }

    // Dynamic Period Summary
    val periodTimeText = remember(periodFilter) {
        when (periodFilter) {
            0 -> "25 daqiqa"
            1 -> "2 soat 40 daq"
            2 -> "11 soat 20 daq"
            else -> "42 soat 15 daq"
        }
    }

    val periodAccuracyText = remember(periodFilter) {
        when (periodFilter) {
            0 -> "89%"
            1 -> "86%"
            2 -> "82%"
            else -> "87%"
        }
    }

    val periodTrendText = remember(periodFilter) {
        when (periodFilter) {
            0 -> "+8%"
            1 -> "+18%"
            2 -> "+24%"
            else -> "+35%"
        }
    }

    val users = remember(periodFilter) {
        when (periodFilter) {
            0 -> listOf( // Bugun
                LeaderboardUser(1, "Madina Umarova", "MU", 25, 95, 45),
                LeaderboardUser(2, "Jasurbek Mirzayev", "JM", 22, 91, 38),
                LeaderboardUser(3, "Dilnoza Rahimova", "DR", 20, 88, 35),
                LeaderboardUser(4, "Azizbek (Siz)", "AS", 18, 86, 32, isCurrentUser = true),
                LeaderboardUser(5, "Bobur To‘rayev", "BT", 15, 82, 25)
            )
            1 -> listOf( // Haftalik
                LeaderboardUser(1, "Madina Umarova", "MU", 145, 95, 210),
                LeaderboardUser(2, "Jasurbek Mirzayev", "JM", 132, 91, 190),
                LeaderboardUser(3, "Dilnoza Rahimova", "DR", 120, 88, 175),
                LeaderboardUser(4, "Azizbek (Siz)", "AS", 108, 86, 160, isCurrentUser = true),
                LeaderboardUser(5, "Bobur To‘rayev", "BT", 95, 82, 140),
                LeaderboardUser(6, "Shahnoza Aliyeva", "SA", 88, 79, 130),
                LeaderboardUser(7, "Otabek Qodirov", "OQ", 76, 75, 110)
            )
            2 -> listOf( // Oylik
                LeaderboardUser(1, "Madina Umarova", "MU", 480, 96, 680),
                LeaderboardUser(2, "Jasurbek Mirzayev", "JM", 450, 93, 620),
                LeaderboardUser(3, "Dilnoza Rahimova", "DR", 410, 89, 570),
                LeaderboardUser(4, "Azizbek (Siz)", "AS", 390, 87, 540, isCurrentUser = true),
                LeaderboardUser(5, "Bobur To‘rayev", "BT", 340, 83, 490)
            )
            else -> listOf( // Barcha vaqt
                LeaderboardUser(1, "Madina Umarova", "MU", 1250, 96, 1850),
                LeaderboardUser(2, "Jasurbek Mirzayev", "JM", 1120, 93, 1620),
                LeaderboardUser(3, "Dilnoza Rahimova", "DR", 980, 89, 1450),
                LeaderboardUser(4, "Azizbek (Siz)", "AS", 850, 87, 1280, isCurrentUser = true),
                LeaderboardUser(5, "Bobur To‘rayev", "BT", 760, 83, 1100)
            )
        }
    }

    val categoryAnalytics = listOf(
        DetailedCategoryAnalytics(
            title = "Tarixiy voqealar",
            subtitle = "Voqealar va xronologiya bobi",
            totalSolved = 140,
            correctCount = 119,
            accuracyPercent = 85,
            growthPercent = 18,
            averageSpeedSeconds = 32,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.50f, 12f),
                StudentChartDataPoint("Se", 0.62f, 18f),
                StudentChartDataPoint("Ch", 0.70f, 25f),
                StudentChartDataPoint("Pa", 0.78f, 30f),
                StudentChartDataPoint("Ju", 0.85f, 38f)
            )
        ),
        DetailedCategoryAnalytics(
            title = "Tarixiy shaxslar",
            subtitle = "Buyuk siymolar va hukmdorlar",
            totalSolved = 184,
            correctCount = 150,
            accuracyPercent = 82,
            growthPercent = 24,
            averageSpeedSeconds = 28,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.40f, 10f),
                StudentChartDataPoint("Se", 0.55f, 16f),
                StudentChartDataPoint("Ch", 0.68f, 22f),
                StudentChartDataPoint("Pa", 0.76f, 29f),
                StudentChartDataPoint("Ju", 0.82f, 35f)
            )
        ),
        DetailedCategoryAnalytics(
            title = "Urushlar va janglar",
            subtitle = "Harbiy yurishlar va jangnomalar",
            totalSolved = 110,
            correctCount = 84,
            accuracyPercent = 76,
            growthPercent = 12,
            averageSpeedSeconds = 40,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.35f, 8f),
                StudentChartDataPoint("Se", 0.48f, 14f),
                StudentChartDataPoint("Ch", 0.60f, 20f),
                StudentChartDataPoint("Pa", 0.70f, 26f),
                StudentChartDataPoint("Ju", 0.76f, 30f)
            )
        ),
        DetailedCategoryAnalytics(
            title = "Yillar va sanalar",
            subtitle = "Sanalar va asrlar sinovi",
            totalSolved = 215,
            correctCount = 168,
            accuracyPercent = 78,
            growthPercent = 15,
            averageSpeedSeconds = 25,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.42f, 15f),
                StudentChartDataPoint("Se", 0.56f, 22f),
                StudentChartDataPoint("Ch", 0.65f, 28f),
                StudentChartDataPoint("Pa", 0.72f, 34f),
                StudentChartDataPoint("Ju", 0.78f, 40f)
            )
        ),
        DetailedCategoryAnalytics(
            title = "6-sinf Qadimgi dunyo tarixi",
            subtitle = "Darslik: Misr, Bobil, Gretsiya va Rim",
            totalSolved = 160,
            correctCount = 140,
            accuracyPercent = 88,
            growthPercent = 22,
            averageSpeedSeconds = 30,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.60f, 18f),
                StudentChartDataPoint("Se", 0.72f, 24f),
                StudentChartDataPoint("Ch", 0.80f, 30f),
                StudentChartDataPoint("Pa", 0.85f, 36f),
                StudentChartDataPoint("Ju", 0.88f, 42f)
            )
        ),
        DetailedCategoryAnalytics(
            title = "7-sinf O‘zbekiston tarixi",
            subtitle = "Darslik: Ilk o'rta asrlar va Somoniylar",
            totalSolved = 175,
            correctCount = 148,
            accuracyPercent = 84,
            growthPercent = 19,
            averageSpeedSeconds = 31,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.52f, 14f),
                StudentChartDataPoint("Se", 0.65f, 20f),
                StudentChartDataPoint("Ch", 0.74f, 26f),
                StudentChartDataPoint("Pa", 0.80f, 32f),
                StudentChartDataPoint("Ju", 0.84f, 38f)
            )
        ),
        DetailedCategoryAnalytics(
            title = "8-sinf Jahon tarixi",
            subtitle = "Darslik: Uyg'onish davri va kashfiyotlar",
            totalSolved = 130,
            correctCount = 104,
            accuracyPercent = 80,
            growthPercent = 16,
            averageSpeedSeconds = 35,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.48f, 12f),
                StudentChartDataPoint("Se", 0.60f, 18f),
                StudentChartDataPoint("Ch", 0.70f, 24f),
                StudentChartDataPoint("Pa", 0.76f, 30f),
                StudentChartDataPoint("Ju", 0.80f, 36f)
            )
        ),
        DetailedCategoryAnalytics(
            title = "Mantiqiy va DTM blok",
            subtitle = "OTMga kirish rasmiy test topshiriqlari",
            totalSolved = 95,
            correctCount = 78,
            accuracyPercent = 82,
            growthPercent = 20,
            averageSpeedSeconds = 45,
            chartData = listOf(
                StudentChartDataPoint("Du", 0.40f, 10f),
                StudentChartDataPoint("Se", 0.54f, 16f),
                StudentChartDataPoint("Ch", 0.66f, 24f),
                StudentChartDataPoint("Pa", 0.76f, 32f),
                StudentChartDataPoint("Ju", 0.82f, 40f)
            )
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
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
                    startAnimation = false
                    delay(500)
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                                Text("AS", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Azizbek (Siz)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("Aktiv bilimdon", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            "#4-o'rin",
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
                                        Text("O'sish sur'ati", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        Text(periodTrendText, color = Color(0xFFFDE047), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Dual-Line Animated Student Growth Chart (Dynamically updates with period filter)
                    item {
                        DualLineStudentChart(
                            points = dynamicChartPoints,
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
                                    Text("${cat.correctCount}/${cat.totalSolved} (${(cat.accuracyPercent * animProgress).toInt()}%)", fontWeight = FontWeight.Bold, color = BrandPurple, fontSize = 12.sp)
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
                                    Text("O'sish progressi: +${(cat.growthPercent * animProgress).toInt()}%", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                    Text("O'rtacha tezlik: ${cat.averageSpeedSeconds} sek/savol", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "📈 +18% Trend",
                            color = Color(0xFF15803D),
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

                    // Line 1 Points: Accuracy (0.2 to 1.0)
                    val accuracyPoints = points.mapIndexed { index, item ->
                        val x = index * spacing
                        val normY = ((item.accuracyRatio - 0.2f) / 0.8f).coerceIn(0f, 1f)
                        val y = height - (normY * height * 0.75f * animProgress) - (height * 0.12f)
                        Offset(x, y)
                    }

                    // Line 2 Points: Time Spent (0 to 60 minutes)
                    val maxTime = points.maxOfOrNull { it.timeSpentMinutes }?.coerceAtLeast(30f) ?: 60f
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
