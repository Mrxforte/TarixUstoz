package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Question
import com.tarixustoz.app.domain.model.Difficulty
import com.tarixustoz.app.domain.model.QuestionCategory
import com.tarixustoz.app.domain.usecase.GenerativeQuizService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizUiState(
    val isLoading: Boolean = true,
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionId: String? = null,
    val showFeedback: Boolean = false,
    val wasCorrect: Boolean = false,
    val correctCount: Int = 0,
    val isFinished: Boolean = false
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mistakesRepository: MistakesRepository,
    private val preferences: UserPreferencesRepository,
    private val generativeQuizService: GenerativeQuizService
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    fun startQuiz(
        topicId: String?,
        customCount: Int? = null,
        customCategory: String? = null,
        customDifficulty: String? = null,
        timeLimitMinutes: Int? = null,
        selectedTopicIds: List<String>? = null
    ) {
        viewModelScope.launch {
            _uiState.value = QuizUiState(isLoading = true)
            contentRepository.seedIfNeeded()
            
            val questions = if (topicId == "wrong_questions") {
                contentRepository.getWrongQuestionsForReview(limit = customCount ?: 10)
            } else if (topicId != null && topicId != "custom") {
                val dbQuestions = contentRepository.getQuestionsForTopic(topicId)
                if (dbQuestions.isNotEmpty()) {
                    dbQuestions
                } else {
                    val topic = contentRepository.getTopicById(topicId)
                    if (topic != null && topic.content.isNotBlank()) {
                        val genQuestions = generativeQuizService.generateQuestions(
                            topicContent = topic.content,
                            count = customCount ?: 10,
                            difficulty = customDifficulty?.let { enumValueOf<Difficulty>(it) } ?: Difficulty.MEDIUM,
                            category = customCategory?.let { enumValueOf<QuestionCategory>(it) } ?: QuestionCategory.ALL
                        )
                        genQuestions.ifEmpty { dbQuestions }
                    } else {
                        dbQuestions
                    }
                }
            } else {
                var currentGrade = 6
                try {
                    preferences.selectedGrade.collect { grade ->
                        currentGrade = grade
                    }
                } catch (_: Exception) { }

                contentRepository.getCustomQuestions(
                    limit = customCount ?: 10,
                    category = customCategory ?: "ALL",
                    difficulty = customDifficulty ?: "ALL",
                    topicIds = selectedTopicIds ?: emptyList(),
                    gradeId = currentGrade
                )
            }
            
            _uiState.value = QuizUiState(
                isLoading = false,
                questions = questions.shuffled()
            )
        }
    }

    fun selectOption(optionId: String) {
        _uiState.update { it.copy(selectedOptionId = optionId) }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val question = state.questions.getOrNull(state.currentIndex) ?: return
        val selected = state.selectedOptionId ?: return
        val correctOption = question.options.find { it.isCorrect }
        val isCorrect = correctOption?.id == selected

        viewModelScope.launch {
            if (!isCorrect) {
                val userAnswerText = question.options.find { it.id == selected }?.text ?: selected
                mistakesRepository.recordIncorrect(question, userAnswerText)
            } else {
                preferences.addXp(10)
            }
        }

        _uiState.update {
            it.copy(
                showFeedback = true,
                wasCorrect = isCorrect,
                correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount
            )
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex >= state.questions.lastIndex) {
            _uiState.update { it.copy(isFinished = true) }
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = it.currentIndex + 1,
                    selectedOptionId = null,
                    showFeedback = false,
                    wasCorrect = false
                )
            }
        }
    }
}
