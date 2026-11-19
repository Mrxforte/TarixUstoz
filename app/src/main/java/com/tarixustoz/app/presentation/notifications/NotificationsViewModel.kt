package com.tarixustoz.app.presentation.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.ActiveQuizState
import com.tarixustoz.app.data.repository.PedagogicalActionType
import com.tarixustoz.app.data.repository.PedagogicalAiRepository
import com.tarixustoz.app.data.repository.PedagogicalAiState
import com.tarixustoz.app.data.repository.PedagogicalRecommendation
import com.tarixustoz.app.data.repository.PedagogicalTone
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

const val DAILY_GOAL_TARGET = 30

enum class NotificationCategory {
    DAILY_GOAL,
    DAILY_QUIZ,
    XP_STREAK
}

enum class NotificationFilter(val label: String) {
    ALL("Barchasi"),
    UNREAD("O‘qilmagan"),
    DAILY_XP("Kunlik & XP"),
    AI_TIPS("AI Tavsiyalar")
}

enum class NotificationTarget(val defaultHint: String) {
    RESUME_QUIZ("Tashlab ketilgan testni davom ettirish"),
    DAILY_QUIZ("Kun savolini yechish"),
    TESTS("Testlarga o‘tish"),
    TEXTBOOKS("Darsliklarni ochish"),
    MISTAKES("Xatolarni ishlash"),
    LEADERBOARD("Reyting va XPni ko‘rish")
}

data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val timeLabel: String,
    val category: NotificationCategory,
    val isRead: Boolean,
    val target: NotificationTarget,
    val actionHint: String = target.defaultHint,
    val resumeRoute: String? = null,
    val progressFraction: Float? = null,
    val progressText: String? = null,
    val highlightTag: String? = null
)

data class AiPedagogicalNotificationItem(
    val id: String,
    val badge: String,
    val title: String,
    val summary: String,
    val pedagogicalTip: String,
    val focusArea: String,
    val tone: PedagogicalTone,
    val target: NotificationTarget,
    val actionHint: String,
    val isRead: Boolean
)

data class NotificationsPreferencesState(
    val dailyReminder: Boolean = true,
    val mistakeReminder: Boolean = true,
    val newTestsReminder: Boolean = true,
    val soundEnabled: Boolean = true,
    val reminderTime: String = "08:30"
)

sealed interface AiSectionUiState {
    data object Idle : AiSectionUiState
    data object Loading : AiSectionUiState
    data class Ready(
        val overallAssessment: String,
        val generatedAtMs: Long,
        val items: List<AiPedagogicalNotificationItem>
    ) : AiSectionUiState
    data class Error(val message: String) : AiSectionUiState
}

data class NotificationsUiState(
    val coreNotifications: List<NotificationItem> = emptyList(),
    val filteredCoreNotifications: List<NotificationItem> = emptyList(),
    val aiSectionState: AiSectionUiState = AiSectionUiState.Loading,
    val showCoreSection: Boolean = true,
    val showAiSection: Boolean = true,
    val selectedFilter: NotificationFilter = NotificationFilter.ALL,
    val unreadCount: Int = 0,
    val hasDismissedNotifications: Boolean = false,
    val preferences: NotificationsPreferencesState = NotificationsPreferencesState()
)

object NotificationCatalog {
    const val ID_DAILY_GOAL = "n_daily_goal"
    const val ID_DAILY_QUIZ = "n_daily_quiz"
    const val ID_STREAK = "n_streak"

    fun activeIds(): List<String> = listOf(
        ID_DAILY_GOAL,
        ID_DAILY_QUIZ,
        ID_STREAK
    )
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferencesRepository,
    private val database: AppDatabase,
    private val pedagogicalAiRepository: PedagogicalAiRepository
) : ViewModel() {

    private val selectedFilter = MutableStateFlow(NotificationFilter.ALL)

    init {
        refreshPedagogicalAdvice(forceRefresh = false)
    }

    private fun startOfDayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private data class CoreStats(
        val streak: Int,
        val xp: Long,
        val todaySolved: Int,
        val activeQuiz: ActiveQuizState?
    )

    private data class NotificationStorageState(
        val readIds: Set<String>,
        val dismissedIds: Set<String>,
        val lastDailyText: String?
    )

    private val coreStatsFlow = combine(
        preferences.userStreak,
        preferences.userXp,
        database.quizAttemptDao().getTodayTotalCountFlow(startOfDayMillis()),
        preferences.activeQuizState
    ) { streak, xp, todaySolved, activeQuiz ->
        CoreStats(
            streak = streak.coerceAtLeast(0),
            xp = xp.coerceAtLeast(0L),
            todaySolved = todaySolved.coerceAtLeast(0),
            activeQuiz = activeQuiz?.takeIf { it.questions.isNotEmpty() }
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
        notifPrefsFlow,
        pedagogicalAiRepository.state
    ) { stats, storage, filter, prefsState, aiState ->
        val rawCore = buildCoreNotifications(
            stats = stats,
            readIds = storage.readIds,
            lastDailyText = storage.lastDailyText,
            reminderTime = prefsState.reminderTime
        )
        val visibleCore = rawCore.filterNot { it.id in storage.dismissedIds }

        val filteredCore = when (filter) {
            NotificationFilter.ALL, NotificationFilter.DAILY_XP -> visibleCore
            NotificationFilter.UNREAD -> visibleCore.filter { !it.isRead }
            NotificationFilter.AI_TIPS -> emptyList()
        }

        val mappedAiState = mapAiState(
            aiState = aiState,
            readIds = storage.readIds,
            dismissedIds = storage.dismissedIds,
            filter = filter
        )

        val visibleAiItems = when (aiState) {
            is PedagogicalAiState.Success -> aiState.report.recommendations
                .filterNot { it.id in storage.dismissedIds }
            else -> emptyList()
        }

        val coreUnread = visibleCore.count { !it.isRead }
        val aiUnread = visibleAiItems.count { it.id !in storage.readIds }

        NotificationsUiState(
            coreNotifications = visibleCore,
            filteredCoreNotifications = filteredCore,
            aiSectionState = mappedAiState,
            showCoreSection = filter != NotificationFilter.AI_TIPS,
            showAiSection = filter != NotificationFilter.DAILY_XP,
            selectedFilter = filter,
            unreadCount = coreUnread + aiUnread,
            hasDismissedNotifications = storage.dismissedIds.isNotEmpty(),
            preferences = prefsState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationsUiState()
    )

    private fun buildCoreNotifications(
        stats: CoreStats,
        readIds: Set<String>,
        lastDailyText: String?,
        reminderTime: String
    ): List<NotificationItem> = buildList {
        val remaining = (DAILY_GOAL_TARGET - stats.todaySolved).coerceAtLeast(0)
        val activeQuiz = stats.activeQuiz
        val goalProgress = (stats.todaySolved.toFloat() / DAILY_GOAL_TARGET.toFloat()).coerceIn(0f, 1f)

        if (activeQuiz != null) {
            val currentQuestionNum = (activeQuiz.currentIndex + 1).coerceIn(1, activeQuiz.questions.size)
            val remainingInQuiz = (activeQuiz.questions.size - activeQuiz.currentIndex).coerceAtLeast(1)
            add(
                NotificationItem(
                    id = NotificationCatalog.ID_DAILY_GOAL,
                    title = if (remaining == 0) {
                        "Kunlik maqsad bajarildi ($DAILY_GOAL_TARGET/$DAILY_GOAL_TARGET)"
                    } else {
                        "Kunlik maqsad: ${stats.todaySolved} / $DAILY_GOAL_TARGET test"
                    },
                    message = "Tashlab chiqib ketilgan \"${activeQuiz.quizTitle}\" testi ($currentQuestionNum/${activeQuiz.questions.size}-savol, yana $remainingInQuiz ta qoldi) sizni kutmoqda.",
                    timeLabel = "Davom ettirish",
                    category = NotificationCategory.DAILY_GOAL,
                    isRead = NotificationCatalog.ID_DAILY_GOAL in readIds,
                    target = NotificationTarget.RESUME_QUIZ,
                    actionHint = "Tashlab ketilgan testga qaytish",
                    resumeRoute = activeQuiz.quizRoute,
                    progressFraction = goalProgress,
                    progressText = "${stats.todaySolved}/$DAILY_GOAL_TARGET",
                    highlightTag = "Yakunlanmagan test"
                )
            )
        } else {
            add(
                NotificationItem(
                    id = NotificationCatalog.ID_DAILY_GOAL,
                    title = if (remaining == 0) {
                        "Kunlik maqsad bajarildi ($DAILY_GOAL_TARGET/$DAILY_GOAL_TARGET)"
                    } else {
                        "Kunlik maqsad: ${stats.todaySolved} / $DAILY_GOAL_TARGET test"
                    },
                    message = if (remaining == 0) {
                        "Ajoyib natija! Bugungi $DAILY_GOAL_TARGET ta test maqsadini to‘liq bajardingiz."
                    } else {
                        "Bugungi $DAILY_GOAL_TARGET ta test rejasiga yetish uchun yana $remaining ta savol yeching."
                    },
                    timeLabel = "Bugun",
                    category = NotificationCategory.DAILY_GOAL,
                    isRead = NotificationCatalog.ID_DAILY_GOAL in readIds,
                    target = NotificationTarget.TESTS,
                    actionHint = "Testlarga o‘tish",
                    resumeRoute = null,
                    progressFraction = goalProgress,
                    progressText = "${stats.todaySolved}/$DAILY_GOAL_TARGET",
                    highlightTag = if (remaining == 0) "Bajarildi" else "Kunlik reja"
                )
            )
        }

        add(
            NotificationItem(
                id = NotificationCatalog.ID_DAILY_QUIZ,
                title = "Bugungi kun savoli",
                message = lastDailyText?.takeIf { it.isNotBlank() }
                    ?: "Bugungi kun savolini yechib, +10 XP qo‘lga kiriting.",
                timeLabel = "Bugun, $reminderTime",
                category = NotificationCategory.DAILY_QUIZ,
                isRead = NotificationCatalog.ID_DAILY_QUIZ in readIds,
                target = NotificationTarget.DAILY_QUIZ,
                actionHint = "Kun savolini yechish",
                highlightTag = "+10 XP"
            )
        )

        add(
            NotificationItem(
                id = NotificationCatalog.ID_STREAK,
                title = "${stats.xp} XP • ${stats.streak} kunlik seriya",
                message = if (stats.streak > 0) {
                    "To‘plangan ${stats.xp} XP va ${stats.streak} kunlik faollik seriyangizni saqlab, reytingda yuqoriga chiqing."
                } else {
                    "Hozirgi natijangiz ${stats.xp} XP. Bugun test ishlab kunlik seriyani boshlang!"
                },
                timeLabel = "Faollik",
                category = NotificationCategory.XP_STREAK,
                isRead = NotificationCatalog.ID_STREAK in readIds,
                target = NotificationTarget.LEADERBOARD,
                actionHint = "Reytingni ko‘rish",
                highlightTag = "${stats.xp} XP"
            )
        )
    }

    private fun mapAiState(
        aiState: PedagogicalAiState,
        readIds: Set<String>,
        dismissedIds: Set<String>,
        filter: NotificationFilter
    ): AiSectionUiState {
        return when (aiState) {
            PedagogicalAiState.Idle -> AiSectionUiState.Idle
            PedagogicalAiState.Loading -> AiSectionUiState.Loading
            is PedagogicalAiState.Error -> AiSectionUiState.Error(aiState.message)
            is PedagogicalAiState.Success -> {
                val mappedItems = aiState.report.recommendations
                    .filterNot { it.id in dismissedIds }
                    .map { it.toUiItem(isRead = it.id in readIds) }
                    .let { list ->
                        if (filter == NotificationFilter.UNREAD) list.filter { !it.isRead } else list
                    }
                AiSectionUiState.Ready(
                    overallAssessment = aiState.report.overallAssessment,
                    generatedAtMs = aiState.report.generatedAtMs,
                    items = mappedItems
                )
            }
        }
    }

    private fun PedagogicalRecommendation.toUiItem(isRead: Boolean): AiPedagogicalNotificationItem {
        val (target, hint) = when (parsedActionType) {
            PedagogicalActionType.MISTAKES -> NotificationTarget.MISTAKES to "Xatolarni ishlash"
            PedagogicalActionType.TEXTBOOKS -> NotificationTarget.TEXTBOOKS to "Darslikdan takrorlash"
            PedagogicalActionType.DAILY_QUIZ -> NotificationTarget.DAILY_QUIZ to "Kun savolini yechish"
            PedagogicalActionType.TESTS -> NotificationTarget.TESTS to "Testlarni yechish"
        }
        return AiPedagogicalNotificationItem(
            id = id,
            badge = badge,
            title = title,
            summary = summary,
            pedagogicalTip = pedagogicalTip,
            focusArea = focusArea,
            tone = parsedTone,
            target = target,
            actionHint = hint,
            isRead = isRead
        )
    }

    fun refreshPedagogicalAdvice(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            pedagogicalAiRepository.generateRecommendations(forceRefresh = forceRefresh)
        }
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
        val state = uiState.value
        val coreIds = state.coreNotifications.map { it.id }
        val aiIds = (state.aiSectionState as? AiSectionUiState.Ready)?.items?.map { it.id }.orEmpty()
        val allIds = coreIds + aiIds
        if (allIds.isEmpty()) return
        viewModelScope.launch {
            preferences.markAllNotificationsRead(allIds)
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
