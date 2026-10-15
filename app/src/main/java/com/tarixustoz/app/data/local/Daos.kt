package com.tarixustoz.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TextbookDao {
    @Query("SELECT * FROM textbooks ORDER BY gradeId, subjectId")
    fun getAll(): Flow<List<TextbookEntity>>

    @Query("SELECT * FROM textbooks WHERE gradeId = :gradeId")
    fun getByGrade(gradeId: Int): Flow<List<TextbookEntity>>

    @Query("SELECT * FROM textbooks WHERE id = :id")
    suspend fun getById(id: String): TextbookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TextbookEntity>)
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE textbookId = :textbookId ORDER BY number")
    fun getByTextbook(textbookId: String): Flow<List<ChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChapterEntity>)
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics WHERE chapterId = :chapterId ORDER BY number")
    fun getByChapter(chapterId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE textbookId = :textbookId ORDER BY number")
    fun getByTextbook(textbookId: String): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TopicEntity>)
}

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE topicId = :topicId")
    suspend fun getByTopic(topicId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE textbookId = :textbookId")
    suspend fun getByTextbook(textbookId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE gradeId = :gradeId AND subjectId = :subjectId LIMIT :limit")
    suspend fun getRandom(gradeId: Int, subjectId: String, limit: Int): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QuestionEntity>)
}

@Dao
interface WrongQuestionDao {
    @Query("SELECT * FROM wrong_questions ORDER BY nextRevisionDate ASC")
    fun getAll(): Flow<List<WrongQuestionEntity>>

    @Query("SELECT COUNT(*) FROM wrong_questions WHERE masteryStatus != 'MASTERED'")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT * FROM wrong_questions WHERE questionId = :id")
    suspend fun getById(id: String): WrongQuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: WrongQuestionEntity)

    @Update
    suspend fun update(item: WrongQuestionEntity)

    @Query("DELETE FROM wrong_questions WHERE questionId = :id")
    suspend fun delete(id: String)
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress")
    fun getAll(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress WHERE topicId = :topicId")
    suspend fun getByTopic(topicId: String): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UserProgressEntity)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements")
    fun getAll(): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<AchievementEntity>)
}

@Dao
interface DailyQuestionDao {
    @Query("SELECT * FROM daily_questions WHERE date = :date")
    suspend fun getByDate(date: String): DailyQuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DailyQuestionEntity)
}

@Dao
interface QuizAttemptDao {
    @Insert
    suspend fun insert(item: QuizAttemptEntity)

    @Query("SELECT COUNT(*) FROM quiz_attempts WHERE wasCorrect = 1")
    suspend fun getCorrectCount(): Int

    @Query("SELECT COUNT(*) FROM quiz_attempts")
    suspend fun getTotalCount(): Int
}
