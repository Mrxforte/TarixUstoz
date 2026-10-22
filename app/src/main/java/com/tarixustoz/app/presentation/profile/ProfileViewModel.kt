package com.tarixustoz.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val displayName: StateFlow<String> = preferencesRepository.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Azizbek")

    val userBio: StateFlow<String> = preferencesRepository.userBio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Tarix bilimdoni • DTM tayyorgarlik")

    val userAvatar: StateFlow<String> = preferencesRepository.userAvatar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AS")

    val userXp: StateFlow<Long> = preferencesRepository.userXp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 78L)

    val userStreak: StateFlow<Int> = preferencesRepository.userStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val selectedGrade: StateFlow<Int> = preferencesRepository.selectedGrade
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val dailyReminder: StateFlow<Boolean> = preferencesRepository.dailyReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val mistakeReminder: StateFlow<Boolean> = preferencesRepository.mistakeReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val newTestsReminder: StateFlow<Boolean> = preferencesRepository.newTestsReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val soundEnabled: StateFlow<Boolean> = preferencesRepository.soundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val reminderTime: StateFlow<String> = preferencesRepository.reminderTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "08:30")

    fun updateProfile(name: String, bio: String, avatar: String, grade: Int) {
        viewModelScope.launch {
            preferencesRepository.setDisplayName(name.trim().ifEmpty { "Azizbek" })
            preferencesRepository.setUserBio(bio.trim())
            preferencesRepository.setUserAvatar(avatar)
            preferencesRepository.setSelectedGrade(grade)
        }
    }

    fun setDailyReminder(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDailyReminder(enabled) }
    }

    fun setMistakeReminder(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMistakeReminder(enabled) }
    }

    fun setNewTestsReminder(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNewTestsReminder(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSoundEnabled(enabled) }
    }

    fun setReminderTime(time: String) {
        viewModelScope.launch { preferencesRepository.setReminderTime(time) }
    }
}
