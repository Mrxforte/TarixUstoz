package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ActiveQuizState
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private var currentQuizRoute: String? = null
    private var currentQuizTitle: String? = null
    private var currentTicketCategory: String? = null
    private var currentTicketIndex: Int? = null

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

            val route = "quiz/${topicId ?: "custom"}"
            val savedState = preferences.activeQuizState.first()
            if (savedState != null && savedState.quizRoute == route) {
                _uiState.value = QuizUiState(
                    isLoading = false,
                    questions = savedState.questions,
                    currentIndex = savedState.currentIndex,
                    answeredCorrectly = savedState.answeredCorrectly,
                    correctCount = savedState.correctCount,
                    isFinished = false
                )
                currentQuizRoute = route
                currentQuizTitle = savedState.quizTitle
                if (topicId != null && topicId.startsWith("ticket_")) {
                    val rest = topicId.removePrefix("ticket_")
                    val lastUnderscore = rest.lastIndexOf('_')
                    if (lastUnderscore > 0) {
                        currentTicketCategory = rest.substring(0, lastUnderscore)
                        currentTicketIndex = rest.substring(lastUnderscore + 1).toIntOrNull() ?: 1
                    } else {
                        currentTicketCategory = rest
                        currentTicketIndex = 1
                    }
                }
                return@launch
            }

            val currentGrade = try {
                preferences.selectedGrade.first()
            } catch (_: Exception) { 6 }

            contentRepository.seedIfNeeded()

            val count = customCount ?: 10
            val catCode = customCategory ?: "ALL"
            val diffCode = customDifficulty ?: "ALL"

            var questions: List<Question> = emptyList()
            var title = "Test"

            if (!topicId.isNullOrBlank() && topicId.startsWith("ticket_")) {
                val rest = topicId.removePrefix("ticket_")
                val lastUnderscore = rest.lastIndexOf('_')
                if (lastUnderscore > 0) {
                    currentTicketCategory = rest.substring(0, lastUnderscore)
                    currentTicketIndex = rest.substring(lastUnderscore + 1).toIntOrNull() ?: 1
                } else {
                    currentTicketCategory = rest
                    currentTicketIndex = 1
                }
                questions = contentRepository.getTicketQuestions(
                    currentTicketCategory!!, currentTicketIndex!!, 30, currentGrade
                )
                title = "$currentTicketIndex-Bilet"
            } else if (topicId == "wrong_questions") {
                questions = contentRepository.getWrongQuestionsForReview(limit = count)
                title = "Xatolar ustida ishlash"
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
                title = "$varIndex-Variant"
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
                title = "Mavzu testi"
            } else {
                val hasTopicFilters = !selectedTopicIds.isNullOrEmpty()
                val reqGrade = if (hasTopicFilters) 0 else currentGrade

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
                    questions = contentRepository.getCustomQuestions(
                        limit = count,
                        category = catCode,
                        difficulty = diffCode,
                        topicIds = selectedTopicIds ?: emptyList(),
                        gradeId = reqGrade
                    )
                }
                title = "Maxsus test"
            }

            if (questions.isEmpty()) {
                val hasTopicFilters = !selectedTopicIds.isNullOrEmpty() || (!topicId.isNullOrBlank() && topicId != "custom")
                if (!hasTopicFilters) {
                    questions = contentRepository.getFallbackQuestions(count, catCode, currentGrade)
                }
            }

            val shuffledQuestions = questions
                .map { question -> question.copy(options = question.options.shuffled()) }
                .shuffled()

            val initialState = QuizUiState(
                isLoading = false,
                questions = shuffledQuestions,
                answeredCorrectly = List(shuffledQuestions.size) { null }
            )
            _uiState.value = initialState

            currentQuizRoute = route
            currentQuizTitle = title

            preferences.saveActiveQuizState(
                ActiveQuizState(
                    quizRoute = route,
                    quizTitle = title,
                    questions = shuffledQuestions,
                    currentIndex = 0,
                    answeredCorrectly = initialState.answeredCorrectly,
                    correctCount = 0
                )
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
            val newState = it.copy(
                answeredCorrectly = answers,
                showFeedback = true,
                wasCorrect = isCorrect,
                correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount
            )
            viewModelScope.launch {
                currentQuizRoute?.let { route ->
                    preferences.saveActiveQuizState(
                        ActiveQuizState(
                            quizRoute = route,
                            quizTitle = currentQuizTitle ?: "Test",
                            questions = newState.questions,
                            currentIndex = newState.currentIndex,
                            answeredCorrectly = newState.answeredCorrectly,
                            correctCount = newState.correctCount
                        )
                    )
                }
            }
            newState
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex >= state.questions.lastIndex) {
            _uiState.update { it.copy(isFinished = true) }
            val cat = currentTicketCategory
            val idx = currentTicketIndex
            viewModelScope.launch {
                preferences.clearActiveQuiz()
                if (cat != null && idx != null) {
                    val score = state.correctCount
                    val passed = score >= 10
                    preferences.saveTicketResult(cat, idx, score, passed)
                }
            }
        } else {
            _uiState.update {
                val newState = it.copy(
                    currentIndex = it.currentIndex + 1,
                    selectedOptionId = null,
                    showFeedback = false,
                    wasCorrect = false
                )
                viewModelScope.launch {
                    currentQuizRoute?.let { route ->
                        preferences.saveActiveQuizState(
                            ActiveQuizState(
                                quizRoute = route,
                                quizTitle = currentQuizTitle ?: "Test",
                                questions = newState.questions,
                                currentIndex = newState.currentIndex,
                                answeredCorrectly = newState.answeredCorrectly,
                                correctCount = newState.correctCount
                            )
                        )
                    }
                }
                newState
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val state = _uiState.value
        val route = currentQuizRoute
        val title = currentQuizTitle
        if (!state.isFinished && state.questions.isNotEmpty() && route != null) {
            try {
                runBlocking(Dispatchers.IO) {
                    preferences.saveActiveQuizState(
                        ActiveQuizState(
                            quizRoute = route,
                            quizTitle = title ?: "Test",
                            questions = state.questions,
                            currentIndex = state.currentIndex,
                            answeredCorrectly = state.answeredCorrectly,
                            correctCount = state.correctCount
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
