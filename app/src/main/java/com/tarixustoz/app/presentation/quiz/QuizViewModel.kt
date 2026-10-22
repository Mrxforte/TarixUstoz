package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizUiState(
    val isLoading: Boolean = true,
    val categoryTitle: String = "Test",
    val questions: List<Question> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionId: String? = null,
    val showFeedback: Boolean = false,
    val wasCorrect: Boolean = false,
    val correctCount: Int = 0,
    val isFinished: Boolean = false,
    val earnedXp: Int = 0,
    val isDtmMode: Boolean = false,
    val isCampaignMode: Boolean = false,
    val isCurrentFavorite: Boolean = false,
    val favorites: Set<String> = emptySet()
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mistakesRepository: MistakesRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.favorites.collect { favs ->
                _uiState.update { state ->
                    val currentQId = state.questions.getOrNull(state.currentIndex)?.id
                    state.copy(
                        favorites = favs,
                        isCurrentFavorite = currentQId != null && favs.contains(currentQId)
                    )
                }
            }
        }
    }

    fun startQuiz(param: String?) {
        viewModelScope.launch {
            _uiState.value = QuizUiState(isLoading = true)
            contentRepository.seedIfNeeded()

            val title = when {
                param == "daily" -> "Kun savoli"
                param == "streak_campaign" || param == "campaign_6" -> "6-sinf Tarix: Qadimgi dunyo"
                param == "shahar_nomlari" -> "Shahar nomlari"
                param == "tarixiy_shaxslar" -> "Tarixiy shaxslar"
                param == "tarixiy_voqealar" -> "Tarixiy voqealar"
                param == "tarixiy_obidalar" -> "Tarixiy obidalar"
                param == "urushlar_va_janglar" -> "Urushlar va janglar"
                param == "mavzu_boyicha" -> "Mavzu bo‘yicha test"
                param == "yillar_boyicha" -> "Yillar bo‘yicha test"
                param == "dtm_exam" -> "DTM Sinov Imtihoni"
                param == "mistakes" -> "Xatolar ustida ishlash"
                param == "favorites" -> "Sevimli testlarim"
                param?.startsWith("topics:") == true -> "Tanlangan mavzular testi"
                else -> "Tarix testi"
            }

            val questions = when {
                param == "daily" -> {
                    listOf(contentRepository.getDailyQuestion())
                }
                param == "streak_campaign" || param == "campaign_6" -> {
                    contentRepository.getQuestionsByCategory("streak_campaign")
                }
                param?.startsWith("topics:") == true -> {
                    val topicIds = param.removePrefix("topics:").split(",").filter { it.isNotBlank() }
                    contentRepository.getQuestionsForTopics(topicIds)
                }
                param == "favorites" -> {
                    val favIds = preferences.favorites
                    val all = contentRepository.getAllQuestions()
                    val filtered = all.filter { _uiState.value.favorites.contains(it.id) }
                    if (filtered.isNotEmpty()) filtered else all.take(5)
                }
                param == "mistakes" -> {
                    contentRepository.getAllQuestions().shuffled().take(10)
                }
                param in listOf(
                    "shahar_nomlari", "tarixiy_shaxslar", "tarixiy_voqealar",
                    "tarixiy_obidalar", "urushlar_va_janglar", "mavzu_boyicha",
                    "yillar_boyicha", "dtm_exam"
                ) -> {
                    contentRepository.getQuestionsByCategory(param!!)
                }
                param != null -> {
                    val topicQuestions = contentRepository.getQuestionsForTopic(param)
                    if (topicQuestions.isNotEmpty()) topicQuestions else contentRepository.getAllQuestions()
                }
                else -> {
                    contentRepository.getAllQuestions()
                }
            }

            val curQId = questions.firstOrNull()?.id
            _uiState.value = QuizUiState(
                isLoading = false,
                categoryTitle = title,
                questions = if (param == "daily") questions else questions.shuffled(),
                isDtmMode = (param == "dtm_exam"),
                isCampaignMode = (param == "streak_campaign" || param == "campaign_6"),
                isCurrentFavorite = curQId != null && _uiState.value.favorites.contains(curQId),
                favorites = _uiState.value.favorites
            )
        }
    }

    fun selectOption(optionId: String) {
        if (_uiState.value.showFeedback) return
        _uiState.update { it.copy(selectedOptionId = optionId) }
    }

    fun toggleCurrentFavorite() {
        val currentQ = _uiState.value.questions.getOrNull(_uiState.value.currentIndex) ?: return
        viewModelScope.launch {
            val isNowFav = preferences.toggleFavorite(currentQ.id)
            _uiState.update { it.copy(isCurrentFavorite = isNowFav) }
        }
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
                // Modest, realistic small XP (+2 XP)
                preferences.addXp(2)
            }
        }

        _uiState.update {
            it.copy(
                showFeedback = true,
                wasCorrect = isCorrect,
                correctCount = if (isCorrect) it.correctCount + 1 else it.correctCount,
                earnedXp = if (isCorrect) it.earnedXp + 2 else it.earnedXp
            )
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex >= state.questions.lastIndex) {
            if (state.isCampaignMode) {
                viewModelScope.launch {
                    preferences.advanceCampaign()
                }
            }
            _uiState.update { it.copy(isFinished = true) }
        } else {
            val nextIndex = state.currentIndex + 1
            val nextQId = state.questions.getOrNull(nextIndex)?.id
            _uiState.update {
                it.copy(
                    currentIndex = nextIndex,
                    selectedOptionId = null,
                    showFeedback = false,
                    wasCorrect = false,
                    isCurrentFavorite = nextQId != null && it.favorites.contains(nextQId)
                )
            }
        }
    }
}
