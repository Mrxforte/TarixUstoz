package com.tarixustoz.app.presentation.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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

data class NotificationModel(
    val id: String,
    val title: String,
    val message: String,
    val time: String,
    val type: String, // "mistake", "streak", "test", "system"
    val isRead: Boolean = false,
    val actionLabel: String? = null,
    val actionTarget: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToMistakes: () -> Unit,
    onNavigateToDailyQuiz: () -> Unit,
    onNavigateToTests: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf("all") }
    val notifications = remember {
        mutableStateListOf(
            NotificationModel(
                id = "n_mistakes",
                title = "Xatolaringiz bor! ⚠️",
                message = "Testlarda 11 ta bilmagan savolingiz to‘plandi. Ularni qayta ishlab, eslab qoling.",
                time = "5 daqiqa oldin",
                type = "mistake",
                actionLabel = "Xatolarni qayta yechish",
                actionTarget = "mistakes"
            ),
            NotificationModel(
                id = "n_streak",
                title = "Kunlik seriya davom etmoqda! 🔥",
                message = "Tabriklaymiz! Bugun ham faol bo‘lib, 7 kunlik seriyani ushlab turibsiz.",
                time = "Bugun, 08:30",
                type = "streak",
                actionLabel = "Kun savolini yechish",
                actionTarget = "daily"
            ),
            NotificationModel(
                id = "n_new_tests",
                title = "Yangi testlar yuklandi 📚",
                message = "Shahar nomlari va Amir Temur saltanati bo‘yicha 40 ta yangi test bazaga kiritildi.",
                time = "Kecha, 19:15",
                type = "test",
                actionLabel = "Testlarga o‘tish",
                actionTarget = "tests"
            ),
            NotificationModel(
                id = "n_dtm",
                title = "DTM Blok sinovi yangilandi 🏆",
                message = "30 savolli haftalik yangi DTM namunaviy bloki tayyor.",
                time = "2 kun oldin",
                type = "test",
                actionLabel = "Sinovni boshlash",
                actionTarget = "dtm"
            )
        )
    }

    var dailyReminderEnabled by remember { mutableStateOf(true) }
    var mistakeReminderEnabled by remember { mutableStateOf(true) }

    val filteredList = remember(selectedFilter, notifications) {
        when (selectedFilter) {
            "mistake" -> notifications.filter { it.type == "mistake" }
            "streak" -> notifications.filter { it.type == "streak" }
            "test" -> notifications.filter { it.type == "test" }
            else -> notifications
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bildirishnomalar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Orqaga")
                    }
                },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    TextButton(
                        onClick = {
                            val updated = notifications.map { it.copy(isRead = true) }
                            notifications.clear()
                            notifications.addAll(updated)
                        }
                    ) {
                        Text(
                            text = "Barchasini o‘qish",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPurple
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Category filter chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "all",
                        onClick = { selectedFilter = "all" },
                        label = { Text("Barchasi") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPurple,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    FilterChip(
                        selected = selectedFilter == "mistake",
                        onClick = { selectedFilter = "mistake" },
                        label = { Text("Xatolar (11)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF4444),
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    FilterChip(
                        selected = selectedFilter == "streak",
                        onClick = { selectedFilter = "streak" },
                        label = { Text("Seriya") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPurple,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    )
                    FilterChip(
                        selected = selectedFilter == "test",
                        onClick = { selectedFilter = "test" },
                        label = { Text("Testlar") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPurple,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(50)
                    )
                }
            }

            // Featured Mistakes Callout Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToMistakes() }
                        .testTag("notification_mistakes_callout")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "11 ta xato savol to‘plangan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "Takrorlash orqali darslik bilimlarini eslab qoling",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Notification List
            items(filteredList, key = { it.id }) { item ->
                val (badgeColor, badgeIcon) = when (item.type) {
                    "mistake" -> Pair(Color(0xFFEF4444), Icons.Default.Warning)
                    "streak" -> Pair(Color(0xFFF97316), Icons.Default.LocalFireDepartment)
                    else -> Pair(BrandPurple, Icons.Default.Quiz)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (item.isRead) MaterialTheme.colorScheme.surface else Color(0xFFFBFBFE),
                    border = BorderStroke(
                        1.dp,
                        if (!item.isRead) BrandPurple.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = badgeColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        badgeIcon,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Delete button
                            IconButton(
                                onClick = { notifications.remove(item) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "O‘chirish",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )

                        if (item.actionLabel != null) {
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    when (item.actionTarget) {
                                        "mistakes" -> onNavigateToMistakes()
                                        "daily" -> onNavigateToDailyQuiz()
                                        else -> onNavigateToTests()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (item.type == "mistake") Color(0xFFEF4444) else BrandPurple
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Text(
                                    text = item.actionLabel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Notification Settings linked to Profile Screen (moved to Profile as requested)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToProfile() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bildirishnoma sozlamalari",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kunlik eslatmalar, xatolar tahlili va vaqtini Profil bo‘limida boshqaring",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = BrandPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
