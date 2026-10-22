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

    private val _selectedGrade = MutableStateFlow(0) // 0 = All grades
    val selectedGrade: StateFlow<Int> = _selectedGrade

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val textbooks: StateFlow<List<Textbook>> = _selectedGrade
        .flatMapLatest { grade ->
            if (grade == 0) contentRepository.getTextbooks()
            else contentRepository.getTextbooksByGrade(grade)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun load() {
        viewModelScope.launch {
            contentRepository.seedIfNeeded()
        }
    }

    fun setGrade(grade: Int) {
        _selectedGrade.value = grade
    }

    fun getTopicsForTextbook(textbookId: String) =
        contentRepository.getTopicsByTextbook(textbookId)

    fun resetAndReloadData(customJson: String? = null) {
        viewModelScope.launch {
            _isRefreshing.value = true
            contentRepository.resetAndReimport(customJson)
            _isRefreshing.value = false
        }
    }
}
