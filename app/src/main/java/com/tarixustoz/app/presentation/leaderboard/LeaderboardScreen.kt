package com.tarixustoz.app.presentation.leaderboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tarixustoz.app.presentation.theme.BrandPurple

data class LeaderboardUser(
    val rank: Int,
    val name: String,
    val initials: String,
    val score: Int, // Modest, realistic small XP number
    val accuracy: Int,
    val isCurrentUser: Boolean = false
)

data class TestProgressStat(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val completionPercent: Int, // Necha foiz tugagan
    val errorPercent: Int,      // Necha foiz xato
    val growthPercent: Int,     // O'sish progress foizi
    val totalCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onStartQuiz: (String) -> Unit = {},
    onNavigateToAnalysis: (String) -> Unit = {}
) {
    var mainTab by remember { mutableIntStateOf(0) } // 0 = Reyting jadvali, 1 = Natijalar & Tahlil
    var timeFilter by remember { mutableIntStateOf(0) } // 0 = Haftalik, 1 = Barcha vaqt

    // Modest, realistic small XP numbers
    val leaders = listOf(
        LeaderboardUser(1, "Madina Umarova", "MU", 96, 95),
        LeaderboardUser(2, "Jasurbek Mirzayev", "JM", 92, 91),
        LeaderboardUser(3, "Dilnoza Rahimova", "DR", 89, 88),
        LeaderboardUser(4, "Azizbek (Siz)", "AS", 78, 86, isCurrentUser = true),
        LeaderboardUser(5, "Bobur To‘rayev", "BT", 68, 82),
        LeaderboardUser(6, "Shahnoza Aliyeva", "SA", 55, 79),
        LeaderboardUser(7, "Otabek Qodirov", "OQ", 48, 75)
    )

    val categoryStats = listOf(
        TestProgressStat(
            title = "Tarixiy voqealar",
            icon = Icons.Default.Event,
            iconColor = Color(0xFF6366F1),
            completionPercent = 65,
            errorPercent = 12,
            growthPercent = 22,
            totalCount = 120
        ),
        TestProgressStat(
            title = "Tarixiy faktlar",
            icon = Icons.Default.AccountBalance,
            iconColor = Color(0xFF8B5CF6),
            completionPercent = 48,
            errorPercent = 15,
            growthPercent = 18,
            totalCount = 95
        ),
        TestProgressStat(
            title = "Urushlar va janglar",
            icon = Icons.Default.Security,
            iconColor = Color(0xFFDC2626),
            completionPercent = 58,
            errorPercent = 16,
            growthPercent = 25,
            totalCount = 110
        ),
        TestProgressStat(
            title = "Tarixiy shaxslar",
            icon = Icons.Default.Person,
            iconColor = Color(0xFF7C3AED),
            completionPercent = 82,
            errorPercent = 8,
            growthPercent = 34,
            totalCount = 184
        ),
        TestProgressStat(
            title = "Yillar va sanalar",
            icon = Icons.Default.CalendarMonth,
            iconColor = Color(0xFF16A34A),
            completionPercent = 40,
            errorPercent = 24,
            growthPercent = 14,
            totalCount = 215
        ),
        TestProgressStat(
            title = "Shahar nomlari",
            icon = Icons.Default.LocationOn,
            iconColor = Color(0xFFF97316),
            completionPercent = 60,
            errorPercent = 14,
            growthPercent = 19,
            totalCount = 120
        ),
        TestProgressStat(
            title = "Darsliklar: 6-sinf Qadimgi dunyo",
            icon = Icons.Default.MenuBook,
            iconColor = Color(0xFF0284C7),
            completionPercent = 75,
            errorPercent = 10,
            growthPercent = 28,
            totalCount = 80
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
                ),
                windowInsets = WindowInsets(0.dp)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Top Navigation Tabs: Reyting vs Natijalar & Tahlil
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = mainTab == 0,
                        onClick = { mainTab = 0 },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reyting jadvali", fontWeight = if (mainTab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPurple,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("leaderboard_tab_rank")
                    )
                    FilterChip(
                        selected = mainTab == 1,
                        onClick = { mainTab = 1 },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Natijalar & Tahlil", fontWeight = if (mainTab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPurple,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("leaderboard_tab_analytics")
                    )
                }
            }

            if (mainTab == 0) {
                // 🏆 TAB 1: REYTING JADVALI
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = timeFilter == 0,
                            onClick = { timeFilter = 0 },
                            label = { Text("Haftalik reyting", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = timeFilter == 1,
                            onClick = { timeFilter = 1 },
                            label = { Text("Barcha vaqtlar", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Top 3 Podium with realistic small XP
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("leaderboard_podium")
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(vertical = 20.dp, horizontal = 12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // 2nd Place
                            PodiumColumn(
                                place = 2,
                                name = "Jasurbek",
                                score = "92 XP",
                                badgeColor = Color(0xFF94A3B8),
                                avatarBg = Color(0xFFE2E8F0),
                                initials = "JM",
                                avatarSize = 54.dp
                            )

                            // 1st Place (Highest)
                            PodiumColumn(
                                place = 1,
                                name = "Madina",
                                score = "96 XP",
                                badgeColor = Color(0xFFFBBF24),
                                avatarBg = Color(0xFFFEF3C7),
                                initials = "MU",
                                avatarSize = 68.dp
                            )

                            // 3rd Place
                            PodiumColumn(
                                place = 3,
                                name = "Dilnoza",
                                score = "89 XP",
                                badgeColor = Color(0xFFD97706),
                                avatarBg = Color(0xFFFFEDD5),
                                initials = "DR",
                                avatarSize = 50.dp
                            )
                        }
                    }
                }

                // Rank list header
                item {
                    Text(
                        text = "Barcha qatnashchilar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Rank list
                itemsIndexed(leaders) { _, user ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (user.isCurrentUser) Color(0xFFEEF2FF) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (user.isCurrentUser) 1.5.dp else 1.dp,
                            if (user.isCurrentUser) BrandPurple else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${user.rank}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (user.isCurrentUser) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp)
                            )

                            Surface(
                                shape = CircleShape,
                                color = if (user.isCurrentUser) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = user.initials,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (user.isCurrentUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (user.isCurrentUser) FontWeight.Bold else FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Aniqlik: ${user.accuracy}%",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (user.isCurrentUser) BrandPurple else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${user.score} XP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (user.isCurrentUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // 📊 TAB 2: NATIJALAR VA PROGRESBAR TAHLIL (User requested feature!)
                // 1. Overall summary progress card (Barcha testlar umumiy tahlili)
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAnalysis("Barcha testlar umumiy tahlili") }
                            .testTag("leaderboard_overall_progress_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Barcha testlar umumiy progressi",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color(0xFFECFDF5)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "+26% O‘sish",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // 1. Tugaganlik progressi
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAnalysis("Barcha testlar umumiy tahlili") }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tugallangan testlar ulushi",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "58% tugagan (534/920)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPurple
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { 0.58f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = BrandPurple,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            // 2. Xatolik ulushi
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAnalysis("Barcha testlar umumiy tahlili") }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Xatoliklar foizi",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "14% xato",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { 0.14f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = Color(0xFFEF4444),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            // 3. O'sish dinamikasi foizi
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAnalysis("Barcha testlar umumiy tahlili") }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Bilim o‘sish progressi",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "+26% o‘sish",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { 0.72f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = Color(0xFF10B981),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            // Banner Callout to open full analysis screen
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BrandPurple.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAnalysis("Barcha testlar umumiy tahlili") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PieChart,
                                            contentDescription = null,
                                            tint = BrandPurple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Batafsil doira va chiziqli grafik tahlili",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandPurple
                                        )
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = BrandPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Header for Category breakdown
                item {
                    Text(
                        text = "Alohida test yo‘nalishlari progressi (Bosib ko‘ring)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // List of progress cards for each category
                items(categoryStats) { stat ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAnalysis(stat.title) }
                            .testTag("test_progress_card_${stat.title.replace(" ", "_")}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = stat.iconColor.copy(alpha = 0.12f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            stat.icon,
                                            contentDescription = null,
                                            tint = stat.iconColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stat.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Jami: ${stat.totalCount} ta test",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECFDF5)
                                ) {
                                    Text(
                                        text = "+${stat.growthPercent}% o‘sish",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF059669),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Completion Progress bar (Clickable!)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAnalysis(stat.title) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Tugallangan:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${stat.completionPercent}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPurple
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { stat.completionPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = BrandPurple,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Error rate progress bar (Clickable!)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAnalysis(stat.title) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Xatolik ko‘rsatkichi:",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${stat.errorPercent}% xato",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { stat.errorPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = Color(0xFFEF4444),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            // Bottom prompt to open full diagram screen
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToAnalysis(stat.title) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PieChart,
                                        contentDescription = null,
                                        tint = BrandPurple,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Diagramma, grafik va maslahatlar",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandPurple
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(16.dp)
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

@Composable
private fun PodiumColumn(
    place: Int,
    name: String,
    score: String,
    badgeColor: Color,
    avatarBg: Color,
    initials: String,
    avatarSize: androidx.compose.ui.unit.Dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = avatarBg,
                border = BorderStroke(2.dp, badgeColor),
                modifier = Modifier.size(avatarSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        fontSize = if (place == 1) 18.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = badgeColor,
                modifier = Modifier
                    .size(20.dp)
                    .offset(x = 2.dp, y = 2.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$place",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = score,
            fontSize = 11.sp,
            color = BrandPurple,
            fontWeight = FontWeight.SemiBold
        )
    }
}
