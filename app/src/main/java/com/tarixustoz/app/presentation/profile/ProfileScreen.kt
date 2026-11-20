package com.tarixustoz.app.presentation.profile

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import com.tarixustoz.app.ThemeMode
import com.tarixustoz.app.ads.AdMobManager
import com.tarixustoz.app.ads.AdProbeStatus
import com.tarixustoz.app.ads.AdSlotLoadState
import com.tarixustoz.app.ads.findActivity
import com.tarixustoz.app.presentation.theme.BrandPurple
import com.tarixustoz.app.presentation.theme.ThemeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToSavedTests: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    profileViewModel: ProfileViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val isGuest by profileViewModel.isGuest.collectAsState()
    val displayName by profileViewModel.displayName.collectAsState()
    val userAvatar by profileViewModel.userAvatar.collectAsState()
    val userXp by profileViewModel.userXp.collectAsState()
    val userStreak by profileViewModel.userStreak.collectAsState()
    val selectedGrade by profileViewModel.selectedGrade.collectAsState()
    val userRole by profileViewModel.userRole.collectAsState()

    val totalSolvedCount by profileViewModel.totalSolvedCount.collectAsState()
    val accuracyPercentage by profileViewModel.accuracyPercentage.collectAsState()
    val totalSavedCount by profileViewModel.totalSavedCount.collectAsState()
    val savedItemsList by profileViewModel.savedItemsList.collectAsState()
    val achievementsList by profileViewModel.achievementsList.collectAsState()
    val dailyGoalUiState by profileViewModel.dailyGoalUiState.collectAsState()

    val dailyReminder by profileViewModel.dailyReminder.collectAsState()
    val mistakeReminder by profileViewModel.mistakeReminder.collectAsState()
    val newTestsReminder by profileViewModel.newTestsReminder.collectAsState()
    val soundEnabled by profileViewModel.soundEnabled.collectAsState()
    val reminderTime by profileViewModel.reminderTime.collectAsState()

    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showSavedTestsDialog by remember { mutableStateOf(false) }
    var showAchievementsModal by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profil va Sozlamalar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToEditProfile,
                        modifier = Modifier.testTag("profile_edit_top_btn")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Profilni tahrirlash",
                            tint = BrandPurple
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Header Card - Clicking avatar or name opens Profile Edit Screen
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.2.dp, BrandPurple.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToEditProfile)
                    .testTag("profile_user_card")
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .clickable(onClick = onNavigateToEditProfile)
                            .testTag("profile_avatar_btn")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = userAvatar,
                                    fontSize = if (userAvatar.length > 2) 28.sp else 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        // Edit Badge
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, BrandPurple),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Tahrirlash",
                                    tint = BrandPurple,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    val userRoleText = remember(selectedGrade, userRole) {
                        when (userRole) {
                            "O'qituvchi" -> "O'qituvchi"
                            "Abituriyent" -> "Abituriyent"
                            "Talaba" -> "Talaba"
                            else -> "$selectedGrade-sinf"
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToEditProfile)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = userRoleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tahrirlash",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Natijalar Chart Card
            DailyGoalChartCard(dailyGoal = dailyGoalUiState)

            // NOTIFICATION SETTINGS
            Text(
                text = "Bildirishnoma sozlamalari",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Daily reminder toggle
                    NotificationSettingRow(
                        title = "Kunlik eslatmalar",
                        subtitle = "Har kuni ertalab kun savolini eslatish",
                        icon = Icons.Default.NotificationsActive,
                        iconTint = BrandPurple,
                        checked = dailyReminder,
                        onCheckedChange = { profileViewModel.setDailyReminder(it) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Reminder time picker row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePickerDialog = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Eslatma vaqti",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Har kuni soat $reminderTime da bildirishnoma keladi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BrandPurple.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = reminderTime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPurple,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Mistakes practice reminder toggle
                    NotificationSettingRow(
                        title = "Xatolar tahlili eslatmasi",
                        subtitle = "Yig‘ilgan xatolarni takrorlash eslatmasi",
                        icon = Icons.Default.Warning,
                        iconTint = Color(0xFFEF4444),
                        checked = mistakeReminder,
                        onCheckedChange = { profileViewModel.setMistakeReminder(it) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // New tests & textbooks alert
                    NotificationSettingRow(
                        title = "Yangi test va darsliklar",
                        subtitle = "Yangi darslik boblari va savollar qo‘shilganda",
                        icon = Icons.Default.MenuBook,
                        iconTint = Color(0xFF0284C7),
                        checked = newTestsReminder,
                        onCheckedChange = { profileViewModel.setNewTestsReminder(it) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Sound & vibration
                    NotificationSettingRow(
                        title = "Tovush va tebranish",
                        subtitle = "Savol javoblarida audio va vibratsiya signallari",
                        icon = Icons.Default.VolumeUp,
                        iconTint = Color(0xFF10B981),
                        checked = soundEnabled,
                        onCheckedChange = { profileViewModel.setSoundEnabled(it) }
                    )
                }
            }

            // Saqlangan testlar va resurslar
            Text(
                text = "Saqlanganlar va manbalar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileMenuRow(
                        title = "Saqlanganlar",
                        subtitle = if (totalSavedCount > 0) "$totalSavedCount ta saqlangan mavzu va manbalar" else "Hali saqlanganlar yo'q",
                        icon = Icons.Default.Bookmark,
                        onClick = onNavigateToSavedTests
                    )
                }
            }

            // Theme Setting Section
            Text(
                text = "Ilova ko‘rinishi va Mavzu",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // System follow switch row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandPurple.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SettingsSuggest,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tizim rejimiga moslashish",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Android qurilmangizning dark/light sozlamasiga avtomatik moslashadi",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = themeMode == ThemeMode.SYSTEM,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    themeViewModel.setThemeMode(ThemeMode.SYSTEM)
                                } else {
                                    themeViewModel.setThemeMode(ThemeMode.LIGHT)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = BrandPurple
                            )
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Alohida rejim tanlash:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 3 Segmented Theme Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileThemeChoiceCard(
                            title = "Tizim",
                            subtitle = "Avto",
                            icon = Icons.Default.PhoneAndroid,
                            isSelected = themeMode == ThemeMode.SYSTEM,
                            onClick = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )

                        ProfileThemeChoiceCard(
                            title = "Yorug‘",
                            subtitle = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == ThemeMode.LIGHT,
                            onClick = { themeViewModel.setThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )

                        ProfileThemeChoiceCard(
                            title = "Qorong‘u",
                            subtitle = "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == ThemeMode.DARK,
                            onClick = { themeViewModel.setThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        text = "Shrift o‘lchami (Font size):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val fontScale by themeViewModel.appFontScale.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileThemeChoiceCard(
                            title = "Standart",
                            subtitle = "100%",
                            icon = Icons.Default.TextFields,
                            isSelected = fontScale <= 1.05f,
                            onClick = { themeViewModel.setAppFontScale(1.0f) },
                            modifier = Modifier.weight(1f)
                        )

                        ProfileThemeChoiceCard(
                            title = "O‘rtacha",
                            subtitle = "112%",
                            icon = Icons.Default.TextFields,
                            isSelected = fontScale > 1.05f && fontScale <= 1.18f,
                            onClick = { themeViewModel.setAppFontScale(1.12f) },
                            modifier = Modifier.weight(1f)
                        )

                        ProfileThemeChoiceCard(
                            title = "Katta",
                            subtitle = "124%",
                            icon = Icons.Default.TextFields,
                            isSelected = fontScale > 1.18f,
                            onClick = { themeViewModel.setAppFontScale(1.24f) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Hisobdan chiqish (Log out)
            OutlinedButton(
                onClick = {
                    if (isGuest) {
                        profileViewModel.signOut()
                    } else {
                        showSignOutDialog = true
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("profile_sign_out_btn")
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Hisobdan chiqish",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Hisobdan chiqish", fontWeight = FontWeight.Bold) },
            text = { Text("Barcha natijalaringiz bulutda xavfsiz saqlanadi. Haqiqatan ham hisobdan chiqmoqchimisiz?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        profileViewModel.signOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Chiqish", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Bekor qilish")
                }
            }
        )
    }

    // Time Picker Dialog for Notifications (Alarm Clock Style)
    if (showTimePickerDialog) {
        AlarmClockTimePickerDialog(
            initialTime = reminderTime,
            onTimeSelected = { newTime ->
                profileViewModel.setReminderTime(newTime)
                showTimePickerDialog = false
            },
            onDismiss = { showTimePickerDialog = false }
        )
    }

    // Barcha Yutuqlar Modal Dialog
    if (showAchievementsModal) {
        AlertDialog(
            onDismissRequest = { showAchievementsModal = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Barcha Yutuqlar 🏆", fontWeight = FontWeight.Bold)
                    Text(
                        "${achievementsList.count { it.isUnlocked }}/${achievementsList.size}",
                        color = BrandPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    achievementsList.forEach { achievement ->
                        AchievementItemCard(
                            achievement = achievement,
                            onClick = {}
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAchievementsModal = false }) {
                    Text("Yopish")
                }
            }
        )
    }

    // Saqlangan testlar va manbalar Dialog
    if (showSavedTestsDialog) {
        AlertDialog(
            onDismissRequest = { showSavedTestsDialog = false },
            title = { Text("Saqlangan manbalar va testlar", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedItemsList.forEach { item ->
                        SavedTestItem(title = item.title, tag = item.subtitle)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedTestsDialog = false }) {
                    Text("Yopish")
                }
            }
        )
    }
}

@Composable
private fun NotificationSettingRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandPurple
            )
        )
    }
}

@Composable
private fun DailyGoalChartCard(
    dailyGoal: DailyGoalUiState
) {
    var selectedPeriod by remember { mutableStateOf(ResultsChartPeriod.DAY) }

    val activeBars = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> dailyGoal.daySlots
        ResultsChartPeriod.WEEK -> dailyGoal.weekDays
        ResultsChartPeriod.MONTH -> dailyGoal.monthWeeks
        ResultsChartPeriod.YEAR -> dailyGoal.yearMonths
    }

    val periodTitle = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> "Bugun"
        ResultsChartPeriod.WEEK -> "Shu hafta"
        ResultsChartPeriod.MONTH -> "Shu oy"
        ResultsChartPeriod.YEAR -> "Shu yil"
    }

    val periodSolved = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> dailyGoal.todaySolved
        ResultsChartPeriod.WEEK -> dailyGoal.weeklyTotalSolved
        ResultsChartPeriod.MONTH -> dailyGoal.monthlyTotalSolved
        ResultsChartPeriod.YEAR -> dailyGoal.yearlyTotalSolved
    }

    val periodCorrect = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> dailyGoal.todayCorrect
        ResultsChartPeriod.WEEK -> dailyGoal.weeklyCorrect
        ResultsChartPeriod.MONTH -> dailyGoal.monthlyCorrect
        ResultsChartPeriod.YEAR -> dailyGoal.yearlyCorrect
    }

    val periodIncorrect = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> dailyGoal.todayIncorrect
        ResultsChartPeriod.WEEK -> dailyGoal.weeklyIncorrect
        ResultsChartPeriod.MONTH -> dailyGoal.monthlyIncorrect
        ResultsChartPeriod.YEAR -> dailyGoal.yearlyIncorrect
    }

    val periodAccuracy = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> dailyGoal.todayAccuracy
        ResultsChartPeriod.WEEK -> dailyGoal.weeklyAccuracy
        ResultsChartPeriod.MONTH -> dailyGoal.monthlyAccuracy
        ResultsChartPeriod.YEAR -> dailyGoal.yearlyAccuracy
    }

    val periodBadgeText = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> "${dailyGoal.todaySolved}/${dailyGoal.todayGoalTotal} savol"
        ResultsChartPeriod.WEEK -> "${dailyGoal.weeklyTotalSolved} savol"
        ResultsChartPeriod.MONTH -> "${dailyGoal.monthlyTotalSolved} savol"
        ResultsChartPeriod.YEAR -> "${dailyGoal.yearlyTotalSolved} savol"
    }

    val defaultChartBaseline = when (selectedPeriod) {
        ResultsChartPeriod.DAY -> 10
        ResultsChartPeriod.WEEK -> 30
        ResultsChartPeriod.MONTH -> 100
        ResultsChartPeriod.YEAR -> 300
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("results_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Kunlik natijalar + savol pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.EventNote,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Kunlik natijalar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = periodBadgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Kun / Hafta / Oy / Yil Segmented Selector
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResultsChartPeriod.entries.forEach { period ->
                        val isSelected = selectedPeriod == period
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) BrandPurple else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { selectedPeriod = period }
                                .testTag("results_chart_tab_${period.name.lowercase()}")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = period.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Dynamic Bar Chart for Kun / Hafta / Oy / Yil
            val maxCount = activeBars.maxOfOrNull { it.count }?.coerceAtLeast(defaultChartBaseline)
                ?: defaultChartBaseline
            val barWidth = if (activeBars.size > 8) 16.dp else 26.dp
            val labelFontSize = if (activeBars.size > 8) 9.sp else 11.sp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                activeBars.forEach { item ->
                    val barRatio = (item.count.toFloat() / maxCount).coerceIn(0.08f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (item.count > 0) {
                            Text(
                                text = item.count.toString(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isToday) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(3.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .height(64.dp * barRatio)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(
                                    if (item.isToday) BrandPurple
                                    else if (item.count > 0) BrandPurple.copy(alpha = 0.42f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = item.dayName,
                            fontSize = labelFontSize,
                            maxLines = 1,
                            fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.isToday) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Progress Box with Dual-color bar chart (Green correct, Red incorrect) — no extra text below this box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(periodTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            periodBadgeText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BrandPurple
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    val correctRatio = if (periodSolved > 0) periodCorrect.toFloat() / periodSolved else 0f
                    val incorrectRatio = if (periodSolved > 0) periodIncorrect.toFloat() / periodSolved else 0f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        if (correctRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(correctRatio)
                                    .fillMaxHeight()
                                    .background(Color(0xFF16A34A))
                            )
                        }
                        if (incorrectRatio > 0f) {
                            Box(
                                modifier = Modifier
                                    .weight(incorrectRatio)
                                    .fillMaxHeight()
                                    .background(Color(0xFFEF4444))
                            )
                        }
                        if (periodSolved == 0) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "To'g'ri: $periodCorrect",
                            fontSize = 11.sp,
                            color = Color(0xFF16A34A),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Noto'g'ri: $periodIncorrect",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Aniqlik: $periodAccuracy%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementItemCard(
    achievement: AchievementUiState,
    onClick: () -> Unit
) {
    val cardBg = if (achievement.isUnlocked) Color(0xFF16A34A).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    val borderClr = if (achievement.isUnlocked) Color(0xFF16A34A).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
    val iconTint = if (achievement.isUnlocked) Color(0xFF16A34A) else BrandPurple

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderClr),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        achievement.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (achievement.isUnlocked) "Ochildi 🎉" else "${achievement.currentProgress}/${achievement.targetProgress}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (achievement.isUnlocked) Color(0xFF16A34A) else BrandPurple
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (achievement.currentProgress.toFloat() / achievement.targetProgress.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (achievement.isUnlocked) Color(0xFF16A34A) else BrandPurple,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileMenuRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = BrandPurple.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = BrandPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SavedTestItem(title: String, tag: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(tag, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileThemeChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) BrandPurple.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmClockTimePickerDialog(
    initialTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = remember(initialTime) { initialTime.split(":") }
    val defaultHour = remember(parts) { parts.getOrNull(0)?.toIntOrNull() ?: 8 }
    val defaultMinute = remember(parts) { parts.getOrNull(1)?.toIntOrNull() ?: 30 }

    var selectedHour by remember { mutableIntStateOf(defaultHour) }
    var selectedMinute by remember { mutableIntStateOf(defaultMinute) }

    var showKeyboardInput by remember { mutableStateOf(false) }

    key(selectedHour, selectedMinute) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )

        val currentFormattedTime = remember(timePickerState.hour, timePickerState.minute) {
            String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.2.dp, BrandPurple.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header with Alarm Clock Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = BrandPurple.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = BrandPurple,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Eslatma vaqti",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Budilnik sozlamasi",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Mode Toggle (Dial vs Keyboard)
                        IconButton(
                            onClick = { showKeyboardInput = !showKeyboardInput },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = if (showKeyboardInput) Icons.Default.Schedule else Icons.Default.Edit,
                                contentDescription = "Kiritish rejimi",
                                tint = BrandPurple
                            )
                        }
                    }

                    // Large Digital Time Display (Alarm Clock Style)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BrandPurple.copy(alpha = 0.08f),
                        border = BorderStroke(1.5.dp, BrandPurple.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentFormattedTime,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandPurple,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Har kuni shu vaqtda eslatiladi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Preset Quick Choice Chips
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Tayyor vaqtlar:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickTimes = listOf("07:00", "08:30", "12:00", "18:00", "21:30")
                            quickTimes.forEach { qTime ->
                                val isSelected = currentFormattedTime == qTime
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val qParts = qTime.split(":")
                                            selectedHour = qParts.getOrNull(0)?.toIntOrNull() ?: 8
                                            selectedMinute = qParts.getOrNull(1)?.toIntOrNull() ?: 30
                                        }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = qTime,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Time Picker Component
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showKeyboardInput) {
                            TimeInput(
                                state = timePickerState,
                                colors = TimePickerDefaults.colors(
                                    timeSelectorSelectedContainerColor = BrandPurple.copy(alpha = 0.15f),
                                    timeSelectorSelectedContentColor = BrandPurple
                                )
                            )
                        } else {
                            TimePicker(
                                state = timePickerState,
                                colors = TimePickerDefaults.colors(
                                    clockDialSelectedContentColor = Color.White,
                                    clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface,
                                    selectorColor = BrandPurple,
                                    timeSelectorSelectedContainerColor = BrandPurple.copy(alpha = 0.15f),
                                    timeSelectorSelectedContentColor = BrandPurple
                                )
                            )
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Bekor qilish",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val timeStr = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
                                onTimeSelected(timeStr)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Saqlash",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

