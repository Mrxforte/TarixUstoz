package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TicketItemUiState(
    val index: Int,
    val isUnlocked: Boolean = false,
    val isPassed: Boolean = false,
    val bestScore: Int = 0
)

data class TicketListUiState(
    val isLoading: Boolean = true,
    val categoryKey: String = "",
    val totalQuestions: Int = 0,
    val tickets: List<TicketItemUiState> = emptyList(),
    val activeQuizRoute: String? = null,
    val activeQuizTitle: String? = null
)

private const val QUESTIONS_PER_TICKET = 30

@HiltViewModel
class TicketListViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val preferencesRepository: UserPreferencesRepository
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
    }

    fun loadTickets(categoryKey: String) {
        viewModelScope.launch {
            _state.value = TicketListUiState(isLoading = true, categoryKey = categoryKey)
            contentRepository.seedIfNeeded()

            val currentGrade = try {
                preferencesRepository.selectedGrade.first()
            } catch (_: Exception) { 7 }

            val totalQuestions = when {
                categoryKey.startsWith("book_") || categoryKey.startsWith("uz_") -> {
                    val book = contentRepository.getTextbookById(categoryKey)
                    book?.totalQuestions ?: 0
                }
                categoryKey.startsWith("topic_") -> {
                    val topicId = categoryKey.removePrefix("topic_")
                    val topic = contentRepository.getTopicById(topicId)
                    topic?.questionsCount ?: 0
                }
                else -> {
                    // Same numbers the direction cards show (cached, so this is instant).
                    val counts = contentRepository.fetchDirectionCounts(currentGrade)
                    counts[categoryKey] ?: 0
                }
            }

            // Har bilet 30 savol; oxirgi bilet serverda to'ldiriladi, shuning uchun yaxlitlash.
            val totalTicketCount =
                if (totalQuestions <= 0) 0
                else (totalQuestions + QUESTIONS_PER_TICKET - 1) / QUESTIONS_PER_TICKET
            val progress = preferencesRepository.getTicketProgress(categoryKey)
            val ticketList = mutableListOf<TicketItemUiState>()

            var prevPassed = true
            for (i in 1..totalTicketCount) {
                val score = progress.scores[i] ?: 0
                val isPassed = progress.passed.contains(i) || score >= 10
                val isUnlocked = i == 1 || prevPassed

                ticketList.add(
                    TicketItemUiState(
                        index = i,
                        isUnlocked = isUnlocked,
                        isPassed = isPassed,
                        bestScore = score
                    )
                )

                prevPassed = isPassed
            }

            _state.value = TicketListUiState(
                isLoading = false,
                categoryKey = categoryKey,
                totalQuestions = totalQuestions,
                tickets = ticketList
            )
        }
    }
}
