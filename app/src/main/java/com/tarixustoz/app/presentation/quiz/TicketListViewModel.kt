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
    val ongoingScore: Int? = null,
    val ongoingAnsweredCount: Int? = null,
    val isLastOpened: Boolean = false
)

data class TicketListUiState(
    val isLoading: Boolean = true,
    val categoryKey: String = "",
    val totalQuestions: Int = 0,
    val lastOpenedTicket: Int = 1,
    val highestUnlockedTicket: Int = 1,
    val tickets: List<TicketItemUiState> = emptyList(),
    val activeQuizRoute: String? = null,
    val activeQuizTitle: String? = null,
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
            preferencesRepository.activeQuizRoute.collect { route ->
                _state.update { it.copy(activeQuizRoute = route) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.activeQuizTitle.collect { title ->
                _state.update { it.copy(activeQuizTitle = title) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.activeQuizState.collect { quizState ->
                _state.update { it.copy(activeQuizState = quizState) }
            }
        }
    }

    fun loadTickets(categoryKey: String, grade: Int = 0, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, categoryKey = categoryKey) }
            contentRepository.seedIfNeeded()
            contentRepository.syncWithDjangoBackend(force = forceRefresh)
            cloudSyncRepository.pullTicketProgressForCategory(categoryKey)

            val currentGrade = if (grade in 6..11) grade else 0

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

            val activeState = preferencesRepository.activeQuizState.first()
            val progress = preferencesRepository.getTicketProgress(categoryKey)
            val savedLastOpened = progress.lastOpenedTicket.coerceIn(1, totalTicketCount.coerceAtLeast(1))
            val ticketList = ArrayList<TicketItemUiState>(totalTicketCount)

            var prevPassed = true
            var highestUnlocked = 1
            for (i in 1..totalTicketCount) {
                val score = progress.scores[i] ?: 0
                val isPassed = progress.passed.contains(i) || score >= 10
                val isUnlocked = i == 1 || prevPassed
                if (isUnlocked) highestUnlocked = i

                val activeBaseRoute = activeState?.quizRoute?.substringBefore("?")
                val isActiveTicket = activeBaseRoute == "quiz/ticket_${categoryKey}_$i"

                val ongoingScore = if (isActiveTicket) activeState?.correctCount else null
                val ongoingAnswered = if (isActiveTicket) activeState?.answeredCorrectly?.count { it != null } else null

                ticketList.add(
                    TicketItemUiState(
                        index = i,
                        isUnlocked = isUnlocked,
                        isPassed = isPassed,
                        bestScore = maxOf(score, ongoingScore ?: 0),
                        ongoingScore = ongoingScore,
                        ongoingAnsweredCount = ongoingAnswered,
                        isLastOpened = i == savedLastOpened
                    )
                )

                prevPassed = isPassed || (ongoingScore != null && ongoingScore >= 10)
            }

            val effectiveLastOpened = savedLastOpened.coerceIn(1, highestUnlocked.coerceAtLeast(1))

            _state.update {
                it.copy(
                    isLoading = false,
                    categoryKey = categoryKey,
                    totalQuestions = totalQuestions,
                    lastOpenedTicket = effectiveLastOpened,
                    highestUnlockedTicket = highestUnlocked,
                    tickets = ticketList
                )
            }

            // Light prefetch around user's active ticket
            prefetchNextTicketsIfNeeded(categoryKey, effectiveLastOpened, currentGrade)
        }
    }

    fun markTicketOpened(categoryKey: String, ticketIndex: Int) {
        viewModelScope.launch {
            preferencesRepository.setLastOpenedTicket(categoryKey, ticketIndex)
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
