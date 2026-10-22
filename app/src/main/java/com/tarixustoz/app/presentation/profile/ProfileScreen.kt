package com.tarixustoz.app.presentation.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tarixustoz.app.ThemeMode
import com.tarixustoz.app.presentation.theme.BrandPurple
import com.tarixustoz.app.presentation.theme.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profileViewModel: ProfileViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel()
) {
    val themeMode by themeViewModel.themeMode.collectAsState()
    val displayName by profileViewModel.displayName.collectAsState()
    val userBio by profileViewModel.userBio.collectAsState()
    val userAvatar by profileViewModel.userAvatar.collectAsState()
    val userXp by profileViewModel.userXp.collectAsState()
    val userStreak by profileViewModel.userStreak.collectAsState()
    val selectedGrade by profileViewModel.selectedGrade.collectAsState()

    val dailyReminder by profileViewModel.dailyReminder.collectAsState()
    val mistakeReminder by profileViewModel.mistakeReminder.collectAsState()
    val newTestsReminder by profileViewModel.newTestsReminder.collectAsState()
    val soundEnabled by profileViewModel.soundEnabled.collectAsState()
    val reminderTime by profileViewModel.reminderTime.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showSavedTestsDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                        onClick = { showEditProfileDialog = true },
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
                    .clickable { showEditProfileDialog = true }
                    .testTag("profile_user_card")
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .clickable { showEditProfileDialog = true }
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

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showEditProfileDialog = true }
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
                        Text(
                            text = "$selectedGrade-sinf • $userBio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "3-daraja • Bilimdon",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEEF2FF)
                            ) {
                                Text(
                                    text = "Profilni tahrirlash",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandPurple,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tahrirlash",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Stats: Ball, testlar soni, to'g'rilik va seriya
            Text(
                text = "Statistika va yutuqlar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Umumiy ball",
                    value = "$userXp XP",
                    icon = Icons.Default.Star,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Testlar soni",
                    value = "48 ta",
                    icon = Icons.Default.Quiz,
                    tint = BrandPurple,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "To‘g‘rilik natijasi",
                    value = "86%",
                    icon = Icons.Default.CheckCircle,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Seriya",
                    value = "$userStreak kun",
                    icon = Icons.Default.LocalFireDepartment,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f)
                )
            }

            // NOTIFICATION SETTINGS (Moved to Profile as requested!)
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
                        title = "Saqlangan testlar",
                        subtitle = "5 ta muhim mavzu va savollar saqlangan",
                        icon = Icons.Default.Bookmark,
                        onClick = { showSavedTestsDialog = true }
                    )
                }
            }

            // Theme Setting
            Text(
                text = "Ilova ko‘rinishi (Mavzu)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = { themeViewModel.setThemeMode(ThemeMode.SYSTEM) },
                    label = { Text("Tizim") },
                    leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandPurple,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = { themeViewModel.setThemeMode(ThemeMode.LIGHT) },
                    label = { Text("Yorug‘") },
                    leadingIcon = { Icon(Icons.Default.LightMode, null, Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandPurple,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = themeMode == ThemeMode.DARK,
                    onClick = { themeViewModel.setThemeMode(ThemeMode.DARK) },
                    label = { Text("Qorong‘u") },
                    leadingIcon = { Icon(Icons.Default.DarkMode, null, Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandPurple,
                        selectedLabelColor = Color.White
                    )
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    // PROFILE EDIT SCREEN / DIALOG (Opened on clicking avatar or name)
    if (showEditProfileDialog) {
        ProfileEditDialog(
            currentName = displayName,
            currentBio = userBio,
            currentAvatar = userAvatar,
            currentGrade = selectedGrade,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, bio, avatar, grade ->
                profileViewModel.updateProfile(name, bio, avatar, grade)
                showEditProfileDialog = false
            }
        )
    }

    // Time Picker Dialog for Notifications
    if (showTimePickerDialog) {
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = { Text("Eslatma vaqtini tanlang", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val times = listOf("07:00", "08:30", "12:00", "18:00", "20:00", "21:30")
                    times.forEach { time ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    profileViewModel.setReminderTime(time)
                                    showTimePickerDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = reminderTime == time,
                                onClick = {
                                    profileViewModel.setReminderTime(time)
                                    showTimePickerDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = BrandPurple)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(time, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Yopish")
                }
            }
        )
    }

    // Saqlangan testlar Dialog
    if (showSavedTestsDialog) {
        AlertDialog(
            onDismissRequest = { showSavedTestsDialog = false },
            title = { Text("Saqlangan testlar", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SavedTestItem(title = "Amir Temur davlati va harbiy yurishlari", tag = "7-sinf • O‘zbekiston tarixi")
                    SavedTestItem(title = "Qadimgi Misr va Nil madaniyati", tag = "6-sinf • Jahon tarixi")
                    SavedTestItem(title = "Somoniylar davrida ilm-fan yuksalishi", tag = "7-sinf • O‘zbekiston tarixi")
                    SavedTestItem(title = "Turk xoqonligining tashkil topishi", tag = "7-sinf • O‘zbekiston tarixi")
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
private fun ProfileEditDialog(
    currentName: String,
    currentBio: String,
    currentAvatar: String,
    currentGrade: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var bio by remember { mutableStateOf(currentBio) }
    var avatar by remember { mutableStateOf(currentAvatar) }
    var grade by remember { mutableIntStateOf(currentGrade) }

    val avatarOptions = listOf("AS", "🎓", "🏛️", "⚔️", "👑", "📜", "🏹", "🦁", "🌟", "TM", "OT")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = BrandPurple)
                Spacer(Modifier.width(8.dp))
                Text("Profilni tahrirlash", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Avatar selection preview
                Text(
                    text = "Avatarni tanlang:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    avatarOptions.take(5).forEach { opt ->
                        Surface(
                            shape = CircleShape,
                            color = if (avatar == opt) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (avatar == opt) BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { avatar = opt }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = opt,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (avatar == opt) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    avatarOptions.drop(5).forEach { opt ->
                        Surface(
                            shape = CircleShape,
                            color = if (avatar == opt) BrandPurple else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (avatar == opt) BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { avatar = opt }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = opt,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (avatar == opt) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ism yoki Taxallus") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_edit_name_input")
                )

                // Bio / Status input
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Maqom / Bio") },
                    singleLine = true,
                    placeholder = { Text("Tarix bilimdoni, DTM 189 ball...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_edit_bio_input")
                )

                // Grade selection
                Text(
                    text = "Sinfingiz:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(6, 7, 8, 9, 10, 11).forEach { g ->
                        FilterChip(
                            selected = grade == g,
                            onClick = { grade = g },
                            label = { Text("$g") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandPurple,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, bio, avatar, grade) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                modifier = Modifier.testTag("profile_edit_save_btn")
            ) {
                Text("Saqlash")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Bekor qilish")
            }
        }
    )
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
            color = Color(0xFFEEF2FF),
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
