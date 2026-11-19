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
import com.google.firebase.auth.FirebaseAuth
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.AuthRepository
import com.tarixustoz.app.data.repository.CloudQuizAttempt
import com.tarixustoz.app.data.repository.CloudSyncRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.data.sync.OfflineSyncStatus
import com.tarixustoz.app.data.sync.SyncScheduler
import com.tarixustoz.app.presentation.common.DailyNotificationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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

enum class ResultsChartPeriod(val label: String) {
    DAY("Kun"),
    WEEK("Hafta"),
    MONTH("Oy"),
    YEAR("Yil")
}

data class DayActivity(
    val dayName: String,
    val count: Int,
    val isToday: Boolean
)

data class DailyGoalUiState(
    val todaySolved: Int = 0,
    val todayCorrect: Int = 0,
    val todayIncorrect: Int = 0,
    val todayGoalTotal: Int = 30,
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
    val daySlots: List<DayActivity> = listOf(
        DayActivity("00-06", 0, false),
        DayActivity("06-10", 0, false),
        DayActivity("10-14", 0, false),
        DayActivity("14-18", 0, false),
        DayActivity("18-21", 0, false),
        DayActivity("21-24", 0, false)
    ),
    val weekDays: List<DayActivity> = listOf(
        DayActivity("Du", 0, false),
        DayActivity("Se", 0, false),
        DayActivity("Ch", 0, false),
        DayActivity("Pa", 0, false),
        DayActivity("Ju", 0, false),
        DayActivity("Sh", 0, false),
        DayActivity("Ya", 0, false)
    ),
    val monthWeeks: List<DayActivity> = listOf(
        DayActivity("1-haf", 0, false),
        DayActivity("2-haf", 0, false),
        DayActivity("3-haf", 0, false),
        DayActivity("4-haf", 0, false),
        DayActivity("5-haf", 0, false)
    ),
    val yearMonths: List<DayActivity> = listOf(
        DayActivity("Yan", 0, false),
        DayActivity("Fev", 0, false),
        DayActivity("Mar", 0, false),
        DayActivity("Apr", 0, false),
        DayActivity("May", 0, false),
        DayActivity("Iyn", 0, false),
        DayActivity("Iyl", 0, false),
        DayActivity("Avg", 0, false),
        DayActivity("Sen", 0, false),
        DayActivity("Okt", 0, false),
        DayActivity("Noy", 0, false),
        DayActivity("Dek", 0, false)
    )
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val database: AppDatabase,
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val syncScheduler: SyncScheduler,
    private val auth: FirebaseAuth
) : ViewModel() {

    val syncStatus: StateFlow<OfflineSyncStatus> = syncScheduler.observeSyncStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfflineSyncStatus())

    val userEmail: StateFlow<String?> = preferencesRepository.userEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isGuest: StateFlow<Boolean> = preferencesRepository.isGuest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val displayName: StateFlow<String> = preferencesRepository.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Tarixchi")

    val userBio: StateFlow<String> = preferencesRepository.userBio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Tarix fani izlanuvchisi")

    val userAvatar: StateFlow<String> = preferencesRepository.userAvatar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "TU")

    val userPhone: StateFlow<String> = preferencesRepository.userPhone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userTarget: StateFlow<String> = preferencesRepository.userTarget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DTM va Milliy sertifikat")

    val userXp: StateFlow<Long> = preferencesRepository.userXp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val userStreak: StateFlow<Int> = preferencesRepository.userStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val selectedGrade: StateFlow<Int> = preferencesRepository.selectedGrade
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val levelTitle: StateFlow<String> = preferencesRepository.userXp.map { xp ->
        when {
            xp < 100 -> "1-daraja • Boshlang'ich"
            xp < 300 -> "2-daraja • Izlanuvchi"
            xp < 600 -> "3-daraja • Bilimdon"
            xp < 1000 -> "4-daraja • Tarixchi"
            else -> "5-daraja • Ustoz"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1-daraja • Boshlang'ich")

    val totalSolvedCount: StateFlow<Int> = combine(
        database.quizAttemptDao().getTotalCountFlow(),
        preferencesRepository.totalSolvedTests
    ) { localCount, prefCount ->
        maxOf(localCount, prefCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val correctAnswersCount: StateFlow<Int> = combine(
        database.quizAttemptDao().getCorrectCountFlow(),
        preferencesRepository.totalCorrectAnswers
    ) { localCorrect, prefCorrect ->
        maxOf(localCorrect, prefCorrect)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val accuracyPercentage: StateFlow<Int> = combine(
        correctAnswersCount,
        totalSolvedCount
    ) { correct, total ->
        if (total > 0) ((correct * 100) / total).coerceIn(0, 100) else 0
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

    private fun hourToDaySlotIndex(hour: Int): Int = when (hour) {
        in 0..5 -> 0
        in 6..9 -> 1
        in 10..13 -> 2
        in 14..17 -> 3
        in 18..20 -> 4
        else -> 5
    }

    private val cloudAttemptsFlow = auth.currentUser?.uid?.let { uid ->
        cloudSyncRepository.observeUserAttempts(uid)
    } ?: flowOf(emptyList<CloudQuizAttempt>())

    val dailyGoalUiState: StateFlow<DailyGoalUiState> = combine(
        database.quizAttemptDao().getWeeklyAttemptsFlow(getStartOfWeekMillis()),
        database.quizAttemptDao().getMonthlyAttemptsFlow(getStartOfMonthMillis()),
        database.quizAttemptDao().getYearlyAttemptsFlow(getStartOfYearMillis()),
        cloudAttemptsFlow
    ) { weeklyAttempts, monthlyAttempts, yearlyAttempts, cloudAttempts ->
        val dayStart = getStartOfDayMillis()
        val weekStart = getStartOfWeekMillis()
        val monthStart = getStartOfMonthMillis()
        val yearStart = getStartOfYearMillis()

        val localToday = weeklyAttempts.filter { it.timestamp >= dayStart }
        val cloudToday = cloudAttempts.filter { (it.createdAt?.toDate()?.time ?: 0L) >= dayStart }
        val todayTotal = maxOf(localToday.size, cloudToday.sumOf { it.solvedCount.toInt() })
        val todayCorrect = maxOf(
            localToday.count { it.wasCorrect },
            cloudToday.sumOf { it.correctCount.toInt() }
        ).coerceIn(0, todayTotal)
        val todayIncorrect = (todayTotal - todayCorrect).coerceAtLeast(0)
        val accuracy = if (todayTotal > 0) (todayCorrect * 100) / todayTotal else 0

        val cloudWeekly = cloudAttempts.filter { (it.createdAt?.toDate()?.time ?: 0L) >= weekStart }
        val weeklyTotal = maxOf(weeklyAttempts.size, cloudWeekly.sumOf { it.solvedCount.toInt() })
        val weeklyCorrect = maxOf(
            weeklyAttempts.count { it.wasCorrect },
            cloudWeekly.sumOf { it.correctCount.toInt() }
        ).coerceIn(0, weeklyTotal)
        val weeklyIncorrect = (weeklyTotal - weeklyCorrect).coerceAtLeast(0)
        val weeklyAccuracy = if (weeklyTotal > 0) (weeklyCorrect * 100) / weeklyTotal else 0

        val cloudMonthly = cloudAttempts.filter { (it.createdAt?.toDate()?.time ?: 0L) >= monthStart }
        val monthlyTotal = maxOf(monthlyAttempts.size, cloudMonthly.sumOf { it.solvedCount.toInt() })
        val monthlyCorrect = maxOf(
            monthlyAttempts.count { it.wasCorrect },
            cloudMonthly.sumOf { it.correctCount.toInt() }
        ).coerceIn(0, monthlyTotal)
        val monthlyIncorrect = (monthlyTotal - monthlyCorrect).coerceAtLeast(0)
        val monthlyAccuracy = if (monthlyTotal > 0) (monthlyCorrect * 100) / monthlyTotal else 0

        val cloudYearly = cloudAttempts.filter { (it.createdAt?.toDate()?.time ?: 0L) >= yearStart }
        val yearlyTotal = maxOf(yearlyAttempts.size, cloudYearly.sumOf { it.solvedCount.toInt() })
        val yearlyCorrect = maxOf(
            yearlyAttempts.count { it.wasCorrect },
            cloudYearly.sumOf { it.correctCount.toInt() }
        ).coerceIn(0, yearlyTotal)
        val yearlyIncorrect = (yearlyTotal - yearlyCorrect).coerceAtLeast(0)
        val yearlyAccuracy = if (yearlyTotal > 0) (yearlyCorrect * 100) / yearlyTotal else 0

        val cal = Calendar.getInstance()

        // 1. Day slots (6 intervals of today)
        val daySlotLabels = listOf("00-06", "06-10", "10-14", "14-18", "18-21", "21-24")
        val currentHourSlot = hourToDaySlotIndex(cal.get(Calendar.HOUR_OF_DAY))
        val localBySlot = IntArray(6) { 0 }
        localToday.forEach { attempt ->
            val attemptCal = Calendar.getInstance().apply { timeInMillis = attempt.timestamp }
            val slotIdx = hourToDaySlotIndex(attemptCal.get(Calendar.HOUR_OF_DAY))
            localBySlot[slotIdx]++
        }
        val cloudBySlot = IntArray(6) { 0 }
        cloudToday.forEach { attempt ->
            val ts = attempt.createdAt?.toDate()?.time ?: 0L
            if (ts > 0L) {
                val attemptCal = Calendar.getInstance().apply { timeInMillis = ts }
                val slotIdx = hourToDaySlotIndex(attemptCal.get(Calendar.HOUR_OF_DAY))
                cloudBySlot[slotIdx] += attempt.solvedCount.toInt()
            }
        }
        val daySlotsList = daySlotLabels.mapIndexed { idx, label ->
            DayActivity(
                dayName = label,
                count = maxOf(localBySlot[idx], cloudBySlot[idx]),
                isToday = idx == currentHourSlot
            )
        }

        // 2. Week days (7 days of week)
        val daysOfWeekUz = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")
        val currentDayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val localByDay = IntArray(7) { 0 }
        weeklyAttempts.forEach { attempt ->
            val attemptCal = Calendar.getInstance().apply { timeInMillis = attempt.timestamp }
            val dayIdx = (attemptCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (dayIdx in 0..6) localByDay[dayIdx]++
        }
        val cloudByDay = IntArray(7) { 0 }
        cloudWeekly.forEach { attempt ->
            val ts = attempt.createdAt?.toDate()?.time ?: 0L
            if (ts > 0L) {
                val attemptCal = Calendar.getInstance().apply { timeInMillis = ts }
                val dayIdx = (attemptCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                if (dayIdx in 0..6) cloudByDay[dayIdx] += attempt.solvedCount.toInt()
            }
        }
        val countsByDay = IntArray(7) { idx -> maxOf(localByDay[idx], cloudByDay[idx]) }
        val weekDaysList = daysOfWeekUz.mapIndexed { index, name ->
            DayActivity(
                dayName = name,
                count = countsByDay[index],
                isToday = index == currentDayOfWeek
            )
        }

        // 3. Month weeks (5 week buckets of current month)
        val monthWeekLabels = listOf("1-haf", "2-haf", "3-haf", "4-haf", "5-haf")
        val currentMonthWeekIdx = ((cal.get(Calendar.DAY_OF_MONTH) - 1) / 7).coerceIn(0, 4)
        val localByMonthWeek = IntArray(5) { 0 }
        monthlyAttempts.forEach { attempt ->
            val attemptCal = Calendar.getInstance().apply { timeInMillis = attempt.timestamp }
            val wIdx = ((attemptCal.get(Calendar.DAY_OF_MONTH) - 1) / 7).coerceIn(0, 4)
            localByMonthWeek[wIdx]++
        }
        val cloudByMonthWeek = IntArray(5) { 0 }
        cloudMonthly.forEach { attempt ->
            val ts = attempt.createdAt?.toDate()?.time ?: 0L
            if (ts > 0L) {
                val attemptCal = Calendar.getInstance().apply { timeInMillis = ts }
                val wIdx = ((attemptCal.get(Calendar.DAY_OF_MONTH) - 1) / 7).coerceIn(0, 4)
                cloudByMonthWeek[wIdx] += attempt.solvedCount.toInt()
            }
        }
        val monthWeeksList = monthWeekLabels.mapIndexed { idx, label ->
            DayActivity(
                dayName = label,
                count = maxOf(localByMonthWeek[idx], cloudByMonthWeek[idx]),
                isToday = idx == currentMonthWeekIdx
            )
        }

        // 4. Year months (12 months of current year)
        val yearMonthLabels = listOf("Yan", "Fev", "Mar", "Apr", "May", "Iyn", "Iyl", "Avg", "Sen", "Okt", "Noy", "Dek")
        val currentMonthIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
        val localByMonth = IntArray(12) { 0 }
        yearlyAttempts.forEach { attempt ->
            val attemptCal = Calendar.getInstance().apply { timeInMillis = attempt.timestamp }
            val mIdx = attemptCal.get(Calendar.MONTH).coerceIn(0, 11)
            localByMonth[mIdx]++
        }
        val cloudByMonth = IntArray(12) { 0 }
        cloudYearly.forEach { attempt ->
            val ts = attempt.createdAt?.toDate()?.time ?: 0L
            if (ts > 0L) {
                val attemptCal = Calendar.getInstance().apply { timeInMillis = ts }
                val mIdx = attemptCal.get(Calendar.MONTH).coerceIn(0, 11)
                cloudByMonth[mIdx] += attempt.solvedCount.toInt()
            }
        }
        val yearMonthsList = yearMonthLabels.mapIndexed { idx, label ->
            DayActivity(
                dayName = label,
                count = maxOf(localByMonth[idx], cloudByMonth[idx]),
                isToday = idx == currentMonthIdx
            )
        }

        val daysGoalMet = countsByDay.count { it >= 30 }

        DailyGoalUiState(
            todaySolved = todayTotal,
            todayCorrect = todayCorrect,
            todayIncorrect = todayIncorrect,
            todayGoalTotal = 30,
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
            daySlots = daySlotsList,
            weekDays = weekDaysList,
            monthWeeks = monthWeeksList,
            yearMonths = yearMonthsList
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyGoalUiState())

    val achievementsList: StateFlow<List<AchievementUiState>> = combine(
        totalSolvedCount,
        preferencesRepository.userStreak,
        accuracyPercentage,
        preferencesRepository.userXp
    ) { solved, streak, accuracy, _ ->
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
            preferencesRepository.setDisplayName(name.trim().ifEmpty { "Tarixchi" })
            preferencesRepository.setUserBio(bio.trim())
            preferencesRepository.setUserAvatar(avatar)
            preferencesRepository.setSelectedGrade(grade)
            cloudSyncRepository.syncEditedProfileToCloud()
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

    fun triggerManualSync() {
        syncScheduler.triggerImmediateSync(forceRefresh = true)
    }

    fun claimTestAdReward(onAwarded: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Disabled rewarded ad in this version
            onAwarded(false)
        }
    }

    fun exitTestMode() {
        viewModelScope.launch {
            authRepository.exitTestMode()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
