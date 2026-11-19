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
import com.tarixustoz.app.domain.model.gradeId
import com.tarixustoz.app.presentation.common.components.backgroundResForBook
import com.tarixustoz.app.presentation.common.components.backgroundResForDirection
import com.tarixustoz.app.presentation.common.components.backgroundResForTopic
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

    fun loadCounts(grade: Int, forceRefresh: Boolean = false) {
        if (!forceRefresh && grade == loadedGrade && _directionCounts.value.isNotEmpty() && testsGradeCards.value.isNotEmpty()) return
        loadedGrade = grade
        viewModelScope.launch {
            contentRepository.seedIfNeeded()
            if (forceRefresh) {
                contentRepository.syncWithDjangoBackend(force = true)
            } else {
                contentRepository.syncWithDjangoBackend(force = false)
            }

            val counts = contentRepository.fetchDirectionCounts(grade, forceRefresh = forceRefresh)
            val fallbackDefaults = contentRepository.defaultDirectionCounts(grade)
            _directionCounts.value = counts

            val prefsSnapshot = preferences.getAllPreferences()

            // Build Direction Cards
            val directionData = listOf(
                Triple("EVENT", "Tarixiy Hodisalar", "Qadimgi va o'rta asrlar"),
                Triple("PERSON", "Tarixiy Shaxslar", "Buyuk siymolar"),
                Triple("YEAR", "Yillar va Sanalar", "Xronologik tartib"),
                Triple("WAR", "Urushlar va Janglar", "Harbiy yurishlar"),
                Triple("CITY", "Shahar va Hududlar", "Joylashuvlar"),
                Triple("FACT", "Tarixiy Faktlar", "Manbalar va obidalar"),
                Triple("LOGICAL", "Mantiqiy va DTM", "DTM savollari")
            )
            val directions = directionData.map { (key, title, subtitle) ->
                val totalQ = counts[key]?.takeIf { it > 0 } ?: fallbackDefaults[key] ?: 100_000
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
                    categoryType = "direction",
                    backgroundImageRes = backgroundResForDirection(key)
                )
            }
            testsDirectionCards.value = directions

            // Build Topic Cards
            val allDbTopics = contentRepository.getAllTopicsFlow().first()
            val filteredTopics = if (grade in 6..11) {
                allDbTopics.filter { it.textbookId.endsWith("_$grade") }
            } else {
                allDbTopics
            }
            val topics = filteredTopics.take(12).map { t ->
                val tCount = if (t.questionsCount > 0) t.questionsCount else 2500
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
                    categoryType = "topic",
                    backgroundImageRes = backgroundResForTopic(t.title, t.textbookId, t.gradeId)
                )
            }
            testsTopicCards.value = topics

            // Build Grade Cards (Separate Uzb/Qadimgi and Jahon, 100,000 tests = 3,334 tickets each)
            val grades = mutableListOf<TestCardItem>()
            val targetGrades = if (grade in 6..11) listOf(grade) else (6..11).toList()
            for (g in targetGrades) {
                val uzbBookId = "uz_$g"
                val uzbBook = contentRepository.getTextbookById(uzbBookId)
                val uzbTitle = if (g == 6) "6-Sinf Qadimgi Dunyo Tarixi" else "$g-Sinf O'zbekiston Tarixi"
                val uzbQs = uzbBook?.totalQuestions?.takeIf { it > 0 } ?: 100_000
                val uzbTickets = (uzbQs + 29) / 30
                val uzbPassed = preferences.getTicketProgressFromPrefs(prefsSnapshot, "book_$uzbBookId").passed.size
                grades.add(
                    TestCardItem(
                        id = "book_$uzbBookId",
                        title = uzbTitle,
                        subtitle = "${uzbBook?.title ?: uzbTitle} • $uzbTickets ta bilet • $uzbQs ta test",
                        totalTickets = uzbTickets,
                        passedTickets = uzbPassed,
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        gradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B)),
                        accentColor = Color(0xFF3B82F6),
                        categoryType = "grade",
                        backgroundImageRes = backgroundResForBook(uzbBookId, g, "uzbekiston")
                    )
                )

                // Jahon Tarixi (only for 7-11)
                if (g > 6) {
                    val jahonBookId = "jahon_$g"
                    val jahonBook = contentRepository.getTextbookById(jahonBookId)
                    val jahonTitle = "$g-Sinf Jahon Tarixi"
                    val jahonQs = jahonBook?.totalQuestions?.takeIf { it > 0 } ?: 100_000
                    val jahonTickets = (jahonQs + 29) / 30
                    val jahonPassed = preferences.getTicketProgressFromPrefs(prefsSnapshot, "book_$jahonBookId").passed.size
                    grades.add(
                        TestCardItem(
                            id = "book_$jahonBookId",
                            title = jahonTitle,
                            subtitle = "${jahonBook?.title ?: jahonTitle} • $jahonTickets ta bilet • $jahonQs ta test",
                            totalTickets = jahonTickets,
                            passedTickets = jahonPassed,
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            gradientColors = listOf(Color(0xFF064E3B), Color(0xFF065F46)),
                            accentColor = Color(0xFF10B981),
                            categoryType = "grade",
                            backgroundImageRes = backgroundResForBook(jahonBookId, g, "jahon")
                        )
                    )
                }
            }
            testsGradeCards.value = grades
        }
    }
}
