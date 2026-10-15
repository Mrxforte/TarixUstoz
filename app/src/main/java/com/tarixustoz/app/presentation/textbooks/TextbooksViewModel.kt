package com.tarixustoz.app.presentation.textbooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Textbook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TextbooksViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val _selectedGrade = MutableStateFlow(7)
    val selectedGrade: StateFlow<Int> = _selectedGrade

    val textbooks: StateFlow<List<Textbook>> = _selectedGrade
        .flatMapLatest { grade -> contentRepository.getTextbooksByGrade(grade) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load() {
        viewModelScope.launch {
            contentRepository.seedIfNeeded()
            preferences.selectedGrade.collect { _selectedGrade.value = it }
        }
    }

    fun setGrade(grade: Int) {
        _selectedGrade.value = grade
        viewModelScope.launch { preferences.setSelectedGrade(grade) }
    }
}
