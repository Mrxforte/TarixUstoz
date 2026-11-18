package com.tarixustoz.app.presentation.leaderboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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

data class CategoryAnalytics(
    val title: String,
    val totalSolved: Int,
    val correctCount: Int,
    val accuracyPercent: Int,
    val growthPercent: Int
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
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()

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
        CategoryAnalytics("Tarixiy voqealar", 140, 119, 85, 18),
        CategoryAnalytics("Tarixiy shaxslar", 184, 150, 82, 24),
        CategoryAnalytics("Urushlar va janglar", 110, 84, 76, 12),
        CategoryAnalytics("Yillar va sanalar", 215, 168, 78, 15),
        CategoryAnalytics("Mantiqiy va DTM", 95, 78, 82, 20)
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
                    delay(800)
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                        Text("2 soat 40 daq", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column {
                                        Text("To'g'rilik", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        Text("86%", color = Color(0xFF86EFAC), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Column {
                                        Text("O'sish sur'ati", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        Text("+18%", color = Color(0xFFFDE047), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Bo'limlar bo'yicha tahlil",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    itemsIndexed(categoryAnalytics) { _, cat ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
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
                                    Text(cat.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${cat.correctCount}/${cat.totalSolved} (${cat.accuracyPercent}%)", fontWeight = FontWeight.Bold, color = BrandPurple, fontSize = 12.sp)
                                }
                                LinearProgressIndicator(
                                    progress = { (cat.accuracyPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = BrandPurple,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text("O'sish progressi: +${cat.growthPercent}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
