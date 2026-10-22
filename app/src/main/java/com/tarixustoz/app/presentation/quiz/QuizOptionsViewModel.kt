package com.tarixustoz.app.presentation.quiz

import androidx.lifecycle.ViewModel
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.domain.model.Topic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class QuizOptionsViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {
    val topics: Flow<List<Topic>> = contentRepository.getAllTopicsFlow()
}
