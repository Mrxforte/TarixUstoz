package com.tarixustoz.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.CloudSyncRepository
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Question
import com.tarixustoz.app.domain.model.gradeId
import com.tarixustoz.app.presentation.common.components.backgroundResForBook
import com.tarixustoz.app.presentation.common.components.backgroundResForDirection
import com.tarixustoz.app.presentation.common.components.backgroundResForTopic
import com.tarixustoz.app.presentation.notifications.NotificationCatalog
import com.tarixustoz.app.presentation.tests.TestCardItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class HomeCategoryItem(
    val id: String,
    val title: String,
    val countLabel: String,
    val iconType: String,
    val count: Int,
    /** Backend direction code (EVENT/PERSON/...); null means "open the topic list". */
    val direction: String? = null
)

/** The backend direction cards shown on the home screen, in display order. */
private val HOME_DIRECTIONS = listOf(
    Triple("CITY", "Shahar nomlari", "location"),
    Triple("PERSON", "Tarixiy shaxslar", "person"),
    Triple("YEAR", "Yillar bo'yicha", "calendar"),
    Triple("EVENT", "Tarixiy voqealar", "event"),
    Triple("FACT", "Tarixiy faktlar", "fact"),
    Triple("WAR", "Urushlar va janglar", "war"),
    Triple("LOGICAL", "Mantiqiy va DTM", "logical"),
    Triple(null, "Mavzu bo'yicha", "topic")
)

private fun formatCount(count: Int): String =
    if (count <= 0) "…" else "%,d".format(count).replace(',', '\u00A0') + " test"

data class HomeUiState(
    val displayName: String = "Tarixchi",
    val userAvatar: String = "TU",
    val greeting: String = "Xayrli kun,",
    val xp: Long = 0L,
    val streak: Int = 0,
    val level: Int = 1,
    val todayGoalCompleted: Int = 0,
    val todayGoalTotal: Int = 30,
    val dailyQuestion: Question? = null,
    val isDailyLoading: Boolean = true,
    val selectedDailyOptionId: String? = null,
    val isDailyAnswered: Boolean = false,
    val isDailyCorrect: Boolean = false,
    val dailyAnswerFeedback: String? = null,
    val dailyTimeRemaining: String = "Kunlik",
    val mistakesCount: Int = 0,
    val unreadNotifications: Int = 0,
    val rewardedAdCountToday: Int = 0,
    val canShowRewardedAd: Boolean = true,
    val categories: List<HomeCategoryItem> = listOf(
        HomeCategoryItem("shahar_nomlari", "Shahar nomlari", "…", "location", 0, "CITY"),
        HomeCategoryItem("tarixiy_shaxslar", "Tarixiy shaxslar", "…", "person", 0, "PERSON"),
        HomeCategoryItem("yillar_boyicha", "Yillar bo'yicha", "…", "calendar", 0, "YEAR"),
        HomeCategoryItem("tarixiy_voqealar", "Tarixiy voqealar", "…", "event", 0, "EVENT"),
        HomeCategoryItem("tarixiy_faktlar", "Tarixiy faktlar", "…", "fact", 0, "FACT"),
        HomeCategoryItem("urushlar", "Urushlar va janglar", "…", "war", 0, "WAR"),
        HomeCategoryItem("mantiqiy_dtm", "Mantiqiy va DTM", "…", "logical", 0, "LOGICAL"),
        HomeCategoryItem("mavzu_boyicha", "Mavzu bo'yicha", "Mavzular ro'yxati", "topic", 0, null)
    ),
    val globalContinueRoute: String = "quiz/ticket_EVENT_1?time=20&grade=0&shuffle=false",
    val totalStars: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val contentRepository: ContentRepository,
    private val mistakesRepository: MistakesRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val database: AppDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val homeDirectionCards = MutableStateFlow<List<TestCardItem>>(emptyList())
    val homeTopicCards = MutableStateFlow<List<TestCardItem>>(emptyList())
    val homeGradeCards = MutableStateFlow<List<TestCardItem>>(emptyList())

    private val seenQuestionIds = mutableSetOf<String>()
    private var dailyNonce = 0
    private var hasInitializedLoad = false

    private fun startOfDayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Shows the cached/default numbers instantly, then refreshes them from the backend. */
    fun loadDirectionCounts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val instant = contentRepository.cachedDirectionCounts(0)
                ?: contentRepository.defaultDirectionCounts(0)
            applyCounts(instant)
            val counts = contentRepository.fetchDirectionCounts(grade = 0, forceRefresh = forceRefresh)
            if (counts.isNotEmpty()) applyCounts(counts)
        }
    }

    private fun applyCounts(counts: Map<String, Int>) {
        _uiState.update { state ->
            state.copy(categories = state.categories.map { item ->
                val direction = item.direction
                if (direction == null) item
                else item.copy(count = counts[direction] ?: 0, countLabel = formatCount(counts[direction] ?: 0))
            })
        }
    }

    private fun computeDailyTimeRemaining(): String {
        val now = Calendar.getInstance()
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMs = (nextMidnight.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
        val totalMinutes = diffMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return "%02ds %02dd".format(hours, minutes)
    }

    fun load() {
        if (!hasInitializedLoad) {
            hasInitializedLoad = true
            viewModelScope.launch {
                while (isActive) {
                    val remaining = computeDailyTimeRemaining()
                    _uiState.update { it.copy(dailyTimeRemaining = remaining) }
                    delay(60_000L)
                }
            }
            viewModelScope.launch {
                contentRepository.seedIfNeeded()
                if (_uiState.value.dailyQuestion == null) {
                    loadNextDailyQuestion(userTriggered = false)
                }
                contentRepository.syncWithDjangoBackend()
                buildHomeCards()
            }
        } else {
            viewModelScope.launch {
                buildHomeCards()
            }
        }

        loadDirectionCounts()

        viewModelScope.launch {
            combine(
                preferences.displayName,
                preferences.userAvatar,
                preferences.userXp,
                preferences.userStreak,
                mistakesRepository.getActiveCount()
            ) { name, avatar, xp, streak, mistakes ->
                val effectiveName = name.ifBlank { "Tarixchi" }
                val effectiveAvatar = avatar.ifBlank { "TU" }
                val actualXp = xp.coerceAtLeast(0L)
                val actualStreak = streak.coerceAtLeast(0)
                val actualMistakes = mistakes.coerceAtLeast(0)
                _uiState.value.copy(
                    displayName = effectiveName,
                    userAvatar = effectiveAvatar,
                    xp = actualXp,
                    streak = actualStreak,
                    level = (actualXp / 100 + 1).toInt(),
                    mistakesCount = actualMistakes
                )
            }.collect { updated ->
                _uiState.update { current ->
                    current.copy(
                        displayName = updated.displayName,
                        userAvatar = updated.userAvatar,
                        xp = updated.xp,
                        streak = updated.streak,
                        level = updated.level,
                        mistakesCount = updated.mistakesCount
                    )
                }
            }
        }

        viewModelScope.launch {
            database.quizAttemptDao().getTodayTotalCountFlow(startOfDayMillis()).collect { todayCount ->
                _uiState.update { current ->
                    current.copy(todayGoalCompleted = todayCount.coerceAtLeast(0))
                }
            }
        }

        viewModelScope.launch {
            preferences.rewardedAdCountToday.collect { count ->
                _uiState.update { current ->
                    current.copy(
                        rewardedAdCountToday = count,
                        canShowRewardedAd = count < 2
                    )
                }
            }
        }

        viewModelScope.launch {
            preferences.globalContinueRoute.collect { route ->
                _uiState.update { current ->
                    current.copy(globalContinueRoute = route)
                }
            }
        }

        viewModelScope.launch {
            preferences.totalEarnedStars.collect { stars ->
                _uiState.update { current ->
                    current.copy(totalStars = stars.coerceAtLeast(0))
                }
            }
        }

        viewModelScope.launch {
            combine(
                preferences.readNotificationIds,
                preferences.dismissedNotificationIds
            ) { readIds, dismissedIds ->
                NotificationCatalog.activeIds()
                    .count { it !in readIds && it !in dismissedIds }
            }.collect { unread ->
                _uiState.update { current ->
                    current.copy(unreadNotifications = unread)
                }
            }
        }
    }

    private suspend fun buildHomeCards() {
        val prefsSnapshot = preferences.getAllPreferences()
        val counts = contentRepository.fetchDirectionCounts(0)
        val fallbackDefaults = contentRepository.defaultDirectionCounts(0)

        val directionData = listOf(
            Triple("EVENT", "Tarixiy Hodisalar", "Qadimgi va o'rta asrlar"),
            Triple("PERSON", "Tarixiy Shaxslar", "Buyuk siymolar"),
            Triple("YEAR", "Yillar va Sanalar", "Xronologik tartib"),
            Triple("WAR", "Urushlar va Janglar", "Harbiy yurishlar"),
            Triple("CITY", "Shahar va Hududlar", "Joylashuvlar"),
            Triple("FACT", "Tarixiy Faktlar", "Manbalar va obidalar"),
            Triple("LOGICAL", "Mantiqiy va DTM", "DTM savollari")
        )
        val directions = directionData.map { (key, title, subtitle) ->
            val totalQ = counts[key]?.takeIf { it > 0 } ?: fallbackDefaults[key] ?: 100_000
            val totalT = (totalQ + 29) / 30
            val subText = "$subtitle • $totalT ta bilet • $totalQ ta test"
            val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, key).passed.size
            TestCardItem(
                id = key,
                title = title,
                subtitle = subText,
                totalTickets = totalT,
                passedTickets = passed,
                icon = Icons.Default.Event,
                gradientColors = listOf(Color(0xFF1E1B4B), Color(0xFF3730A3)),
                accentColor = Color(0xFF10B981),
                categoryType = "direction",
                backgroundImageRes = backgroundResForDirection(key)
            )
        }
        homeDirectionCards.value = directions

        val topics = contentRepository.getAllTopicsFlow().first().take(15).map { t ->
            val totalQs = if (t.questionsCount > 0) t.questionsCount else 2500
            val totalT = (totalQs + 29) / 30
            val subText = "${t.summary.ifBlank { "Mavzular to'plami" }} • $totalT ta bilet • $totalQs ta test"
            val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, t.id).passed.size
            TestCardItem(
                id = t.id,
                title = t.title,
                subtitle = subText,
                totalTickets = totalT,
                passedTickets = passed,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                gradientColors = listOf(Color(0xFF854D0E), Color(0xFFA16207)),
                accentColor = Color(0xFF10B981),
                categoryType = "topic",
                backgroundImageRes = backgroundResForTopic(t.title, t.textbookId, t.gradeId)
            )
        }
        homeTopicCards.value = topics

        val books = contentRepository.getTextbooks().first()
        val grades = if (books.isNotEmpty()) {
            books.map { book ->
                val bookKey = "book_${book.id}"
                val totalQs = book.totalQuestions.takeIf { it > 0 } ?: 100_000
                val totalT = (totalQs + 29) / 30
                val subjectLabel = when {
                    book.gradeId == 6 -> "Qadimgi dunyo tarixi"
                    book.subjectId.contains("jahon", ignoreCase = true) -> "Jahon tarixi"
                    else -> "O'zbekiston tarixi"
                }
                val subText = "${book.gradeId}-sinf $subjectLabel • $totalT ta bilet • $totalQs ta test"
                val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, bookKey).passed.size
                TestCardItem(
                    id = bookKey,
                    title = "${book.gradeId}-Sinf $subjectLabel",
                    subtitle = subText,
                    totalTickets = totalT,
                    passedTickets = passed,
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    gradientColors = if (book.subjectId.contains("jahon", ignoreCase = true)) {
                        listOf(Color(0xFF064E3B), Color(0xFF065F46))
                    } else {
                        listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    },
                    accentColor = Color(0xFF3B82F6),
                    categoryType = "grade",
                    backgroundImageRes = backgroundResForBook(book.id, book.gradeId, book.subjectId)
                )
            }
        } else {
            (6..11).flatMap { g ->
                val list = mutableListOf(
                    "book_uz_$g" to (if (g == 6) "$g-Sinf Qadimgi Dunyo Tarixi" else "$g-Sinf O'zbekiston Tarixi")
                )
                if (g > 6) list.add("book_jahon_$g" to "$g-Sinf Jahon Tarixi")
                list.map { (bookKey, title) ->
                    val totalQs = 100_000
                    val totalT = (totalQs + 29) / 30
                    val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, bookKey).passed.size
                    TestCardItem(
                        id = bookKey,
                        title = title,
                        subtitle = "$g-sinf darsligi • $totalT ta bilet • $totalQs ta test",
                        totalTickets = totalT,
                        passedTickets = passed,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                        accentColor = Color(0xFF3B82F6),
                        categoryType = "grade",
                        backgroundImageRes = backgroundResForBook(bookKey, g)
                    )
                }
            }
        }
        homeGradeCards.value = grades
    }

    fun claimAdReward(onSuccess: () -> Unit, onLimitReached: () -> Unit) {
        viewModelScope.launch {
            val allowed = preferences.incrementRewardedAdCount()
            if (allowed) {
                preferences.addXp(50)
                cloudSyncRepository.pushStatsToCloud()
                onSuccess()
            } else {
                onLimitReached()
            }
        }
    }

    fun loadNextDailyQuestion(userTriggered: Boolean = true) {
        val currentState = _uiState.value
        if (userTriggered && currentState.isDailyLoading && currentState.dailyQuestion != null) return

        val previousId = currentState.dailyQuestion?.id
        if (previousId != null) {
            seenQuestionIds.add(previousId)
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isDailyLoading = true) }
            if (userTriggered && dailyNonce == 0) {
                dailyNonce = 1
            }
            val currentNonce = dailyNonce++
            var nextQuestion = contentRepository.getDailyQuestion(
                excludeIds = seenQuestionIds.toSet(),
                grade = 0,
                nonce = currentNonce
            )
            if ((nextQuestion == null || nextQuestion.id == previousId) && seenQuestionIds.size > 1) {
                seenQuestionIds.clear()
                if (previousId != null) seenQuestionIds.add(previousId)
                nextQuestion = contentRepository.getDailyQuestion(
                    excludeIds = seenQuestionIds.toSet(),
                    grade = 0,
                    nonce = dailyNonce++
                )
            }
            if (nextQuestion != null) {
                seenQuestionIds.add(nextQuestion.id)
            }
            _uiState.update {
                it.copy(
                    dailyQuestion = nextQuestion ?: it.dailyQuestion,
                    isDailyLoading = false,
                    selectedDailyOptionId = null,
                    isDailyAnswered = false,
                    isDailyCorrect = false,
                    dailyAnswerFeedback = null
                )
            }
        }
    }

    fun answerDailyQuestion(optionId: String) {
        val currentState = _uiState.value
        if (currentState.isDailyAnswered) return

        val question = currentState.dailyQuestion ?: return
        val correctOption = question.options.find { it.isCorrect }
        val isCorrect = correctOption?.id == optionId

        viewModelScope.launch {
            if (isCorrect) {
                preferences.addXp(10)
                cloudSyncRepository.pushStatsToCloud()
            } else {
                val userAnswerText = question.options.find { it.id == optionId }?.text ?: ""
                mistakesRepository.recordIncorrect(question, userAnswerText)
            }
            _uiState.update {
                it.copy(
                    selectedDailyOptionId = optionId,
                    isDailyAnswered = true,
                    isDailyCorrect = isCorrect,
                    dailyAnswerFeedback = if (isCorrect) "To'g'ri! +10 XP qo'shildi 🎉" else "Noto'g'ri. Xatolar bo'limiga qo'shildi."
                )
            }
        }
    }
}
