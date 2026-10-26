package com.tarixustoz.app.presentation.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.MistakesRepository
import com.tarixustoz.app.domain.model.AnswerOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class WrongQuestionUiModel(
    val questionId: String,
    val text: String,
    val options: List<AnswerOption>,
    val correctOption: String,
    val userAnswer: String,
    val explanation: String,
    val sourceCitation: String,
    val gradeId: Int,
    val subjectId: String,
    val incorrectCount: Int,
    val attemptCount: Int,
    val lastAttemptedMillis: Long,
    val formattedDate: String,
    val masteryStatus: String
)

enum class MistakesSortOrder(val label: String) {
    NEWEST("Eng yangilari"),
    OLDEST("Eng eskilari"),
    MOST_ERRORS("Eng ko'p xatolar")
}

enum class MistakesViewMode {
    LIST, GRID
}

data class MistakesUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedGradeFilter: Int = 0,
    val sortOrder: MistakesSortOrder = MistakesSortOrder.NEWEST,
    val viewMode: MistakesViewMode = MistakesViewMode.LIST,
    val items: List<WrongQuestionUiModel> = emptyList(),
    val totalCount: Int = 0
)

@HiltViewModel
class MistakesViewModel @Inject constructor(
    private val mistakesRepository: MistakesRepository,
    private val contentRepository: ContentRepository,
    private val db: AppDatabase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedGradeFilter = MutableStateFlow(0)
    private val _sortOrder = MutableStateFlow(MistakesSortOrder.NEWEST)
    private val _viewMode = MutableStateFlow(MistakesViewMode.LIST)

    val activeCount: StateFlow<Int> = mistakesRepository.getActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uiState: StateFlow<MistakesUiState> = combine(
        db.wrongQuestionDao().getAll(),
        _searchQuery,
        _selectedGradeFilter,
        _sortOrder,
        _viewMode
    ) { wrongEntities, query, gradeFilter, sort, viewMode ->
        val models = wrongEntities.mapNotNull { entity ->
            val q = contentRepository.getQuestionById(entity.questionId)
            if (q != null) {
                val dateStr = try {
                    val sdf = SimpleDateFormat("dd-MMM, HH:mm", Locale("uz"))
                    sdf.format(Date(entity.lastAttempted))
                } catch (_: Exception) { "Yaqinda" }

                val correctOpt = q.options.firstOrNull { it.isCorrect }?.id?.uppercase() ?: "A"

                WrongQuestionUiModel(
                    questionId = entity.questionId,
                    text = q.text,
                    options = q.options,
                    correctOption = correctOpt,
                    userAnswer = entity.userAnswer,
                    explanation = q.explanation.ifBlank { "Darslik ma'lumotiga ko'ra $correctOpt varianti to'g'ri." },
                    sourceCitation = q.sourceReference ?: "${q.gradeId}-sinf tarix",
                    gradeId = q.gradeId,
                    subjectId = q.subjectId,
                    incorrectCount = entity.incorrectCount,
                    attemptCount = entity.attemptCount,
                    lastAttemptedMillis = entity.lastAttempted,
                    formattedDate = dateStr,
                    masteryStatus = entity.masteryStatus
                )
            } else null
        }

        // Apply filtering
        var filtered = models

        if (query.isNotBlank()) {
            val qLower = query.trim().lowercase()
            filtered = filtered.filter {
                it.text.lowercase().contains(qLower) ||
                it.sourceCitation.lowercase().contains(qLower)
            }
        }

        if (gradeFilter > 0) {
            filtered = filtered.filter { it.gradeId == gradeFilter }
        }

        // Apply sorting
        filtered = when (sort) {
            MistakesSortOrder.NEWEST -> filtered.sortedByDescending { it.lastAttemptedMillis }
            MistakesSortOrder.OLDEST -> filtered.sortedBy { it.lastAttemptedMillis }
            MistakesSortOrder.MOST_ERRORS -> filtered.sortedByDescending { it.incorrectCount }
        }

        MistakesUiState(
            isLoading = false,
            searchQuery = query,
            selectedGradeFilter = gradeFilter,
            sortOrder = sort,
            viewMode = viewMode,
            items = filtered,
            totalCount = models.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MistakesUiState())

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onGradeFilterChange(grade: Int) {
        _selectedGradeFilter.value = grade
    }

    fun onSortOrderChange(sort: MistakesSortOrder) {
        _sortOrder.value = sort
    }

    fun onToggleViewMode() {
        _viewMode.value = if (_viewMode.value == MistakesViewMode.LIST) MistakesViewMode.GRID else MistakesViewMode.LIST
    }

    fun deleteMistake(questionId: String) {
        viewModelScope.launch {
            db.wrongQuestionDao().delete(questionId)
        }
    }

    fun clearAllMistakes() {
        viewModelScope.launch {
            db.wrongQuestionDao().deleteAll()
        }
    }
}
