package com.tarixustoz.app.presentation.tests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.tarixustoz.app.data.repository.ContentRepository
import com.tarixustoz.app.data.repository.UserPreferencesRepository
import com.tarixustoz.app.domain.model.Topic
import com.tarixustoz.app.presentation.tests.TestCardItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Question counts for the "Yo'nalishlar bo'yicha testlar" cards, straight from the backend. */
@HiltViewModel
class TestsViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val _directionCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val directionCounts: StateFlow<Map<String, Int>> = _directionCounts

    val testsDirectionCards = MutableStateFlow<List<TestCardItem>>(emptyList())
    val testsTopicCards = MutableStateFlow<List<TestCardItem>>(emptyList())
    val testsGradeCards = MutableStateFlow<List<TestCardItem>>(emptyList())

    val allTopics: StateFlow<List<Topic>> = contentRepository.getAllTopicsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var loadedGrade = -1

    fun loadCounts(grade: Int) {
        if (grade == loadedGrade && _directionCounts.value.isNotEmpty()) return
        loadedGrade = grade
        viewModelScope.launch {
            val counts = contentRepository.fetchDirectionCounts(grade)
            _directionCounts.value = counts
            
            val prefsSnapshot = preferences.getAllPreferences()
            
            // Build Direction Cards
            val directionData = listOf(
                Triple("EVENT", "Tarixiy Hodisalar", "Qadimgi va o'rta asrlar"),
                Triple("PERSON", "Tarixiy Shaxslar", "Buyuk siymolar"),
                Triple("YEAR", "Yillar va Sanalar", "Xronologik tartib"),
                Triple("WAR", "Urushlar va Janglar", "Harbiy yurishlar"),
                Triple("CITY", "Shahar va Hududlar", "Joylashuvlar"),
                Triple("LOGICAL", "Mantiqiy va DTM", "DTM savollari")
            )
            val directions = directionData.map { (key, title, subtitle) ->
                val totalQ = counts[key] ?: 300
                val totalT = (totalQ + 29) / 30
                val subText = "$subtitle • $totalT ta bilet • $totalQ ta test"
                val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, key).passed.size
                TestCardItem(
                    id = key,
                    title = title,
                    subtitle = subText,
                    totalTickets = totalT,
                    passedTickets = passed,
                    icon = Icons.Default.Event,
                    gradientColors = listOf(Color(0xFF1E1B4B), Color(0xFF3730A3)),
                    accentColor = Color(0xFF10B981),
                    categoryType = "direction"
                )
            }
            testsDirectionCards.value = directions
            
            // Build Topic Cards
            val topics = contentRepository.getAllTopicsFlow().first().take(4).map { t ->
                val tCount = if (t.questionsCount > 0) t.questionsCount else 30
                val totalT = (tCount + 29) / 30
                val subText = "${t.summary.ifBlank { "Mavzular to'plami" }} • $totalT ta bilet • $tCount ta test"
                val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, t.id).passed.size
                TestCardItem(
                    id = t.id,
                    title = t.title,
                    subtitle = subText,
                    totalTickets = totalT,
                    passedTickets = passed,
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    gradientColors = listOf(Color(0xFF854D0E), Color(0xFFA16207)),
                    accentColor = Color(0xFF10B981),
                    categoryType = "topic"
                )
            }
            testsTopicCards.value = topics

            // Build Grade Cards (Separate Uzb and Jahon)
            val grades = mutableListOf<TestCardItem>()
            for (g in 6..11) {
                // O'zbekiston Tarixi
                val uzbBookId = "uz_$g"
                val uzbBook = contentRepository.getTextbookById(uzbBookId)
                if (uzbBook != null) {
                    val totalQs = uzbBook.totalQuestions.coerceAtLeast(100)
                    val totalT = (totalQs + 29) / 30
                    val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, "book_$uzbBookId").passed.size
                    grades.add(
                        TestCardItem(
                            id = "book_$uzbBookId",
                            title = "$g-Sinf O'zbekiston Tarixi",
                            subtitle = uzbBook.title,
                            totalTickets = totalT,
                            passedTickets = passed,
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                            accentColor = Color(0xFF3B82F6),
                            categoryType = "grade"
                        )
                    )
                }
                
                // Jahon Tarixi (only for 7-11)
                if (g > 6) {
                    val jahonBookId = "jahon_$g"
                    val jahonBook = contentRepository.getTextbookById(jahonBookId)
                    if (jahonBook != null) {
                        val totalQs = jahonBook.totalQuestions.coerceAtLeast(100)
                        val totalT = (totalQs + 29) / 30
                        val passed = preferences.getTicketProgressFromPrefs(prefsSnapshot, "book_$jahonBookId").passed.size
                        grades.add(
                            TestCardItem(
                                id = "book_$jahonBookId",
                                title = "$g-Sinf Jahon Tarixi",
                                subtitle = jahonBook.title,
                                totalTickets = totalT,
                                passedTickets = passed,
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                gradientColors = listOf(Color(0xFF064E3B), Color(0xFF065F46)),
                                accentColor = Color(0xFF10B981),
                                categoryType = "grade"
                            )
                        )
                    }
                }
            }
            testsGradeCards.value = grades
        }
    }
}
