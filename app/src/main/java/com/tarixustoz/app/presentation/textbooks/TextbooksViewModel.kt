package com.tarixustoz.app.presentation.textbooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Textbook
import com.tarixustoz.app.domain.model.Topic
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

    val allTopicsFlow: StateFlow<List<Topic>> = contentRepository.getAllTopicsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _topicCountsMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val topicCountsMap: StateFlow<Map<String, Int>> = _topicCountsMap

    init {
        viewModelScope.launch {
            _topicCountsMap.value = contentRepository.getTopicCountsMap()
        }
    }

    fun load() {
        viewModelScope.launch {
            _isRefreshing.value = true
            contentRepository.syncWithDjangoBackend()
            _topicCountsMap.value = contentRepository.getTopicCountsMap()
            _isRefreshing.value = false
        }
    }

    fun setGrade(grade: Int) {
        _selectedGrade.value = grade
        if (grade > 0) {
            viewModelScope.launch {
                _isRefreshing.value = true
                contentRepository.syncBooksForGrade(grade)
                _isRefreshing.value = false
            }
        }
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
