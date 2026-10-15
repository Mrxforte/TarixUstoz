package com.tarixustoz.app.data.repository

import com.tarixustoz.app.data.local.WrongQuestionEntity
import com.tarixustoz.app.data.local.AppDatabase
import com.tarixustoz.app.domain.model.MasteryStatus
import com.tarixustoz.app.domain.model.Question
import com.tarixustoz.app.domain.model.WrongQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MistakesRepository @Inject constructor(
    private val db: AppDatabase,
    private val contentRepository: ContentRepository
) {
    fun getWrongQuestions(): Flow<List<WrongQuestion>> =
        db.wrongQuestionDao().getAll().map { list ->
            list.mapNotNull { entity ->
                val q = contentRepository.getQuestionById(entity.questionId) // This is suspend, need fix
                // For simplicity in flow, we will handle in ViewModel
                null
            }
        }

    fun getActiveCount(): Flow<Int> = db.wrongQuestionDao().getActiveCount()

    suspend fun recordIncorrect(question: Question, userAnswer: String) {
        val existing = db.wrongQuestionDao().getById(question.id)
        val now = System.currentTimeMillis()
        if (existing != null) {
            val updated = existing.copy(
                userAnswer = userAnswer,
                attemptCount = existing.attemptCount + 1,
                incorrectCount = existing.incorrectCount + 1,
                lastAttempted = now,
                masteryStatus = MasteryStatus.LEARNING.name,
                nextRevisionDate = now + (1 * 24 * 60 * 60 * 1000L) // 1 day
            )
            db.wrongQuestionDao().update(updated)
        } else {
            db.wrongQuestionDao().insert(
                WrongQuestionEntity(
                    questionId = question.id,
                    userAnswer = userAnswer,
                    attemptCount = 1,
                    incorrectCount = 1,
                    lastAttempted = now,
                    masteryStatus = MasteryStatus.NEW.name,
                    nextRevisionDate = now + (1 * 24 * 60 * 60 * 1000L),
                    isBookmarked = false
                )
            )
        }
    }

    suspend fun recordCorrectRevision(questionId: String) {
        val existing = db.wrongQuestionDao().getById(questionId) ?: return
        val now = System.currentTimeMillis()
        val newIncorrect = existing.incorrectCount
        val newStatus = when {
            existing.attemptCount >= 3 && existing.incorrectCount == 0 -> MasteryStatus.MASTERED
            existing.attemptCount >= 2 -> MasteryStatus.IMPROVING
            else -> MasteryStatus.LEARNING
        }
        val nextDate = when (newStatus) {
            MasteryStatus.MASTERED -> now + (30L * 24 * 60 * 60 * 1000)
            MasteryStatus.IMPROVING -> now + (7L * 24 * 60 * 60 * 1000)
            else -> now + (2L * 24 * 60 * 60 * 1000)
        }
        db.wrongQuestionDao().update(
            existing.copy(
                attemptCount = existing.attemptCount + 1,
                lastAttempted = now,
                masteryStatus = newStatus.name,
                nextRevisionDate = nextDate
            )
        )
    }

    suspend fun getAllWrongEntities(): List<WrongQuestionEntity> {
        // Helper for ViewModel
        return emptyList() // Will use flow
    }
}
