package com.tarixustoz.app.data.repository

import android.content.Context
import com.tarixustoz.app.data.local.*
import com.tarixustoz.app.data.remote.ApiService
import com.tarixustoz.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val apiService: ApiService
) {
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    fun getTextbooks(): Flow<List<Textbook>> =
        db.textbookDao().getAll().map { list -> list.map { it.toDomain() } }

    fun getTextbooksByGrade(gradeId: Int): Flow<List<Textbook>> =
        db.textbookDao().getByGrade(gradeId).map { list -> list.map { it.toDomain() } }

    fun getChapters(textbookId: String): Flow<List<Chapter>> =
        db.chapterDao().getByTextbook(textbookId).map { list ->
            list.map { Chapter(it.id, it.textbookId, it.number, it.title) }
        }

    fun getTopics(chapterId: String): Flow<List<Topic>> =
        db.topicDao().getByChapter(chapterId).map { list ->
            list.map { it.toDomain(json) }
        }

    fun getTopicsByTextbook(textbookId: String): Flow<List<Topic>> =
        db.topicDao().getByTextbook(textbookId).map { list ->
            list.map { it.toDomain(json) }
        }

    suspend fun getTopicById(topicId: String): Topic? =
        db.topicDao().getById(topicId)?.toDomain(json)

    suspend fun getQuestionsForTopic(topicId: String): List<Question> =
        db.questionDao().getByTopic(topicId).map { it.toDomain(json) }

    suspend fun getQuestionById(id: String): Question? =
        db.questionDao().getById(id)?.toDomain(json)

    suspend fun getRandomQuestions(gradeId: Int, subjectId: String, count: Int): List<Question> =
        db.questionDao().getRandom(gradeId, subjectId, count).map { it.toDomain(json) }

    suspend fun getCustomQuestions(
        limit: Int, 
        category: String, 
        difficulty: String, 
        topicIds: List<String>,
        gradeId: Int = 0
    ): List<Question> =
        db.questionDao().getFilteredWithTopics(
            gradeId = gradeId,
            category = category, 
            difficulty = difficulty, 
            topicIds = topicIds, 
            selectAllTopics = topicIds.isEmpty(), 
            limit = limit
        ).map { it.toDomain(json) }

    suspend fun getWrongQuestionsForReview(limit: Int): List<Question> =
        db.questionDao().getWrongQuestions(limit).map { it.toDomain(json) }

    suspend fun getRandomQuestionForNotification(): Question? =
        db.questionDao().getRandom(7, "uzbekiston", 10).randomOrNull()?.toDomain(json)

    suspend fun getDailyQuestion(): Question? =
        getRandomQuestionForNotification()

    fun getAllTopicsFlow(): Flow<List<Topic>> = 
        db.topicDao().getByTextbook("uz_7").map { list -> 
            list.map { Topic(it.id, it.chapterId, it.textbookId, it.number, it.title) } 
        }

    suspend fun syncWithDjangoBackend(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Fetch PDF Books from Django REST API
            val apiBooks = apiService.getPdfBooks()
            if (apiBooks.isNotEmpty()) {
                val entities = apiBooks.map { b ->
                    TextbookEntity(
                        id = "book_${b.id}",
                        title = b.title,
                        gradeId = b.gradeId,
                        subjectId = b.subjectId,
                        authors = b.authors ?: "Tarixchi olimlar",
                        year = b.year,
                        edition = b.edition ?: "1-nashr",
                        totalChapters = b.totalChapters,
                        totalTopics = b.totalTopics,
                        totalQuestions = b.totalQuestions,
                        coverAsset = b.coverUrl,
                        pdfAsset = b.pdfUrl ?: if (b.gradeId == 6) "textbooks/6_sinf_tarix.pdf" else null
                    )
                }
                db.textbookDao().insertAll(entities)
            }

            // Fetch Topics from Django REST API
            val apiTopics = apiService.getTopics()
            if (apiTopics.isNotEmpty()) {
                val entities = apiTopics.map { t ->
                    TopicEntity(
                        id = "topic_${t.id}",
                        chapterId = "ch_${t.categoryId}",
                        textbookId = "book_${t.categoryId}",
                        number = t.order,
                        title = t.title,
                        content = t.content ?: "",
                        summary = t.chapterTitle ?: ""
                    )
                }
                db.topicDao().insertAll(entities)
            }

            // Fetch Questions from Django REST API
            val apiQuestions = apiService.getQuestions()
            if (apiQuestions.isNotEmpty()) {
                val entities = apiQuestions.map { q ->
                    val optionsList = listOf(
                        AnswerOption("a", q.optionA, q.correctOption.equals("A", ignoreCase = true)),
                        AnswerOption("b", q.optionB, q.correctOption.equals("B", ignoreCase = true)),
                        AnswerOption("c", q.optionC, q.correctOption.equals("C", ignoreCase = true)),
                        AnswerOption("d", q.optionD, q.correctOption.equals("D", ignoreCase = true))
                    )
                    QuestionEntity(
                        id = "q_${q.id}",
                        topicId = if (q.topicId != null) "topic_${q.topicId}" else "general_topic",
                        textbookId = "book_${q.gradeId}",
                        gradeId = q.gradeId,
                        subjectId = q.subjectId,
                        type = QuestionType.SINGLE_CHOICE.name,
                        difficulty = q.difficulty,
                        category = q.categoryType,
                        text = q.text,
                        optionsJson = json.encodeToString(optionsList),
                        explanation = q.explanation ?: "Darslik va manba asosida tayyorlangan.",
                        historicalDate = null,
                        personality = null,
                        sourceReference = "Django Backend API",
                        pageHint = null
                    )
                }
                db.questionDao().insertAll(entities)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun clearAllData() {
        db.textbookDao().deleteAll()
        db.chapterDao().deleteAll()
        db.topicDao().deleteAll()
        db.questionDao().deleteAll()
        db.wrongQuestionDao().deleteAll()
        db.userProgressDao().deleteAll()
        db.quizAttemptDao().deleteAll()
    }

    suspend fun resetAndReimport(jsonContent: String? = null) {
        val synced = syncWithDjangoBackend()
        if (!synced) {
            clearAllData()
            if (!jsonContent.isNullOrBlank()) {
                importFromJsonString(jsonContent)
            } else {
                val assetJson = loadAssetJson("content.json") ?: loadAssetJson("textbooks.json")
                if (!assetJson.isNullOrBlank()) {
                    importFromJsonString(assetJson)
                } else {
                    seedContent()
                }
            }
        }
    }

    suspend fun seedIfNeeded() {
        val synced = syncWithDjangoBackend()
        if (!synced && db.textbookDao().getById("uz_6") == null) {
            clearAllData()
            val assetJson = loadAssetJson("content.json") ?: loadAssetJson("textbooks.json")
            if (!assetJson.isNullOrBlank()) {
                importFromJsonString(assetJson)
            } else {
                seedContent()
            }
        }
    }

    private fun loadAssetJson(fileName: String): String? {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun importFromJsonString(rawJson: String): Boolean {
        return try {
            val data = json.decodeFromString<ContentDataDto>(rawJson)
            if (data.textbooks.isNotEmpty()) {
                db.textbookDao().insertAll(data.textbooks.map { it.toEntity() })
            }
            if (data.chapters.isNotEmpty()) {
                db.chapterDao().insertAll(data.chapters.map { it.toEntity() })
            }
            if (data.topics.isNotEmpty()) {
                db.topicDao().insertAll(data.topics.map { it.toEntity(json) })
            }
            if (data.questions.isNotEmpty()) {
                db.questionDao().insertAll(data.questions.map { it.toEntity(json) })
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun seedContent() {
        val textbooks = listOf(
            TextbookEntity("uz_6", "Tarix. Qadimgi dunyo", 6, "jahon", "A.S. Sagdullayev, V.A. Kostetskiy", 2017, "5-nashr", 12, 40, 80, "file:///android_asset/images/uz_6_cover.png", "textbooks/6_sinf_tarix.pdf"),
            TextbookEntity("uz_7", "O‘zbekiston tarixi (IV–XVI asr)", 7, "uzbekiston", "A. Muhammadjonov", 2017, "3-nashr", 10, 35, 70),
            TextbookEntity("jahon_7", "Jahon tarixi (V–XV asr)", 7, "jahon", "T.O‘. Salimov, F.E. Sultonov", 2017, "3-nashr", 12, 40, 75),
            TextbookEntity("uz_8", "O‘zbekiston tarixi", 8, "uzbekiston", "U. Jo‘rayev va b.", 2019, "4-nashr", 12, 45, 90),
            TextbookEntity("jahon_8", "Jahon tarixi (XVI–XIX asr)", 8, "jahon", "R. Farmonov, U. Jo‘rayev, Sh. Ergashev", 2019, "4-nashr", 10, 35, 80),
            TextbookEntity("uz_9", "O‘zbekiston tarixi (XIX asr II yarmi – XX asr boshi)", 9, "uzbekiston", "S. Tillaboyev, A. Zamonov", 2019, "3-nashr", 10, 30, 70),
            TextbookEntity("jahon_9", "Jahon tarixi", 9, "jahon", "M. Lafasov va b.", 2019, "4-nashr", 12, 40, 85),
            TextbookEntity("uz_10", "O‘zbekiston tarixi (1917–1991)", 10, "uzbekiston", "Q. Rajabov, A. Zamonov", 2017, "1-nashr", 10, 30, 65),
            TextbookEntity("jahon_10", "Jahon tarixi (1918–1991)", 10, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2017, "1-nashr", 10, 30, 60),
            TextbookEntity("uz_11", "O‘zbekiston tarixi (Mustaqillik davri)", 11, "uzbekiston", "N. Jo‘rayev, A. Zamonov", 2018, "1-nashr", 8, 25, 55),
            TextbookEntity("jahon_11", "Jahon tarixi (1991–2017)", 11, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2018, "1-nashr", 8, 25, 50)
        )
        db.textbookDao().insertAll(textbooks)
    }

    private fun TextbookEntity.toDomain() = Textbook(
        id = id, title = title, gradeId = gradeId, subjectId = subjectId,
        authors = authors, year = year, edition = edition, coverAsset = coverAsset, pdfAsset = pdfAsset,
        totalChapters = totalChapters, totalTopics = totalTopics, totalQuestions = totalQuestions
    )

    private fun TopicEntity.toDomain(json: Json): Topic {
        val dates = try {
            json.decodeFromString<List<ImportantDate>>(importantDatesJson)
        } catch (e: Exception) {
            emptyList()
        }
        val termsList = try {
            json.decodeFromString<List<HistoricalTerm>>(termsJson)
        } catch (e: Exception) {
            emptyList()
        }
        return Topic(
            id = id,
            chapterId = chapterId,
            textbookId = textbookId,
            number = number,
            title = title,
            content = content,
            importantDates = dates,
            terms = termsList,
            summary = summary,
            imageAsset = imageAsset,
            imageCaption = imageCaption
        )
    }

    private fun QuestionEntity.toDomain(json: Json): Question {
        val options = try {
            json.decodeFromString<List<AnswerOptionDto>>(optionsJson).map {
                AnswerOption(it.id, it.text, it.isCorrect)
            }
        } catch (e: Exception) {
            emptyList()
        }
        val cat = try { QuestionCategory.valueOf(category) } catch (e: Exception) { QuestionCategory.ALL }
        return Question(
            id = id, topicId = topicId, textbookId = textbookId, gradeId = gradeId,
            subjectId = subjectId,
            type = QuestionType.valueOf(type),
            difficulty = Difficulty.valueOf(difficulty),
            category = cat,
            text = text, options = options, explanation = explanation,
            historicalDate = historicalDate, personality = personality,
            sourceReference = sourceReference, pageHint = pageHint
        )
    }
}

@Serializable
data class ContentDataDto(
    val textbooks: List<TextbookDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
    val topics: List<TopicDto> = emptyList(),
    val questions: List<QuestionDto> = emptyList()
)

@Serializable
data class TextbookDto(
    val id: String,
    val title: String,
    val gradeId: Int,
    val subjectId: String = "uzbekiston",
    val authors: String = "",
    val year: Int = 2024,
    val edition: String = "1-nashr",
    val coverAsset: String? = null,
    val pdfAsset: String? = null,
    val totalChapters: Int = 0,
    val totalTopics: Int = 0,
    val totalQuestions: Int = 0
) {
    fun toEntity() = TextbookEntity(
        id = id, title = title, gradeId = gradeId, subjectId = subjectId,
        authors = authors, year = year, edition = edition,
        totalChapters = totalChapters, totalTopics = totalTopics, totalQuestions = totalQuestions,
        coverAsset = coverAsset ?: if (id == "uz_6") "file:///android_asset/images/uz_6_cover.png" else null,
        pdfAsset = pdfAsset ?: if (id == "uz_6") "textbooks/6_sinf_tarix.pdf" else null
    )
}

@Serializable
data class ChapterDto(
    val id: String,
    val textbookId: String,
    val number: Int,
    val title: String
) {
    fun toEntity() = ChapterEntity(id = id, textbookId = textbookId, number = number, title = title)
}

@Serializable
data class TopicDto(
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
    val imageCaption: String? = null
) {
    fun toEntity(json: Json) = TopicEntity(
        id = id, chapterId = chapterId, textbookId = textbookId, number = number, title = title,
        content = content, importantDatesJson = json.encodeToString(importantDates),
        termsJson = json.encodeToString(terms), summary = summary, imageAsset = imageAsset, imageCaption = imageCaption
    )
}

@Serializable
data class QuestionDto(
    val id: String,
    val topicId: String,
    val textbookId: String,
    val gradeId: Int,
    val subjectId: String,
    val type: String,
    val difficulty: String,
    val category: String = "ALL",
    val text: String,
    val options: List<AnswerOptionDto>,
    val explanation: String,
    val historicalDate: String? = null,
    val personality: String? = null,
    val sourceReference: String? = null,
    val pageHint: String? = null
) {
    fun toEntity(json: Json) = QuestionEntity(
        id = id, topicId = topicId, textbookId = textbookId, gradeId = gradeId, subjectId = subjectId,
        type = type, difficulty = difficulty, category = category, text = text,
        optionsJson = json.encodeToString(options), explanation = explanation,
        historicalDate = historicalDate, personality = personality, sourceReference = sourceReference, pageHint = pageHint
    )
}

@Serializable
data class AnswerOptionDto(
    val id: String,
    val text: String,
    val isCorrect: Boolean
)
