package com.tarixustoz.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val displayName: String = "O‘quvchi",
    val xp: Long = 0,
    val streak: Int = 0,
    val level: Int = 1,
    val uzProgress: Float = 0.15f,
    val worldProgress: Float = 0.08f,
    val overallProgress: Float = 0.12f,
    val mistakesCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val contentRepository: ContentRepository,
    private val mistakesRepository: MistakesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            contentRepository.seedIfNeeded()
        }
        viewModelScope.launch {
            combine(
                preferences.displayName,
                preferences.userXp,
                preferences.userStreak,
                mistakesRepository.getActiveCount()
            ) { name, xp, streak, mistakes ->
                HomeUiState(
                    displayName = name,
                    xp = xp,
                    streak = streak,
                    level = (xp / 500 + 1).toInt(),
                    mistakesCount = mistakes
                )
            }.collect { _uiState.value = it }
        }
    }
}
