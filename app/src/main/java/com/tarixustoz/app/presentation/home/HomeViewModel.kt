package com.tarixustoz.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Question
import com.tarixustoz.app.presentation.tests.TestCardItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val displayName: String = "Azizbek",
    val userAvatar: String = "AS",
    val greeting: String = "Xayrli tong,",
    val xp: Long = 78,
    val streak: Int = 7,
    val level: Int = 3,
    val todayGoalCompleted: Int = 12,
    val todayGoalTotal: Int = 20,
    val dailyQuestion: Question? = null,
    val selectedDailyOptionId: String? = null,
    val isDailyAnswered: Boolean = false,
    val isDailyCorrect: Boolean = false,
    val dailyAnswerFeedback: String? = null,
    val dailyTimeRemaining: String = "08:42:16",
    val mistakesCount: Int = 11,
    val unreadNotifications: Int = 3,
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
    )
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val contentRepository: ContentRepository,
    private val mistakesRepository: MistakesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val homeDirectionCards = MutableStateFlow<List<TestCardItem>>(emptyList())
    val homeTopicCards = MutableStateFlow<List<TestCardItem>>(emptyList())
    val homeGradeCards = MutableStateFlow<List<TestCardItem>>(emptyList())

    private val seenQuestionIds = mutableSetOf<String>()

    /** Shows the cached numbers instantly, then refreshes them from the backend. */
    fun loadDirectionCounts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Same grade the quiz uses, so the shown count matches the real pool.
            val grade = runCatching { preferences.selectedGrade.first() }.getOrDefault(0)
            contentRepository.cachedDirectionCounts(grade)?.let { applyCounts(it) }
            val counts = contentRepository.fetchDirectionCounts(grade = grade, forceRefresh = forceRefresh)
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

    fun load() {
        viewModelScope.launch {
            contentRepository.seedIfNeeded()
            if (_uiState.value.dailyQuestion == null) {
                loadNextDailyQuestion()
            }
        }

        loadDirectionCounts()

        viewModelScope.launch {
            val prefsSnapshot = preferences.getAllPreferences()
            val counts = contentRepository.fetchDirectionCounts(0)

            val directionData = listOf(
                Triple("EVENT", "Tarixiy Hodisalar", "Qadimgi va o'rta asrlar"),
                Triple("PERSON", "Tarixiy Shaxslar", "Buyuk siymolar"),
                Triple("YEAR", "Yillar va Sanalar", "Xronologik tartib"),
                Triple("WAR", "Urushlar va Janglar", "Harbiy yurishlar"),
                Triple("CITY", "Shahar va Hududlar", "Joylashuvlar"),
                Triple("LOGICAL", "Mantiqiy va DTM", "DTM savollari")
            )
            val directions = directionData.map { (key, title, subtitle) ->
                val totalQ = counts[key] ?: 450
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
                    categoryType = "direction"
                )
            }
            homeDirectionCards.value = directions

            val topics = contentRepository.getAllTopicsFlow().first().take(15).map { t ->
                val totalT = 10
                val totalQs = 300
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
                    categoryType = "topic"
                )
            }
            homeTopicCards.value = topics

            val grades = (6..11).map { g ->
                val bookId = "book_$g"
                val totalQs = 240
                val totalT = (totalQs + 29) / 30
                val subText = "$g-sinf darsligi • $totalT ta bilet • $totalQs ta test"
                val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, bookId).passed.size
                TestCardItem(
                    id = bookId,
                    title = "$g-Sinf Tarixi",
                    subtitle = subText,
                    totalTickets = totalT,
                    passedTickets = passed,
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                    accentColor = Color(0xFF3B82F6),
                    categoryType = "grade"
                )
            }
            homeGradeCards.value = grades
        }

        viewModelScope.launch {
            combine(
                preferences.displayName,
                preferences.userAvatar,
                preferences.userXp,
                preferences.userStreak,
                mistakesRepository.getActiveCount()
            ) { name, avatar, xp, streak, mistakes ->
                val actualMistakes = if (mistakes == 0) 11 else mistakes
                val actualStreak = if (streak == 0) 7 else streak
                val actualXp = if (xp == 0L) 78L else xp
                val effectiveName = if (name.isBlank() || name == "O‘quvchi") "Azizbek" else name
                val effectiveAvatar = if (avatar.isBlank()) "AS" else avatar
                _uiState.value.copy(
                    displayName = effectiveName,
                    userAvatar = effectiveAvatar,
                    xp = actualXp,
                    streak = actualStreak,
                    level = (actualXp / 30 + 1).toInt(),
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
            preferences.rewardedAdCountToday.collect { count ->
                _uiState.update { current ->
                    current.copy(
                        rewardedAdCountToday = count,
                        canShowRewardedAd = count < 2
                    )
                }
            }
        }
    }

    fun claimAdReward(onSuccess: () -> Unit, onLimitReached: () -> Unit) {
        viewModelScope.launch {
            val allowed = preferences.incrementRewardedAdCount()
            if (allowed) {
                preferences.addXp(50)
                onSuccess()
            } else {
                onLimitReached()
            }
        }
    }

    fun loadNextDailyQuestion() {
        viewModelScope.launch {
            val grade = runCatching { preferences.selectedGrade.first() }.getOrDefault(0)
            val nextQuestion = contentRepository.getDailyQuestion(seenQuestionIds, grade = grade)
            if (nextQuestion != null) {
                seenQuestionIds.add(nextQuestion.id)
            }
            _uiState.update {
                it.copy(
                    dailyQuestion = nextQuestion,
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
