package com.tarixustoz.app.presentation.profile

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.presentation.common.DailyNotificationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SavedItemDto(
    val title: String,
    val subtitle: String
)

data class AchievementUiState(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isUnlocked: Boolean,
    val currentProgress: Int,
    val targetProgress: Int,
    val tier: String
)

data class DayActivity(
    val dayName: String,
    val count: Int,
    val isToday: Boolean
)

data class DailyGoalUiState(
    val todaySolved: Int = 0,
    val todayCorrect: Int = 0,
    val todayIncorrect: Int = 0,
    val todayGoalTotal: Int = 20,
    val todayAccuracy: Int = 0,
    val weeklyTotalSolved: Int = 0,
    val weeklyCorrect: Int = 0,
    val weeklyIncorrect: Int = 0,
    val weeklyAccuracy: Int = 0,
    val monthlyTotalSolved: Int = 0,
    val monthlyCorrect: Int = 0,
    val monthlyIncorrect: Int = 0,
    val monthlyAccuracy: Int = 0,
    val yearlyTotalSolved: Int = 0,
    val yearlyCorrect: Int = 0,
    val yearlyIncorrect: Int = 0,
    val yearlyAccuracy: Int = 0,
    val daysGoalMetThisWeek: Int = 0,
    val weekDays: List<DayActivity> = listOf(
        DayActivity("Du", 0, false),
        DayActivity("Se", 0, false),
        DayActivity("Ch", 0, false),
        DayActivity("Pa", 0, false),
        DayActivity("Ju", 0, false),
        DayActivity("Sh", 0, false),
        DayActivity("Ya", 0, false)
    )
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val database: AppDatabase
) : ViewModel() {

    val userEmail: StateFlow<String?> = preferencesRepository.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isGuest: StateFlow<Boolean> = preferencesRepository.isGuest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val displayName: StateFlow<String> = preferencesRepository.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Azizbek")

    val userBio: StateFlow<String> = preferencesRepository.userBio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Tarix bilimdoni • DTM tayyorgarlik")

    val userAvatar: StateFlow<String> = preferencesRepository.userAvatar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AS")

    val userPhone: StateFlow<String> = preferencesRepository.userPhone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "+998 90 123 45 67")

    val userTarget: StateFlow<String> = preferencesRepository.userTarget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DTM 189 ball • O‘zbekiston tarixi")

    val userXp: StateFlow<Long> = preferencesRepository.userXp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 78L)

    val userStreak: StateFlow<Int> = preferencesRepository.userStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val selectedGrade: StateFlow<Int> = preferencesRepository.selectedGrade
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 6)

    val levelTitle: StateFlow<String> = preferencesRepository.userXp.map { xp ->
        when {
            xp < 100 -> "1-daraja • Boshlang'ich"
            xp < 300 -> "2-daraja • Izlanuvchi"
            xp < 600 -> "3-daraja • Bilimdon"
            xp < 1000 -> "4-daraja • Tarixchi"
            else -> "5-daraja • Ustoz"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1-daraja • Boshlang'ich")

    val totalSolvedCount: StateFlow<Int> = database.quizAttemptDao().getTotalCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val correctAnswersCount: StateFlow<Int> = database.quizAttemptDao().getCorrectCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val accuracyPercentage: StateFlow<Int> = combine(
        database.quizAttemptDao().getCorrectCountFlow(),
        database.quizAttemptDao().getTotalCountFlow()
    ) { correct, total ->
        if (total > 0) (correct * 100) / total else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private fun getStartOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfWeekMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfMonthMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    private fun getStartOfYearMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    val dailyGoalUiState: StateFlow<DailyGoalUiState> = combine(
        database.quizAttemptDao().getTodayTotalCountFlow(getStartOfDayMillis()),
        database.quizAttemptDao().getTodayCorrectCountFlow(getStartOfDayMillis()),
        database.quizAttemptDao().getWeeklyAttemptsFlow(getStartOfWeekMillis()),
        database.quizAttemptDao().getMonthlyAttemptsFlow(getStartOfMonthMillis()),
        database.quizAttemptDao().getYearlyAttemptsFlow(getStartOfYearMillis())
    ) { todayTotal, todayCorrect, weeklyAttempts, monthlyAttempts, yearlyAttempts ->
        val todayIncorrect = (todayTotal - todayCorrect).coerceAtLeast(0)
        val accuracy = if (todayTotal > 0) (todayCorrect * 100) / todayTotal else 0

        val weeklyTotal = weeklyAttempts.size
        val weeklyCorrect = weeklyAttempts.count { it.wasCorrect }
        val weeklyIncorrect = (weeklyTotal - weeklyCorrect).coerceAtLeast(0)
        val weeklyAccuracy = if (weeklyTotal > 0) (weeklyCorrect * 100) / weeklyTotal else 0

        val monthlyTotal = monthlyAttempts.size
        val monthlyCorrect = monthlyAttempts.count { it.wasCorrect }
        val monthlyIncorrect = (monthlyTotal - monthlyCorrect).coerceAtLeast(0)
        val monthlyAccuracy = if (monthlyTotal > 0) (monthlyCorrect * 100) / monthlyTotal else 0

        val yearlyTotal = yearlyAttempts.size
        val yearlyCorrect = yearlyAttempts.count { it.wasCorrect }
        val yearlyIncorrect = (yearlyTotal - yearlyCorrect).coerceAtLeast(0)
        val yearlyAccuracy = if (yearlyTotal > 0) (yearlyCorrect * 100) / yearlyTotal else 0

        val daysOfWeekUz = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")

        val cal = Calendar.getInstance()
        val currentDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        val countsByDay = IntArray(7) { 0 }
        weeklyAttempts.forEach { attempt ->
            val attemptCal = Calendar.getInstance()
            attemptCal.timeInMillis = attempt.timestamp
            val dayIdx = (attemptCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (dayIdx in 0..6) {
                countsByDay[dayIdx]++
            }
        }

        val weekDaysList = daysOfWeekUz.mapIndexed { index, name ->
            DayActivity(
                dayName = name,
                count = countsByDay[index],
                isToday = index == currentDayOfWeek
            )
        }

        val daysGoalMet = countsByDay.count { it >= 20 }

        DailyGoalUiState(
            todaySolved = todayTotal,
            todayCorrect = todayCorrect,
            todayIncorrect = todayIncorrect,
            todayGoalTotal = 20,
            todayAccuracy = accuracy,
            weeklyTotalSolved = weeklyTotal,
            weeklyCorrect = weeklyCorrect,
            weeklyIncorrect = weeklyIncorrect,
            weeklyAccuracy = weeklyAccuracy,
            monthlyTotalSolved = monthlyTotal,
            monthlyCorrect = monthlyCorrect,
            monthlyIncorrect = monthlyIncorrect,
            monthlyAccuracy = monthlyAccuracy,
            yearlyTotalSolved = yearlyTotal,
            yearlyCorrect = yearlyCorrect,
            yearlyIncorrect = yearlyIncorrect,
            yearlyAccuracy = yearlyAccuracy,
            daysGoalMetThisWeek = daysGoalMet,
            weekDays = weekDaysList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyGoalUiState())

    val achievementsList: StateFlow<List<AchievementUiState>> = combine(
        database.quizAttemptDao().getTotalCountFlow(),
        preferencesRepository.userStreak,
        accuracyPercentage,
        preferencesRepository.userXp
    ) { solved, streak, accuracy, xp ->
        listOf(
            AchievementUiState(
                id = "ach_first_step",
                title = "Birinchi Qadam",
                description = "Kamida 1 ta test yechish",
                icon = Icons.Default.CheckCircle,
                isUnlocked = solved >= 1,
                currentProgress = solved.coerceAtMost(1),
                targetProgress = 1,
                tier = "BRONZE"
            ),
            AchievementUiState(
                id = "ach_explorer",
                title = "Tarix Izlanuvchisi",
                description = "20 ta test savolini yechish",
                icon = Icons.Default.Explore,
                isUnlocked = solved >= 20,
                currentProgress = solved.coerceAtMost(20),
                targetProgress = 20,
                tier = "BRONZE"
            ),
            AchievementUiState(
                id = "ach_streak_master",
                title = "Mug'ambir O'quvchi",
                description = "7 kunlik uzluksiz seriyaga erishish",
                icon = Icons.Default.LocalFireDepartment,
                isUnlocked = streak >= 7,
                currentProgress = streak.coerceAtMost(7),
                targetProgress = 7,
                tier = "SILVER"
            ),
            AchievementUiState(
                id = "ach_master",
                title = "Tarix Bilimdoni",
                description = "50 ta test savolini yechish",
                icon = Icons.Default.EmojiEvents,
                isUnlocked = solved >= 50,
                currentProgress = solved.coerceAtMost(50),
                targetProgress = 50,
                tier = "SILVER"
            ),
            AchievementUiState(
                id = "ach_sharpshooter",
                title = "Mergan O'quvchi",
                description = "80% va undan yuqori aniqlikka erishish",
                icon = Icons.Default.Stars,
                isUnlocked = accuracy >= 80 && solved >= 5,
                currentProgress = if (solved >= 5) accuracy.coerceAtMost(80) else 0,
                targetProgress = 80,
                tier = "GOLD"
            ),
            AchievementUiState(
                id = "ach_historian",
                title = "Buyuk Tarixchi",
                description = "100 ta test savolini yechish",
                icon = Icons.Default.MilitaryTech,
                isUnlocked = solved >= 100,
                currentProgress = solved.coerceAtMost(100),
                targetProgress = 100,
                tier = "GOLD"
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyReminder: StateFlow<Boolean> = preferencesRepository.dailyReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val mistakeReminder: StateFlow<Boolean> = preferencesRepository.mistakeReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val newTestsReminder: StateFlow<Boolean> = preferencesRepository.newTestsReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val soundEnabled: StateFlow<Boolean> = preferencesRepository.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reminderTime: StateFlow<String> = preferencesRepository.reminderTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "08:30")

    val wrongQuestionsCount: StateFlow<Int> = database.wrongQuestionDao().getAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalSavedCount: StateFlow<Int> = combine(
        database.wrongQuestionDao().getAll(),
        preferencesRepository.favorites
    ) { wrongQs, favs ->
        wrongQs.size + favs.size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedItemsList: StateFlow<List<SavedItemDto>> = combine(
        database.wrongQuestionDao().getAll(),
        preferencesRepository.favorites
    ) { wrongQs, favs ->
        val list = mutableListOf<SavedItemDto>()
        if (wrongQs.isNotEmpty()) {
            list.add(SavedItemDto("Xato ishlangan testlar", "${wrongQs.size} ta xatolar ustida ishlash saqlangan"))
        }
        if (favs.isNotEmpty()) {
            list.add(SavedItemDto("Tanlangan savollar", "${favs.size} ta savol sevimlilarga qo'shilgan"))
        }
        if (list.isEmpty()) {
            list.add(SavedItemDto("Mavzular to'plami", "Saqlangan test va darsliklar"))
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateProfile(name: String, bio: String, avatar: String, grade: Int) {
        viewModelScope.launch {
            preferencesRepository.setDisplayName(name.trim().ifEmpty { "Azizbek" })
            preferencesRepository.setUserBio(bio.trim())
            preferencesRepository.setUserAvatar(avatar)
            preferencesRepository.setSelectedGrade(grade)
        }
    }

    fun setDailyReminder(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDailyReminder(enabled)
            scheduleDailyNotification(enabled)
        }
    }

    fun setMistakeReminder(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMistakeReminder(enabled) }
    }

    fun setNewTestsReminder(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNewTestsReminder(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundEnabled(enabled) }
    }

    fun setReminderTime(time: String) {
        viewModelScope.launch {
            preferencesRepository.setReminderTime(time)
            scheduleDailyNotification(true)
        }
    }

    private fun scheduleDailyNotification(enabled: Boolean) {
        try {
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val timeStr = reminderTime.value
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

                val initialDelayMillis = target.timeInMillis - now.timeInMillis

                val request = PeriodicWorkRequestBuilder<DailyNotificationWorker>(
                    24, TimeUnit.HOURS
                )
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            preferencesRepository.setUserEmail(null)
            preferencesRepository.setIsGuest(true)
        }
    }
}
