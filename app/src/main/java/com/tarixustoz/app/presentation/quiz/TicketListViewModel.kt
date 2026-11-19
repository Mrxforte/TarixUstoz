package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ActiveQuizState
import com.tarixustoz.app.data.repository.CloudSyncRepository
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TicketItemUiState(
    val index: Int,
    val isUnlocked: Boolean = false,
    val isPassed: Boolean = false,
    val bestScore: Int = 0,
    val stars: Int = 0,
    val isFavorite: Boolean = false,
    val ongoingScore: Int? = null,
    val ongoingAnsweredCount: Int? = null,
    val isLastOpened: Boolean = false
)

data class TicketListUiState(
    val isLoading: Boolean = true,
    val categoryKey: String = "",
    val selectedGrade: Int = 0,
    val totalQuestions: Int = 0,
    val timerMinutes: Int = 20,
    val favoriteTicketKeys: Set<String> = emptySet(),
    val totalFavoritesCount: Int = 0,
    val lastOpenedTicket: Int = 1,
    val highestUnlockedTicket: Int = 1,
    val continueTicketIndex: Int = 1,
    val continueAnsweredCount: Int? = null,
    val isContinueInProgress: Boolean = false,
    val tickets: List<TicketItemUiState> = emptyList(),
    val activeQuizState: ActiveQuizState? = null
)

private const val QUESTIONS_PER_TICKET = 30

@HiltViewModel
class TicketListViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TicketListUiState())
    val state: StateFlow<TicketListUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.ticketTimerMinutes.collect { mins ->
                _state.update { it.copy(timerMinutes = mins) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.favoriteTicketKeys.collect { favKeys ->
                val favQs = preferencesRepository.favoriteQuestionRecords.first().size
                _state.update { current ->
                    current.copy(
                        favoriteTicketKeys = favKeys,
                        totalFavoritesCount = favKeys.size + favQs,
                        tickets = current.tickets.map { item ->
                            item.copy(isFavorite = "${current.categoryKey}_${item.index}" in favKeys)
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            preferencesRepository.favoriteQuestionRecords.collect { favQs ->
                _state.update { current ->
                    current.copy(totalFavoritesCount = current.favoriteTicketKeys.size + favQs.size)
                }
            }
        }
    }

    fun setTimerMinutes(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.setTicketTimerMinutes(minutes)
        }
    }

    fun toggleTicketFavorite(categoryKey: String, ticketIndex: Int, categoryTitle: String, grade: Int = 0) {
        viewModelScope.launch {
            preferencesRepository.toggleFavoriteTicket(
                categoryKey = categoryKey,
                ticketIndex = ticketIndex,
                categoryTitle = categoryTitle,
                grade = grade
            )
        }
    }

    fun prepareTicketForStart(categoryKey: String, ticketIndex: Int, isRetry: Boolean, grade: Int = 0) {
        viewModelScope.launch {
            if (isRetry) {
                val active = preferencesRepository.getActiveQuizStateForCategory(categoryKey)
                if (active?.quizRoute?.startsWith("quiz/ticket_${categoryKey}_$ticketIndex") == true) {
                    preferencesRepository.clearActiveQuiz(categoryKey = categoryKey)
                }
            }
            markTicketOpened(categoryKey, ticketIndex, grade)
        }
    }

    fun loadTickets(categoryKey: String, grade: Int = 0, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val currentGrade = if (grade in 6..11) grade else 0
            _state.update { it.copy(isLoading = true, categoryKey = categoryKey, selectedGrade = currentGrade) }
            contentRepository.seedIfNeeded()
            contentRepository.syncWithDjangoBackend(force = forceRefresh)
            cloudSyncRepository.pullTicketProgressForCategory(categoryKey)

            val totalQuestions = when {
                contentRepository.isBookKey(categoryKey) -> {
                    contentRepository.getBookQuestionCount(categoryKey)
                }
                categoryKey.startsWith("topic_") || contentRepository.getTopicById(categoryKey) != null || contentRepository.getTopicById("topic_$categoryKey") != null -> {
                    val topicObj = contentRepository.getTopicById(categoryKey)
                        ?: contentRepository.getTopicById("topic_$categoryKey")
                    topicObj?.questionsCount?.takeIf { it > 0 } ?: 2500
                }
                else -> {
                    val counts = contentRepository.fetchDirectionCounts(currentGrade, forceRefresh = forceRefresh)
                    val fallbackDefaults = contentRepository.defaultDirectionCounts(currentGrade)
                    counts[categoryKey]?.takeIf { it > 0 } ?: fallbackDefaults[categoryKey] ?: 100_000
                }
            }

            val totalTicketCount =
                if (totalQuestions <= 0) 0
                else (totalQuestions + QUESTIONS_PER_TICKET - 1) / QUESTIONS_PER_TICKET

            // Faqat shu categoryKey (masalan, 7-sinf darsligi) uchun saqlangan faol test va progressni olamiz
            val categoryActiveState = preferencesRepository.getActiveQuizStateForCategory(categoryKey)
            val favTicketKeys = preferencesRepository.favoriteTicketKeys.first()
            val favQsCount = preferencesRepository.favoriteQuestionRecords.first().size
            val timerMins = preferencesRepository.ticketTimerMinutes.first()
            val progress = preferencesRepository.getTicketProgress(categoryKey)
            val savedLastOpened = progress.lastOpenedTicket.coerceIn(1, totalTicketCount.coerceAtLeast(1))
            val ticketList = ArrayList<TicketItemUiState>(totalTicketCount)

            var prevPassed = true
            var highestUnlocked = 1
            var activeInProgressTicketIdx: Int? = null
            var activeInProgressAnsweredCount: Int? = null

            for (i in 1..totalTicketCount) {
                val score = progress.scores[i] ?: 0
                val isPassed = progress.passed.contains(i) || score >= 10
                val isUnlocked = i == 1 || prevPassed
                if (isUnlocked) highestUnlocked = i

                val savedStars = progress.stars[i] ?: if (isPassed) (score / 10).coerceIn(1, 3) else 0

                val activeBaseRoute = categoryActiveState?.quizRoute?.substringBefore("?")
                val isActiveTicket = activeBaseRoute == "quiz/ticket_${categoryKey}_$i"

                val ongoingScore = if (isActiveTicket) categoryActiveState?.correctCount else null
                val ongoingAnswered = if (isActiveTicket) categoryActiveState?.answeredCorrectly?.count { it != null } else null

                if (isActiveTicket && ongoingAnswered != null) {
                    activeInProgressTicketIdx = i
                    activeInProgressAnsweredCount = ongoingAnswered
                }

                ticketList.add(
                    TicketItemUiState(
                        index = i,
                        isUnlocked = isUnlocked,
                        isPassed = isPassed,
                        bestScore = maxOf(score, ongoingScore ?: 0),
                        stars = savedStars.coerceIn(0, 3),
                        isFavorite = "${categoryKey}_$i" in favTicketKeys,
                        ongoingScore = ongoingScore,
                        ongoingAnsweredCount = ongoingAnswered,
                        isLastOpened = i == savedLastOpened
                    )
                )

                prevPassed = isPassed || (ongoingScore != null && ongoingScore >= 10)
            }

            val effectiveLastOpened = savedLastOpened.coerceIn(1, highestUnlocked.coerceAtLeast(1))
            val lastOpenedItem = ticketList.getOrNull(effectiveLastOpened - 1)
            val targetContinueTicket = when {
                activeInProgressTicketIdx != null -> activeInProgressTicketIdx
                lastOpenedItem != null && !lastOpenedItem.isPassed -> effectiveLastOpened
                else -> highestUnlocked.coerceIn(1, totalTicketCount.coerceAtLeast(1))
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    categoryKey = categoryKey,
                    selectedGrade = currentGrade,
                    totalQuestions = totalQuestions,
                    timerMinutes = timerMins,
                    favoriteTicketKeys = favTicketKeys,
                    totalFavoritesCount = favTicketKeys.size + favQsCount,
                    lastOpenedTicket = effectiveLastOpened,
                    highestUnlockedTicket = highestUnlocked,
                    continueTicketIndex = targetContinueTicket,
                    continueAnsweredCount = activeInProgressAnsweredCount,
                    isContinueInProgress = activeInProgressTicketIdx != null,
                    tickets = ticketList,
                    activeQuizState = categoryActiveState
                )
            }

            // Light prefetch around user's active ticket
            prefetchNextTicketsIfNeeded(categoryKey, targetContinueTicket, currentGrade)
        }
    }

    fun markTicketOpened(categoryKey: String, ticketIndex: Int, grade: Int = 0) {
        viewModelScope.launch {
            preferencesRepository.setLastOpenedTicket(categoryKey, ticketIndex, grade)
            val progress = preferencesRepository.getTicketProgress(categoryKey)
            val highestUnlocked = ((progress.passed.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
            cloudSyncRepository.saveTicketProgressToCloud(
                categoryKey = categoryKey,
                passedTickets = progress.passed,
                scores = progress.scores,
                lastOpenedTicket = ticketIndex,
                highestUnlockedTicket = highestUnlocked
            )
            _state.update { current ->
                current.copy(
                    lastOpenedTicket = ticketIndex,
                    continueTicketIndex = ticketIndex,
                    tickets = current.tickets.map { item ->
                        item.copy(isLastOpened = item.index == ticketIndex)
                    }
                )
            }
        }
    }

    fun prefetchNextTicketsIfNeeded(categoryKey: String, currentTicketIndex: Int, grade: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val startTicket = currentTicketIndex
            val endTicket = (currentTicketIndex + 2).coerceAtMost(3334)
            for (ticketIdx in startTicket..endTicket) {
                try {
                    contentRepository.fetchTicketFromApi(categoryKey, ticketIdx, count = 30, grade = grade)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
