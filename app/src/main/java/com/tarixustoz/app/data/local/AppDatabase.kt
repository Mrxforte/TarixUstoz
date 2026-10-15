package com.tarixustoz.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TextbookEntity::class,
        ChapterEntity::class,
        TopicEntity::class,
        QuestionEntity::class,
        WrongQuestionEntity::class,
        UserProgressEntity::class,
        AchievementEntity::class,
        DailyQuestionEntity::class,
        QuizAttemptEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun textbookDao(): TextbookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun topicDao(): TopicDao
    abstract fun questionDao(): QuestionDao
    abstract fun wrongQuestionDao(): WrongQuestionDao
    abstract fun userProgressDao(): UserProgressDao
    abstract fun achievementDao(): AchievementDao
    abstract fun dailyQuestionDao(): DailyQuestionDao
    abstract fun quizAttemptDao(): QuizAttemptDao
}
