package com.tarixustoz.app.presentation.home

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeCategoryItem(
    val id: String,
    val title: String,
    val countLabel: String,
    val iconType: String,
    val count: Int
)

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
    val categories: List<HomeCategoryItem> = listOf(
        HomeCategoryItem("shahar_nomlari", "Shahar nomlari", "120 test", "location", 120),
        HomeCategoryItem("tarixiy_shaxslar", "Tarixiy shaxslar", "184 test", "person", 184),
        HomeCategoryItem("mavzu_boyicha", "Mavzu bo‘yicha", "320 test", "topic", 320),
        HomeCategoryItem("yillar_boyicha", "Yillar bo‘yicha", "215 test", "calendar", 215)
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

    private val seenQuestionIds = mutableSetOf<String>()

    fun load() {
        viewModelScope.launch {
            contentRepository.seedIfNeeded()
            if (_uiState.value.dailyQuestion == null) {
                loadNextDailyQuestion()
            }
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
    }

    fun loadNextDailyQuestion() {
        viewModelScope.launch {
            val nextQuestion = contentRepository.getDailyQuestion(seenQuestionIds)
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
                val userAnswerText = question.options.find { it.id == optionId }?.text ?: optionId
                mistakesRepository.recordIncorrect(question, userAnswerText)
            }
        }

        _uiState.update {
            it.copy(
                selectedDailyOptionId = optionId,
                isDailyAnswered = true,
                isDailyCorrect = isCorrect,
                dailyAnswerFeedback = if (isCorrect) {
                    "Barakalla! To‘g‘ri javob! +10 XP berildi 🎉"
                } else {
                    "Noto‘g‘ri javob. To‘g‘ri javob belgilandi."
                },
                todayGoalCompleted = if (isCorrect) it.todayGoalCompleted + 1 else it.todayGoalCompleted
            )
        }
    }
}
