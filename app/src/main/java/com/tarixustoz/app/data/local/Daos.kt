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

    @Query("SELECT COUNT(*) FROM textbooks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TextbookEntity>)

    @Query("DELETE FROM textbooks")
    suspend fun deleteAll()
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE textbookId = :textbookId ORDER BY number")
    fun getByTextbook(textbookId: String): Flow<List<ChapterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChapterEntity>)

    @Query("DELETE FROM chapters")
    suspend fun deleteAll()
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics ORDER BY textbookId, number")
    fun getAll(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getById(id: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE chapterId = :chapterId ORDER BY number")
    fun getByChapter(chapterId: String): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE textbookId = :textbookId ORDER BY number")
    fun getByTextbook(textbookId: String): Flow<List<TopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TopicEntity>)

    @Query("DELETE FROM topics")
    suspend fun deleteAll()
}

data class TopicCountTuple(
    val topicId: String,
    val count: Int
)

@Dao
interface QuestionDao {
    @Query("SELECT topicId, COUNT(*) as count FROM questions GROUP BY topicId")
    suspend fun getTopicCounts(): List<TopicCountTuple>

    @Query("SELECT COUNT(*) FROM questions WHERE topicId = :topicId")
    suspend fun countByTopic(topicId: String): Int

    @Query("SELECT * FROM questions WHERE topicId = :topicId")
    suspend fun getByTopic(topicId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE gradeId = :gradeId AND (subjectId = :subjectId OR :subjectId = 'ALL') LIMIT :limit OFFSET :offset")
    suspend fun getByGradeAndSubjectWithOffset(gradeId: Int, subjectId: String, limit: Int, offset: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE gradeId = :gradeId AND (subjectId = :subjectId OR :subjectId = 'ALL') LIMIT :limit")
    suspend fun getByGradeAndSubject(gradeId: Int, subjectId: String, limit: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE textbookId = :textbookId")
    suspend fun getByTextbook(textbookId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE gradeId = :gradeId AND subjectId = :subjectId LIMIT :limit")
    suspend fun getRandom(gradeId: Int, subjectId: String, limit: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE category = :category LIMIT :limit")
    suspend fun getByCategory(category: String, limit: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE difficulty = :difficulty LIMIT :limit")
    suspend fun getByDifficulty(difficulty: String, limit: Int): List<QuestionEntity>

    @Query("""
        SELECT * FROM questions 
        WHERE (:gradeId = 0 OR gradeId = :gradeId) 
          AND (
              :category = 'ALL' 
              OR category = :category 
              OR (:category = 'YEAR' AND (historicalDate IS NOT NULL AND historicalDate != ''))
              OR (:category = 'PERSON' AND (personality IS NOT NULL AND personality != ''))
              OR (:category IN ('ATTESTATION', 'NATIONAL_CERTIFICATE', 'DTM_PRACTICE') AND (category = :category OR category = 'ALL'))
          ) 
          AND (:difficulty = 'ALL' OR difficulty = :difficulty) 
          AND (topicId IN (:topicIds) OR :selectAllTopics = 1) 
        LIMIT :limit
    """)
    suspend fun getFilteredWithTopics(gradeId: Int, category: String, difficulty: String, topicIds: List<String>, selectAllTopics: Boolean, limit: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (SELECT questionId FROM wrong_questions) LIMIT :limit")
    suspend fun getWrongQuestions(limit: Int): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE textbookId = :textbookId")
    suspend fun countByTextbook(textbookId: String): Int

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE gradeId = :gradeId AND subjectId = :subjectId")
    suspend fun countByGradeAndSubject(gradeId: Int, subjectId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<QuestionEntity>)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()
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

    @Query("DELETE FROM wrong_questions")
    suspend fun deleteAll()
}

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress")
    fun getAll(): Flow<List<UserProgressEntity>>

    @Query("SELECT * FROM user_progress WHERE topicId = :topicId")
    suspend fun getByTopic(topicId: String): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: UserProgressEntity)

    @Query("DELETE FROM user_progress")
    suspend fun deleteAll()
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

    @Query("SELECT COUNT(*) FROM quiz_attempts WHERE wasCorrect = 1")
    fun getCorrectCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM quiz_attempts")
    fun getTotalCountFlow(): Flow<Int>

    @Query("DELETE FROM quiz_attempts")
    suspend fun deleteAll()
}

@Dao
interface PdfBookmarkDao {
    @Query("SELECT * FROM pdf_bookmarks WHERE pdfPath = :pdfPath ORDER BY pageNumber ASC")
    fun getBookmarks(pdfPath: String): Flow<List<PdfBookmarkEntity>>

    @Query("SELECT * FROM pdf_bookmarks WHERE pdfPath = :pdfPath AND pageNumber = :pageNumber")
    suspend fun getBookmark(pdfPath: String, pageNumber: Int): PdfBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: PdfBookmarkEntity)

    @Query("DELETE FROM pdf_bookmarks WHERE pdfPath = :pdfPath AND pageNumber = :pageNumber")
    suspend fun delete(pdfPath: String, pageNumber: Int)
}

@Dao
interface PdfProgressDao {
    @Query("SELECT * FROM pdf_progress WHERE pdfPath = :pdfPath")
    suspend fun getProgress(pdfPath: String): PdfProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PdfProgressEntity)
}

@Dao
interface PdfAnnotationDao {
    @Query("SELECT * FROM pdf_annotations WHERE pdfPath = :pdfPath AND pageNumber = :pageNumber")
    fun getAnnotations(pdfPath: String, pageNumber: Int): Flow<List<PdfAnnotationEntity>>

    @Query("SELECT * FROM pdf_annotations WHERE pdfPath = :pdfPath")
    suspend fun getAllAnnotations(pdfPath: String): List<PdfAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(annotation: PdfAnnotationEntity)

    @Query("DELETE FROM pdf_annotations WHERE pdfPath = :pdfPath AND pageNumber = :pageNumber")
    suspend fun clearPageAnnotations(pdfPath: String, pageNumber: Int)

    @Query("DELETE FROM pdf_annotations WHERE id = :id")
    suspend fun delete(id: Long)
}
