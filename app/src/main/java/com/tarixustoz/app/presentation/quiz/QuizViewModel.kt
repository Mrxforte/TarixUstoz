package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Question
import com.tarixustoz.app.domain.model.Difficulty
import com.tarixustoz.app.domain.model.QuestionCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizUiState(
    val isLoading: Boolean = true,
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val answeredCorrectly: List<Boolean?> = emptyList(),
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
    private val preferences: UserPreferencesRepository
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
            
            val currentGrade = try {
                preferences.selectedGrade.first()
            } catch (_: Exception) { 6 }

            contentRepository.seedIfNeeded()

            val count = customCount ?: 10
            val catCode = customCategory ?: "ALL"
            val diffCode = customDifficulty ?: "ALL"

            var questions: List<Question> = emptyList()

            if (topicId == "wrong_questions") {
                questions = contentRepository.getWrongQuestionsForReview(limit = count)
            } else if (!topicId.isNullOrBlank() && topicId.startsWith("variant_")) {
                val parts = topicId.split("_")
                val reqGrade = parts.getOrNull(1)?.toIntOrNull() ?: currentGrade
                val reqSubject = if (parts.size >= 4) parts[2] else "ALL"
                val varIndexStr = if (parts.size >= 4) parts[3] else parts.getOrNull(2)
                val varIndex = varIndexStr?.toIntOrNull() ?: 1
                val offset = (varIndex - 1) * 30

                val localVariant = contentRepository.getQuestionsForGradeAndSubjectWithOffset(reqGrade, reqSubject, 30, offset)
                questions = if (localVariant.isNotEmpty()) {
                    localVariant
                } else {
                    contentRepository.getQuestionsForGradeAndSubject(reqGrade, reqSubject, count)
                }
            } else if (!topicId.isNullOrBlank() && topicId != "custom") {
                if (topicId.startsWith("book_") || topicId == "uz_6") {
                    val book = contentRepository.getTextbookById(topicId)
                    val reqGrade = book?.gradeId ?: if (topicId == "uz_6") 6 else 0
                    val reqSubject = book?.subjectId ?: ""

                    val apiQuestions = contentRepository.fetchQuizFromApi(
                        count = count,
                        categoryCode = catCode,
                        grade = reqGrade,
                        subject = reqSubject,
                        difficulty = diffCode
                    )
                    questions = if (apiQuestions.isNotEmpty()) apiQuestions else contentRepository.getCustomQuestions(
                        limit = count,
                        category = catCode,
                        difficulty = diffCode,
                        topicIds = emptyList(),
                        gradeId = reqGrade
                    )
                } else {
                    val dbQuestions = contentRepository.getQuestionsForTopic(topicId)
                    if (dbQuestions.isNotEmpty()) {
                        questions = dbQuestions
                    } else {
                        val apiQuestions = contentRepository.fetchQuizFromApi(
                            count = count,
                            categoryCode = catCode,
                            grade = 0,
                            subject = "",
                            difficulty = diffCode,
                            topicIds = listOf(topicId)
                        )
                        questions = if (apiQuestions.isNotEmpty()) apiQuestions else dbQuestions
                    }
                }
            } else {
                val hasTopicFilters = !selectedTopicIds.isNullOrEmpty()
                val reqGrade = if (hasTopicFilters) 0 else currentGrade

                // 1. Try API
                val apiQuestions = contentRepository.fetchQuizFromApi(
                    count = count,
                    categoryCode = catCode,
                    grade = reqGrade,
                    subject = "",
                    difficulty = diffCode,
                    topicIds = selectedTopicIds ?: emptyList()
                )
                if (apiQuestions.isNotEmpty()) {
                    questions = apiQuestions
                } else {
                    // 2. Fallback to Local Room DB
                    questions = contentRepository.getCustomQuestions(
                        limit = count,
                        category = catCode,
                        difficulty = diffCode,
                        topicIds = selectedTopicIds ?: emptyList(),
                        gradeId = reqGrade
                    )
                }
            }

            // 3. Guarantee fallback questions ONLY if no specific topic filter was selected
            if (questions.isEmpty()) {
                val hasTopicFilters = !selectedTopicIds.isNullOrEmpty() || (!topicId.isNullOrBlank() && topicId != "custom")
                if (!hasTopicFilters) {
                    questions = contentRepository.getFallbackQuestions(count, catCode, currentGrade)
                }
            }
            
            _uiState.value = QuizUiState(
                isLoading = false,
                questions = questions
                    .map { question -> question.copy(options = question.options.shuffled()) }
                    .shuffled(),
                answeredCorrectly = List(questions.size) { null }
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
            val answers = it.answeredCorrectly.toMutableList()
            if (state.currentIndex in answers.indices) answers[state.currentIndex] = isCorrect
            it.copy(
                answeredCorrectly = answers,
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
