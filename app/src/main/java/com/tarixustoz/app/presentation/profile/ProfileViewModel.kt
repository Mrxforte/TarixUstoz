package com.tarixustoz.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavedItemDto(
    val title: String,
    val subtitle: String
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val database: AppDatabase
) : ViewModel() {

    val displayName: StateFlow<String> = preferencesRepository.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Azizbek")

    val userBio: StateFlow<String> = preferencesRepository.userBio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Tarix bilimdoni • DTM tayyorgarlik")

    val userAvatar: StateFlow<String> = preferencesRepository.userAvatar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "AS")

    val userPhone: StateFlow<String> = preferencesRepository.userPhone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "+998 90 123 45 67")

    val userTarget: StateFlow<String> = preferencesRepository.userTarget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DTM 189 ball • O‘zbekiston tarixi")

    val userXp: StateFlow<Long> = preferencesRepository.userXp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 78L)

    val userStreak: StateFlow<Int> = preferencesRepository.userStreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val selectedGrade: StateFlow<Int> = preferencesRepository.selectedGrade
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 6)

    val levelTitle: StateFlow<String> = preferencesRepository.userXp.map { xp ->
        when {
            xp < 100 -> "1-daraja • Boshlang'ich"
            xp < 300 -> "2-daraja • Izlanuvchi"
            xp < 600 -> "3-daraja • Bilimdon"
            xp < 1000 -> "4-daraja • Tarixchi"
            else -> "5-daraja • Ustoz"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "1-daraja • Boshlang'ich")

    val totalSolvedCount: StateFlow<Int> = database.quizAttemptDao().getTotalCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val correctAnswersCount: StateFlow<Int> = database.quizAttemptDao().getCorrectCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val accuracyPercentage: StateFlow<Int> = combine(
        database.quizAttemptDao().getCorrectCountFlow(),
        database.quizAttemptDao().getTotalCountFlow()
    ) { correct, total ->
        if (total > 0) (correct * 100) / total else 100
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

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

    val wrongQuestionsCount: StateFlow<Int> = database.wrongQuestionDao().getAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedItemsList: StateFlow<List<SavedItemDto>> = combine(
        database.wrongQuestionDao().getAll(),
        preferencesRepository.favorites
    ) { wrongQs, favs ->
        val list = mutableListOf<SavedItemDto>()
        if (wrongQs.isNotEmpty()) {
            list.add(SavedItemDto("Xato ishlangan testlar", "${wrongQs.size} ta xatolar ustida ishlash saqlangan"))
        }
        if (favs.isNotEmpty()) {
            list.add(SavedItemDto("Tanlangan savollar", "${favs.size} ta savol sevimlilarga qo'shilgan"))
        }
        if (list.isEmpty()) {
            list.add(SavedItemDto("6-sinf Qadimgi dunyo tarixi", "Barcha 1100 ta savollar to'plami va darslik"))
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
