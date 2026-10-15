package com.tarixustoz.app.presentation.mistakes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarixustoz.app.data.local.WrongQuestionEntity
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.repository.MistakesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MistakesViewModel @Inject constructor(
    private val mistakesRepository: MistakesRepository,
    private val db: AppDatabase
) : ViewModel() {

    val activeCount: StateFlow<Int> = mistakesRepository.getActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val wrongEntities: StateFlow<List<WrongQuestionEntity>> = db.wrongQuestionDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
