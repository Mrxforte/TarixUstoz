package com.tarixustoz.app.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
data class Grade(
    val id: Int,
    val name: String,
    val displayName: String
)

@Immutable
data class Subject(
    val id: String,
    val name: String,
    val nameUz: String
)

@Immutable
data class Textbook(
    val id: String,
    val title: String,
    val gradeId: Int,
    val subjectId: String,
    val authors: String,
    val year: Int,
    val edition: String,
    val coverAsset: String? = null,
    val pdfAsset: String? = null,
    val totalChapters: Int = 0,
    val totalTopics: Int = 0,
    val totalQuestions: Int = 0,
    val completionPercent: Float = 0f,
    val accuracyPercent: Float = 0f
)

@Immutable
data class Chapter(
    val id: String,
    val textbookId: String,
    val number: Int,
    val title: String,
    val topicsCount: Int = 0
)

@Immutable
@Serializable
data class ImportantDate(
    val date: String,
    val event: String
)

@Immutable
@Serializable
data class HistoricalTerm(
    val term: String,
    val definition: String
)

@Immutable
data class Topic(
    val id: String,
    val chapterId: String,
    val textbookId: String,
    val number: Int,
    val title: String,
    val content: String = "",
    val importantDates: List<ImportantDate> = emptyList(),
    val terms: List<HistoricalTerm> = emptyList(),
    val summary: String = "",
    val imageAsset: String? = null,
    val imageCaption: String? = null,
    val questionsCount: Int = 0,
    val masteryPercent: Float = 0f,
    val isCompleted: Boolean = false
)

val Topic.gradeId: Int
    get() = when {
        textbookId == "uz_6" || textbookId.contains("6") -> 6
        textbookId.startsWith("uz_") -> textbookId.removePrefix("uz_").toIntOrNull() ?: 7
        textbookId.startsWith("jahon_") -> textbookId.removePrefix("jahon_").toIntOrNull() ?: 7
        else -> 7
    }

enum class QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_SELECT,
    TRUE_FALSE,
    MATCH_PERSONALITY,
    MATCH_DATE,
    CHRONOLOGY,
    FILL_BLANK,
    CAUSE_EFFECT,
    SOURCE_INTERPRETATION,
    ASSERTION_REASON
}

enum class QuestionCategory(val titleUz: String, val icon: String) {
    ALL("Barcha testlar", "🌐"),
    PERSON("Tarixiy Shaxslar", "👑"),
    CITY("Shaharlar va Hududlar", "🏛"),
    YEAR("Yillar va Sanalar", "📅"),
    EVENT("Hodisalar va Janglar", "⚔️"),
    LOGICAL("Mantiqiy va DTM", "🧩")
}

enum class Difficulty {
    EASY, MEDIUM, HARD, EXPERT
}

@Immutable
@Serializable
data class AnswerOption(
    val id: String,
    val text: String,
    val isCorrect: Boolean
)

fun String.cleanQuestionText(): String {
    return this
        .replace(Regex("Darslik\\s+ma'lumotlariga\\s+ko'ra\\s*\\(.*?\\),?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("Darslik\\s+ma'lumotlariga\\s+ko'ra.*?:\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\(?\\d+-sinf.*?\\d+-bet\\)?,?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b\\d+-bet\\b,?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\d+\\s+[IVX]+\\s+BOB\\..*?\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(6|7|8|9|10|11)-sinf.*?:\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(DTM|BMBA).*?:\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^Milliy\\s+[Ss]ertifikat.*?:\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^Attestatsiya.*?:\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^[A-Za-z0-9_\\s—–-]+mavzusiga\\s+oid:\\s*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\\""), "")
        .replace(Regex("\\\"\\?$"), "?")
        .trim()
}

@Immutable
data class Question(
    val id: String,
    val topicId: String,
    val textbookId: String,
    val gradeId: Int,
    val subjectId: String,
    val type: QuestionType,
    val difficulty: Difficulty,
    val category: QuestionCategory = QuestionCategory.ALL,
    val text: String,
    val options: List<AnswerOption>,
    val explanation: String,
    val historicalDate: String? = null,
    val personality: String? = null,
    val sourceReference: String? = null,
    val pageHint: String? = null
) {
    val cleanText: String
        get() = text.cleanQuestionText()
}

@Immutable
data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String?,
    val gradeId: Int,
    val xp: Long = 0,
    val level: Int = 1,
    val streak: Int = 0,
    val avatarUrl: String? = null,
    val totalCorrect: Int = 0,
    val totalAttempted: Int = 0
)

@Immutable
data class WrongQuestion(
    val questionId: String,
    val question: Question,
    val userAnswer: String,
    val attemptCount: Int,
    val incorrectCount: Int,
    val lastAttempted: Long,
    val masteryStatus: MasteryStatus,
    val nextRevisionDate: Long,
    val isBookmarked: Boolean = false
)

enum class MasteryStatus {
    NEW, LEARNING, IMPROVING, MASTERED
}

@Immutable
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val tier: AchievementTier,
    val criteria: String,
    val progress: Float = 0f,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

enum class AchievementTier { BRONZE, SILVER, GOLD, EXPERT }

@Immutable
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val xp: Long,
    val accuracy: Float,
    val gradeId: Int
)

@Immutable
data class DailyQuestion(
    val date: String,
    val question: Question,
    val isAnswered: Boolean = false,
    val wasCorrect: Boolean? = null
)

@Immutable
data class ExamResult(
    val id: String,
    val totalQuestions: Int,
    val correct: Int,
    val incorrect: Int,
    val unanswered: Int,
    val timeUsedSeconds: Int,
    val accuracy: Float,
    val score: Int,
    val topicBreakdown: Map<String, Float>,
    val timestamp: Long
)
