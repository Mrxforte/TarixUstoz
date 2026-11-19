package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.local.QuizAttemptEntity
import com.tarixustoz.app.data.repository.ActiveQuizState
import com.tarixustoz.app.data.repository.CloudSyncRepository
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.AnswerOption
import com.tarixustoz.app.domain.model.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val quizTitle: String = "Test",
    val isTicketQuiz: Boolean = false,
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val answeredCorrectly: List<Boolean?> = emptyList(),
    val selectedOptionId: String? = null,
    val showFeedback: Boolean = false,
    val wasCorrect: Boolean = false,
    val correctCount: Int = 0,
    val isFinished: Boolean = false,
    val isTimedOut: Boolean = false,
    val earnedStars: Int = 0,
    val remainingSeconds: Int = 1200,
    val totalLimitSeconds: Int = 1200,
    val favoriteQuestionIds: Set<String> = emptySet(),
    val isCurrentTicketFavorite: Boolean = false,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val timeSpentSeconds: Int = 0
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mistakesRepository: MistakesRepository,
    private val preferences: UserPreferencesRepository,
    private val database: AppDatabase,
    private val cloudSyncRepository: CloudSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var currentQuizRoute: String? = null
    private var currentQuizTitle: String? = null
    private var currentTicketCategory: String? = null
    private var currentTicketIndex: Int? = null
    private var currentTicketGrade: Int = 0
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.favorites.collect { favIds ->
                _uiState.update { it.copy(favoriteQuestionIds = favIds) }
            }
        }
        viewModelScope.launch {
            preferences.favoriteTicketKeys.collect { favTickets ->
                val cat = currentTicketCategory
                val idx = currentTicketIndex
                val isFav = cat != null && idx != null && "${cat}_$idx" in favTickets
                _uiState.update { it.copy(isCurrentTicketFavorite = isFav) }
            }
        }
    }

    /**
     * Shuffles both the sequence of questions AND the A/B/C/D options inside each question,
     * reassigning clean option IDs ("a", "b", "c", "d") so the letters A, B, C, D always get new positions.
     */
    private fun shuffleQuestionsAndAbcd(rawQuestions: List<Question>): List<Question> {
        val optionIds = listOf("a", "b", "c", "d")
        return rawQuestions.shuffled().map { question ->
            val shuffledOpts = question.options.shuffled().mapIndexed { idx, opt ->
                val newId = optionIds.getOrElse(idx) { opt.id }
                AnswerOption(
                    id = newId,
                    text = opt.text,
                    isCorrect = opt.isCorrect
                )
            }
            question.copy(options = shuffledOpts)
        }
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value
                if (current.isLoading || current.isFinished) break
                val nextRemaining = (current.remainingSeconds - 1).coerceAtLeast(0)
                if (nextRemaining <= 0) {
                    // Time ran out before completing the quiz: 0 stars, must restart!
                    _uiState.update {
                        it.copy(
                            remainingSeconds = 0,
                            isTimedOut = true,
                            isFinished = true,
                            earnedStars = 0,
                            timeSpentSeconds = it.totalLimitSeconds
                        )
                    }
                    preferences.clearActiveQuiz(categoryKey = currentTicketCategory)
                    break
                } else {
                    _uiState.update { it.copy(remainingSeconds = nextRemaining) }
                }
            }
        }
    }

    fun toggleCurrentQuestionFavorite() {
        val state = _uiState.value
        val question = state.questions.getOrNull(state.currentIndex) ?: return
        viewModelScope.launch {
            preferences.toggleFavoriteQuestion(
                question = question,
                categoryTitle = currentQuizTitle ?: "Test savoli"
            )
        }
    }

    fun toggleCurrentTicketFavorite() {
        val cat = currentTicketCategory ?: return
        val idx = currentTicketIndex ?: return
        viewModelScope.launch {
            preferences.toggleFavoriteTicket(
                categoryKey = cat,
                ticketIndex = idx,
                categoryTitle = currentQuizTitle ?: "$idx-Bilet",
                grade = currentTicketGrade
            )
        }
    }

    fun restartWithShuffle() {
        val current = _uiState.value
        if (current.questions.isEmpty()) return
        timerJob?.cancel()
        viewModelScope.launch {
            preferences.clearActiveQuiz(categoryKey = currentTicketCategory)
            val reshuffled = shuffleQuestionsAndAbcd(current.questions)
            val limitSec = current.totalLimitSeconds.coerceAtLeast(300)
            val favIds = preferences.favorites.first()
            val favTickets = preferences.favoriteTicketKeys.first()
            val isTicketFav = currentTicketCategory != null && currentTicketIndex != null &&
                "${currentTicketCategory}_$currentTicketIndex" in favTickets

            val newState = QuizUiState(
                isLoading = false,
                quizTitle = currentQuizTitle ?: current.quizTitle,
                isTicketQuiz = currentTicketCategory != null,
                questions = reshuffled,
                currentIndex = 0,
                answeredCorrectly = List(reshuffled.size) { null },
                selectedOptionId = null,
                showFeedback = false,
                wasCorrect = false,
                correctCount = 0,
                isFinished = false,
                isTimedOut = false,
                earnedStars = 0,
                remainingSeconds = limitSec,
                totalLimitSeconds = limitSec,
                favoriteQuestionIds = favIds,
                isCurrentTicketFavorite = isTicketFav,
                startTimeMillis = System.currentTimeMillis()
            )
            _uiState.value = newState
            currentQuizRoute?.let { route ->
                preferences.saveActiveQuizState(
                    ActiveQuizState(
                        quizRoute = route,
                        quizTitle = newState.quizTitle,
                        questions = reshuffled,
                        currentIndex = 0,
                        answeredCorrectly = newState.answeredCorrectly,
                        correctCount = 0,
                        remainingSeconds = limitSec,
                        totalLimitSeconds = limitSec
                    ),
                    categoryKey = currentTicketCategory
                )
            }
            startCountdownTimer()
        }
    }

    fun startQuiz(
        topicId: String?,
        customCount: Int? = null,
        customCategory: String? = null,
        customDifficulty: String? = null,
        timeLimitMinutes: Int? = null,
        selectedTopicIds: List<String>? = null,
        explicitGrade: Int? = null,
        forceShuffle: Boolean = false
    ) {
        viewModelScope.launch {
            timerJob?.cancel()
            _uiState.value = QuizUiState(isLoading = true)

            val savedTimerMins = preferences.ticketTimerMinutes.first()
            val effectiveMinutes = (timeLimitMinutes ?: savedTimerMins).coerceIn(5, 60)
            val totalLimitSec = effectiveMinutes * 60

            val route = buildString {
                append("quiz/${topicId ?: "custom"}")
                if (explicitGrade != null) append("?grade=$explicitGrade")
            }

            currentTicketCategory = null
            currentTicketIndex = null
            currentTicketGrade = explicitGrade ?: 0

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

            val favIds = preferences.favorites.first()
            val favTickets = preferences.favoriteTicketKeys.first()
            val isTicketFav = currentTicketCategory != null && currentTicketIndex != null &&
                "${currentTicketCategory}_$currentTicketIndex" in favTickets

            if (forceShuffle) {
                preferences.clearActiveQuiz(categoryKey = currentTicketCategory)
            } else {
                val catKey = currentTicketCategory
                val savedState = if (catKey != null) {
                    preferences.getActiveQuizStateForCategory(catKey)
                } else {
                    preferences.activeQuizState.first()
                }
                val savedBaseRoute = savedState?.quizRoute?.substringBefore("?")
                val currentBaseRoute = route.substringBefore("?")
                if (savedState != null && savedBaseRoute == currentBaseRoute && savedState.questions.isNotEmpty()) {
                    val firstUnanswered = savedState.answeredCorrectly.indexOfFirst { it == null }
                    val resumeIndex = if (firstUnanswered in savedState.questions.indices) {
                        firstUnanswered
                    } else {
                        savedState.currentIndex.coerceIn(0, savedState.questions.lastIndex)
                    }
                    val restoredLimit = savedState.totalLimitSeconds ?: totalLimitSec
                    val restoredRemaining = (savedState.remainingSeconds ?: restoredLimit).coerceIn(10, restoredLimit)
                    _uiState.value = QuizUiState(
                        isLoading = false,
                        quizTitle = savedState.quizTitle,
                        isTicketQuiz = currentTicketCategory != null,
                        questions = savedState.questions,
                        currentIndex = resumeIndex,
                        answeredCorrectly = savedState.answeredCorrectly,
                        correctCount = savedState.correctCount,
                        isFinished = false,
                        isTimedOut = false,
                        earnedStars = 0,
                        remainingSeconds = restoredRemaining,
                        totalLimitSeconds = restoredLimit,
                        favoriteQuestionIds = favIds,
                        isCurrentTicketFavorite = isTicketFav
                    )
                    currentQuizRoute = route
                    currentQuizTitle = savedState.quizTitle
                    currentTicketCategory?.let { cat ->
                        val openedIdx = currentTicketIndex ?: 1
                        preferences.setLastOpenedTicket(cat, openedIdx, currentTicketGrade)
                    }
                    startCountdownTimer()
                    return@launch
                }
            }

            val currentGrade = explicitGrade ?: try {
                preferences.selectedGrade.first()
            } catch (_: Exception) { 0 }

            contentRepository.seedIfNeeded()

            val count = customCount ?: 10
            val catCode = customCategory ?: "ALL"
            val diffCode = customDifficulty ?: "ALL"

            var questions: List<Question> = emptyList()
            var title = "Test"

            if (!topicId.isNullOrBlank() && topicId.startsWith("ticket_")) {
                val ticketGrade = explicitGrade ?: 0
                currentTicketCategory?.let { cat ->
                    val openedIdx = currentTicketIndex ?: 1
                    preferences.setLastOpenedTicket(cat, openedIdx, ticketGrade)
                }
                questions = contentRepository.getTicketQuestions(
                    currentTicketCategory!!, currentTicketIndex!!, 30, ticketGrade
                )
                title = "$currentTicketIndex-Bilet"
            } else if (topicId == "favorite_questions") {
                val favRecords = preferences.favoriteQuestionRecords.first()
                questions = favRecords.map { it.question }.ifEmpty {
                    contentRepository.getWrongQuestionsForReview(limit = count)
                }
                title = "Sevimli savollar"
            } else if (topicId == "daily") {
                val daily = contentRepository.getDailyQuestion(grade = currentGrade)
                val extra = contentRepository.fetchQuizFromApi(
                    count = 4,
                    categoryCode = "ALL",
                    grade = currentGrade,
                    subject = "",
                    difficulty = "ALL"
                ).ifEmpty {
                    contentRepository.getFallbackQuestions(4, "ALL", currentGrade)
                }
                questions = buildList {
                    if (daily != null) add(daily)
                    extra.filter { it.id != daily?.id }.take(4).forEach { add(it) }
                }
                title = "Kun savoli"
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
                val book = contentRepository.getTextbookById(topicId)
                val reqGrade = book?.gradeId ?: topicId.filter { it.isDigit() }.toIntOrNull() ?: currentGrade
                val reqSubject = book?.subjectId ?: if (topicId.contains("jahon")) "jahon" else "uzbekiston"

                if (topicId.startsWith("topic_")) {
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
                    title = "Mavzu testi"
                } else {
                    val apiQuestions = contentRepository.fetchQuizFromApi(
                        count = count,
                        categoryCode = catCode,
                        grade = reqGrade,
                        subject = reqSubject,
                        difficulty = diffCode
                    )
                    questions = if (apiQuestions.isNotEmpty()) {
                        apiQuestions
                    } else {
                        contentRepository.getCustomQuestions(
                            limit = count,
                            category = catCode,
                            difficulty = diffCode,
                            topicIds = emptyList(),
                            gradeId = reqGrade
                        )
                    }
                    title = book?.title ?: "$reqGrade-sinf testi"
                }
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

            val shuffledQuestions = shuffleQuestionsAndAbcd(questions)

            val initialState = QuizUiState(
                isLoading = false,
                quizTitle = title,
                isTicketQuiz = currentTicketCategory != null,
                questions = shuffledQuestions,
                answeredCorrectly = List(shuffledQuestions.size) { null },
                remainingSeconds = totalLimitSec,
                totalLimitSeconds = totalLimitSec,
                favoriteQuestionIds = favIds,
                isCurrentTicketFavorite = isTicketFav
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
                    correctCount = 0,
                    remainingSeconds = totalLimitSec,
                    totalLimitSeconds = totalLimitSec
                ),
                categoryKey = currentTicketCategory
            )
            startCountdownTimer()
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
            val userAnswerText = question.options.find { it.id == selected }?.text ?: selected
            try {
                database.quizAttemptDao().insert(
                    QuizAttemptEntity(
                        questionId = question.id,
                        wasCorrect = isCorrect,
                        userAnswer = userAnswerText,
                        timestamp = System.currentTimeMillis(),
                        sessionId = currentQuizRoute
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (!isCorrect) {
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
                            correctCount = newState.correctCount,
                            remainingSeconds = newState.remainingSeconds,
                            totalLimitSeconds = newState.totalLimitSeconds
                        ),
                        categoryKey = currentTicketCategory
                    )
                }
            }
            newState
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex >= state.questions.lastIndex) {
            timerJob?.cancel()
            val totalSeconds = ((System.currentTimeMillis() - state.startTimeMillis) / 1000).toInt().coerceAtLeast(1)
            val completedWithinTime = !state.isTimedOut && state.remainingSeconds > 0
            // 10 ta to'g'ri savolga 1 yulduz (agar belgilangan vaqtda ishlab tugatsa; tugatmasa 0 yulduz)
            val earnedStars = if (completedWithinTime) {
                (state.correctCount / 10).coerceIn(0, 3)
            } else {
                0
            }
            _uiState.update {
                it.copy(
                    isFinished = true,
                    isTimedOut = !completedWithinTime,
                    earnedStars = earnedStars,
                    timeSpentSeconds = totalSeconds
                )
            }
            val cat = currentTicketCategory
            val idx = currentTicketIndex
            val ticketGrade = currentTicketGrade
            val quizTitleSnapshot = currentQuizTitle ?: "Test"
            val firstQuestionGrade = state.questions.firstOrNull()?.gradeId ?: 0
            viewModelScope.launch {
                preferences.clearActiveQuiz(categoryKey = cat)
                preferences.recordQuizCompletion(
                    solvedCount = state.questions.size,
                    correctCount = state.correctCount,
                    timeSpentSeconds = totalSeconds
                )
                if (cat != null && idx != null && completedWithinTime) {
                    val score = state.correctCount
                    val passed = earnedStars >= 1 && score >= 10
                    preferences.saveTicketResult(cat, idx, score, passed, stars = earnedStars, grade = ticketGrade)
                    
                    if (passed) {
                        preferences.incrementTicketAdCounter()
                    }
                    
                    val progress = preferences.getTicketProgress(cat)
                    val highestUnlocked = ((progress.passed.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
                    val nextTarget = if (passed) (idx + 1).coerceAtLeast(1) else idx
                    cloudSyncRepository.saveTicketProgressToCloud(
                        categoryKey = cat,
                        passedTickets = progress.passed,
                        scores = progress.scores,
                        lastOpenedTicket = nextTarget,
                        highestUnlockedTicket = highestUnlocked
                    )
                }
                cloudSyncRepository.recordQuizAttemptToCloud(
                    categoryKey = cat ?: (currentQuizRoute?.removePrefix("quiz/") ?: "custom"),
                    categoryTitle = quizTitleSnapshot,
                    grade = firstQuestionGrade,
                    solvedCount = state.questions.size,
                    correctCount = state.correctCount,
                    durationSeconds = totalSeconds
                )
                cloudSyncRepository.pushStatsToCloud()
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
                                correctCount = newState.correctCount,
                                remainingSeconds = newState.remainingSeconds,
                                totalLimitSeconds = newState.totalLimitSeconds
                            ),
                            categoryKey = currentTicketCategory
                        )
                    }
                }
                newState
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
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
                            correctCount = state.correctCount,
                            remainingSeconds = state.remainingSeconds,
                            totalLimitSeconds = state.totalLimitSeconds
                        ),
                        categoryKey = currentTicketCategory
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
