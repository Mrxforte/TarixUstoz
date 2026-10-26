package com.tarixustoz.app.presentation.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Question counts for the "Yo'nalishlar bo'yicha testlar" cards, straight from the backend. */
@HiltViewModel
class TestsViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _directionCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val directionCounts: StateFlow<Map<String, Int>> = _directionCounts

    private var loadedGrade = -1

    fun loadCounts(grade: Int) {
        if (grade == loadedGrade && _directionCounts.value.isNotEmpty()) return
        loadedGrade = grade
        viewModelScope.launch {
            // The all-grades total is summed per book on the server, so it is cached there too.
            _directionCounts.value = contentRepository.fetchDirectionCounts(grade)
        }
    }
}
