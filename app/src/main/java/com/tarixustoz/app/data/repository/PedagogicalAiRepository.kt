package com.tarixustoz.app.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.data.local.QuestionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PedagogicalAiRepo"
private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L

enum class PedagogicalActionType {
    MISTAKES,
    TESTS,
    TEXTBOOKS,
    DAILY_QUIZ;

    companion object {
        fun fromRaw(raw: String?): PedagogicalActionType = when (raw?.uppercase()?.trim()) {
            "MISTAKES" -> MISTAKES
            "TEXTBOOKS" -> TEXTBOOKS
            "DAILY_QUIZ" -> DAILY_QUIZ
            else -> TESTS
        }
    }
}

enum class PedagogicalTone {
    WARNING,
    STRENGTH,
    STRATEGY,
    PRACTICE;

    companion object {
        fun fromRaw(raw: String?): PedagogicalTone = when (raw?.uppercase()?.trim()) {
            "WARNING" -> WARNING
            "STRENGTH" -> STRENGTH
            "PRACTICE" -> PRACTICE
            else -> STRATEGY
        }
    }
}

@Serializable
data class PedagogicalRecommendation(
    val id: String,
    val badge: String,
    val title: String,
    val summary: String,
    val pedagogicalTip: String,
    val focusArea: String,
    val actionType: String,
    val tone: String
) {
    val parsedActionType: PedagogicalActionType get() = PedagogicalActionType.fromRaw(actionType)
    val parsedTone: PedagogicalTone get() = PedagogicalTone.fromRaw(tone)
}

@Serializable
data class PedagogicalReport(
    val overallAssessment: String,
    val generatedAtMs: Long,
    val statsSignature: String,
    val recommendations: List<PedagogicalRecommendation>
)

sealed interface PedagogicalAiState {
    data object Idle : PedagogicalAiState
    data object Loading : PedagogicalAiState
    data class Success(val report: PedagogicalReport) : PedagogicalAiState
    data class Error(val message: String) : PedagogicalAiState
}

@Singleton
class PedagogicalAiRepository @Inject constructor(
    private val database: AppDatabase,
    private val preferences: UserPreferencesRepository
) {
    private val jsonCodec = Json { ignoreUnknownKeys = true }
    private val requestMutex = Mutex()

    private val recommendationItemSchema = Schema.obj(
        properties = mapOf(
            "id" to Schema.string(description = "Unique short snake_case identifier"),
            "badge" to Schema.string(description = "Short 1-2 word Uzbek label e.g. Xatolar tahlili, O'tilgan mavzu, Strategiya, Takrorlash"),
            "title" to Schema.string(description = "Concise Uzbek pedagogical heading, max 58 characters"),
            "summary" to Schema.string(description = "Short 1-2 sentence Uzbek summary referencing user performance"),
            "pedagogicalTip" to Schema.string(description = "Practical 2-3 sentence Uzbek teaching recommendation explaining how to master the topic or fix the mistakes"),
            "focusArea" to Schema.string(description = "Specific grade, subject, historical topic, or skill"),
            "actionType" to Schema.enumeration(
                values = listOf("MISTAKES", "TESTS", "TEXTBOOKS", "DAILY_QUIZ"),
                description = "Recommended section in the app for the next step"
            ),
            "tone" to Schema.enumeration(
                values = listOf("WARNING", "STRENGTH", "STRATEGY", "PRACTICE"),
                description = "Pedagogical visual tone"
            )
        )
    )

    private val reportSchema = Schema.obj(
        properties = mapOf(
            "overallAssessment" to Schema.string(
                description = "2 sentence encouraging yet realistic Uzbek pedagogical overview of solved topics and mistakes"
            ),
            "recommendations" to Schema.array(
                items = recommendationItemSchema,
                description = "3 to 5 varied, actionable Uzbek pedagogical recommendations"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = reportSchema
                temperature = 0.65f
            }
        )
    }

    private val _state = MutableStateFlow<PedagogicalAiState>(PedagogicalAiState.Idle)
    val state: StateFlow<PedagogicalAiState> = _state.asStateFlow()

    suspend fun generateRecommendations(forceRefresh: Boolean = false) {
        requestMutex.withLock {
            try {
                val contextSnapshot = withContext(Dispatchers.IO) {
                    buildLearnerAnalyticsSnapshot()
                }

                if (!forceRefresh) {
                    val currentSuccess = _state.value as? PedagogicalAiState.Success
                    if (currentSuccess != null &&
                        currentSuccess.report.statsSignature == contextSnapshot.signature &&
                        System.currentTimeMillis() - currentSuccess.report.generatedAtMs < CACHE_TTL_MS
                    ) {
                        return
                    }

                    val cachedJson = preferences.getCachedPedagogicalReportJson()
                    if (!cachedJson.isNullOrBlank()) {
                        val cachedReport = runCatching {
                            jsonCodec.decodeFromString<PedagogicalReport>(cachedJson)
                        }.getOrNull()
                        if (cachedReport != null &&
                            cachedReport.statsSignature == contextSnapshot.signature &&
                            System.currentTimeMillis() - cachedReport.generatedAtMs < CACHE_TTL_MS &&
                            cachedReport.recommendations.isNotEmpty()
                        ) {
                            _state.value = PedagogicalAiState.Success(cachedReport)
                            return
                        }
                    }
                }

                _state.value = PedagogicalAiState.Loading

                val prompt = buildPedagogicalPrompt(contextSnapshot)
                val response = withTimeout(60_000L) {
                    model.generateContent(prompt)
                }

                val jsonString = response.text?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Gemini bo‘sh javob qaytardi.")

                val root = JSONObject(jsonString)
                val overallAssessment = root.optString("overallAssessment").ifBlank {
                    "Natijalaringiz va xatolaringiz asosida shaxsiy o‘quv tavsiyalari tayyorlandi."
                }
                val recsArray = root.getJSONArray("recommendations")
                val parsedRecommendations = buildList {
                    for (i in 0 until recsArray.length()) {
                        val item = recsArray.optJSONObject(i) ?: continue
                        val title = item.optString("title").trim()
                        val summary = item.optString("summary").trim()
                        val tip = item.optString("pedagogicalTip").trim()
                        if (title.isBlank() || (summary.isBlank() && tip.isBlank())) continue
                        add(
                            PedagogicalRecommendation(
                                id = "ai_rec_${i}_" + item.optString("id")
                                    .ifBlank { "tip_$i" }
                                    .replace(Regex("[^a-zA-Z0-9_]"), "_"),
                                badge = item.optString("badge").ifBlank { "AI Tavsiya" },
                                title = title,
                                summary = summary.ifBlank { tip },
                                pedagogicalTip = tip.ifBlank { summary },
                                focusArea = item.optString("focusArea").ifBlank { "${contextSnapshot.grade}-sinf tarix" },
                                actionType = item.optString("actionType").ifBlank { "TESTS" },
                                tone = item.optString("tone").ifBlank { "STRATEGY" }
                            )
                        )
                    }
                }

                if (parsedRecommendations.isEmpty()) {
                    throw IllegalStateException("AI tavsiyalari ro‘yxati shakllanmadi.")
                }

                val report = PedagogicalReport(
                    overallAssessment = overallAssessment,
                    generatedAtMs = System.currentTimeMillis(),
                    statsSignature = contextSnapshot.signature,
                    recommendations = parsedRecommendations
                )

                preferences.saveCachedPedagogicalReportJson(jsonCodec.encodeToString(report))
                _state.value = PedagogicalAiState.Success(report)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini pedagogical generation timed out", e)
                _state.value = PedagogicalAiState.Error(
                    "AI tahlili 60 soniya ichida javob bermadi. Internet aloqasini tekshirib qayta urinib ko‘ring."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate Gemini pedagogical recommendations", e)
                _state.value = PedagogicalAiState.Error(
                    e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Gemini AI tahlilini yuklashda xatolik yuz berdi."
                )
            }
        }
    }

    private data class LearnerAnalyticsSnapshot(
        val signature: String,
        val displayName: String,
        val grade: Int,
        val role: String,
        val target: String,
        val xp: Long,
        val streak: Int,
        val totalSolved: Int,
        val totalCorrect: Int,
        val accuracyPercent: Int,
        val abandonedQuizSummary: String?,
        val strongAreas: List<String>,
        val weakAreas: List<String>,
        val recentMistakeDetails: List<String>,
        val passedTicketsSummary: List<String>
    )

    private suspend fun buildLearnerAnalyticsSnapshot(): LearnerAnalyticsSnapshot {
        val displayName = preferences.displayName.first()
        val grade = preferences.selectedGrade.first()
        val role = preferences.userRole.first()
        val target = preferences.userTarget.first()
        val xp = preferences.userXp.first()
        val streak = preferences.userStreak.first()
        val prefSolved = preferences.totalSolvedTests.first()
        val prefCorrect = preferences.totalCorrectAnswers.first()
        val activeQuiz = preferences.activeQuizState.first()

        val allAttempts = database.quizAttemptDao().getAllFlow().first()
        val wrongQuestions = database.wrongQuestionDao().getAll().first()
            .filter { it.masteryStatus != "MASTERED" }
        val topicTitles = database.topicDao().getAllOnce().associateBy({ it.id }, { it.title })

        val totalSolved = maxOf(allAttempts.size, prefSolved)
        val totalCorrect = maxOf(allAttempts.count { it.wasCorrect }, prefCorrect).coerceAtMost(totalSolved)
        val accuracyPercent = if (totalSolved > 0) (totalCorrect * 100) / totalSolved else 0

        val questionCache = mutableMapOf<String, QuestionEntity?>()
        suspend fun loadQuestion(id: String): QuestionEntity? {
            if (!questionCache.containsKey(id)) {
                questionCache[id] = database.questionDao().getById(id)
            }
            return questionCache[id]
        }

        // Aggregate recent attempts by topic / grade+subject / category
        val topicStats = mutableMapOf<String, Pair<Int, Int>>() // label -> (correct, total)
        val categoryStats = mutableMapOf<String, Pair<Int, Int>>()

        for (attempt in allAttempts.take(150)) {
            val q = loadQuestion(attempt.questionId)
            val topicTitle = q?.let { topicTitles[it.topicId] }
            val areaLabel = when {
                !topicTitle.isNullOrBlank() -> "${q.gradeId}-sinf: $topicTitle"
                q != null -> "${q.gradeId}-sinf (${subjectLabel(q.subjectId)})"
                !attempt.sessionId.isNullOrBlank() -> attempt.sessionId.removePrefix("quiz/")
                else -> null
            }
            if (areaLabel != null) {
                val prev = topicStats[areaLabel] ?: (0 to 0)
                topicStats[areaLabel] = (prev.first + if (attempt.wasCorrect) 1 else 0) to (prev.second + 1)
            }
            if (q != null && q.category.isNotBlank() && q.category != "ALL") {
                val catLabel = directionLabel(q.category)
                val prev = categoryStats[catLabel] ?: (0 to 0)
                categoryStats[catLabel] = (prev.first + if (attempt.wasCorrect) 1 else 0) to (prev.second + 1)
            }
        }

        val strongAreas = (topicStats.entries + categoryStats.entries)
            .filter { (_, stats) -> stats.second >= 2 && (stats.first * 100 / stats.second) >= 70 }
            .sortedByDescending { it.value.first * 100 / it.value.second }
            .take(5)
            .map { (name, stats) -> "$name (${stats.first}/${stats.second} to‘g‘ri, ${stats.first * 100 / stats.second}%)" }

        val weakAreas = (topicStats.entries + categoryStats.entries)
            .filter { (_, stats) -> stats.second >= 1 && (stats.first * 100 / stats.second) < 70 }
            .sortedBy { it.value.first * 100 / it.value.second }
            .take(5)
            .map { (name, stats) -> "$name (${stats.first}/${stats.second} to‘g‘ri, ${stats.first * 100 / stats.second}%)" }

        val recentMistakeDetails = wrongQuestions.take(8).mapNotNull { wrong ->
            val q = loadQuestion(wrong.questionId) ?: return@mapNotNull null
            val topicTitle = topicTitles[q.topicId] ?: subjectLabel(q.subjectId)
            val dateNote = q.historicalDate?.takeIf { it.isNotBlank() }?.let { " | Sana: $it" } ?: ""
            val personNote = q.personality?.takeIf { it.isNotBlank() }?.let { " | Shaxs: $it" } ?: ""
            "${q.gradeId}-sinf ($topicTitle$dateNote$personNote): \"${q.text.take(110)}\" (${wrong.incorrectCount} marta xato)"
        }

        val prefsSnapshot = preferences.getAllPreferences()
        val trackedCategories = buildList {
            add("EVENT" to "Tarixiy hodisalar")
            add("PERSON" to "Tarixiy shaxslar")
            add("YEAR" to "Yillar va sanalar")
            add("WAR" to "Urushlar va janglar")
            add("CITY" to "Shahar va hududlar")
            add("FACT" to "Tarixiy faktlar")
            add("LOGICAL" to "Mantiqiy va DTM")
            for (g in 6..11) {
                add("book_uz_$g" to if (g == 6) "6-sinf Qadimgi dunyo" else "$g-sinf O‘zbekiston tarixi")
                if (g > 6) add("book_jahon_$g" to "$g-sinf Jahon tarixi")
            }
        }
        val passedTicketsSummary = trackedCategories.mapNotNull { (key, label) ->
            val snap = preferences.getTicketProgressFromPrefs(prefsSnapshot, key)
            if (snap.passed.isNotEmpty() || snap.scores.isNotEmpty()) {
                val best = snap.scores.values.maxOrNull() ?: 0
                "$label: ${snap.passed.size} ta bilet topshirilgan (eng yuqori ball: $best/30)"
            } else null
        }.take(6)

        val abandonedQuizSummary = activeQuiz?.takeIf { it.questions.isNotEmpty() }?.let { quiz ->
            "\"${quiz.quizTitle}\" (${quiz.currentIndex + 1}/${quiz.questions.size}-savolda to‘xtab qolgan)"
        }

        val signature = listOf(
            grade,
            role,
            totalSolved,
            totalCorrect,
            wrongQuestions.size,
            wrongQuestions.take(3).joinToString(",") { it.questionId },
            abandonedQuizSummary.orEmpty(),
            passedTicketsSummary.size
        ).joinToString("|")

        return LearnerAnalyticsSnapshot(
            signature = signature,
            displayName = displayName,
            grade = grade,
            role = role,
            target = target,
            xp = xp,
            streak = streak,
            totalSolved = totalSolved,
            totalCorrect = totalCorrect,
            accuracyPercent = accuracyPercent,
            abandonedQuizSummary = abandonedQuizSummary,
            strongAreas = strongAreas,
            weakAreas = weakAreas,
            recentMistakeDetails = recentMistakeDetails,
            passedTicketsSummary = passedTicketsSummary
        )
    }

    private fun buildPedagogicalPrompt(s: LearnerAnalyticsSnapshot): String {
        return buildString {
            appendLine("Sen tajribali O‘zbekiston va jahon tarixi pedagogi hamda mentorisan.")
            appendLine("Quyidagi o‘quvchining haqiqiy o‘quv statistikasi, yechgan testlari (o‘tilgan mavzulari) va yecholmagan/xato savollari asosida O‘ZBEK TILIDA (lotin alifbosida) 4 ta har xil, aniq va foydali pedagogik tavsiya tayyorla.")
            appendLine()
            appendLine("O‘QUVCHI MA’LUMOTLARI:")
            appendLine("- Ismi: ${s.displayName}")
            appendLine("- Maqomi: ${s.role}, Tanlangan sinf: ${s.grade}-sinf, Maqsadi: ${s.target}")
            appendLine("- XP: ${s.xp}, Kunlik seriya: ${s.streak} kun")
            appendLine("- Jami yechilgan testlar: ${s.totalSolved} ta, To‘g‘ri: ${s.totalCorrect} ta (Aniqlik: ${s.accuracyPercent}%)")
            appendLine("- Kunlik maqsad: 30 ta test")
            if (s.abandonedQuizSummary != null) {
                appendLine("- Tugallanmay tashlab ketilgan test: ${s.abandonedQuizSummary}")
            }
            appendLine()
            appendLine("O‘TILGAN MAVZULAR VA MUVAFFAQIYATLI YECHILGAN TESTLAR:")
            if (s.strongAreas.isNotEmpty()) {
                s.strongAreas.forEach { appendLine("  * $it") }
            } else {
                appendLine("  * Hozircha yetarli muvaffaqiyatli mavzu statistikasi to‘planmagan.")
            }
            if (s.passedTicketsSummary.isNotEmpty()) {
                appendLine("BILET NATIJALARI:")
                s.passedTicketsSummary.forEach { appendLine("  * $it") }
            }
            appendLine()
            appendLine("QIYINCHILIK TUG‘DIRGAN MAVZULAR VA YECHOLMAGAN/XATO SAVOLLAR:")
            if (s.weakAreas.isNotEmpty()) {
                s.weakAreas.forEach { appendLine("  * $it") }
            }
            if (s.recentMistakeDetails.isNotEmpty()) {
                s.recentMistakeDetails.forEach { appendLine("  * $it") }
            } else if (s.weakAreas.isEmpty()) {
                appendLine("  * Hozircha faol xatolar qayd etilmagan (yoki endi boshlagan).")
            }
            appendLine()
            appendLine("TALABLAR:")
            appendLine("1. Tavsiyalar faqat umumiy gap emas, balki tarix fani pedagogikasiga mos amaliy usullar bo‘lsin (xronologiya jadvali, sabab-oqibat zanjiri, shaxslar va sulolalarni taqqoslash, manba bilan ishlash, takrorlash oralig‘i).")
            appendLine("2. Har bir tavsiya o‘quvchining yechgan va yecholmagan testlariga tayansin; agar hali kam test yechgan bo‘lsa, ${s.grade}-sinf va ${s.target} uchun boshlang‘ich pedagogik yo‘l xaritasi ber.")
            appendLine("3. Har xil toifalarni qamrab ol: kamida 1 ta xatolar/yecholmagan testlar bo‘yicha (tone=WARNING, actionType=MISTAKES), 1 ta o‘tilgan kuchli mavzular/takrorlash bo‘yicha (tone=STRENGTH yoki PRACTICE), 1 ta o‘qish strategiyasi (tone=STRATEGY).")
            appendLine("4. Barcha matn sof, ravon va chiroyli o‘zbek lotin yozuvida bo‘lsin.")
        }
    }

    private fun subjectLabel(subjectId: String): String = when {
        subjectId.contains("jahon", ignoreCase = true) -> "Jahon tarixi"
        else -> "O‘zbekiston tarixi"
    }

    private fun directionLabel(code: String): String = when (code.uppercase()) {
        "EVENT" -> "Tarixiy voqealar"
        "PERSON" -> "Tarixiy shaxslar"
        "YEAR" -> "Yillar va sanalar"
        "WAR" -> "Urushlar va janglar"
        "CITY" -> "Shahar va hududlar"
        "FACT" -> "Tarixiy faktlar"
        "LOGICAL" -> "Mantiqiy va DTM"
        else -> code
    }
}
