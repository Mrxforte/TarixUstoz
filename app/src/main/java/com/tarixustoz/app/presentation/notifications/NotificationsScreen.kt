package com.tarixustoz.app.presentation.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToMistakes: () -> Unit,
    onNavigateToDailyQuiz: () -> Unit,
    onNavigateToTests: () -> Unit,
    onNavigateToTextbooks: () -> Unit = {},
    onNavigateToLeaderboard: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onResumeQuiz: (String) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val handleDismissById: (String) -> Unit = { id ->
        viewModel.dismiss(id)
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Bildirishnoma o‘chirildi",
                actionLabel = "Bekor qilish",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDismiss(id)
            }
        }
    }

    val navigateForTarget: (NotificationTarget, String?) -> Unit = { target, resumeRoute ->
        when (target) {
            NotificationTarget.RESUME_QUIZ -> {
                if (!resumeRoute.isNullOrBlank()) onResumeQuiz(resumeRoute) else onNavigateToTests()
            }
            NotificationTarget.MISTAKES -> onNavigateToMistakes()
            NotificationTarget.DAILY_QUIZ -> onNavigateToDailyQuiz()
            NotificationTarget.TESTS -> onNavigateToTests()
            NotificationTarget.TEXTBOOKS -> onNavigateToTextbooks()
            NotificationTarget.LEADERBOARD -> onNavigateToLeaderboard()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            MediumTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Bildirishnomalar",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.unreadCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(
                                    text = uiState.unreadCount.toString(),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("notifications_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Orqaga"
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = uiState.unreadCount > 0,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(
                            onClick = { viewModel.markAllAsRead() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("notifications_mark_all_read_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DoneAll,
                                contentDescription = "Barchasini o‘qilgan deb belgilash",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("notifications_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "Bildirishnoma sozlamalari"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val aiItems = (uiState.aiSectionState as? AiSectionUiState.Ready)?.items.orEmpty()
            val hasVisibleAi = uiState.showAiSection && (
                uiState.aiSectionState is AiSectionUiState.Loading ||
                    uiState.aiSectionState is AiSectionUiState.Error ||
                    aiItems.isNotEmpty()
                )
            val isCompletelyEmpty = (!uiState.showCoreSection || uiState.filteredCoreNotifications.isEmpty()) &&
                !hasVisibleAi

            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.hasDismissedNotifications) {
                    item(key = "restore_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "O‘chirilgan xabarlar mavjud",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { viewModel.restoreAll() },
                                modifier = Modifier.testTag("notifications_restore_all_button")
                            ) {
                                Text("Tiklash")
                            }
                        }
                    }
                }

                // Filter Chips
                item(key = "filter_chips") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(NotificationFilter.entries, key = { it.name }) { filter ->
                            val selected = uiState.selectedFilter == filter
                            val labelText = if (filter == NotificationFilter.UNREAD && uiState.unreadCount > 0) {
                                "${filter.label} (${uiState.unreadCount})"
                            } else {
                                filter.label
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.selectFilter(filter) },
                                label = { Text(text = labelText, maxLines = 1) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                    item(key = "permission_banner") {
                        ElevatedCard(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notification_permission_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Kunlik eslatmalarni yoqing",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Har kuni kun savolini olish uchun bildirishnomaga ruxsat bering.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("grant_notification_permission_button")
                                ) {
                                    Text("Yoqish")
                                }
                            }
                        }
                    }
                }

                if (isCompletelyEmpty) {
                    item(key = "empty_state") {
                        NotificationsEmptyState(
                            hasDismissed = uiState.hasDismissedNotifications,
                            isFiltered = uiState.selectedFilter != NotificationFilter.ALL,
                            onRestoreAll = { viewModel.restoreAll() },
                            onClearFilter = { viewModel.selectFilter(NotificationFilter.ALL) },
                            onNavigateToTests = onNavigateToTests
                        )
                    }
                } else {
                    if (uiState.showCoreSection && uiState.filteredCoreNotifications.isNotEmpty()) {
                        item(key = "header_core") {
                            SectionHeader(title = "Kunlik maqsad, Savol va Malaka")
                        }
                        items(uiState.filteredCoreNotifications, key = { it.id }) { item ->
                            SwipeableNotificationCard(
                                item = item,
                                onClick = {
                                    viewModel.markAsRead(item.id)
                                    navigateForTarget(item.target, item.resumeRoute)
                                },
                                onDismiss = { handleDismissById(item.id) }
                            )
                        }
                    }

                    if (uiState.showAiSection) {
                        val isAiLoading = uiState.aiSectionState is AiSectionUiState.Loading
                        item(key = "ai_header") {
                            AiPedagogicalSectionHeader(
                                isLoading = isAiLoading,
                                onRefresh = { viewModel.refreshPedagogicalAdvice(forceRefresh = true) }
                            )
                        }

                        when (val aiState = uiState.aiSectionState) {
                            AiSectionUiState.Idle,
                            AiSectionUiState.Loading -> {
                                item(key = "ai_loading") {
                                    AiPedagogicalLoadingCard()
                                }
                            }

                            is AiSectionUiState.Error -> {
                                item(key = "ai_error") {
                                    AiPedagogicalErrorCard(
                                        message = aiState.message,
                                        onRetry = { viewModel.refreshPedagogicalAdvice(forceRefresh = true) }
                                    )
                                }
                            }

                            is AiSectionUiState.Ready -> {
                                if (aiState.items.isEmpty()) {
                                    item(key = "ai_filtered_empty") {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Tanlangan filtr bo‘yicha yangi AI tavsiyalari yo‘q.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    item(key = "ai_overview") {
                                        AiPedagogicalOverviewBanner(assessment = aiState.overallAssessment)
                                    }
                                    items(aiState.items, key = { it.id }) { aiItem ->
                                        SwipeableAiPedagogicalCard(
                                            item = aiItem,
                                            onClick = {
                                                viewModel.markAsRead(aiItem.id)
                                                navigateForTarget(aiItem.target, null)
                                            },
                                            onDismiss = { handleDismissById(aiItem.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bildirishnoma sozlamalari",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            showSettingsSheet = false
                            onNavigateToProfile()
                        }
                    ) {
                        Text("Profil")
                    }
                }
                Text(
                    text = "Kunlik eslatmalar va ogohlantirish turlarini boshqaring",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                NotificationToggleListItem(
                    headline = "Kunlik eslatmalar",
                    supporting = "Har kuni kun savolini eslatish",
                    icon = Icons.Default.NotificationsActive,
                    checked = uiState.preferences.dailyReminder,
                    onCheckedChange = { viewModel.setDailyReminder(it) },
                    testTag = "notif_sheet_daily_switch"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ListItem(
                    headlineContent = { Text("Eslatma vaqti", fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Text("Har kuni soat ${uiState.preferences.reminderTime} da")
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = uiState.preferences.reminderTime,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showTimePickerDialog = true }
                        .testTag("notif_sheet_time_picker_row")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                NotificationToggleListItem(
                    headline = "Xatolar tahlili",
                    supporting = "Bilmagan savollarni takrorlash eslatmasi",
                    icon = Icons.Default.Warning,
                    checked = uiState.preferences.mistakeReminder,
                    onCheckedChange = { viewModel.setMistakeReminder(it) },
                    testTag = "notif_sheet_mistakes_switch"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                NotificationToggleListItem(
                    headline = "Yangi test va darsliklar",
                    supporting = "Bazaga yangi savollar qo‘shilganda xabar berish",
                    icon = Icons.Outlined.MenuBook,
                    checked = uiState.preferences.newTestsReminder,
                    onCheckedChange = { viewModel.setNewTestsReminder(it) },
                    testTag = "notif_sheet_new_tests_switch"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                NotificationToggleListItem(
                    headline = "Ovozli bildirishnoma",
                    supporting = "Bildirishnoma kelganda ovoz chiqarish",
                    icon = Icons.Outlined.VolumeUp,
                    checked = uiState.preferences.soundEnabled,
                    onCheckedChange = { viewModel.setSoundEnabled(it) },
                    testTag = "notif_sheet_sound_switch"
                )
            }
        }
    }

    if (showTimePickerDialog) {
        val parts = uiState.preferences.reminderTime.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 30
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = {
                Text(
                    text = "Eslatma vaqtini tanlang",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setReminderTime(timePickerState.hour, timePickerState.minute)
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("notif_time_picker_confirm")
                ) {
                    Text("Saqlash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Bekor qilish")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDismiss()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val bgColor by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Bildirishnomani o‘chirish",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        NotificationCard(
            item = item,
            onClick = onClick,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isResumeCard = item.resumeRoute != null
    val (containerTone, iconTint, iconVector) = when (item.category) {
        NotificationCategory.DAILY_GOAL -> Triple(
            if (isResumeCard) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            if (isResumeCard) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            if (isResumeCard) Icons.Default.PlayArrow else Icons.Default.Flag
        )
        NotificationCategory.DAILY_QUIZ -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.Quiz
        )
        NotificationCategory.XP_STREAK -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.LocalFireDepartment
        )
    }

    val cardColor = when {
        isResumeCard && !item.isRead -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        !item.isRead -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = when {
        isResumeCard -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
        !item.isRead -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = if (item.isRead) 0.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_card_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = containerTone,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!item.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isResumeCard) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!item.highlightTag.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = containerTone
                        ) {
                            Text(
                                text = item.highlightTag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = iconTint,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = item.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.progressFraction != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { item.progressFraction },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isResumeCard) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        if (!item.progressText.isNullOrBlank()) {
                            Text(
                                text = item.progressText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.actionHint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isResumeCard) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = item.actionHint,
                            tint = if (isResumeCard) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = item.timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() }
                    .testTag("notification_dismiss_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Bildirishnomani yopish",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NotificationsEmptyState(
    hasDismissed: Boolean,
    isFiltered: Boolean,
    onRestoreAll: () -> Unit,
    onClearFilter: () -> Unit,
    onNavigateToTests: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("notifications_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFiltered) {
                            Icons.Default.NotificationsNone
                        } else {
                            Icons.Outlined.NotificationsOff
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Text(
                text = if (isFiltered) {
                    "Bu bo‘limda bildirishnomalar yo‘q"
                } else {
                    "Hozircha yangi bildirishnomalar yo‘q"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isFiltered) {
                    "Boshqa toifalarni ko‘rish uchun filtrni o‘zgartirib ko‘ring."
                } else {
                    "Kunlik maqsad (30), kun savoli, Malaka va AI pedagogik tavsiyalari shu yerda ko‘rinadi."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            when {
                isFiltered -> FilledTonalButton(
                    onClick = onClearFilter,
                    modifier = Modifier.testTag("notifications_clear_filter_button")
                ) {
                    Text("Barchasini ko‘rsatish")
                }
                hasDismissed -> FilledTonalButton(
                    onClick = onRestoreAll,
                    modifier = Modifier.testTag("notifications_empty_restore_button")
                ) {
                    Text("O‘chirilganlarni tiklash")
                }
                else -> FilledTonalButton(
                    onClick = onNavigateToTests,
                    modifier = Modifier.testTag("notifications_empty_tests_button")
                ) {
                    Text("Testlarga o‘tish")
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleListItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    ListItem(
        headlineContent = { Text(headline, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(supporting) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
    )
}
