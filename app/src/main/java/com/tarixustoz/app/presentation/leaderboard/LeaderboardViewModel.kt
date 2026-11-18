package com.tarixustoz.app.presentation.leaderboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.local.QuizAttemptEntity
import com.tarixustoz.app.data.repository.CloudLeaderboardEntry
import com.tarixustoz.app.data.repository.CloudQuizAttempt
import com.tarixustoz.app.data.repository.CloudSyncRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.presentation.theme.BrandPurple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class PeriodStatsSummary(
    val periodIndex: Int,
    val solvedCount: Int,
    val correctCount: Int,
    val accuracyPct: Int,
    val timeSpentMinutes: Int,
    val chartPoints: List<StudentChartDataPoint>
)

data class LeaderboardUiState(
    val displayName: String = "Tarixchi",
    val avatar: String = "TU",
    val xp: Long = 0L,
    val streak: Int = 0,
    val currentUserRankLabel: String = "—",
    val currentUserScoreLabel: String = "0 XP",
    val leaderboardUsers: List<LeaderboardUser> = emptyList(),
    val periodSummaries: Map<Int, PeriodStatsSummary> = emptyMap(),
    val categoryAnalytics: List<DetailedCategoryAnalytics> = emptyList(),
    val analysisCategories: List<CategoryAnalysisItem> = emptyList(),
    val isLoaded: Boolean = false
)

private data class CategorySpec(
    val title: String,
    val subtitle: String,
    val keys: List<String>,
    val quizRouteKey: String,
    val icon: ImageVector,
    val iconColor: Color,
    val barGradients: List<Color>,
    val recommendedChapter: String
)

private val CATEGORY_SPECS = listOf(
    CategorySpec(
        title = "Tarixiy voqealar",
        subtitle = "Voqealar va xronologiya bobi",
        keys = listOf("EVENT"),
        quizRouteKey = "EVENT",
        icon = Icons.Default.Event,
        iconColor = Color(0xFF6366F1),
        barGradients = listOf(Color(0xFF4F46E5), Color(0xFF818CF8)),
        recommendedChapter = "6-sinf Qadimgi dunyo tarixi: Davlatlar va sivilizatsiyalar"
    ),
    CategorySpec(
        title = "Tarixiy shaxslar",
        subtitle = "Buyuk siymolar va hukmdorlar",
        keys = listOf("PERSON"),
        quizRouteKey = "PERSON",
        icon = Icons.Default.Person,
        iconColor = Color(0xFF059669),
        barGradients = listOf(Color(0xFF059669), Color(0xFF34D399)),
        recommendedChapter = "7-sinf Tarix: Buyuk allomalar va sarkardalar"
    ),
    CategorySpec(
        title = "Urushlar va janglar",
        subtitle = "Harbiy yurishlar va jangnomalar",
        keys = listOf("WAR"),
        quizRouteKey = "WAR",
        icon = Icons.Default.Security,
        iconColor = Color(0xFFDC2626),
        barGradients = listOf(Color(0xFFDC2626), Color(0xFFFB7185)),
        recommendedChapter = "7-sinf Tarix: Harbiy yurishlar va mudofaa janglari"
    ),
    CategorySpec(
        title = "Yillar va sanalar",
        subtitle = "Sanalar va asrlar sinovi",
        keys = listOf("YEAR"),
        quizRouteKey = "YEAR",
        icon = Icons.Default.DateRange,
        iconColor = Color(0xFFD97706),
        barGradients = listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
        recommendedChapter = "8-sinf Tarix: Xronologik jadvallar va muhim sanalar"
    ),
    CategorySpec(
        title = "Shahar va hududlar",
        subtitle = "Qadimgi shaharlar va xaritalar",
        keys = listOf("CITY"),
        quizRouteKey = "CITY",
        icon = Icons.Default.LocationOn,
        iconColor = Color(0xFFF97316),
        barGradients = listOf(Color(0xFFF97316), Color(0xFFFDBA74)),
        recommendedChapter = "7-sinf Tarix: Qadimgi shaharlar va Buyuk Ipak Yo'li"
    ),
    CategorySpec(
        title = "Tarixiy faktlar",
        subtitle = "Faktlar va yozma manbalar",
        keys = listOf("FACT"),
        quizRouteKey = "FACT",
        icon = Icons.Default.FactCheck,
        iconColor = Color(0xFF8B5CF6),
        barGradients = listOf(Color(0xFF8B5CF6), Color(0xFFC084FC)),
        recommendedChapter = "8-sinf Tarix: Yozma manbalar va arxeologik faktlar"
    ),
    CategorySpec(
        title = "Tarixiy obidalar",
        subtitle = "Me'moriy obidalar va yodgorliklar",
        keys = listOf("MONUMENT"),
        quizRouteKey = "FACT",
        icon = Icons.Default.AccountBalance,
        iconColor = Color(0xFFD97706),
        barGradients = listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
        recommendedChapter = "O'zbekiston me'moriy obidalari va tarixiy merosi"
    ),
    CategorySpec(
        title = "Mantiqiy va DTM",
        subtitle = "OTMga kirish rasmiy testlari",
        keys = listOf("LOGICAL", "DTM_PRACTICE", "ATTESTATION", "NATIONAL_CERTIFICATE"),
        quizRouteKey = "LOGICAL",
        icon = Icons.Default.Psychology,
        iconColor = Color(0xFF0284C7),
        barGradients = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
        recommendedChapter = "11-sinf Tarix: DTM va Milliy sertifikat topshiriqlari"
    ),
    CategorySpec(
        title = "6-sinf Tarix (Qadimgi dunyo)",
        subtitle = "Misr, Bobil, Gretsiya va Rim",
        keys = listOf("book_uz_6", "uz_6"),
        quizRouteKey = "book_uz_6",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF16A34A),
        barGradients = listOf(Color(0xFF16A34A), Color(0xFF4ADE80)),
        recommendedChapter = "6-sinf Tarix: Qadimgi dunyo sivilizatsiyalari"
    ),
    CategorySpec(
        title = "7-sinf O‘zbekiston tarixi",
        subtitle = "Ilk o'rta asrlar va Somoniylar",
        keys = listOf("book_uz_7", "uz_7"),
        quizRouteKey = "book_uz_7",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF6366F1),
        barGradients = listOf(Color(0xFF6366F1), Color(0xFF818CF8)),
        recommendedChapter = "7-sinf O'zbekiston tarixi: Somoniylar va Temuriylar"
    ),
    CategorySpec(
        title = "7-sinf Jahon tarixi",
        subtitle = "V-XV asrlar Yevropa va Osiyo",
        keys = listOf("book_jahon_7", "jahon_7"),
        quizRouteKey = "book_jahon_7",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF0284C7),
        barGradients = listOf(Color(0xFF0284C7), Color(0xFF38BDF8)),
        recommendedChapter = "7-sinf Jahon tarixi: O'rta asrlar Yevropasi va Osiyosi"
    ),
    CategorySpec(
        title = "8-sinf O‘zbekiston tarixi",
        subtitle = "XVI-XIX asr I yarmi Xonliklar",
        keys = listOf("book_uz_8", "uz_8"),
        quizRouteKey = "book_uz_8",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFFD97706),
        barGradients = listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
        recommendedChapter = "8-sinf O'zbekiston tarixi: Buxoro, Xiva va Qo'qon xonliklari"
    ),
    CategorySpec(
        title = "8-sinf Jahon tarixi",
        subtitle = "Uyg'onish davri va kashfiyotlar",
        keys = listOf("book_jahon_8", "jahon_8"),
        quizRouteKey = "book_jahon_8",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF7C3AED),
        barGradients = listOf(Color(0xFF7C3AED), Color(0xFFC084FC)),
        recommendedChapter = "8-sinf Jahon tarixi: Yangi davr tarixi"
    ),
    CategorySpec(
        title = "9-sinf O‘zbekiston tarixi",
        subtitle = "Rossiya istilosi va Jadidchilik",
        keys = listOf("book_uz_9", "uz_9"),
        quizRouteKey = "book_uz_9",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFFDC2626),
        barGradients = listOf(Color(0xFFDC2626), Color(0xFFFCA5A5)),
        recommendedChapter = "9-sinf O'zbekiston tarixi: Jadidchilik harakati"
    ),
    CategorySpec(
        title = "9-sinf Jahon tarixi",
        subtitle = "XIX asr oxiri - XX asr boshlari",
        keys = listOf("book_jahon_9", "jahon_9"),
        quizRouteKey = "book_jahon_9",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF2563EB),
        barGradients = listOf(Color(0xFF2563EB), Color(0xFF93C5FD)),
        recommendedChapter = "9-sinf Jahon tarixi: Birinchi jahon urushi"
    ),
    CategorySpec(
        title = "10-sinf O‘zbekiston tarixi",
        subtitle = "1917-1991 yillar tarixi",
        keys = listOf("book_uz_10", "uz_10"),
        quizRouteKey = "book_uz_10",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF059669),
        barGradients = listOf(Color(0xFF059669), Color(0xFF6EE7B7)),
        recommendedChapter = "10-sinf O'zbekiston tarixi: XX asr sinovlari"
    ),
    CategorySpec(
        title = "10-sinf Jahon tarixi",
        subtitle = "1918-1991 yillar Jahon tarixi",
        keys = listOf("book_jahon_10", "jahon_10"),
        quizRouteKey = "book_jahon_10",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF9333EA),
        barGradients = listOf(Color(0xFF9333EA), Color(0xFFE9D5FF)),
        recommendedChapter = "10-sinf Jahon tarixi: Ikkinchi jahon urushi va Sovuq urush"
    ),
    CategorySpec(
        title = "11-sinf O‘zbekiston tarixi",
        subtitle = "Mustaqillik davri taraqqiyoti",
        keys = listOf("book_uz_11", "uz_11"),
        quizRouteKey = "book_uz_11",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF16A34A),
        barGradients = listOf(Color(0xFF16A34A), Color(0xFF86EFAC)),
        recommendedChapter = "11-sinf O'zbekiston tarixi: Mustaqil O'zbekiston taraqqiyoti"
    ),
    CategorySpec(
        title = "11-sinf Jahon tarixi",
        subtitle = "Zamonaviy dunyo va globallashuv",
        keys = listOf("book_jahon_11", "jahon_11"),
        quizRouteKey = "book_jahon_11",
        icon = Icons.Default.MenuBook,
        iconColor = Color(0xFF0284C7),
        barGradients = listOf(Color(0xFF0284C7), Color(0xFF7DD3FC)),
        recommendedChapter = "11-sinf Jahon tarixi: Zamonaviy dunyo va xalqaro munosabatlar"
    )
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val cloudSyncRepository: CloudSyncRepository,
    private val preferences: UserPreferencesRepository,
    private val database: AppDatabase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val refreshTick = MutableStateFlow(0)

    private val currentUid: String?
        get() = auth.currentUser?.uid

    private val cloudLeaderboardFlow: FlowWrapper<List<CloudLeaderboardEntry>> = FlowWrapper(
        if (currentUid != null) cloudSyncRepository.observeLeaderboard(limit = 50L) else flowOf(emptyList())
    )

    private val cloudAttemptsFlow = if (currentUid != null) {
        cloudSyncRepository.observeUserAttempts(currentUid!!, limit = 250L)
    } else {
        flowOf(emptyList())
    }

    val uiState: StateFlow<LeaderboardUiState> = combine(
        preferences.displayName,
        preferences.userAvatar,
        preferences.userXp,
        preferences.userStreak,
        preferences.totalSolvedTests,
        preferences.totalCorrectAnswers,
        preferences.totalTimeSpentSeconds,
        database.quizAttemptDao().getAllFlow(),
        cloudLeaderboardFlow.flow,
        cloudAttemptsFlow,
        refreshTick
    ) { values ->
        val name = (values[0] as String).ifBlank { "Tarixchi" }
        val avatar = (values[1] as String).ifBlank { "TU" }
        val xp = (values[2] as Long).coerceAtLeast(0L)
        val streak = (values[3] as Int).coerceAtLeast(0)
        val prefSolved = (values[4] as Int).coerceAtLeast(0)
        val prefCorrect = (values[5] as Int).coerceAtLeast(0)
        val prefTimeSec = (values[6] as Int).coerceAtLeast(0)
        @Suppress("UNCHECKED_CAST")
        val localAttempts = values[7] as List<QuizAttemptEntity>
        @Suppress("UNCHECKED_CAST")
        val cloudLeaderboard = values[8] as List<CloudLeaderboardEntry>
        @Suppress("UNCHECKED_CAST")
        val cloudAttempts = values[9] as List<CloudQuizAttempt>

        val prefsSnapshot = preferences.getAllPreferences()
        val uid = currentUid

        // Build real ranked leaderboard
        val usersList = cloudLeaderboard
            .sortedByDescending { it.xp }
            .mapIndexed { idx, entry ->
                val isMe = uid != null && entry.userId == uid
                val entryName = entry.displayName.ifBlank { "Tarixchi" }
                LeaderboardUser(
                    rank = idx + 1,
                    name = if (isMe) "$entryName (Siz)" else entryName,
                    initials = entry.initials.ifBlank { entryName.take(2).uppercase() },
                    score = entry.xp.toInt().coerceAtLeast(0),
                    accuracy = entry.accuracy.toInt().coerceIn(0, 100),
                    timeSpentMinutes = entry.timeSpentMinutes.toInt().coerceAtLeast(0),
                    isCurrentUser = isMe
                )
            }

        val myRankEntry = usersList.find { it.isCurrentUser }
        val rankLabel = myRankEntry?.let { "#${it.rank}-o'rin" } ?: "—"

        // Real Period Summaries (0=Bugun, 1=Haftalik, 2=Oylik, 3=Barchasi)
        val periodMap = mapOf(
            0 to buildPeriodSummary(0, localAttempts, cloudAttempts, prefSolved, prefCorrect, prefTimeSec),
            1 to buildPeriodSummary(1, localAttempts, cloudAttempts, prefSolved, prefCorrect, prefTimeSec),
            2 to buildPeriodSummary(2, localAttempts, cloudAttempts, prefSolved, prefCorrect, prefTimeSec),
            3 to buildPeriodSummary(3, localAttempts, cloudAttempts, prefSolved, prefCorrect, prefTimeSec)
        )

        // Real Category Analytics from CloudQuizAttempt + TicketProgressSnapshot
        val detailedAnalytics = mutableListOf<DetailedCategoryAnalytics>()
        val analysisItems = mutableListOf<CategoryAnalysisItem>()

        var overallSolved = 0
        var overallCorrect = 0
        var overallTimeSec = 0

        CATEGORY_SPECS.forEach { spec ->
            val matchedAttempts = cloudAttempts.filter { attempt ->
                spec.keys.any { k -> attempt.categoryKey.equals(k, ignoreCase = true) }
            }
            var ticketSolved = 0
            var ticketCorrect = 0
            spec.keys.forEach { k ->
                val snap = preferences.getTicketProgressFromPrefs(prefsSnapshot, k)
                if (snap.scores.isNotEmpty()) {
                    ticketSolved += snap.scores.size * 30
                    ticketCorrect += snap.scores.values.sum()
                }
            }

            val attemptSolved = matchedAttempts.sumOf { it.solvedCount.toInt() }
            val attemptCorrect = matchedAttempts.sumOf { it.correctCount.toInt() }
            val attemptDuration = matchedAttempts.sumOf { it.durationSeconds.toInt() }

            val solved = maxOf(attemptSolved, ticketSolved)
            val correct = maxOf(attemptCorrect, ticketCorrect).coerceIn(0, solved)
            val mistakes = (solved - correct).coerceAtLeast(0)
            val accuracyPct = if (solved > 0) ((correct * 100) / solved).coerceIn(0, 100) else 0
            val mistakePct = if (solved > 0) (100 - accuracyPct).coerceIn(0, 100) else 0
            val timeMinutes = if (attemptDuration > 0) (attemptDuration / 60).coerceAtLeast(1) else (solved / 3)
            val avgSpeed = if (solved > 0 && attemptDuration > 0) (attemptDuration / solved).coerceIn(3, 180) else 0

            overallSolved += solved
            overallCorrect += correct
            overallTimeSec += if (attemptDuration > 0) attemptDuration else timeMinutes * 60

            detailedAnalytics += DetailedCategoryAnalytics(
                title = spec.title,
                subtitle = if (solved > 0) spec.subtitle else "${spec.subtitle} • Hali ishlanmagan",
                totalSolved = solved,
                correctCount = correct,
                accuracyPercent = accuracyPct,
                timeSpentMinutes = timeMinutes,
                growthPercent = accuracyPct,
                averageSpeedSeconds = avgSpeed,
                chartData = emptyList()
            )

            analysisItems += CategoryAnalysisItem(
                title = spec.title,
                icon = spec.icon,
                iconColor = spec.iconColor,
                correctPct = accuracyPct,
                mistakePct = mistakePct,
                unattemptedPct = if (solved == 0) 100 else 0,
                totalQuestions = solved,
                timeSpentMinutes = timeMinutes,
                growthPct = accuracyPct,
                categoryKey = spec.quizRouteKey,
                barGradients = spec.barGradients,
                adviceList = buildAdvice(spec.title, solved, accuracyPct, mistakePct),
                recommendedChapter = spec.recommendedChapter
            )
        }

        val totalOverallSolved = maxOf(overallSolved, prefSolved, localAttempts.size)
        val totalOverallCorrect = maxOf(overallCorrect, prefCorrect, localAttempts.count { it.wasCorrect })
            .coerceIn(0, totalOverallSolved)
        val totalCorrectPct = if (totalOverallSolved > 0) ((totalOverallCorrect * 100) / totalOverallSolved).coerceIn(0, 100) else 0
        val totalMistakePct = if (totalOverallSolved > 0) (100 - totalCorrectPct).coerceIn(0, 100) else 0
        val totalMinutes = maxOf(overallTimeSec / 60, prefTimeSec / 60)

        val overallAnalysis = CategoryAnalysisItem(
            title = "Barcha testlar umumiy tahlili",
            icon = Icons.Default.Leaderboard,
            iconColor = BrandPurple,
            correctPct = totalCorrectPct,
            mistakePct = totalMistakePct,
            unattemptedPct = if (totalOverallSolved == 0) 100 else 0,
            totalQuestions = totalOverallSolved,
            timeSpentMinutes = totalMinutes,
            growthPct = totalCorrectPct,
            categoryKey = "EVENT",
            barGradients = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
            adviceList = buildAdvice("Umumiy tahlil", totalOverallSolved, totalCorrectPct, totalMistakePct),
            recommendedChapter = "6-sinf Tarix: Asosiy davrlar va sanalar"
        )

        LeaderboardUiState(
            displayName = name,
            avatar = avatar,
            xp = xp,
            streak = streak,
            currentUserRankLabel = rankLabel,
            currentUserScoreLabel = "$xp XP",
            leaderboardUsers = usersList,
            periodSummaries = periodMap,
            categoryAnalytics = detailedAnalytics,
            analysisCategories = listOf(overallAnalysis) + analysisItems,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LeaderboardUiState())

    private class FlowWrapper<T>(val flow: kotlinx.coroutines.flow.Flow<T>)

    fun refresh() {
        viewModelScope.launch {
            cloudSyncRepository.pushStatsToCloud()
            refreshTick.value++
        }
    }

    private fun buildAdvice(title: String, solved: Int, accuracyPct: Int, mistakePct: Int): List<String> {
        if (solved == 0) {
            return listOf(
                "$title bo'yicha hali test ishlanmagan. Ilk biletni boshlab natijangizni ko'ring!",
                "Har bir biletda 30 ta savol mavjud. 10 va undan ko'p to'g'ri javob keyingi biletni ochadi."
            )
        }
        return buildList {
            when {
                accuracyPct >= 80 -> add("$title bo'yicha aniqligingiz $accuracyPct%! A'lo natija, murakkab DTM savollarini ham sinab ko'ring.")
                accuracyPct >= 55 -> add("$title bo'yicha $solved ta savol yechdingiz ($accuracyPct% to'g'ri). Sanalar va shaxslarni takrorlab aniqlikni 80%+ ga ko'taring.")
                else -> add("$title bo'yicha xatolar ulushi $mistakePct%. Avval Xatolar bo'limidagi savollarni qayta ko'rib chiqishni tavsiya qilamiz.")
            }
            add("Jami $solved ta savol ishlandi. Muntazam mashq qilish bilan seriyangizni saqlab turing!")
        }
    }

    private fun buildPeriodSummary(
        periodIndex: Int,
        localAttempts: List<QuizAttemptEntity>,
        cloudAttempts: List<CloudQuizAttempt>,
        prefSolved: Int,
        prefCorrect: Int,
        prefTimeSec: Int
    ): PeriodStatsSummary {
        val now = Calendar.getInstance()
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            when (periodIndex) {
                0 -> Unit
                1 -> set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                2 -> set(Calendar.DAY_OF_MONTH, 1)
                else -> timeInMillis = 0L
            }
        }
        val startMs = startCal.timeInMillis

        val filteredLocal = localAttempts.filter { it.timestamp >= startMs }
        val filteredCloud = cloudAttempts.filter { (it.createdAt?.toDate()?.time ?: 0L) >= startMs }

        val localSolved = filteredLocal.size
        val localCorrect = filteredLocal.count { it.wasCorrect }
        val cloudSolved = filteredCloud.sumOf { it.solvedCount.toInt() }
        val cloudCorrect = filteredCloud.sumOf { it.correctCount.toInt() }
        val cloudDurationMin = (filteredCloud.sumOf { it.durationSeconds.toInt() } / 60).coerceAtLeast(0)

        val solved = when (periodIndex) {
            3 -> maxOf(localSolved, cloudSolved, prefSolved)
            else -> maxOf(localSolved, cloudSolved)
        }
        val correct = when (periodIndex) {
            3 -> maxOf(localCorrect, cloudCorrect, prefCorrect).coerceIn(0, solved)
            else -> maxOf(localCorrect, cloudCorrect).coerceIn(0, solved)
        }
        val accuracy = if (solved > 0) ((correct * 100) / solved).coerceIn(0, 100) else 0
        val minutes = when (periodIndex) {
            3 -> maxOf(cloudDurationMin, prefTimeSec / 60, if (solved > 0) 1 else 0)
            else -> maxOf(cloudDurationMin, if (solved > 0) (solved / 2).coerceAtLeast(1) else 0)
        }

        val points = when (periodIndex) {
            0 -> {
                val buckets = listOf(0, 9, 13, 17, 21)
                val labels = listOf("08:00", "12:00", "16:00", "20:00", "23:59")
                labels.mapIndexed { idx, label ->
                    val lo = buckets[idx]
                    val hi = if (idx < buckets.lastIndex) buckets[idx + 1] else 24
                    val bucketCloud = filteredCloud.filter {
                        val ts = it.createdAt?.toDate()?.time ?: return@filter false
                        val hr = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
                        hr in lo until hi
                    }
                    val bucketLocal = filteredLocal.filter {
                        val hr = Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
                        hr in lo until hi
                    }
                    val bSolved = maxOf(bucketLocal.size, bucketCloud.sumOf { it.solvedCount.toInt() })
                    val bCorrect = maxOf(bucketLocal.count { it.wasCorrect }, bucketCloud.sumOf { it.correctCount.toInt() })
                    val bMin = (bucketCloud.sumOf { it.durationSeconds.toInt() } / 60f)
                        .takeIf { it > 0f } ?: (bSolved * 0.5f)
                    StudentChartDataPoint(
                        dayLabel = label,
                        accuracyRatio = if (bSolved > 0) (bCorrect.toFloat() / bSolved).coerceIn(0f, 1f) else 0f,
                        timeSpentMinutes = bMin
                    )
                }
            }
            1 -> {
                val days = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")
                days.mapIndexed { dayIdx, label ->
                    val dayCloud = filteredCloud.filter {
                        val ts = it.createdAt?.toDate()?.time ?: return@filter false
                        val d = (Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_WEEK) + 5) % 7
                        d == dayIdx
                    }
                    val dayLocal = filteredLocal.filter {
                        val d = (Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_WEEK) + 5) % 7
                        d == dayIdx
                    }
                    val dSolved = maxOf(dayLocal.size, dayCloud.sumOf { it.solvedCount.toInt() })
                    val dCorrect = maxOf(dayLocal.count { it.wasCorrect }, dayCloud.sumOf { it.correctCount.toInt() })
                    val dMin = (dayCloud.sumOf { it.durationSeconds.toInt() } / 60f)
                        .takeIf { it > 0f } ?: (dSolved * 0.5f)
                    StudentChartDataPoint(
                        dayLabel = label,
                        accuracyRatio = if (dSolved > 0) (dCorrect.toFloat() / dSolved).coerceIn(0f, 1f) else 0f,
                        timeSpentMinutes = dMin
                    )
                }
            }
            2 -> {
                (1..4).map { weekNum ->
                    val weekCloud = filteredCloud.filter {
                        val ts = it.createdAt?.toDate()?.time ?: return@filter false
                        val w = ((Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1
                        w.coerceIn(1, 4) == weekNum
                    }
                    val weekLocal = filteredLocal.filter {
                        val w = ((Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1
                        w.coerceIn(1, 4) == weekNum
                    }
                    val wSolved = maxOf(weekLocal.size, weekCloud.sumOf { it.solvedCount.toInt() })
                    val wCorrect = maxOf(weekLocal.count { it.wasCorrect }, weekCloud.sumOf { it.correctCount.toInt() })
                    val wMin = (weekCloud.sumOf { it.durationSeconds.toInt() } / 60f)
                        .takeIf { it > 0f } ?: (wSolved * 0.5f)
                    StudentChartDataPoint(
                        dayLabel = "$weekNum-hafta",
                        accuracyRatio = if (wSolved > 0) (wCorrect.toFloat() / wSolved).coerceIn(0f, 1f) else 0f,
                        timeSpentMinutes = wMin
                    )
                }
            }
            else -> {
                val curMonth = now.get(Calendar.MONTH)
                val monthNames = listOf("Yan", "Fev", "Mar", "Apr", "May", "Iyn", "Iyl", "Avg", "Sen", "Okt", "Noy", "Dek")
                val indices = ((curMonth - 3)..curMonth).map { (it + 12) % 12 }
                indices.map { mIdx ->
                    val mCloud = filteredCloud.filter {
                        val ts = it.createdAt?.toDate()?.time ?: return@filter false
                        Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.MONTH) == mIdx
                    }
                    val mLocal = filteredLocal.filter {
                        Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.MONTH) == mIdx
                    }
                    val mSolved = maxOf(mLocal.size, mCloud.sumOf { it.solvedCount.toInt() })
                    val mCorrect = maxOf(mLocal.count { it.wasCorrect }, mCloud.sumOf { it.correctCount.toInt() })
                    val mMin = (mCloud.sumOf { it.durationSeconds.toInt() } / 60f)
                        .takeIf { it > 0f } ?: (mSolved * 0.5f)
                    StudentChartDataPoint(
                        dayLabel = monthNames[mIdx],
                        accuracyRatio = if (mSolved > 0) (mCorrect.toFloat() / mSolved).coerceIn(0f, 1f) else 0f,
                        timeSpentMinutes = mMin
                    )
                }
            }
        }

        return PeriodStatsSummary(
            periodIndex = periodIndex,
            solvedCount = solved,
            correctCount = correct,
            accuracyPct = accuracy,
            timeSpentMinutes = minutes,
            chartPoints = points
        )
    }
}
