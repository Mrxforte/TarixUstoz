package com.tarixustoz.app.presentation.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.presentation.common.DailyNotificationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class NotificationCategory {
    MISTAKE,
    DAILY,
    STREAK,
    TEXTBOOK,
    ACHIEVEMENT
}

enum class NotificationGroup(val label: String) {
    TODAY("Bugun"),
    EARLIER("Avvalgi")
}

enum class NotificationFilter(val label: String) {
    ALL("Barchasi"),
    UNREAD("O‘qilmagan"),
    MISTAKE("Xatolar"),
    DAILY("Kunlik"),
    TEXTBOOK("Darsliklar")
}

enum class NotificationTarget(val actionHint: String) {
    MISTAKES("Xatolar bo‘limini ochish"),
    DAILY_QUIZ("Kun savolini yechish"),
    TESTS("Testlarga o‘tish"),
    TEXTBOOKS("Darsliklarni ochish"),
    LEADERBOARD("Reytingni ko‘rish")
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timeLabel: String,
    val group: NotificationGroup,
    val category: NotificationCategory,
    val isRead: Boolean,
    val target: NotificationTarget
)

data class NotificationsPreferencesState(
    val dailyReminder: Boolean = true,
    val mistakeReminder: Boolean = true,
    val newTestsReminder: Boolean = true,
    val soundEnabled: Boolean = true,
    val reminderTime: String = "08:30"
)

data class NotificationsUiState(
    val allNotifications: List<NotificationItem> = emptyList(),
    val filteredNotifications: List<NotificationItem> = emptyList(),
    val selectedFilter: NotificationFilter = NotificationFilter.ALL,
    val unreadCount: Int = 0,
    val mistakesCount: Int = 0,
    val hasDismissedNotifications: Boolean = false,
    val preferences: NotificationsPreferencesState = NotificationsPreferencesState()
)

object NotificationCatalog {
    const val ID_DAILY_QUIZ = "n_daily_quiz"
    const val ID_MISTAKES = "n_mistakes"
    const val ID_DAILY_GOAL = "n_daily_goal"
    const val ID_TEXTBOOKS = "n_textbooks"
    const val ID_STREAK = "n_streak"

    fun activeIds(mistakesCount: Int): List<String> = buildList {
        add(ID_DAILY_QUIZ)
        if (mistakesCount > 0) add(ID_MISTAKES)
        add(ID_DAILY_GOAL)
        add(ID_TEXTBOOKS)
        add(ID_STREAK)
    }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val mistakesRepository: MistakesRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(NotificationFilter.ALL)

    private fun startOfDayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private data class CoreStats(
        val mistakesCount: Int,
        val streak: Int,
        val xp: Long,
        val selectedGrade: Int,
        val todaySolved: Int
    )

    private data class NotificationStorageState(
        val readIds: Set<String>,
        val dismissedIds: Set<String>,
        val lastDailyText: String?
    )

    private val coreStatsFlow = combine(
        mistakesRepository.getActiveCount(),
        preferences.userStreak,
        preferences.userXp,
        preferences.selectedGrade,
        database.quizAttemptDao().getTodayTotalCountFlow(startOfDayMillis())
    ) { mistakes, streak, xp, grade, todaySolved ->
        CoreStats(
            mistakesCount = mistakes,
            streak = streak.coerceAtLeast(1),
            xp = xp,
            selectedGrade = grade,
            todaySolved = todaySolved
        )
    }

    private val storageStateFlow = combine(
        preferences.readNotificationIds,
        preferences.dismissedNotificationIds,
        preferences.lastDailyNotificationText
    ) { readIds, dismissedIds, lastDailyText ->
        NotificationStorageState(readIds, dismissedIds, lastDailyText)
    }

    private val notifPrefsFlow = combine(
        preferences.dailyReminder,
        preferences.mistakeReminder,
        preferences.newTestsReminder,
        preferences.soundEnabled,
        preferences.reminderTime
    ) { daily, mistake, newTests, sound, time ->
        NotificationsPreferencesState(
            dailyReminder = daily,
            mistakeReminder = mistake,
            newTestsReminder = newTests,
            soundEnabled = sound,
            reminderTime = time
        )
    }

    val uiState: StateFlow<NotificationsUiState> = combine(
        coreStatsFlow,
        storageStateFlow,
        selectedFilter,
        notifPrefsFlow
    ) { stats, storage, filter, prefsState ->
        val rawItems = buildNotifications(stats, storage.readIds, storage.lastDailyText, prefsState.reminderTime)
        val visibleItems = rawItems.filterNot { it.id in storage.dismissedIds }
        val filtered = when (filter) {
            NotificationFilter.ALL -> visibleItems
            NotificationFilter.UNREAD -> visibleItems.filter { !it.isRead }
            NotificationFilter.MISTAKE -> visibleItems.filter { it.category == NotificationCategory.MISTAKE }
            NotificationFilter.DAILY -> visibleItems.filter {
                it.category == NotificationCategory.DAILY || it.category == NotificationCategory.STREAK
            }
            NotificationFilter.TEXTBOOK -> visibleItems.filter { it.category == NotificationCategory.TEXTBOOK }
        }
        NotificationsUiState(
            allNotifications = visibleItems,
            filteredNotifications = filtered,
            selectedFilter = filter,
            unreadCount = visibleItems.count { !it.isRead },
            mistakesCount = stats.mistakesCount,
            hasDismissedNotifications = storage.dismissedIds.isNotEmpty(),
            preferences = prefsState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationsUiState()
    )

    private fun buildNotifications(
        stats: CoreStats,
        readIds: Set<String>,
        lastDailyText: String?,
        reminderTime: String
    ): List<NotificationItem> = buildList {
        add(
            NotificationItem(
                id = NotificationCatalog.ID_DAILY_QUIZ,
                title = "Bugungi kun savoli",
                message = lastDailyText?.takeIf { it.isNotBlank() }
                    ?: "Bugungi kun savolini yechib, +10 XP qo‘lga kiriting.",
                timeLabel = "Bugun, $reminderTime",
                group = NotificationGroup.TODAY,
                category = NotificationCategory.DAILY,
                isRead = NotificationCatalog.ID_DAILY_QUIZ in readIds,
                target = NotificationTarget.DAILY_QUIZ
            )
        )

        if (stats.mistakesCount > 0) {
            add(
                NotificationItem(
                    id = NotificationCatalog.ID_MISTAKES,
                    title = "${stats.mistakesCount} ta xato savol to‘plangan",
                    message = "Xatolarni qayta ishlab, mavzularni mustahkamlang.",
                    timeLabel = "Hozir",
                    group = NotificationGroup.TODAY,
                    category = NotificationCategory.MISTAKE,
                    isRead = NotificationCatalog.ID_MISTAKES in readIds,
                    target = NotificationTarget.MISTAKES
                )
            )
        }

        val dailyGoalTarget = 20
        val remaining = (dailyGoalTarget - stats.todaySolved).coerceAtLeast(0)
        add(
            NotificationItem(
                id = NotificationCatalog.ID_DAILY_GOAL,
                title = if (remaining == 0) {
                    "Kunlik maqsad bajarildi"
                } else {
                    "Kunlik maqsad: ${stats.todaySolved} / $dailyGoalTarget test"
                },
                message = if (remaining == 0) {
                    "Bugungi $dailyGoalTarget ta test rejasini to‘liq bajardingiz."
                } else {
                    "Bugungi rejangizga yetish uchun yana $remaining ta savol yeching."
                },
                timeLabel = "Bugun",
                group = NotificationGroup.TODAY,
                category = NotificationCategory.DAILY,
                isRead = NotificationCatalog.ID_DAILY_GOAL in readIds,
                target = NotificationTarget.TESTS
            )
        )

        add(
            NotificationItem(
                id = NotificationCatalog.ID_TEXTBOOKS,
                title = "${stats.selectedGrade}-sinf tarix darsliklari",
                message = "6–11-sinf darsliklari va mavzularini PDF shaklida o‘qing.",
                timeLabel = "Avvalgi",
                group = NotificationGroup.EARLIER,
                category = NotificationCategory.TEXTBOOK,
                isRead = NotificationCatalog.ID_TEXTBOOKS in readIds,
                target = NotificationTarget.TEXTBOOKS
            )
        )

        add(
            NotificationItem(
                id = NotificationCatalog.ID_STREAK,
                title = "${stats.streak} kunlik seriya • ${stats.xp} XP",
                message = "Reyting jadvalidagi o‘rningizni ko‘ring va seriyangizni saqlang.",
                timeLabel = "Avvalgi",
                group = NotificationGroup.EARLIER,
                category = NotificationCategory.STREAK,
                isRead = NotificationCatalog.ID_STREAK in readIds,
                target = NotificationTarget.LEADERBOARD
            )
        )
    }

    fun selectFilter(filter: NotificationFilter) {
        selectedFilter.value = filter
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            preferences.markNotificationRead(id)
        }
    }

    fun markAllAsRead() {
        val visibleIds = uiState.value.allNotifications.map { it.id }
        if (visibleIds.isEmpty()) return
        viewModelScope.launch {
            preferences.markAllNotificationsRead(visibleIds)
        }
    }

    fun dismiss(id: String) {
        viewModelScope.launch {
            preferences.dismissNotification(id)
        }
    }

    fun undoDismiss(id: String) {
        viewModelScope.launch {
            preferences.restoreNotification(id)
        }
    }

    fun restoreAll() {
        viewModelScope.launch {
            preferences.restoreAllNotifications()
        }
    }

    fun setDailyReminder(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setDailyReminder(enabled)
            scheduleDailyNotification(enabled, uiState.value.preferences.reminderTime)
        }
    }

    fun setMistakeReminder(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setMistakeReminder(enabled)
        }
    }

    fun setNewTestsReminder(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNewTestsReminder(enabled)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSoundEnabled(enabled)
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        val formatted = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)
        viewModelScope.launch {
            preferences.setReminderTime(formatted)
            if (uiState.value.preferences.dailyReminder) {
                scheduleDailyNotification(true, formatted)
            }
        }
    }

    private fun scheduleDailyNotification(enabled: Boolean, timeStr: String) {
        try {
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val parts = timeStr.split(":")
                val targetHour = parts.getOrNull(0)?.toIntOrNull() ?: 8
                val targetMinute = parts.getOrNull(1)?.toIntOrNull() ?: 30

                val now = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (before(now)) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                val initialDelayMillis = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
                val request = PeriodicWorkRequestBuilder<DailyNotificationWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    "daily_quiz_notification",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            } else {
                workManager.cancelUniqueWork("daily_quiz_notification")
            }
        } catch (_: Exception) {
        }
    }
}
