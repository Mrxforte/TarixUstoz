package com.tarixustoz.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.tarixustoz.app.domain.model.Difficulty
import com.tarixustoz.app.domain.model.MasteryStatus
import com.tarixustoz.app.domain.model.QuestionType

@Entity(tableName = "textbooks")
data class TextbookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val gradeId: Int,
    val subjectId: String,
    val authors: String,
    val year: Int,
    val edition: String,
    val totalChapters: Int,
    val totalTopics: Int,
    val totalQuestions: Int
)

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val id: String,
    val textbookId: String,
    val number: Int,
    val title: String
)

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val textbookId: String,
    val number: Int,
    val title: String
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val textbookId: String,
    val gradeId: Int,
    val subjectId: String,
    val type: String,
    val difficulty: String,
    val text: String,
    val optionsJson: String, // JSON array of options
    val explanation: String,
    val historicalDate: String?,
    val personality: String?,
    val sourceReference: String?,
    val pageHint: String?
)

@Entity(tableName = "wrong_questions")
data class WrongQuestionEntity(
    @PrimaryKey val questionId: String,
    val userAnswer: String,
    val attemptCount: Int,
    val incorrectCount: Int,
    val lastAttempted: Long,
    val masteryStatus: String,
    val nextRevisionDate: Long,
    val isBookmarked: Boolean
)

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val topicId: String,
    val correctCount: Int,
    val totalAttempts: Int,
    val masteryPercent: Float,
    val lastStudied: Long
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val isUnlocked: Boolean,
    val unlockedAt: Long?,
    val progress: Float
)

@Entity(tableName = "daily_questions")
data class DailyQuestionEntity(
    @PrimaryKey val date: String,
    val questionId: String,
    val isAnswered: Boolean,
    val wasCorrect: Boolean?
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val wasCorrect: Boolean,
    val userAnswer: String,
    val timestamp: Long,
    val sessionId: String?
)
