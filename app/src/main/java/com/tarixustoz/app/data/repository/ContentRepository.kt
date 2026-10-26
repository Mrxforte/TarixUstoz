package com.tarixustoz.app.data.repository

import android.content.Context
import com.tarixustoz.app.data.local.*
import com.tarixustoz.app.data.remote.ApiService
import com.tarixustoz.app.data.remote.ExamCategoryDto
import com.tarixustoz.app.data.remote.QuizGenerateRequest
import com.tarixustoz.app.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    /** Qaysi kitob allaqachon yuklab olinganini saqlaydi. */
    private val bankPrefs by lazy {
        context.getSharedPreferences("book_banks", Context.MODE_PRIVATE)
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            seedIfNeeded()
        }
    }

    fun getTextbooks(): Flow<List<Textbook>> =
        db.textbookDao().getAll().map { list ->
            list.map { it.toDomain() }
                .distinctBy { "${it.gradeId}_${if (it.subjectId.contains("jahon", ignoreCase = true)) "jahon" else "uzbekiston"}" }
        }

    fun getTextbooksByGrade(gradeId: Int): Flow<List<Textbook>> =
        db.textbookDao().getByGrade(gradeId).map { list ->
            list.map { it.toDomain() }
                .distinctBy { "${it.gradeId}_${if (it.subjectId.contains("jahon", ignoreCase = true)) "jahon" else "uzbekiston"}" }
        }

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

    suspend fun getTextbookById(id: String): Textbook? =
        db.textbookDao().getById(id)?.toDomain()

    suspend fun getTopicById(topicId: String): Topic? =
        db.topicDao().getById(topicId)?.toDomain(json)

    suspend fun getQuestionsForTopic(topicId: String): List<Question> =
        db.questionDao().getByTopic(topicId).map { it.toDomain(json) }

    suspend fun getQuestionsForGradeAndSubjectWithOffset(gradeId: Int, subjectId: String, limit: Int, offset: Int): List<Question> =
        db.questionDao().getByGradeAndSubjectWithOffset(gradeId, subjectId, limit, offset).map { it.toDomain(json) }

    suspend fun getQuestionsForGradeAndSubject(gradeId: Int, subjectId: String, limit: Int = 30): List<Question> =
        db.questionDao().getByGradeAndSubject(gradeId, subjectId, limit).map { it.toDomain(json) }

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

    suspend fun getDailyQuestion(excludeIds: Set<String> = emptySet()): Question? = withContext(Dispatchers.IO) {
        val questions = db.questionDao().getFilteredWithTopics(
            gradeId = 0,
            category = "ALL",
            difficulty = "ALL",
            topicIds = emptyList(),
            selectAllTopics = true,
            limit = 50
        ).map { it.toDomain(json) }

        val available = questions.filterNot { excludeIds.contains(it.id) }
        (if (available.isNotEmpty()) available else questions).shuffled().firstOrNull()
            ?: getRandomQuestionForNotification()
    }

    fun getAllTopicsFlow(): Flow<List<Topic>> = 
        db.topicDao().getAll().map { list -> 
            list.map { it.toDomain(json) } 
        }

    suspend fun getTopicCountsMap(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            db.questionDao().getTopicCounts().associate { it.topicId to it.count }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getExamCategoriesFromApi(): List<ExamCategoryDto> = withContext(Dispatchers.IO) {
        try {
            apiService.getExamCategories()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchDirectionCounts(
        grade: Int = 0,
        subject: String = ""
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            apiService.getDirections(
                grade = grade.takeIf { it > 0 },
                subject = subject.takeIf { it.isNotBlank() }
            ).directions.associate { it.code to it.count }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    suspend fun fetchQuizFromApi(
        count: Int,
        categoryCode: String,
        grade: Int,
        subject: String = "",
        difficulty: String = "MEDIUM",
        topicIds: List<String> = emptyList()
    ): List<Question> = withContext(Dispatchers.IO) {
        try {
            val numericTopicIds = topicIds.mapNotNull { it.removePrefix("topic_").toLongOrNull() }
            val effectiveGrade = if (numericTopicIds.isNotEmpty()) 0 else grade
            val effectiveSubject = if (numericTopicIds.isNotEmpty()) "" else subject

            val req = QuizGenerateRequest(
                count = count,
                categoryCode = categoryCode,
                grade = effectiveGrade,
                subject = effectiveSubject,
                difficulty = difficulty,
                topicIds = numericTopicIds
            )
            val response = apiService.generateQuiz(req)
            val questions = response.questions.map { q ->
                val optionsList = listOf(
                    AnswerOption("a", q.optionA, q.correctOption.equals("A", ignoreCase = true)),
                    AnswerOption("b", q.optionB, q.correctOption.equals("B", ignoreCase = true)),
                    AnswerOption("c", q.optionC, q.correctOption.equals("C", ignoreCase = true)),
                    AnswerOption("d", q.optionD, q.correctOption.equals("D", ignoreCase = true))
                )
                val cat = try { QuestionCategory.valueOf(q.categoryType) } catch (_: Exception) { QuestionCategory.ALL }
                val diff = try { Difficulty.valueOf(q.difficulty) } catch (_: Exception) { Difficulty.MEDIUM }
                Question(
                    id = "q_${q.id}",
                    topicId = if (q.topicId != null) "topic_${q.topicId}" else "general",
                    textbookId = "book_${q.gradeId}",
                    gradeId = q.gradeId,
                    subjectId = q.subjectId ?: "uzbekiston",
                    type = QuestionType.SINGLE_CHOICE,
                    difficulty = diff,
                    category = cat,
                    text = q.text,
                    options = optionsList,
                    explanation = q.explanation ?: q.shortExplanation ?: "Darslik va manba asosida tayyorlangan.",
                    sourceReference = q.sourceCitation ?: "Django Backend API"
                )
            }
            questions
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getFallbackQuestions(count: Int, categoryCode: String = "ALL", gradeId: Int = 0): List<Question> {
        val rawPool = listOf(
            // 6-sinf
            Question(
                id = "fb_6_1",
                topicId = "topic_uz_6_1",
                textbookId = "uz_6",
                gradeId = 6,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.EASY,
                category = QuestionCategory.CITY,
                text = "Qadimgi Baqtriya davlatining poytaxti qaysi shahar bo'lgan?",
                options = listOf(
                    AnswerOption("a", "Baqtra (Zariaspa)", true),
                    AnswerOption("b", "Marakanda", false),
                    AnswerOption("c", "Taxshida", false),
                    AnswerOption("d", "Afrasiyob", false)
                ),
                explanation = "Baqtriya davlatining markazi va poytaxti Baqtra shahri hisoblangan.",
                sourceReference = "6-sinf Qadimgi dunyo tarixi"
            ),
            Question(
                id = "fb_6_2",
                topicId = "topic_jahon_6_1",
                textbookId = "jahon_6",
                gradeId = 6,
                subjectId = "jahon",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                category = QuestionCategory.YEAR,
                text = "Nil daryosi bo'yida birinchi marta Qadimgi Misr davlati taxminan qaysi mingyillikda tashkil topgan?",
                options = listOf(
                    AnswerOption("a", "Mil.avv. IV mingyillik oxiri", true),
                    AnswerOption("b", "Mil.avv. III mingyillik boshi", false),
                    AnswerOption("c", "Mil.avv. II mingyillik", false),
                    AnswerOption("d", "Mil.avv. I mingyillik", false)
                ),
                explanation = "Miloddan avvalgi IV mingyillik oxirida Yuqori va Quyi Misr birlashtirilgan.",
                historicalDate = "Mil.avv. IV mingyillik",
                sourceReference = "6-sinf Jahon tarixi"
            ),

            // 7-sinf
            Question(
                id = "fb_7_1",
                topicId = "topic_uz_7_4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                category = QuestionCategory.YEAR,
                text = "Amir Temur Movarounnahrning oliy hukmdori deb qaysi yilda e'lon qilingan?",
                options = listOf(
                    AnswerOption("a", "1370-yil", true),
                    AnswerOption("b", "1368-yil", false),
                    AnswerOption("c", "1372-yil", false),
                    AnswerOption("d", "1375-yil", false)
                ),
                explanation = "1370-yildagi Bel qurultoyida Amir Temur Movarounnahrning oliy hukmdori deb e'lon qilindi.",
                historicalDate = "1370-yil",
                sourceReference = "O'zbekiston tarixi 7-sinf"
            ),
            Question(
                id = "fb_7_2",
                topicId = "topic_uz_7_4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.HARD,
                category = QuestionCategory.PERSON,
                text = "'Tarixi rashidi' asarining muallifi kim hisoblanadi?",
                options = listOf(
                    AnswerOption("a", "Mirzo Muhammad Haydar", true),
                    AnswerOption("b", "Zahiriddin Muhammad Bobur", false),
                    AnswerOption("c", "Alovuddin Otamalik Juvayniy", false),
                    AnswerOption("d", "Sharafiddin Ali Yazdiy", false)
                ),
                explanation = "'Tarixi rashidi' asari Mirzo Muhammad Haydar Dushlat tomonidan yozilgan.",
                personality = "Mirzo Muhammad Haydar",
                sourceReference = "7-sinf O'zbekiston tarixi"
            ),

            // 8-sinf
            Question(
                id = "fb_8_1",
                topicId = "topic_uz_8_1",
                textbookId = "uz_8",
                gradeId = 8,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.HARD,
                category = QuestionCategory.YEAR,
                text = "Buxoro amirligida amir Nasrullo hukronlik qilgan yillarni ko'rsating:",
                options = listOf(
                    AnswerOption("a", "1826–1860-yillar", true),
                    AnswerOption("b", "1824–1858-yillar", false),
                    AnswerOption("c", "1828–1862-yillar", false),
                    AnswerOption("d", "1830–1865-yillar", false)
                ),
                explanation = "Amir Nasrullo Buxoro amirligini 1826-yildan 1860-yilgacha boshqargan.",
                historicalDate = "1826–1860",
                sourceReference = "8-sinf O'zbekiston tarixi"
            ),

            // 9-sinf
            Question(
                id = "fb_9_1",
                topicId = "topic_uz_9_1",
                textbookId = "uz_9",
                gradeId = 9,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                category = QuestionCategory.EVENT,
                text = "Jadidchilik harakatining asoschilaridan biri, 'Oyna' jurnali va 'Padarkush' dramasi muallifi kim?",
                options = listOf(
                    AnswerOption("a", "Mahmudxo'ja Behbudiy", true),
                    AnswerOption("b", "Munavvarqori Abdurashidxonov", false),
                    AnswerOption("c", "Abdulla Avloniy", false),
                    AnswerOption("d", "Abdurauf Fitrat", false)
                ),
                explanation = "Mahmudxo'ja Behbudiy Turkiston jadidchilik harakatining ma'rifatparvar rahnamosi edi.",
                personality = "Mahmudxo'ja Behbudiy",
                sourceReference = "9-sinf O'zbekiston tarixi"
            ),

            // 10-sinf
            Question(
                id = "fb_10_1",
                topicId = "topic_uz_10_1",
                textbookId = "uz_10",
                gradeId = 10,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.HARD,
                category = QuestionCategory.YEAR,
                text = "Turkiston Muxtoriyati hukomati qaysi yilda va qayerda e'lon qilingan?",
                options = listOf(
                    AnswerOption("a", "1917-yil noyabrda Qo'qon shahrida", true),
                    AnswerOption("b", "1917-yil oktabrda Toshkent shahrida", false),
                    AnswerOption("c", "1918-yil fevralda Buxoro shahrida", false),
                    AnswerOption("d", "1916-yil iyulda Samarqand shahrida", false)
                ),
                explanation = "1917-yil 27-noyabrda IV Favqulodda Buntutkiston musulmonlar qurultoyida Turkiston Muxtoriyati e'lon qilingan.",
                historicalDate = "1917-yil",
                sourceReference = "10-sinf O'zbekiston tarixi"
            ),

            // 11-sinf
            Question(
                id = "fb_11_1",
                topicId = "topic_uz_11_1",
                textbookId = "uz_11",
                gradeId = 11,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                category = QuestionCategory.EVENT,
                text = "O'zbekiston Respublikasining Mustaqillik deklaratsiyasi qachon qabul qilingan?",
                options = listOf(
                    AnswerOption("a", "1990-yil 20-iyun", true),
                    AnswerOption("b", "1990-yil 25-may", false),
                    AnswerOption("c", "1990-yil 1-iyul", false),
                    AnswerOption("d", "1990-yil 31-avgust", false)
                ),
                explanation = "Mustaqillik deklaratsiyasi O'zbekiston SSR Oliy Kengashining II sessiyasida 1990-yil 20-iyunda qabul qilingan.",
                historicalDate = "1990-yil 20-iyun",
                sourceReference = "11-sinf O'zbekiston tarixi"
            )
        )

        var filtered = rawPool
        if (gradeId > 0) {
            val gradeFiltered = filtered.filter { it.gradeId == gradeId }
            if (gradeFiltered.isNotEmpty()) {
                filtered = gradeFiltered
            } else {
                return emptyList()
            }
        }

        if (categoryCode == "YEAR") {
            val yearFiltered = filtered.filter { it.category == QuestionCategory.YEAR || it.historicalDate != null }
            if (yearFiltered.isNotEmpty()) filtered = yearFiltered
        } else if (categoryCode == "PERSON") {
            val personFiltered = filtered.filter { it.category == QuestionCategory.PERSON || it.personality != null }
            if (personFiltered.isNotEmpty()) filtered = personFiltered
        } else if (categoryCode == "CITY") {
            val cityFiltered = filtered.filter { it.category == QuestionCategory.CITY }
            if (cityFiltered.isNotEmpty()) filtered = cityFiltered
        } else if (categoryCode == "EVENT") {
            val eventFiltered = filtered.filter { it.category == QuestionCategory.EVENT }
            if (eventFiltered.isNotEmpty()) filtered = eventFiltered
        }

        return filtered.shuffled().take(count.coerceAtLeast(1))
    }

    suspend fun syncWithDjangoBackend(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Fetch PDF Books from Django REST API
            val apiBooks = try { apiService.getPdfBooks() } catch (e: Exception) { emptyList() }
            if (apiBooks.isNotEmpty()) {
                val entities = apiBooks.map { b ->
                    val rawPdfUrl = b.pdfUrl ?: b.pdfFile
                    val formattedPdfUrl = rawPdfUrl?.replace("127.0.0.1", "10.0.2.2")?.replace("localhost", "10.0.2.2")
                    val formattedCoverUrl = (b.coverUrl ?: b.coverImage)?.replace("127.0.0.1", "10.0.2.2")?.replace("localhost", "10.0.2.2")

                    val subjectFileName = if (b.effectiveGrade == 6) "Qadimgi_dunyo_tarixi" else if (b.effectiveSubject.contains("jahon", ignoreCase = true)) "Jahon_tarixi" else "Ozbekiston_tarixi"
                    val fallbackPdfUrl = "http://10.0.2.2:8000/media/pdfs/${b.effectiveGrade}-sinf_$subjectFileName.pdf"

                    TextbookEntity(
                        id = "book_${b.id}",
                        title = b.title,
                        gradeId = b.effectiveGrade,
                        subjectId = b.effectiveSubject,
                        authors = b.authors ?: "Tarixchi olimlar",
                        year = b.year,
                        edition = b.edition ?: "1-nashr",
                        totalChapters = b.totalChapters,
                        totalTopics = b.totalTopics,
                        totalQuestions = b.totalQuestions,
                        coverAsset = formattedCoverUrl,
                        pdfAsset = formattedPdfUrl?.takeIf { it.isNotBlank() } ?: fallbackPdfUrl
                    )
                }
                db.textbookDao().insertAll(entities)
            } else {
                if (db.textbookDao().count() < 11) {
                    seedContent()
                }
            }

            // Fetch Topics from Django REST API
            val apiTopics = try { apiService.getTopics() } catch (e: Exception) { emptyList() }
            if (apiTopics.isNotEmpty()) {
                val entities = apiTopics.map { t ->
                    val inferredGrade = t.grade ?: run {
                        val text = "${t.title} ${t.chapterTitle} ${t.textbookId}"
                        when {
                            text.contains("11") -> 11
                            text.contains("10") -> 10
                            text.contains("9") -> 9
                            text.contains("8") -> 8
                            text.contains("7") -> 7
                            text.contains("6") -> 6
                            else -> 7
                        }
                    }

                    val isJahon = (t.subject?.contains("jahon", ignoreCase = true) == true) ||
                            t.title.contains("Jahon", ignoreCase = true) ||
                            (t.chapterTitle?.contains("Jahon", ignoreCase = true) == true) ||
                            (t.textbookId?.contains("jahon", ignoreCase = true) == true)

                    val inferredTextbookId = t.textbookId?.takeIf { it.isNotBlank() }
                        ?: if (inferredGrade == 6) "uz_6"
                        else if (isJahon) "jahon_$inferredGrade"
                        else "uz_$inferredGrade"

                    TopicEntity(
                        id = "topic_${t.id}",
                        chapterId = "ch_${t.categoryId}",
                        textbookId = inferredTextbookId,
                        number = t.order,
                        title = t.title,
                        content = t.content ?: "",
                        summary = t.chapterTitle ?: "",
                        gradeId = inferredGrade
                    )
                }
                entities.chunked(50).forEach { chunk ->
                    db.topicDao().insertAll(chunk)
                }
            }

            // Har bir kitob savollari alohida yuklanadi (syncBook / syncBooksForGrade)
            if (db.questionDao().count() == 0) {
                this@ContentRepository.syncBook(grade = 6, subjectId = "jahon")
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Har bir kitob (sinf + fan) savollari alohida-alohida yuklanadi: yuklama kamayadi
     * va foydalanuvchi faqat o‘zi tanlagan kitobni yuklab oladi.
     */
    suspend fun syncBook(grade: Int, subjectId: String, pageSize: Int = 1000): Int =
        withContext(Dispatchers.IO) {
            // 6-sinf darsligi («Qadimgi dunyo tarixi») backendda uzbekiston bucketiga tushadi
            val bookSubjectId = if (grade == 6) "uzbekiston" else subjectId
            val key = "book_${grade}_$bookSubjectId"
            if (bankPrefs.getBoolean(key, false) &&
                db.questionDao().countByGradeAndSubject(grade, bookSubjectId) > 0
            ) {
                return@withContext 0
            }
            val subjectParam = when {
                grade == 6 -> "qadimgi"
                subjectId == "jahon" -> "jahon"
                else -> "zbekiston"
            }

            var offset = 0
            var inserted = 0
            while (true) {
                val page = try {
                    apiService.getQuestions(
                        grade = grade,
                        subject = subjectParam,
                        limit = pageSize,
                        offset = offset
                    )
                } catch (e: Exception) {
                    break
                }
                if (page.isEmpty()) break
                page.map { it.toEntity() }
                    .chunked(500)
                    .forEach { chunk -> db.questionDao().insertAll(chunk) }
                inserted += page.size
                offset += page.size
                if (page.size < pageSize) break
            }
            if (inserted > 0) {
                bankPrefs.edit().putBoolean(key, true).apply()
            }
            inserted
        }

    suspend fun syncBooksForGrade(grade: Int) {
        if (grade <= 0) return
        syncBook(grade, "jahon")
        if (grade != 6) syncBook(grade, "uzbekiston")
    }

    fun isBookDownloaded(grade: Int, subjectId: String): Boolean =
        bankPrefs.getBoolean("book_${grade}_$subjectId", false)

    private fun com.tarixustoz.app.data.remote.ApiQuestionDto.toEntity(): QuestionEntity {
        val optionsList = listOf(
            AnswerOption("a", optionA, correctOption.equals("A", ignoreCase = true)),
            AnswerOption("b", optionB, correctOption.equals("B", ignoreCase = true)),
            AnswerOption("c", optionC, correctOption.equals("C", ignoreCase = true)),
            AnswerOption("d", optionD, correctOption.equals("D", ignoreCase = true))
        )
        return QuestionEntity(
            id = "q_$id",
            topicId = if (topicId != null) "topic_$topicId" else "general_topic",
            textbookId = "book_$gradeId",
            gradeId = gradeId,
            subjectId = subjectId ?: "uzbekiston",
            type = QuestionType.SINGLE_CHOICE.name,
            difficulty = difficulty,
            category = categoryType,
            text = text,
            optionsJson = json.encodeToString(optionsList),
            explanation = explanation ?: "Darslik va manba asosida tayyorlangan.",
            historicalDate = null,
            personality = null,
            sourceReference = "Django Backend API",
            pageHint = null
        )
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

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val hasNewTopics = db.topicDao().getById("topic_10243")
        if (hasNewTopics == null || db.textbookDao().count() < 11) {
            db.questionDao().deleteAll()
            db.topicDao().deleteAll()
            db.chapterDao().deleteAll()
            db.textbookDao().deleteAll()
            db.wrongQuestionDao().deleteAll()

            val assetJson = loadAssetJson("content.json")
            if (!assetJson.isNullOrBlank()) {
                importFromJsonString(assetJson)
            }
            if (db.textbookDao().count() < 11) {
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
            TextbookEntity("uz_6", "Tarix. Qadimgi dunyo", 6, "uzbekiston", "A.S. Sagdullayev, V.A. Kostetskiy", 2017, "5-nashr", 5, 44, 150, "file:///android_asset/images/uz_6_cover.png", "textbooks/6_sinf_tarix.pdf"),
            TextbookEntity("uz_7", "O‘zbekiston tarixi (IV–XVI asr)", 7, "uzbekiston", "A. Muhammadjonov", 2017, "3-nashr", 10, 35, 70, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/7-sinf_Ozbekiston_tarixi.pdf"),
            TextbookEntity("jahon_7", "Jahon tarixi (V–XV asr)", 7, "jahon", "T.O‘. Salimov, F.E. Sultonov", 2017, "3-nashr", 12, 40, 75, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/7-sinf_Jahon_tarixi.pdf"),
            TextbookEntity("uz_8", "O‘zbekiston tarixi (XVI–XIX asr I yarmi)", 8, "uzbekiston", "U. Jo‘rayev va b.", 2019, "4-nashr", 12, 45, 90, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/8-sinf_Ozbekiston_tarixi.pdf"),
            TextbookEntity("jahon_8", "Jahon tarixi (XVI–XIX asr)", 8, "jahon", "R. Farmonov, U. Jo‘rayev, Sh. Ergashev", 2019, "4-nashr", 10, 35, 80, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/8-sinf_Jahon_tarixi.pdf"),
            TextbookEntity("uz_9", "O‘zbekiston tarixi (XIX asr II yarmi – XX asr boshi)", 9, "uzbekiston", "S. Tillaboyev, A. Zamonov", 2019, "3-nashr", 10, 30, 70, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/9-sinf_Ozbekiston_tarixi.pdf"),
            TextbookEntity("jahon_9", "Jahon tarixi (XX asr boshi)", 9, "jahon", "M. Lafasov va b.", 2019, "4-nashr", 12, 40, 85, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/9-sinf_Jahon_tarixi.pdf"),
            TextbookEntity("uz_10", "O‘zbekiston tarixi (1917–1991)", 10, "uzbekiston", "Q. Rajabov, A. Zamonov", 2017, "1-nashr", 10, 30, 65, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/10-sinf_Ozbekiston_tarixi.pdf"),
            TextbookEntity("jahon_10", "Jahon tarixi (1918–1991)", 10, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2017, "1-nashr", 10, 30, 60, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/10-sinf_Jahon_tarixi.pdf"),
            TextbookEntity("uz_11", "O‘zbekiston tarixi (Mustaqillik davri)", 11, "uzbekiston", "N. Jo‘rayev, A. Zamonov", 2018, "1-nashr", 8, 25, 55, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/11-sinf_Ozbekiston_tarixi.pdf"),
            TextbookEntity("jahon_11", "Jahon tarixi (1991–2017)", 11, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2018, "1-nashr", 8, 25, 50, pdfAsset = "http://10.0.2.2:8000/media/books/pdfs/11-sinf_Jahon_tarixi.pdf")
        )
        db.textbookDao().insertAll(textbooks)

        val topics = listOf(
            // All 11 Official Textbooks (384 Curriculum Topics)
            // 6-sinf Qadimgi dunyo tarixi (42 topics) [tb_id=uz_6]
            TopicEntity("topic_uz_6_1", "ch_1", "uz_6", 1, "1-§. Qadimgi tarix - taraqqiyotning boshlanishi", "1-§. Qadimgi tarix - taraqqiyotning boshlanishi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_2", "ch_1", "uz_6", 2, "2-§. Eng qadimgi odamlarning rivojlanish bosqichlari", "2-§. Eng qadimgi odamlarning rivojlanish bosqichlari...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_3", "ch_1", "uz_6", 3, "3-§. Urug'chilik jamiyati", "3-§. Urug'chilik jamiyati...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_4", "ch_1", "uz_6", 4, "4-§. Eneolit va bronza davri", "4-§. Eneolit va bronza davri...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_5", "ch_1", "uz_6", 5, "5-§. Temir davriga o'tishda O'rta Osiyoning rivojlanishi", "5-§. Temir davriga o'tishda O'rta Osiyoning rivojlanishi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_6", "ch_1", "uz_6", 6, "6-§. Nil vodiysi va uning aholisi", "6-§. Nil vodiysi va uning aholisi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_7", "ch_1", "uz_6", 7, "7-§. Qadimgi Misr", "7-§. Qadimgi Misr...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_8", "ch_1", "uz_6", 8, "8-§. Qadimgi Misrda din", "8-§. Qadimgi Misrda din...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_9", "ch_1", "uz_6", 9, "9-§. Piramidalar va daxmalar", "9-§. Piramidalar va daxmalar...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_10", "ch_1", "uz_6", 10, "10-§. Qadimgi Misr madaniyati", "10-§. Qadimgi Misr madaniyati...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_11", "ch_1", "uz_6", 11, "11-§. Mesopotamiya sivilizatsiyalari", "11-§. Mesopotamiya sivilizatsiyalari...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_12", "ch_1", "uz_6", 12, "12-§. Bobil podsholigi 13-. Old Osiyo davlatlari", "12-§. Bobil podsholigi 13-. Old Osiyo davlatlari...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_13", "ch_1", "uz_6", 13, "14-§. Ahamoniylar davlati", "14-§. Ahamoniylar davlati...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_14", "ch_1", "uz_6", 14, "15-§. Qadimgi Hindiston sivilizatsiyasi", "15-§. Qadimgi Hindiston sivilizatsiyasi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_15", "ch_1", "uz_6", 15, "16-§. Miloddan avvalgi I ming yillikda Hindiston", "16-§. Miloddan avvalgi I ming yillikda Hindiston...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_16", "ch_1", "uz_6", 16, "17-§. Qadimgi Xitoy sivilizatsiyalari", "17-§. Qadimgi Xitoy sivilizatsiyalari...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_17", "ch_1", "uz_6", 17, "18-§. Miloddan avvalgi Ill-mil. Il asrlarda Xitoy", "18-§. Miloddan avvalgi Ill-mil. Il asrlarda Xitoy...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_18", "ch_1", "uz_6", 18, "19-§. O'zbekiston hududidagi ilk davlatlar", "19-§. O'zbekiston hududidagi ilk davlatlar...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_19", "ch_1", "uz_6", 19, "20-§. Zardushtiylik II-BO'LIM Qadimgi Yunoniston", "20-§. Zardushtiylik II-BO'LIM Qadimgi Yunoniston...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_20", "ch_1", "uz_6", 20, "21-§. Antik tarixning boshlanishi", "21-§. Antik tarixning boshlanishi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_21", "ch_1", "uz_6", 21, "22-§. Qadimgi Yunonistonning yuksalishi", "22-§. Qadimgi Yunonistonning yuksalishi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_22", "ch_1", "uz_6", 22, "23-§. Yunon koloniyalari", "23-§. Yunon koloniyalari...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_23", "ch_1", "uz_6", 23, "24-§. Afinada demokratiya,", "24-§. Afinada demokratiya,...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_24", "ch_1", "uz_6", 24, "25-§. Yunon-fors urushlari", "25-§. Yunon-fors urushlari...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_25", "ch_1", "uz_6", 25, "26-§. Yunonistonning Makedoniya tomonidan bosib olinishi", "26-§. Yunonistonning Makedoniya tomonidan bosib olinishi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_26", "ch_1", "uz_6", 26, "27-§. Fuqaro tarbiyasi", "27-§. Fuqaro tarbiyasi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_27", "ch_1", "uz_6", 27, "28-§. Qadimgi Yunoniston madaniyati", "28-§. Qadimgi Yunoniston madaniyati...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_28", "ch_1", "uz_6", 28, "29-§. Qadimgi Yunoniston olimlari va mutafakkirlari", "29-§. Qadimgi Yunoniston olimlari va mutafakkirlari...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_29", "ch_1", "uz_6", 29, "30-§. Qadimgi Yunoniston afsonalari. IV-BO'LIM Miloddan avvalgi Vl - milodiy lll asrlarda O'rta Osiyo", "30-§. Qadimgi Yunoniston afsonalari. IV-BO'LIM Miloddan avvalgi Vl - milodiy lll asrlarda O'rta Osiyo...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_30", "ch_1", "uz_6", 30, "31-§. O'rta Osiyoda ahamoniylar hukmronligi", "31-§. O'rta Osiyoda ahamoniylar hukmronligi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_31", "ch_1", "uz_6", 31, "32-§. O'rta Osiyo xalqlarining yunon-makedon istilochilariga qarshi kurashi", "32-§. O'rta Osiyo xalqlarining yunon-makedon istilochilariga qarshi kurashi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_32", "ch_1", "uz_6", 32, "33-§. Salavkiylar davlati va Yunon-Baqtriya podsholigi", "33-§. Salavkiylar davlati va Yunon-Baqtriya podsholigi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_33", "ch_1", "uz_6", 33, "34-§. Qadimgi Xorazm, Qang' va Davan davlati", "34-§. Qadimgi Xorazm, Qang' va Davan davlati...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_34", "ch_1", "uz_6", 34, "35-§. Kushon davlati", "35-§. Kushon davlati...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_35", "ch_1", "uz_6", 35, "36-§. Buyuk Ipak yo'li", "36-§. Buyuk Ipak yo'li...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_36", "ch_1", "uz_6", 36, "37-§. Qadimgi Italiya", "37-§. Qadimgi Italiya...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_37", "ch_1", "uz_6", 37, "38-§. Rim respublikasi", "38-§. Rim respublikasi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_38", "ch_1", "uz_6", 38, "39-§. Rim respublikasining hayoti", "39-§. Rim respublikasining hayoti...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_39", "ch_1", "uz_6", 39, "40-§. O'rtayer dengizida hukmronlik uchun kurash", "40-§. O'rtayer dengizida hukmronlik uchun kurash...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_40", "ch_1", "uz_6", 40, "41-§. Qullar va gladiatorlar", "41-§. Qullar va gladiatorlar...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_41", "ch_1", "uz_6", 41, "42-§. Rim respublikasining qulashi", "42-§. Rim respublikasining qulashi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            TopicEntity("topic_uz_6_42", "ch_1", "uz_6", 42, "43-§. Rim imperiyasining qulashi va zavolga yuz tutishi", "43-§. Rim imperiyasining qulashi va zavolga yuz tutishi...", "[]", "[]", "6-sinf Tarix (Qadimgi dunyo tarixi)", 6),
            // 7-sinf Jahon tarixi (39 topics) [tb_id=jahon_7]
            TopicEntity("topic_jahon_7_1", "ch_1", "jahon_7", 1, "1-§. German qabilalari va Rim imperiyasi", "1-§. German qabilalari va Rim imperiyasi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_2", "ch_1", "jahon_7", 2, "2-§. Franklar. Franklar davlatining tashkil topishi", "2-§. Franklar. Franklar davlatining tashkil topishi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_3", "ch_1", "jahon_7", 3, "3-§. Franklar imperiyasi", "3-§. Franklar imperiyasi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_4", "ch_1", "jahon_7", 4, "4-§. Britaniyadan Angliyaga", "4-§. Britaniyadan Angliyaga...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_5", "ch_1", "jahon_7", 5, "5-§. Muqaddas Rim imperiyasi 6-. Vizantiya: G'arb va Sharq orasida", "5-§. Muqaddas Rim imperiyasi 6-. Vizantiya: G'arb va Sharq orasida...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_6", "ch_1", "jahon_7", 6, "7-§. Slavyanlar va ularda davlatlarning tashkil topishi .33 8-. Sharqiy slavyanlar. Kiyev Rusi", "7-§. Slavyanlar va ularda davlatlarning tashkil topishi .33 8-. Sharqiy slavyanlar. Kiyev Rusi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_7", "ch_1", "jahon_7", 7, "9-§. Yevropa xalqlari madaniyati.. .41 II BOB. ILK O'RTA ASRLARDA OSIYO 10-. Arabiston ilk o'rta asrlar boshida", "9-§. Yevropa xalqlari madaniyati.. .41 II BOB. ILK O'RTA ASRLARDA OSIYO 10-. Arabiston ilk o'rta asrlar boshida...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_8", "ch_1", "jahon_7", 8, "11-§.. Arab xalifaligi", "11-§.. Arab xalifaligi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_9", "ch_1", "jahon_7", 9, "12-§.. Hindiston", "12-§.. Hindiston...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_10", "ch_1", "jahon_7", 10, "13-§.. Xitoy (IV-X asrlarda) 14-. Osiyo mamlakatlari madaniyati. III BOB. JAHON XALQLARI O'RTA ASRLARNING RIVOJLANGAN DAVRIDA (XI-XV ASRLAR)", "13-§.. Xitoy (IV-X asrlarda) 14-. Osiyo mamlakatlari madaniyati. III BOB. JAHON XALQLARI O'RTA ASRLARNING RIVOJLANGAN DAVRIDA (XI-XV ASRLAR)...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_11", "ch_1", "jahon_7", 11, "15-§.. Yevropada o'rta asr shaharlari", "15-§.. Yevropada o'rta asr shaharlari...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_12", "ch_1", "jahon_7", 12, "16-§.. O'rta asrlarda tovar ishlab chiqarishning yuksalishi. Bozor va yarmarkalar", "16-§.. O'rta asrlarda tovar ishlab chiqarishning yuksalishi. Bozor va yarmarkalar...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_13", "ch_1", "jahon_7", 13, "17-§.. Osiyo mamlakatlarining o'rta asr shaharlari", "17-§.. Osiyo mamlakatlarining o'rta asr shaharlari...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_14", "ch_1", "jahon_7", 14, "18-§. O'rta asrlarda xristianlik", "18-§. O'rta asrlarda xristianlik...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_15", "ch_1", "jahon_7", 15, "19-§.. Salib yurishlari", "19-§.. Salib yurishlari...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_16", "ch_1", "jahon_7", 16, "20-§. Dastlabki salib yurishlari", "20-§. Dastlabki salib yurishlari...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_17", "ch_1", "jahon_7", 17, "21-§. Keyingi salib yurishlari. IV BOB. YEVROPADA MARKAZLASHGAN DAVLATLARNING TASHKIL TOPISHI", "21-§. Keyingi salib yurishlari. IV BOB. YEVROPADA MARKAZLASHGAN DAVLATLARNING TASHKIL TOPISHI...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_18", "ch_1", "jahon_7", 18, "22-§.. Fransiyada markazlashgan davlatning tashkil topishi", "22-§.. Fransiyada markazlashgan davlatning tashkil topishi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_19", "ch_1", "jahon_7", 19, "23-§. General shtatlar", "23-§. General shtatlar...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_20", "ch_1", "jahon_7", 20, "24-§.. Yuz yillik urushning boshlanishi va uning dastlabki davri", "24-§.. Yuz yillik urushning boshlanishi va uning dastlabki davri...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_21", "ch_1", "jahon_7", 21, "25-§.. Fransiyada mutlaq monarxiya", "25-§.. Fransiyada mutlaq monarxiya...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_22", "ch_1", "jahon_7", 22, "26-§.. Angliyada markazlashgan davlatning tashkil topishi", "26-§.. Angliyada markazlashgan davlatning tashkil topishi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_23", "ch_1", "jahon_7", 23, "27-§. Angliya o'rta asrlar so'ngida", "27-§. Angliya o'rta asrlar so'ngida...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_24", "ch_1", "jahon_7", 24, "28-§.. Germaniya", "28-§.. Germaniya...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_25", "ch_1", "jahon_7", 25, "29-§.. Rus knyazliklari", "29-§.. Rus knyazliklari...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_26", "ch_1", "jahon_7", 26, "30-§. Ruslarning bosqinchilarga qarshi kurashi", "30-§. Ruslarning bosqinchilarga qarshi kurashi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_27", "ch_1", "jahon_7", 27, "31-§. Rus knyazliklarida markazlashish jarayonlari", "31-§. Rus knyazliklarida markazlashish jarayonlari...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_28", "ch_1", "jahon_7", 28, "32-§. Yevropa madaniyati . 33-. Yevropada me'morchilik, san'at va adabiyot V BOB. OSIYO, AMERIKA VA AFRIKA XALQLARI", "32-§. Yevropa madaniyati . 33-. Yevropada me'morchilik, san'at va adabiyot V BOB. OSIYO, AMERIKA VA AFRIKA XALQLARI...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_29", "ch_1", "jahon_7", 29, "34-§. Saljuqiylar davlati", "34-§. Saljuqiylar davlati...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_30", "ch_1", "jahon_7", 30, "35-§.. Usmonli turklar davlati", "35-§.. Usmonli turklar davlati...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_31", "ch_1", "jahon_7", 31, "36-§. Mo'g'ullar davlati", "36-§. Mo'g'ullar davlati...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_32", "ch_1", "jahon_7", 32, "37-§. Oltin O'rda xonligi", "37-§. Oltin O'rda xonligi...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_33", "ch_1", "jahon_7", 33, "38-§.. Xitoy", "38-§.. Xitoy...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_34", "ch_1", "jahon_7", 34, "39-§. Yaponiya", "39-§. Yaponiya...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_35", "ch_1", "jahon_7", 35, "40-§. Koreya :", "40-§. Koreya :...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_36", "ch_1", "jahon_7", 36, "41-§.. Hindiston", "41-§.. Hindiston...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_37", "ch_1", "jahon_7", 37, "42-§. Amerika o'rta asrlarda", "42-§. Amerika o'rta asrlarda...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_38", "ch_1", "jahon_7", 38, "43-§. Afrika xalqlari o'rta asrlarda", "43-§. Afrika xalqlari o'rta asrlarda...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            TopicEntity("topic_jahon_7_39", "ch_1", "jahon_7", 39, "44-§.. Osiyo, Amerika va Afrika xalqlari madaniyati. ..151 Muhim taixiy sanalar", "44-§.. Osiyo, Amerika va Afrika xalqlari madaniyati. ..151 Muhim taixiy sanalar...", "[]", "[]", "7-sinf Jahon tarixi", 7),
            // 7-sinf O'zbekiston tarixi (42 topics) [tb_id=uz_7]
            TopicEntity("topic_uz_7_1", "ch_1", "uz_7", 1, "1-§.. Ijtimoiy-iqtisodiy o'zgarishlar", "1-§.. Ijtimoiy-iqtisodiy o'zgarishlar...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_2", "ch_1", "uz_7", 2, "2-§. Ilk o'rta asrlarda Xorazm, Xioniylar, Kidariylar davlatlari", "2-§. Ilk o'rta asrlarda Xorazm, Xioniylar, Kidariylar davlatlari...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_3", "ch_1", "uz_7", 3, "3-§. Eftallar davlati. Eftallar davrida ijtimoiy-iqtisodiy va madaniy hayot", "3-§. Eftallar davlati. Eftallar davrida ijtimoiy-iqtisodiy va madaniy hayot...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_4", "ch_1", "uz_7", 4, "4-§.. O'rta Osiyo xalqlari Turk xoqonligi davrida", "4-§.. O'rta Osiyo xalqlari Turk xoqonligi davrida...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_5", "ch_1", "uz_7", 5, "5-§.. G'arbiy turk xoqonligi", "5-§.. G'arbiy turk xoqonligi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_6", "ch_1", "uz_7", 6, "6-§.. Mahalliy hokimliklarning tashkil topishi", "6-§.. Mahalliy hokimliklarning tashkil topishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_7", "ch_1", "uz_7", 7, "7-§. VI-VII asrlarda madaniy hayot.. .22 II BOB ARABXALIFALIGIDAVRIDA MOVAROUNNAHR", "7-§. VI-VII asrlarda madaniy hayot.. .22 II BOB ARABXALIFALIGIDAVRIDA MOVAROUNNAHR...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_8", "ch_1", "uz_7", 8, "8-§.. Movarounnahrda Arab xalifaligining o'rnatilishi", "8-§.. Movarounnahrda Arab xalifaligining o'rnatilishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_9", "ch_1", "uz_7", 9, "9-§. Movarounnahrda Islom dinining yoyilishi", "9-§. Movarounnahrda Islom dinining yoyilishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_10", "ch_1", "uz_7", 10, "10-§. Xalifalikka qarshi xalq noroziligi", "10-§. Xalifalikka qarshi xalq noroziligi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_11", "ch_1", "uz_7", 11, "11-§.. Abbosiylar davrida Xuroson va Movarounnahr. III BOB MOVAROUNNAHRDA MUSTAQILDAVLATLARNING TASHKIL TOPISHI", "11-§.. Abbosiylar davrida Xuroson va Movarounnahr. III BOB MOVAROUNNAHRDA MUSTAQILDAVLATLARNING TASHKIL TOPISHI...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_12", "ch_1", "uz_7", 12, "12-§.. Qarluqlar, o'g'uzlar, tohiriylar", "12-§.. Qarluqlar, o'g'uzlar, tohiriylar...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_13", "ch_1", "uz_7", 13, "13-§. Somoniylar", "13-§. Somoniylar...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_14", "ch_1", "uz_7", 14, "14-§. Somoniylar davrida ijtimoiy-iqtisodiy hayot", "14-§. Somoniylar davrida ijtimoiy-iqtisodiy hayot...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_15", "ch_1", "uz_7", 15, "15-§..G'aznaviylar", "15-§..G'aznaviylar...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_16", "ch_1", "uz_7", 16, "16-§..Qoraxoniylar", "16-§..Qoraxoniylar...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_17", "ch_1", "uz_7", 17, "17-§. Xorazm davlati va uning yuksalishi", "17-§. Xorazm davlati va uning yuksalishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_18", "ch_1", "uz_7", 18, "18-§. Xorazmshoh va Chingizxon munosabatlari", "18-§. Xorazmshoh va Chingizxon munosabatlari...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_19", "ch_1", "uz_7", 19, "19-§.. Muhammad Xorazmshohning mamlakat mudofaasiga oid tadbirlari va uning oqibati", "19-§.. Muhammad Xorazmshohning mamlakat mudofaasiga oid tadbirlari va uning oqibati...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_20", "ch_1", "uz_7", 20, "20-§.. Jaloliddin Manguberdining Xorazm taxtiga o'tirishi", "20-§.. Jaloliddin Manguberdining Xorazm taxtiga o'tirishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_21", "ch_1", "uz_7", 21, "21-§.. Jaloliddin Manguberdi mohir sarkarda", "21-§.. Jaloliddin Manguberdi mohir sarkarda...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_22", "ch_1", "uz_7", 22, "22-§.. Jaloliddin Manguberdi Vatan qahramoni", "22-§.. Jaloliddin Manguberdi Vatan qahramoni...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_23", "ch_1", "uz_7", 23, "23-§.. Chig'atoy ulusining tashkil topishi", "23-§.. Chig'atoy ulusining tashkil topishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_24", "ch_1", "uz_7", 24, "24-§. Ijtimoiy-iqtisodiy hayot", "24-§. Ijtimoiy-iqtisodiy hayot...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_25", "ch_1", "uz_7", 25, "25-§. Etnik jarayonlar va o'zbek xalqining shakllanishi. .88 IV BOB AJDODLARIMIZNING ILMIY MEROSI ABADIYATGA DAXLDOR MA'NAVIY XAZINA", "25-§. Etnik jarayonlar va o'zbek xalqining shakllanishi. .88 IV BOB AJDODLARIMIZNING ILMIY MEROSI ABADIYATGA DAXLDOR MA'NAVIY XAZINA...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_26", "ch_1", "uz_7", 26, "26-§.. Movarounnahr va Xorazmning madaniy hayoti", "26-§.. Movarounnahr va Xorazmning madaniy hayoti...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_27", "ch_1", "uz_7", 27, "27-§..Adabiyot", "27-§..Adabiyot...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_28", "ch_1", "uz_7", 28, "28-§.. Diniy bilimlarning rivojlanishi", "28-§.. Diniy bilimlarning rivojlanishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_29", "ch_1", "uz_7", 29, "29-§. Me'morchilik, san'at va musiqa. V BOB AMIR TEMUR VA TEMURIYLAR DAVRI TARIXI", "29-§. Me'morchilik, san'at va musiqa. V BOB AMIR TEMUR VA TEMURIYLAR DAVRI TARIXI...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_30", "ch_1", "uz_7", 30, "30-§. XIV asrning o'rtalarida Movarounnahrda ijtimoiy-siyosiy vaziyat", "30-§. XIV asrning o'rtalarida Movarounnahrda ijtimoiy-siyosiy vaziyat...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_31", "ch_1", "uz_7", 31, "31-§.. Amir Temur - markazlashgan davlat asoschisi", "31-§.. Amir Temur - markazlashgan davlat asoschisi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_32", "ch_1", "uz_7", 32, "32-§. Amir Temur saltanatida davlat boshqaruvi va harbiy tizim", "32-§. Amir Temur saltanatida davlat boshqaruvi va harbiy tizim...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_33", "ch_1", "uz_7", 33, "33-§.. Amir Temurning tashqi siyosati", "33-§.. Amir Temurning tashqi siyosati...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_34", "ch_1", "uz_7", 34, "34-§.. Amir Temurning jahon tarixida tutgan o'rni", "34-§.. Amir Temurning jahon tarixida tutgan o'rni...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_35", "ch_1", "uz_7", 35, "35-§. Temuriylar davridagi siyosiy jarayonlar", "35-§. Temuriylar davridagi siyosiy jarayonlar...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_36", "ch_1", "uz_7", 36, "36-§.. Mirzo Ulug'bekning hukmronligi davrida Movarounnahr", "36-§.. Mirzo Ulug'bekning hukmronligi davrida Movarounnahr...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_37", "ch_1", "uz_7", 37, "37-§.. Temuriylar saltanatining inqirozga yuz tutishi", "37-§.. Temuriylar saltanatining inqirozga yuz tutishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_38", "ch_1", "uz_7", 38, "38-§.. Ijtimoiy-iqtisodiy taraqqiyot va bunyodkorlik. .141 VI BOB SIVILIZATSIYA RIVOJIDAGI ROLI VA AHAMIYATI", "38-§.. Ijtimoiy-iqtisodiy taraqqiyot va bunyodkorlik. .141 VI BOB SIVILIZATSIYA RIVOJIDAGI ROLI VA AHAMIYATI...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_39", "ch_1", "uz_7", 39, "39-§.. Ta'lim tizimi", "39-§.. Ta'lim tizimi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_40", "ch_1", "uz_7", 40, "40-§. Aniq fanlarning rivojlanishi", "40-§. Aniq fanlarning rivojlanishi...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_41", "ch_1", "uz_7", 41, "41-§..Adabiyot", "41-§..Adabiyot...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            TopicEntity("topic_uz_7_42", "ch_1", "uz_7", 42, "42-§. Tasviriy san'at", "42-§. Tasviriy san'at...", "[]", "[]", "7-sinf O'zbekiston tarixi", 7),
            // 8-sinf Jahon tarixi (32 topics) [tb_id=jahon_8]
            TopicEntity("topic_jahon_8_1", "ch_1", "jahon_8", 1, "1-mavzu.: Buyuk geografik kashfiyotlar va ularning tarixiy ahamiyati", "1-mavzu.: Buyuk geografik kashfiyotlar va ularning tarixiy ahamiyati...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_2", "ch_1", "jahon_8", 2, "2-mavzu.: Yangi davr boshlarida G'arbiy Yevropa mamlakatlarida industrial jamiyatning shakllanishi", "2-mavzu.: Yangi davr boshlarida G'arbiy Yevropa mamlakatlarida industrial jamiyatning shakllanishi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_3", "ch_1", "jahon_8", 3, "3-mavzu.: Yevropada Reformatsiya", "3-mavzu.: Yevropada Reformatsiya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_4", "ch_1", "jahon_8", 4, "4-mavzu.: G'arbiy Yevropada Yangi davr madaniyatining shakllanishi. II BOB. XVI-XVIII ASRLARDA YEVROPA VA AMERIKA MAMLAKATLARI", "4-mavzu.: G'arbiy Yevropada Yangi davr madaniyatining shakllanishi. II BOB. XVI-XVIII ASRLARDA YEVROPA VA AMERIKA MAMLAKATLARI...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_5", "ch_1", "jahon_8", 5, "5-mavzu.: Angliyada qirol hokimiyatining kuchayishi. XVII asrdagi Angliya burjua inqilobi", "5-mavzu.: Angliyada qirol hokimiyatining kuchayishi. XVII asrdagi Angliya burjua inqilobi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_6", "ch_1", "jahon_8", 6, "6-mavzu.: XVI-XVIII asrlarda xalqaro munosabatlar", "6-mavzu.: XVI-XVIII asrlarda xalqaro munosabatlar...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_7", "ch_1", "jahon_8", 7, "7-mavzu.: Fransiyada mutlaq monarxiya. Buyuk fransuz burjua inqilobi", "7-mavzu.: Fransiyada mutlaq monarxiya. Buyuk fransuz burjua inqilobi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_8", "ch_1", "jahon_8", 8, "8-mavzu.: Buyuk fransuz burjua inqilobining yakunlanishi va tarixiy ahamiyati", "8-mavzu.: Buyuk fransuz burjua inqilobining yakunlanishi va tarixiy ahamiyati...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_9", "ch_1", "jahon_8", 9, "9-mavzu.: XVI-XVIII asrlarda Germaniya imperiyasi", "9-mavzu.: XVI-XVIII asrlarda Germaniya imperiyasi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_10", "ch_1", "jahon_8", 10, "10-mavzu.: XVI-XVIII asrlarda Rossiya imperiyasi", "10-mavzu.: XVI-XVIII asrlarda Rossiya imperiyasi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_11", "ch_1", "jahon_8", 11, "11-mavzu.: XVIII asrda Shimoliy Amerika. Amerika Qo'shma Shtatlarining tashkil topishi", "11-mavzu.: XVIII asrda Shimoliy Amerika. Amerika Qo'shma Shtatlarining tashkil topishi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_12", "ch_1", "jahon_8", 12, "12-mavzu.: Ma'rifat asri. III BOB. XVI-XVIII ASRLARDA OSIYO VA AFRIKA MAMLAKATLARI", "12-mavzu.: Ma'rifat asri. III BOB. XVI-XVIII ASRLARDA OSIYO VA AFRIKA MAMLAKATLARI...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_13", "ch_1", "jahon_8", 13, "13-mavzu.: XVI-XVIII asrlarda Osiyo mamlakatlari rivojlanishining asosiy xususiyatlari", "13-mavzu.: XVI-XVIII asrlarda Osiyo mamlakatlari rivojlanishining asosiy xususiyatlari...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_14", "ch_1", "jahon_8", 14, "14-mavzu.: XVI-XVIII asrlarda Xitoy", "14-mavzu.: XVI-XVIII asrlarda Xitoy...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_15", "ch_1", "jahon_8", 15, "15-mavzu.: XVI-XVIII asrlarda Hindiston", "15-mavzu.: XVI-XVIII asrlarda Hindiston...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_16", "ch_1", "jahon_8", 16, "16-mavzu.: XVI-XVIII asrlarda Yaponiya va Koreya", "16-mavzu.: XVI-XVIII asrlarda Yaponiya va Koreya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_17", "ch_1", "jahon_8", 17, "17-mavzu.: XVI-XVIII asrlarda Usmoniylar imperiyasi", "17-mavzu.: XVI-XVIII asrlarda Usmoniylar imperiyasi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_18", "ch_1", "jahon_8", 18, "1-§.-mavzu: XVI-XVIII asrlarda Eron", "1-§.-mavzu: XVI-XVIII asrlarda Eron...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_19", "ch_1", "jahon_8", 19, "19-mavzu.: XVI-XVIII asrlarda Afrika mamlakatlari. .92 IV BOB. 1800-1870-YILLARDA YEVROPA VA AMERIKA MAMLAKATLARI", "19-mavzu.: XVI-XVIII asrlarda Afrika mamlakatlari. .92 IV BOB. 1800-1870-YILLARDA YEVROPA VA AMERIKA MAMLAKATLARI...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_20", "ch_1", "jahon_8", 20, "20-mavzu.: 1800-1870-yillarda Buyuk Britaniya", "20-mavzu.: 1800-1870-yillarda Buyuk Britaniya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_21", "ch_1", "jahon_8", 21, "21-mavzu.: 1800-1870-yillarda Fransiya", "21-mavzu.: 1800-1870-yillarda Fransiya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_22", "ch_1", "jahon_8", 22, "22-mavzu.: 1800-1870-yillarda Germaniya va Italiya", "22-mavzu.: 1800-1870-yillarda Germaniya va Italiya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_23", "ch_1", "jahon_8", 23, "23-mavzu.: 1800-1870-yillarda Rossiya", "23-mavzu.: 1800-1870-yillarda Rossiya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_24", "ch_1", "jahon_8", 24, "24-mavzu.: 1800-1870-yillarda Amerika Qo'shma Shtatlari", "24-mavzu.: 1800-1870-yillarda Amerika Qo'shma Shtatlari...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_25", "ch_1", "jahon_8", 25, "25-mavzu.: Lotin Amerikasi xalqlarining milliy-ozodlik kurashi. V BOB. 1800-1870-YILLARDA OSIYO VA AFRIKA MAMLAKATLARI", "25-mavzu.: Lotin Amerikasi xalqlarining milliy-ozodlik kurashi. V BOB. 1800-1870-YILLARDA OSIYO VA AFRIKA MAMLAKATLARI...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_26", "ch_1", "jahon_8", 26, "26-mavzu.: 1800-1870-yillarda Hindiston", "26-mavzu.: 1800-1870-yillarda Hindiston...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_27", "ch_1", "jahon_8", 27, "27-mavzu.: 1800-1870-yillarda Xitoy", "27-mavzu.: 1800-1870-yillarda Xitoy...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_28", "ch_1", "jahon_8", 28, "2-§.-mavzu: 1800-1870-yillarda Yaponiya", "2-§.-mavzu: 1800-1870-yillarda Yaponiya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_29", "ch_1", "jahon_8", 29, "29-mavzu.: 1800-1870-yillarda Koreya", "29-mavzu.: 1800-1870-yillarda Koreya...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_30", "ch_1", "jahon_8", 30, "30-mavzu.: 1800-1870-yillarda Usm0niylar imperiyasi", "30-mavzu.: 1800-1870-yillarda Usm0niylar imperiyasi...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_31", "ch_1", "jahon_8", 31, "31-mavzu.: 1800-1870-yillarda Eron va Afg'oniston", "31-mavzu.: 1800-1870-yillarda Eron va Afg'oniston...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            TopicEntity("topic_jahon_8_32", "ch_1", "jahon_8", 32, "32-mavzu.: 1800-1870-yillarda Afrika xalqlari. Xulosa.. //edu", "32-mavzu.: 1800-1870-yillarda Afrika xalqlari. Xulosa.. //edu...", "[]", "[]", "8-sinf Jahon tarixi", 8),
            // 8-sinf O'zbekiston tarixi (40 topics) [tb_id=uz_8]
            TopicEntity("topic_uz_8_1", "ch_1", "uz_8", 1, "1-§. Dashti Qipchoqdagi siyosiy vaziyal", "1-§. Dashti Qipchoqdagi siyosiy vaziyal...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_2", "ch_1", "uz_8", 2, "2-§. Movarounnahr va Xurosondagi siyosiy vaziyal", "2-§. Movarounnahr va Xurosondagi siyosiy vaziyal...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_3", "ch_1", "uz_8", 3, "3-§. Zahiriddin Muhammad Bobur va Muhammad Shayboniyxon munosabatlari", "3-§. Zahiriddin Muhammad Bobur va Muhammad Shayboniyxon munosabatlari...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_4", "ch_1", "uz_8", 4, "4-§.. Movarounnahr va Xurosonda shayboniylar hukmronligining o'rnatilishi II BOB. BUXORO XONLIGI VA BUXORO AMIRLIGI", "4-§.. Movarounnahr va Xurosonda shayboniylar hukmronligining o'rnatilishi II BOB. BUXORO XONLIGI VA BUXORO AMIRLIGI...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_5", "ch_1", "uz_8", 5, "5-§. Buxoro xonligining lashkil lopishi", "5-§. Buxoro xonligining lashkil lopishi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_6", "ch_1", "uz_8", 6, "6-§. Abdullaxon II davrida Buxoro xonligining yuksalishi", "6-§. Abdullaxon II davrida Buxoro xonligining yuksalishi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_7", "ch_1", "uz_8", 7, "7-§. Buxoro xonligida ashtarxoniylar sulolasi hukmronligining o'rnatilishi", "7-§. Buxoro xonligida ashtarxoniylar sulolasi hukmronligining o'rnatilishi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_8", "ch_1", "uz_8", 8, "8-§.. Buxoro xonligida markaziy hokimiyatning zaiflashuvi", "8-§.. Buxoro xonligida markaziy hokimiyatning zaiflashuvi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_9", "ch_1", "uz_8", 9, "9-§. Buxoro xonligida davlat boshqaruvi", "9-§. Buxoro xonligida davlat boshqaruvi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_10", "ch_1", "uz_8", 10, "10-§. Buxoro xonligida harbiy ish", "10-§. Buxoro xonligida harbiy ish...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_11", "ch_1", "uz_8", 11, "11-§. Buxoro xonligida ijlimoiy-jqtisodiy hayot", "11-§. Buxoro xonligida ijlimoiy-jqtisodiy hayot...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_12", "ch_1", "uz_8", 12, "12-§.. Buxoro xonligining lashqi siyosali", "12-§.. Buxoro xonligining lashqi siyosali...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_13", "ch_1", "uz_8", 13, "13-§. Buxoro xonligida madaniy hayot", "13-§. Buxoro xonligida madaniy hayot...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_14", "ch_1", "uz_8", 14, "14-§. Buxoro amirligining tashkil topishi", "14-§. Buxoro amirligining tashkil topishi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_15", "ch_1", "uz_8", 15, "15-§. Amirlikda markaziy hokimiyatning mustahkamlanishi", "15-§. Amirlikda markaziy hokimiyatning mustahkamlanishi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_16", "ch_1", "uz_8", 16, "16-§.. Buxoro amirligida davlat boshqaruvi", "16-§.. Buxoro amirligida davlat boshqaruvi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_17", "ch_1", "uz_8", 17, "17-§.. Buxoro amirligida harbiy ish", "17-§.. Buxoro amirligida harbiy ish...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_18", "ch_1", "uz_8", 18, "18-§. Buxoro amirligida jjtimoiy-iqtisodiy hayot", "18-§. Buxoro amirligida jjtimoiy-iqtisodiy hayot...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_19", "ch_1", "uz_8", 19, "19-§. Amirlik shaharlari. Ichki va lashqi savdo", "19-§. Amirlik shaharlari. Ichki va lashqi savdo...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_20", "ch_1", "uz_8", 20, "20-§. Buxoro amirligining lashqi siyosali", "20-§. Buxoro amirligining lashqi siyosali...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_21", "ch_1", "uz_8", 21, "21-§. Buxoro amirligida madaniy hayo1 htt III BOB. XIVA XONLIGI", "21-§. Buxoro amirligida madaniy hayo1 htt III BOB. XIVA XONLIGI...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_22", "ch_1", "uz_8", 22, "22-§.. Xiva xonligining tashkil topishi", "22-§.. Xiva xonligining tashkil topishi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_23", "ch_1", "uz_8", 23, "23-§. XVII-XVIII asrning birinchi yarmida xonlikdagi siyosiy ahvol :", "23-§. XVII-XVIII asrning birinchi yarmida xonlikdagi siyosiy ahvol :...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_24", "ch_1", "uz_8", 24, "24-§.. Xiva xonligida qo'ng'irotlar sulolasi hukmronligining o'rnatilishi. XVIll asr oxiri - XIX asr birinchi yarmida xonlikning siyosiy ahvoli", "24-§.. Xiva xonligida qo'ng'irotlar sulolasi hukmronligining o'rnatilishi. XVIll asr oxiri - XIX asr birinchi yarmida xonlikning siyosiy ahvoli...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_25", "ch_1", "uz_8", 25, "25-§. Xiva xonligida davlal hoshgaruvi", "25-§. Xiva xonligida davlal hoshgaruvi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_26", "ch_1", "uz_8", 26, "26-§. Xiva xonligida ijtimoiy-iqisodiy hayol. Shaharlar hayoti", "26-§. Xiva xonligida ijtimoiy-iqisodiy hayol. Shaharlar hayoti...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_27", "ch_1", "uz_8", 27, "27-§.. Xiva xonligining tashqi siyosati", "27-§.. Xiva xonligining tashqi siyosati...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_28", "ch_1", "uz_8", 28, "2-§.- . Xiva xonligida madaniy hayot", "2-§.- . Xiva xonligida madaniy hayot...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_29", "ch_1", "uz_8", 29, "29-§. Xiva xonligi tarixi bo'yicha manbalar 30- . XVI-XIX asrning birinchi yarmida qoraqalpoqlar", "29-§. Xiva xonligi tarixi bo'yicha manbalar 30- . XVI-XIX asrning birinchi yarmida qoraqalpoqlar...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_30", "ch_1", "uz_8", 30, "31-§. Qoraqalpoqlarda madaniy va ma'naviy hayot IV BOB. XVII-XIX ASRNING BIRINCHI YARMIDA QO'QON XONLIGI", "31-§. Qoraqalpoqlarda madaniy va ma'naviy hayot IV BOB. XVII-XIX ASRNING BIRINCHI YARMIDA QO'QON XONLIGI...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_31", "ch_1", "uz_8", 31, "32-§.. Qo'qon xonligining tashkil topishi", "32-§.. Qo'qon xonligining tashkil topishi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_32", "ch_1", "uz_8", 32, "33-§.. XVIII asrning ikkinchi yarmi - XIX asrning birinchi yarmida Qo'qon xonligida siyosiy ahvol", "33-§.. XVIII asrning ikkinchi yarmi - XIX asrning birinchi yarmida Qo'qon xonligida siyosiy ahvol...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_33", "ch_1", "uz_8", 33, "34-§. Qo'qon xonligida davlat boshqaruvi", "34-§. Qo'qon xonligida davlat boshqaruvi...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_34", "ch_1", "uz_8", 34, "35-§.. Qo'qon xonligida harbiy ish", "35-§.. Qo'qon xonligida harbiy ish...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_35", "ch_1", "uz_8", 35, "36-§. Qo'qon xonligida ijlimoiy-iqlisodiy hayot", "36-§. Qo'qon xonligida ijlimoiy-iqlisodiy hayot...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_36", "ch_1", "uz_8", 36, "37-§. Qo'qon xonligining lashqi siyosati", "37-§. Qo'qon xonligining lashqi siyosati...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_37", "ch_1", "uz_8", 37, "38-§.. Qo'qon xonligida madaniy hayot", "38-§.. Qo'qon xonligida madaniy hayot...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_38", "ch_1", "uz_8", 38, "39-§.. Qo'qon xonligida ta'lim", "39-§.. Qo'qon xonligida ta'lim...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_39", "ch_1", "uz_8", 39, "40-§.. O'zbek davlatlarining taraqqiyotda orqada qolish sabablari va oqibatlari .150 Yakuniy dars (mustaqil ishlash)", "40-§.. O'zbek davlatlarining taraqqiyotda orqada qolish sabablari va oqibatlari .150 Yakuniy dars (mustaqil ishlash)...", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            TopicEntity("topic_uz_8_40", "ch_1", "uz_8", 40, "15-§.", "15-§....", "[]", "[]", "8-sinf O'zbekiston tarixi", 8),
            // 9-sinf Jahon tarixi (39 topics) [tb_id=jahon_9]
            TopicEntity("topic_jahon_9_1", "ch_1", "jahon_9", 1, "1-§. XIX asr oxiri - XX asr boshlarida kapitalizm taraqqiyotida yuz bergan tub o'zgarishlar", "1-§. XIX asr oxiri - XX asr boshlarida kapitalizm taraqqiyotida yuz bergan tub o'zgarishlar...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_2", "ch_1", "jahon_9", 2, "2-§. Industrial sivilizatsiya II bo lim YEVROPA VA AMERIKA DAVLATLARI II bob. G'ARBIY YEVROPA DAVLATLARI XIX ASR OXIRIVA XX ASR BOSHLARIDA", "2-§. Industrial sivilizatsiya II bo lim YEVROPA VA AMERIKA DAVLATLARI II bob. G'ARBIY YEVROPA DAVLATLARI XIX ASR OXIRIVA XX ASR BOSHLARIDA...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_3", "ch_1", "jahon_9", 3, "3-§. Fransiya-Prussiya urushi va uning yakunlari", "3-§. Fransiya-Prussiya urushi va uning yakunlari...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_4", "ch_1", "jahon_9", 4, "4-§. Fransiyada Uchinchi Respublika va 1871-yil 18-mart davlat to ntarishi", "4-§. Fransiyada Uchinchi Respublika va 1871-yil 18-mart davlat to ntarishi...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_5", "ch_1", "jahon_9", 5, "5-§. XIX asr oxiri - XX asr boshlarida Fransiyada iqtisodiy va siyosiy hayot", "5-§. XIX asr oxiri - XX asr boshlarida Fransiyada iqtisodiy va siyosiy hayot...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_6", "ch_1", "jahon_9", 6, "6-§. Fransiyaning ichki siyosati", "6-§. Fransiyaning ichki siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_7", "ch_1", "jahon_9", 7, "7-§. Fransiyaning tashqi siyosati", "7-§. Fransiyaning tashqi siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_8", "ch_1", "jahon_9", 8, "8-§. XIX asr oxiri - XX asr boshlarida Germaniya. Imperiyaning tashkil topishi", "8-§. XIX asr oxiri - XX asr boshlarida Germaniya. Imperiyaning tashkil topishi...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_9", "ch_1", "jahon_9", 9, "9-§. Imperiyaning ichki siyosati", "9-§. Imperiyaning ichki siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_10", "ch_1", "jahon_9", 10, "10-§. Imperiyaning tashqi siyosati", "10-§. Imperiyaning tashqi siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_11", "ch_1", "jahon_9", 11, "11-§. XIX asr oxiri - XX asr boshlarida Buyuk Britaniyaning siyosiy va iqtisodiy ahvoli", "11-§. XIX asr oxiri - XX asr boshlarida Buyuk Britaniyaning siyosiy va iqtisodiy ahvoli...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_12", "ch_1", "jahon_9", 12, "12-§. Buyuk Britaniyaning ichki siyosati", "12-§. Buyuk Britaniyaning ichki siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_13", "ch_1", "jahon_9", 13, "13-§.. Buyuk Britaniyaning tashqi siyosati III bob. XIX ASR OXIRI- XX ASR BOSHLARIDA AMERIKA DAVLATLARI", "13-§.. Buyuk Britaniyaning tashqi siyosati III bob. XIX ASR OXIRI- XX ASR BOSHLARIDA AMERIKA DAVLATLARI...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_14", "ch_1", "jahon_9", 14, "14-§. Amerika Qo'shma Shtatlari", "14-§. Amerika Qo'shma Shtatlari...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_15", "ch_1", "jahon_9", 15, "15-§.. AQSHning ichki siyosati", "15-§.. AQSHning ichki siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_16", "ch_1", "jahon_9", 16, "16-§.. AQSHning tashqi siyosati", "16-§.. AQSHning tashqi siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_17", "ch_1", "jahon_9", 17, "17-§. XIX asr oxiri va XX asr boshlarida Lotin Amerikasi davlatlari", "17-§. XIX asr oxiri va XX asr boshlarida Lotin Amerikasi davlatlari...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_18", "ch_1", "jahon_9", 18, "18-§. Lotin Amerikasining boshqa davlatlari IV bob. XIX ASR OXIRI - XX ASR BOSHLARIDA ROSSIYA IMPERIYASI, MARKAZIY VA JANUBI-SHARQIY YEVROPA DAVLATLARI ijtimoiy-siyosiy va iqtisodiy ahvoli", "18-§. Lotin Amerikasining boshqa davlatlari IV bob. XIX ASR OXIRI - XX ASR BOSHLARIDA ROSSIYA IMPERIYASI, MARKAZIY VA JANUBI-SHARQIY YEVROPA DAVLATLARI ijtimoiy-siyosiy va iqtisodiy ahvoli...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_19", "ch_1", "jahon_9", 19, "20-§. Rossiya imperiyasining tashqi siyosati", "20-§. Rossiya imperiyasining tashqi siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_20", "ch_1", "jahon_9", 20, "21-§. Rossiyada Davlat dumasining tashkil etilishi", "21-§. Rossiyada Davlat dumasining tashkil etilishi...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_21", "ch_1", "jahon_9", 21, "22-§. XIX asr oxiri - XX asr boshlarida Avstriya-Vengriya imperiyasi", "22-§. XIX asr oxiri - XX asr boshlarida Avstriya-Vengriya imperiyasi...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_22", "ch_1", "jahon_9", 22, "23-§. Avstriya- Vengriya imperiyasining tashqi siyosati", "23-§. Avstriya- Vengriya imperiyasining tashqi siyosati...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_23", "ch_1", "jahon_9", 23, "24-§.. XIX asr oxiri - XX asr boshlarida Italiya", "24-§.. XIX asr oxiri - XX asr boshlarida Italiya...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_24", "ch_1", "jahon_9", 24, "25-§. XIX asr oxiri - XX asr boshlarida Bolqon davlatlari III bo lim OSIYO VA AFRIKA DAVLATLARI V bob. XIX ASR OXIRI- XX ASR BOSHLARIDA OSIYO DAVLATLARI", "25-§. XIX asr oxiri - XX asr boshlarida Bolqon davlatlari III bo lim OSIYO VA AFRIKA DAVLATLARI V bob. XIX ASR OXIRI- XX ASR BOSHLARIDA OSIYO DAVLATLARI...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_25", "ch_1", "jahon_9", 25, "26-§. XIX asrning oxiri va XX asr boshlarida Yaponiya", "26-§. XIX asrning oxiri va XX asr boshlarida Yaponiya...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_26", "ch_1", "jahon_9", 26, "27-§. XIX asrning oxirida Xitoy", "27-§. XIX asrning oxirida Xitoy...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_27", "ch_1", "jahon_9", 27, "28-§. XX asr boshlarida Xitoyning ijtimoiy-iqtisodiy va siyosiy ahvoli", "28-§. XX asr boshlarida Xitoyning ijtimoiy-iqtisodiy va siyosiy ahvoli...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_28", "ch_1", "jahon_9", 28, "29-§. XIX asr oxiri - XX asr boshlarida Hindiston", "29-§. XIX asr oxiri - XX asr boshlarida Hindiston...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_29", "ch_1", "jahon_9", 29, "30-§. XIX asr oxiri - XX asr boshlarida Eron", "30-§. XIX asr oxiri - XX asr boshlarida Eron...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_30", "ch_1", "jahon_9", 30, "31-§. XIX asr oxiri - XX asr boshlarida Afg'oniston", "31-§. XIX asr oxiri - XX asr boshlarida Afg'oniston...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_31", "ch_1", "jahon_9", 31, "32-§. XIX asr oxiri - XX asr boshlarida Usmonli davlati. VI bob. XIX ASR OXIRI- XX ASR BOSHLARIDA AFRIKA MAMLAKATLARI", "32-§. XIX asr oxiri - XX asr boshlarida Usmonli davlati. VI bob. XIX ASR OXIRI- XX ASR BOSHLARIDA AFRIKA MAMLAKATLARI...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_32", "ch_1", "jahon_9", 32, "33-§.. Shimoliy Afrika mamlakatlari", "33-§.. Shimoliy Afrika mamlakatlari...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_33", "ch_1", "jahon_9", 33, "34-§. Tropik va Janubiy Afrika mamlakatlari VII bob. 1914 - 1918- YILLARDAGI BIRINCHI JAHON URUSHI", "34-§. Tropik va Janubiy Afrika mamlakatlari VII bob. 1914 - 1918- YILLARDAGI BIRINCHI JAHON URUSHI...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_34", "ch_1", "jahon_9", 34, "35-§. Birinchi jahon urushining boshlanishi va borishi", "35-§. Birinchi jahon urushining boshlanishi va borishi...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_35", "ch_1", "jahon_9", 35, "36-§. 1916 -1917- yillardagi urush harakatlari", "36-§. 1916 -1917- yillardagi urush harakatlari...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_36", "ch_1", "jahon_9", 36, "37-§. Birinchi jahon urushining yakunlari va oqibatlari IVbo‘lim FAN VA MADANIYAT VIII bob. XIX ASR OXIRI - XX ASRNING BOSHLARIDA FAN VA MADANIYATNING RIVOJLANISHI", "37-§. Birinchi jahon urushining yakunlari va oqibatlari IVbo‘lim FAN VA MADANIYAT VIII bob. XIX ASR OXIRI - XX ASRNING BOSHLARIDA FAN VA MADANIYATNING RIVOJLANISHI...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_37", "ch_1", "jahon_9", 37, "38-§. Moddiy ishlab chiqarishda texnika va fan taraqqiyoti 39- . Aniq va tabiiy fanlar rivoji 4O- s. Xal ta'limi. Gumanitar fanlar. San'at", "38-§. Moddiy ishlab chiqarishda texnika va fan taraqqiyoti 39- . Aniq va tabiiy fanlar rivoji 4O- s. Xal ta'limi. Gumanitar fanlar. San'at...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_38", "ch_1", "jahon_9", 38, "41-§. Adabiyot", "41-§. Adabiyot...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            TopicEntity("topic_jahon_9_39", "ch_1", "jahon_9", 39, "15-§. htt", "15-§. htt...", "[]", "[]", "9-sinf Jahon tarixi", 9),
            // 9-sinf O'zbekiston tarixi (37 topics) [tb_id=uz_9]
            TopicEntity("topic_uz_9_1", "ch_1", "uz_9", 1, "1-§. XIX asr o'rtalarida O'rta Osiyo davlatlarining hududi va aholisi", "1-§. XIX asr o'rtalarida O'rta Osiyo davlatlarining hududi va aholisi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_2", "ch_1", "uz_9", 2, "2-§.. XIX asr o'rtalarida o'zbek xonliklarining ma'muriy boshqaruv tizimi", "2-§.. XIX asr o'rtalarida o'zbek xonliklarining ma'muriy boshqaruv tizimi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_3", "ch_1", "uz_9", 3, "6-§. Rossiya imperiyasi tomonidan O'rta Osiyoni bosib olish uchun istilochilik harakatlarining boshlanishi", "6-§. Rossiya imperiyasi tomonidan O'rta Osiyoni bosib olish uchun istilochilik harakatlarining boshlanishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_4", "ch_1", "uz_9", 4, "7-§. Toshkentning bosib olinishi", "7-§. Toshkentning bosib olinishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_5", "ch_1", "uz_9", 5, "8-§.. Turkiston general-gubernatorligining tashkil etilishi", "8-§.. Turkiston general-gubernatorligining tashkil etilishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_6", "ch_1", "uz_9", 6, "9-§. Buxoro amirligiga qarshi harbiy harakatlarning boshlanishi", "9-§. Buxoro amirligiga qarshi harbiy harakatlarning boshlanishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_7", "ch_1", "uz_9", 7, "10-§. Buxoro amirligi ustidan Rossiya imperiyasi protektoratining o'rnatilishi", "10-§. Buxoro amirligi ustidan Rossiya imperiyasi protektoratining o'rnatilishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_8", "ch_1", "uz_9", 8, "11-§.. Xiva xonligi ustidan Rossiya imperiyasi protektoratining o'rnatilishi", "11-§.. Xiva xonligi ustidan Rossiya imperiyasi protektoratining o'rnatilishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_9", "ch_1", "uz_9", 9, "12-§.. Qo'qon xonligining bosib olinishi . II B O' L I M ROSSIYA IMPERIYASINING TURKISTONDAGI MUSTAMLAKACHILIK SIYOSATI", "12-§.. Qo'qon xonligining bosib olinishi . II B O' L I M ROSSIYA IMPERIYASINING TURKISTONDAGI MUSTAMLAKACHILIK SIYOSATI...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_10", "ch_1", "uz_9", 10, "13-§.. Turkiston general-gubernatorligining siyosiy-ma'muriy boshqaruv tizimi . . :", "13-§.. Turkiston general-gubernatorligining siyosiy-ma'muriy boshqaruv tizimi . . :...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_11", "ch_1", "uz_9", 11, "14-§. Turkiston o'lkasida Rossiya imperiyasining yer-suv siyosati", "14-§. Turkiston o'lkasida Rossiya imperiyasining yer-suv siyosati...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_12", "ch_1", "uz_9", 12, "15-§.. Turkistonda sud tizimi va harbiy politsiya tartibotining o'rnatilishi", "15-§.. Turkistonda sud tizimi va harbiy politsiya tartibotining o'rnatilishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_13", "ch_1", "uz_9", 13, "16-§. XIX asrning ikkinchi yarmi - XX asrning boshlarida Turkistonda ijtimoiy- iqtisodiy ahvol . IV B O' L I M XIX ASR OXIRIDA TURKISTONDAGI MILLIY-OZODLIK HARAKATLARI", "16-§. XIX asrning ikkinchi yarmi - XX asrning boshlarida Turkistonda ijtimoiy- iqtisodiy ahvol . IV B O' L I M XIX ASR OXIRIDA TURKISTONDAGI MILLIY-OZODLIK HARAKATLARI...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_14", "ch_1", "uz_9", 14, "17-§.. Turkistonda milliy-ozodlik harakatlarining boshlanishi va uning sabablari . : . :", "17-§.. Turkistonda milliy-ozodlik harakatlarining boshlanishi va uning sabablari . : . :...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_15", "ch_1", "uz_9", 15, "1-§.-. Toshkentda <Vabo isyoni>", "1-§.-. Toshkentda <Vabo isyoni>...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_16", "ch_1", "uz_9", 16, "19-§. Andijon qo'zgoloni", "19-§. Andijon qo'zgoloni...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_17", "ch_1", "uz_9", 17, "20-§.. Milliy-ozodlik harakatlarining ahamiyati VB O' L I M XIX ASRNING IKKINCHI YARMI - XX ASR BOSHLARIDA QORAQALPOQLAR", "20-§.. Milliy-ozodlik harakatlarining ahamiyati VB O' L I M XIX ASRNING IKKINCHI YARMI - XX ASR BOSHLARIDA QORAQALPOQLAR...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_18", "ch_1", "uz_9", 18, "21-§.. XIX asrning ikkinchi yarmi - XX asr boshlarida qoraqalpoqlarning hududiy joylashuvi va ijtimoiy hayoti", "21-§.. XIX asrning ikkinchi yarmi - XX asr boshlarida qoraqalpoqlarning hududiy joylashuvi va ijtimoiy hayoti...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_19", "ch_1", "uz_9", 19, "22-§.. Soliq va majburiyatlar. Xalq qo'zg'olonlari", "22-§.. Soliq va majburiyatlar. Xalq qo'zg'olonlari...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_20", "ch_1", "uz_9", 20, "23-§.. Qoraqalpoqlar Turkiston general-gubernatorligi tarkibida", "23-§.. Qoraqalpoqlar Turkiston general-gubernatorligi tarkibida...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_21", "ch_1", "uz_9", 21, "24-§. XIX asrning ikkinchi yarmi - XX asr boshlarida qoraqalpoqlar madaniyati . . . . 84 VI BO° L I M JADIDLAR HARAKATI VA UNING TURKISTON IJTIMOIY-SIYOSIY VA MADANIY HAYOTIDAGI AHAMIYATI", "24-§. XIX asrning ikkinchi yarmi - XX asr boshlarida qoraqalpoqlar madaniyati . . . . 84 VI BO° L I M JADIDLAR HARAKATI VA UNING TURKISTON IJTIMOIY-SIYOSIY VA MADANIY HAYOTIDAGI AHAMIYATI...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_22", "ch_1", "uz_9", 22, "25-§.. Jadidchilik harakatining vujudga kelishi", "25-§.. Jadidchilik harakatining vujudga kelishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_23", "ch_1", "uz_9", 23, "26-§.. Jadidlarning dasturiy maqsad va vazifalari", "26-§.. Jadidlarning dasturiy maqsad va vazifalari...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_24", "ch_1", "uz_9", 24, "27-§.. Jadidchilik harakati namoyandalari va ularning faoliyati", "27-§.. Jadidchilik harakati namoyandalari va ularning faoliyati...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_25", "ch_1", "uz_9", 25, "28-§.. Jadidchilik harakatining Turkiston ijtimoiy-siyosiy va madaniy hayotiga ta'siri. VII B O°L I M TURKISTON VA BIRINCHI JAHON URUSHI", "28-§.. Jadidchilik harakatining Turkiston ijtimoiy-siyosiy va madaniy hayotiga ta'siri. VII B O°L I M TURKISTON VA BIRINCHI JAHON URUSHI...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_26", "ch_1", "uz_9", 26, "29-§.. Birinchi jahon urushining boshlanishi va uning Turkistonga ta'siri", "29-§.. Birinchi jahon urushining boshlanishi va uning Turkistonga ta'siri...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_27", "ch_1", "uz_9", 27, "30-§.. Turkistonda 1916-yilgi qo'zg'olonning boshlanishi", "30-§.. Turkistonda 1916-yilgi qo'zg'olonning boshlanishi...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_28", "ch_1", "uz_9", 28, "31-§. Jizzax qo'zg'oloni", "31-§. Jizzax qo'zg'oloni...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_29", "ch_1", "uz_9", 29, "32-§.. Turkistonda 1916-yilgi voqealarning oqibatlari va ahamiyati", "32-§.. Turkistonda 1916-yilgi voqealarning oqibatlari va ahamiyati...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_30", "ch_1", "uz_9", 30, "10-§. VIII B O' L I M XIX ASR OXIRI - XX ASR BOSHLARIDA BUXORO AMIIRLIGI VA XIVA XONLIGI", "10-§. VIII B O' L I M XIX ASR OXIRI - XX ASR BOSHLARIDA BUXORO AMIIRLIGI VA XIVA XONLIGI...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_31", "ch_1", "uz_9", 31, "33-§.. Buxoro amirligining davlat tuzumi va ijtimoiy-iqtisodiy hayoti", "33-§.. Buxoro amirligining davlat tuzumi va ijtimoiy-iqtisodiy hayoti...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_32", "ch_1", "uz_9", 32, "34-§.. Yosh buxoroliklar faoliyati va Buxoro amirligining tugatilishi 35-36-ss. XIX asr oxiri - XX asr boshlarida Xiva xonligining davlat tuzumi va ijtimoiy-iqtisodiy ahvoli", "34-§.. Yosh buxoroliklar faoliyati va Buxoro amirligining tugatilishi 35-36-ss. XIX asr oxiri - XX asr boshlarida Xiva xonligining davlat tuzumi va ijtimoiy-iqtisodiy ahvoli...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_33", "ch_1", "uz_9", 33, "37-§.. Xiva xonligining tugatilishi IX B O' L I M MUSTAMLAKA SHAROITIDA TURKISTONDA MADANIY HAYOT", "37-§.. Xiva xonligining tugatilishi IX B O' L I M MUSTAMLAKA SHAROITIDA TURKISTONDA MADANIY HAYOT...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_34", "ch_1", "uz_9", 34, "38-§.. Ta'lim tizimi. Matbuot", "38-§.. Ta'lim tizimi. Matbuot...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_35", "ch_1", "uz_9", 35, "39-§. Turkistonda ilm-fan taraqqiyoti", "39-§. Turkistonda ilm-fan taraqqiyoti...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_36", "ch_1", "uz_9", 36, "40-§. Teatr va musiqa san'ati", "40-§. Teatr va musiqa san'ati...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            TopicEntity("topic_uz_9_37", "ch_1", "uz_9", 37, "41-§.. Me'morchilik va tasviriy san'at. Xulosa", "41-§.. Me'morchilik va tasviriy san'at. Xulosa...", "[]", "[]", "9-sinf O'zbekiston tarixi", 9),
            // 10-sinf Jahon tarixi (35 topics) [tb_id=jahon_10]
            TopicEntity("topic_jahon_10_1", "ch_1", "jahon_10", 1, "1-mavzu.", "1-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_2", "ch_1", "jahon_10", 2, "191-§.-1939-yillarda xalqaro munosabatlar", "191-§.-1939-yillarda xalqaro munosabatlar...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_3", "ch_1", "jahon_10", 3, "2-mavzu.", "2-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_4", "ch_1", "jahon_10", 4, "191-§.-1939-yillarda ilmiy-texnik taraqqiyotning jadallashuvi, ilm-fandagi yutuqlar", "191-§.-1939-yillarda ilmiy-texnik taraqqiyotning jadallashuvi, ilm-fandagi yutuqlar...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_5", "ch_1", "jahon_10", 5, "191-§.-1939-yillarda Buyuk Britaniya", "191-§.-1939-yillarda Buyuk Britaniya...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_6", "ch_1", "jahon_10", 6, "3-mavzu.", "3-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_7", "ch_1", "jahon_10", 7, "4-mavzu.", "4-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_8", "ch_1", "jahon_10", 8, "191-§.-1939-yillarda Fransiya", "191-§.-1939-yillarda Fransiya...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_9", "ch_1", "jahon_10", 9, "191-§.-1939-yillarda Amerika Qo'shma Shtatlari", "191-§.-1939-yillarda Amerika Qo'shma Shtatlari...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_10", "ch_1", "jahon_10", 10, "5-mavzu.", "5-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_11", "ch_1", "jahon_10", 11, "191-§.-1939-yillarda Germaniya", "191-§.-1939-yillarda Germaniya...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_12", "ch_1", "jahon_10", 12, "6-mavzu.", "6-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_13", "ch_1", "jahon_10", 13, "7-§.-mavzular. 1918-1939-yillarda SSSR", "7-§.-mavzular. 1918-1939-yillarda SSSR...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_14", "ch_1", "jahon_10", 14, "191-§.-1939-yillarda Italiya va Ispaniya", "191-§.-1939-yillarda Italiya va Ispaniya...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_15", "ch_1", "jahon_10", 15, "9-mavzu.", "9-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_16", "ch_1", "jahon_10", 16, "10-mavzu.", "10-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_17", "ch_1", "jahon_10", 17, "191-§.-1939-yillarda Lotin Amerikasi mamlakatlari", "191-§.-1939-yillarda Lotin Amerikasi mamlakatlari...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_18", "ch_1", "jahon_10", 18, "191-§.-1939-yillarda Yaponiya", "191-§.-1939-yillarda Yaponiya...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_19", "ch_1", "jahon_10", 19, "11-mavzu.", "11-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_20", "ch_1", "jahon_10", 20, "12-mavzu.", "12-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_21", "ch_1", "jahon_10", 21, "191-§. - 1939-yillarda Xitoy va Hindiston Eron", "191-§. - 1939-yillarda Xitoy va Hindiston Eron...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_22", "ch_1", "jahon_10", 22, "191-§. - 1939-yillarda", "191-§. - 1939-yillarda...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_23", "ch_1", "jahon_10", 23, "13-mavzu.. Turkiya va", "13-mavzu.. Turkiya va...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_24", "ch_1", "jahon_10", 24, "14-mavzu.", "14-mavzu....", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_25", "ch_1", "jahon_10", 25, "191-§. -1939-yillarda Afrika mamlakatlari .62 15-16-mavzular. Ikkinchi jahon urushi va uning yakunlari", "191-§. -1939-yillarda Afrika mamlakatlari .62 15-16-mavzular. Ikkinchi jahon urushi va uning yakunlari...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_26", "ch_1", "jahon_10", 26, "17-mavzu.. Ikkinchi jahon urushidan keyingi xalqaro munosabatlar: <sovuq urush>ning boshlanishi", "17-mavzu.. Ikkinchi jahon urushidan keyingi xalqaro munosabatlar: <sovuq urush>ning boshlanishi...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_27", "ch_1", "jahon_10", 27, "1-§.-mavzu. 1946-1991-yillarda Amerika Qo'shma Shtatlari .81 19-20-mavzular. 1946-1991-yillarda Sovet Ittifoqi", "1-§.-mavzu. 1946-1991-yillarda Amerika Qo'shma Shtatlari .81 19-20-mavzular. 1946-1991-yillarda Sovet Ittifoqi...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_28", "ch_1", "jahon_10", 28, "21-mavzu.. J 1946-1991-yillarda Buyuk Britaniya va Fransiya... 1946-1991-yillarda Italiya", "21-mavzu.. J 1946-1991-yillarda Buyuk Britaniya va Fransiya... 1946-1991-yillarda Italiya...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_29", "ch_1", "jahon_10", 29, "22-mavzu.. Germaniya va", "22-mavzu.. Germaniya va...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_30", "ch_1", "jahon_10", 30, "23-mavzu.. 1946-1991-yillarda Yaponiya va Osiyoning yangi industrial mamlakatlari. 1946- 1991-yillarda Xitoy Xalq", "23-mavzu.. 1946-1991-yillarda Yaponiya va Osiyoning yangi industrial mamlakatlari. 1946- 1991-yillarda Xitoy Xalq...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_31", "ch_1", "jahon_10", 31, "24-mavzu.. Respublikasi", "24-mavzu.. Respublikasi...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_32", "ch_1", "jahon_10", 32, "25-mavzu.. Pokiston . Hindiston 1946-1991-yillarda va Turkiya,", "25-mavzu.. Pokiston . Hindiston 1946-1991-yillarda va Turkiya,...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_33", "ch_1", "jahon_10", 33, "26-mavzu.. 1946- 1991-yillarda Eron va Afg'oniston.. 1946-1991-yillarda", "26-mavzu.. 1946- 1991-yillarda Eron va Afg'oniston.. 1946-1991-yillarda...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_34", "ch_1", "jahon_10", 34, "27-mavzu.. Lotin Amerikasi mamlakatlari", "27-mavzu.. Lotin Amerikasi mamlakatlari...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            TopicEntity("topic_jahon_10_35", "ch_1", "jahon_10", 35, "2-§.-mavzu. Afrika 1946-1991-yillarda mamlakatlari", "2-§.-mavzu. Afrika 1946-1991-yillarda mamlakatlari...", "[]", "[]", "10-sinf Jahon tarixi", 10),
            // 10-sinf O'zbekiston tarixi (28 topics) [tb_id=uz_10]
            TopicEntity("topic_uz_10_1", "ch_1", "uz_10", 1, "1-§.. Turkiston o'lkasi 1917-yil fevral - oktyabr oralig'ida", "1-§.. Turkiston o'lkasi 1917-yil fevral - oktyabr oralig'ida...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_2", "ch_1", "uz_10", 2, "2-§. Turkistonda sovet hokimiyatining majburiy tarzda o'rnatilishi hamda bolsheviklar diktaturasi", "2-§. Turkistonda sovet hokimiyatining majburiy tarzda o'rnatilishi hamda bolsheviklar diktaturasi...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_3", "ch_1", "uz_10", 3, "3-§. Turkiston Muxtoriyati - tub yerli xalqlar davlatchiligi tarixida yangi bosqich", "3-§. Turkiston Muxtoriyati - tub yerli xalqlar davlatchiligi tarixida yangi bosqich...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_4", "ch_1", "uz_10", 4, "4-§. Turkiston o'lkasida sovet hokimiyati boshqaruv tizimining tuzilishi va iqtisodiyot sohasidagi o'zgarishlar", "4-§. Turkiston o'lkasida sovet hokimiyati boshqaruv tizimining tuzilishi va iqtisodiyot sohasidagi o'zgarishlar...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_5", "ch_1", "uz_10", 5, "5-§.. Turkiston ASSRda sovet hokimiyatiga qarshi qurolli harakatlar", "5-§.. Turkiston ASSRda sovet hokimiyatiga qarshi qurolli harakatlar...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_6", "ch_1", "uz_10", 6, "6-§. Buxoro amirligida islohotlar uchun kurash hamda qizil armiya tomonidan amir hokimiyatining ag' darilishi. BXSRning tashkil topishi va faoliyati", "6-§. Buxoro amirligida islohotlar uchun kurash hamda qizil armiya tomonidan amir hokimiyatining ag' darilishi. BXSRning tashkil topishi va faoliyati...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_7", "ch_1", "uz_10", 7, "7-§. Xiva xonligida ahvol hamda xon hokimiyatining ag'darilishi. XXSRning tuzilishi va turli islohotlar", "7-§. Xiva xonligida ahvol hamda xon hokimiyatining ag'darilishi. XXSRning tuzilishi va turli islohotlar...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_8", "ch_1", "uz_10", 8, "8-§.. BXSR va XXSRda qizil armiyaga qarshi kurash", "8-§.. BXSR va XXSRda qizil armiyaga qarshi kurash...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_9", "ch_1", "uz_10", 9, "9-§.. O'rta Osiyo respublikalarida o'tkazilgan milliy-hududiy chegaralanish va uning oqibatlari Ikkinchi bob. Sovet hokimiyatining O'zbekistonda yuritgan siyosati (1925 - 1939-yillar)", "9-§.. O'rta Osiyo respublikalarida o'tkazilgan milliy-hududiy chegaralanish va uning oqibatlari Ikkinchi bob. Sovet hokimiyatining O'zbekistonda yuritgan siyosati (1925 - 1939-yillar)...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_10", "ch_1", "uz_10", 10, "10-§. O'zbekiston SSRning tashkil topishi", "10-§. O'zbekiston SSRning tashkil topishi...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_11", "ch_1", "uz_10", 11, "11-§. O'zbekistonda qishloq xo'jaligini kollektivlashtirish siyosati hamda quloqlarning surgun qilinishi", "11-§. O'zbekistonda qishloq xo'jaligini kollektivlashtirish siyosati hamda quloqlarning surgun qilinishi...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_12", "ch_1", "uz_10", 12, "12-§. O'zbekistonda industrlashtirish siyosatining amalga oshirilishi va kadrlar masalasi", "12-§. O'zbekistonda industrlashtirish siyosatining amalga oshirilishi va kadrlar masalasi...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_13", "ch_1", "uz_10", 13, "13-§. O°zbek xotin-qizlarining ozodlikkachiqarish jarayoni va muammolar", "13-§. O°zbek xotin-qizlarining ozodlikkachiqarish jarayoni va muammolar...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_14", "ch_1", "uz_10", 14, "14-§.. O'zbekistonda sovet hokimiyati yuritgan madaniy siyosat", "14-§.. O'zbekistonda sovet hokimiyati yuritgan madaniy siyosat...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_15", "ch_1", "uz_10", 15, "15-§.. Sovet hokimiyatining O'zbekistondagi qatag'onlik siyosati: uning mohiyati va oqibatlari . Uchinchi bob. O'zbekiston Ikkinchi jahon urushi yillarida (1939 - 1945-yillar)", "15-§.. Sovet hokimiyatining O'zbekistondagi qatag'onlik siyosati: uning mohiyati va oqibatlari . Uchinchi bob. O'zbekiston Ikkinchi jahon urushi yillarida (1939 - 1945-yillar)...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_16", "ch_1", "uz_10", 16, "16-§. Ikkinchi jahon urushining boshlanishi hamda O'zbekistonning urush girdobiga tortilishi", "16-§. Ikkinchi jahon urushining boshlanishi hamda O'zbekistonning urush girdobiga tortilishi...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_17", "ch_1", "uz_10", 17, "17-§. O'zbekiston sanoati va qishloq xo'jaligi front xizmatida", "17-§. O'zbekiston sanoati va qishloq xo'jaligi front xizmatida...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_18", "ch_1", "uz_10", 18, "18-§. O'zbekistonliklarning frontdagi jasorati va qahramonliklari", "18-§. O'zbekistonliklarning frontdagi jasorati va qahramonliklari...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_19", "ch_1", "uz_10", 19, "19-§.. O'zbekiston fani va madaniyati - g'alaba uchun xizmatda ...... 101 To'rtinchi bob. O'zbekistonning ijtimoiy-iqtisodiy ahvoli va rivojlanish muammolari (1945 - 1959-yillar)", "19-§.. O'zbekiston fani va madaniyati - g'alaba uchun xizmatda ...... 101 To'rtinchi bob. O'zbekistonning ijtimoiy-iqtisodiy ahvoli va rivojlanish muammolari (1945 - 1959-yillar)...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_20", "ch_1", "uz_10", 20, "20-§.. O'zbekiston xalq xo'jaligining urushdan keyingi ahvoli", "20-§.. O'zbekiston xalq xo'jaligining urushdan keyingi ahvoli...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_21", "ch_1", "uz_10", 21, "21-§. Ziyolilarning qatag'on qilinishi va sovet jamiyatida shaxsga sig'inishning fosh qilinishi", "21-§. Ziyolilarning qatag'on qilinishi va sovet jamiyatida shaxsga sig'inishning fosh qilinishi...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_22", "ch_1", "uz_10", 22, "22-§.. O'zbekistonda sanoatning bir tomonlama rivojlanishi Beshinchi bob. O'zbekiston barqaror taraqqiyot davrida (1959 - 1983-yillar)", "22-§.. O'zbekistonda sanoatning bir tomonlama rivojlanishi Beshinchi bob. O'zbekiston barqaror taraqqiyot davrida (1959 - 1983-yillar)...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_23", "ch_1", "uz_10", 23, "23-§.. O'zbekistonning siyosiy hayotidagi o'zgarishlar hamda sovetlarning partiya organlariga qaramligi", "23-§.. O'zbekistonning siyosiy hayotidagi o'zgarishlar hamda sovetlarning partiya organlariga qaramligi...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_24", "ch_1", "uz_10", 24, "24-§. O'zbekistonda yoqilg'i va tog'-kon sanoatining vujudga keltirilishi hamda aholi milliy tarkibidagi o'zgarishlar", "24-§. O'zbekistonda yoqilg'i va tog'-kon sanoatining vujudga keltirilishi hamda aholi milliy tarkibidagi o'zgarishlar...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_25", "ch_1", "uz_10", 25, "25-§.. O'zbekistonda ekologik vaziyatning og' irlashuvi va Orol fojiasi .. 132 Oltinchi bob. O'zbekistonda sovet hokimiyatining inqirozi va mustaqillik uchun harakatlar (1984 - 1991-yillar)", "25-§.. O'zbekistonda ekologik vaziyatning og' irlashuvi va Orol fojiasi .. 132 Oltinchi bob. O'zbekistonda sovet hokimiyatining inqirozi va mustaqillik uchun harakatlar (1984 - 1991-yillar)...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_26", "ch_1", "uz_10", 26, "26-§.. O'zbekistonda paxta ishi va uning oqibatlari", "26-§.. O'zbekistonda paxta ishi va uning oqibatlari...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_27", "ch_1", "uz_10", 27, "27-§. Qayta qurish siyosati va uning inqirozi. Farg'ona fojialari", "27-§. Qayta qurish siyosati va uning inqirozi. Farg'ona fojialari...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            TopicEntity("topic_uz_10_28", "ch_1", "uz_10", 28, "28-§.. O'zbekiston mustaqillikka erishish ostonasida", "28-§.. O'zbekiston mustaqillikka erishish ostonasida...", "[]", "[]", "10-sinf O'zbekiston tarixi", 10),
            // 11-sinf Jahon tarixi (30 topics) [tb_id=jahon_11]
            TopicEntity("topic_jahon_11_1", "ch_1", "jahon_11", 1, "1-mavzu.. XX asr oxiri-XXI asr boshlarida xalqaro munosabatlar", "1-mavzu.. XX asr oxiri-XXI asr boshlarida xalqaro munosabatlar...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_2", "ch_1", "jahon_11", 2, "2-mavzu.. Sharqiy Yevropa mamlakatlarida demokratik inqiloblar va sotsialistik lagerning parchalanishi", "2-mavzu.. Sharqiy Yevropa mamlakatlarida demokratik inqiloblar va sotsialistik lagerning parchalanishi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_3", "ch_1", "jahon_11", 3, "3-mavzu.. Sovet davlatining parchalanishi va sobiq sovet respublikalarida mustaqillikning e'lon qilinishi. BOB. 1991 - 2017-YILLARDA DUNYO MAMLAKATLARI", "3-mavzu.. Sovet davlatining parchalanishi va sobiq sovet respublikalarida mustaqillikning e'lon qilinishi. BOB. 1991 - 2017-YILLARDA DUNYO MAMLAKATLARI...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_4", "ch_1", "jahon_11", 4, "4-mavzu.. 1991 - 2017-yillarda Rossiya Federatsiyasi", "4-mavzu.. 1991 - 2017-yillarda Rossiya Federatsiyasi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_5", "ch_1", "jahon_11", 5, "5-mavzu.. 1991 - 2017-yillarda Ukraina, Belarus va Moldova respublikalari", "5-mavzu.. 1991 - 2017-yillarda Ukraina, Belarus va Moldova respublikalari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_6", "ch_1", "jahon_11", 6, "6-mavzu.. 1991 - 2017-yillarda Boltiqbo'yi davlatlari", "6-mavzu.. 1991 - 2017-yillarda Boltiqbo'yi davlatlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_7", "ch_1", "jahon_11", 7, "7-mavzu.. 1991 - 2017-yillarda Kavkazorti davlatlari", "7-mavzu.. 1991 - 2017-yillarda Kavkazorti davlatlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_8", "ch_1", "jahon_11", 8, "8-mavzu.. 1991 - 2017-yillarda Markaziy Osiyo davlatlari", "8-mavzu.. 1991 - 2017-yillarda Markaziy Osiyo davlatlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_9", "ch_1", "jahon_11", 9, "9-mavzu.. G'arb mamlakatlarida integratsiyalashuv jarayonlarining jadallashuvi. Yevropa Ittifoqi va AQSH munosabatlari", "9-mavzu.. G'arb mamlakatlarida integratsiyalashuv jarayonlarining jadallashuvi. Yevropa Ittifoqi va AQSH munosabatlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_10", "ch_1", "jahon_11", 10, "10-mavzu.. 1991 - 2017-yillarda Qo'shma Shtatlari Amerika", "10-mavzu.. 1991 - 2017-yillarda Qo'shma Shtatlari Amerika...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_11", "ch_1", "jahon_11", 11, "11-mavzu.. 1991 - 2017-yillarda Germaniya Federativ Respublikasi", "11-mavzu.. 1991 - 2017-yillarda Germaniya Federativ Respublikasi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_12", "ch_1", "jahon_11", 12, "12-mavzu.. 1991 - 2017-yillarda Buyuk Britaniya", "12-mavzu.. 1991 - 2017-yillarda Buyuk Britaniya...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_13", "ch_1", "jahon_11", 13, "13-mavzu.. 1991 - 2017-yillarda Fransiya", "13-mavzu.. 1991 - 2017-yillarda Fransiya...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_14", "ch_1", "jahon_11", 14, "14-mavzu.. 1991 - 2017-yillarda Italiya", "14-mavzu.. 1991 - 2017-yillarda Italiya...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_15", "ch_1", "jahon_11", 15, "15-mavzu.. ( Osiyo, Afrika va Lotin Amerikasi mamlakatlari siyosiy, ijtimoiy-iqtisodiy rivojlanishining asosiy yo'nalishlari", "15-mavzu.. ( Osiyo, Afrika va Lotin Amerikasi mamlakatlari siyosiy, ijtimoiy-iqtisodiy rivojlanishining asosiy yo'nalishlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_16", "ch_1", "jahon_11", 16, "16-mavzu.. 1991 - 2017-yillarda Xitoy Xalq Respublikasi", "16-mavzu.. 1991 - 2017-yillarda Xitoy Xalq Respublikasi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_17", "ch_1", "jahon_11", 17, "17-mavzu.. 1991 - 2017-yillarda Yaponiya .. Janubi-Sharqiy", "17-mavzu.. 1991 - 2017-yillarda Yaponiya .. Janubi-Sharqiy...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_18", "ch_1", "jahon_11", 18, "1-§.-mavzu. 1991 - 2017-yillarda Osiyo mamlakatlari", "1-§.-mavzu. 1991 - 2017-yillarda Osiyo mamlakatlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_19", "ch_1", "jahon_11", 19, "19-mavzu.. 1991 - 2017-yillarda Hindiston Respublikasi", "19-mavzu.. 1991 - 2017-yillarda Hindiston Respublikasi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_20", "ch_1", "jahon_11", 20, "20-mavzu.. 1991 - 2017-yillarda Turkiya Respublikasi", "20-mavzu.. 1991 - 2017-yillarda Turkiya Respublikasi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_21", "ch_1", "jahon_11", 21, "21-mavzu.. 1991 - 2017-yillarda Eron Islom Respublikasi", "21-mavzu.. 1991 - 2017-yillarda Eron Islom Respublikasi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_22", "ch_1", "jahon_11", 22, "22-mavzu.. 1991 - 2017-yillarda Pokiston va Afg'oniston", "22-mavzu.. 1991 - 2017-yillarda Pokiston va Afg'oniston...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_23", "ch_1", "jahon_11", 23, "23-mavzu.. 1991 - 2017-yillarda Suriya va Iroq", "23-mavzu.. 1991 - 2017-yillarda Suriya va Iroq...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_24", "ch_1", "jahon_11", 24, "24-mavzu.. 1991 - 2017-yillarda Isroil davlati va Falastin muammosi", "24-mavzu.. 1991 - 2017-yillarda Isroil davlati va Falastin muammosi...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_25", "ch_1", "jahon_11", 25, "25-mavzu.. 1991 - 2017-yillarda Amerikasi Lotin mamlakatlari", "25-mavzu.. 1991 - 2017-yillarda Amerikasi Lotin mamlakatlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_26", "ch_1", "jahon_11", 26, "26-mavzu.. 1991 - 2017-yillarda Afrika mamlakatlari", "26-mavzu.. 1991 - 2017-yillarda Afrika mamlakatlari...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_27", "ch_1", "jahon_11", 27, "11-§. III BOB. YANGI MINGYILLIK BOSHLARIDA JAHON SIVILIZATSIYASI MUAMMOLARI", "11-§. III BOB. YANGI MINGYILLIK BOSHLARIDA JAHON SIVILIZATSIYASI MUAMMOLARI...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_28", "ch_1", "jahon_11", 28, "27-mavzu.. XX asr oxiri- XXI asr boshlarida globallashuv muammolari, harbiy, ekstremistik va ekologik xavf-xatarlar", "27-mavzu.. XX asr oxiri- XXI asr boshlarida globallashuv muammolari, harbiy, ekstremistik va ekologik xavf-xatarlar...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_29", "ch_1", "jahon_11", 29, "2-§.-mavzu. XX asr oxiri -XXI asr boshlarida barqaror rivojlanish va etno-ijtimoiy muammolar", "2-§.-mavzu. XX asr oxiri -XXI asr boshlarida barqaror rivojlanish va etno-ijtimoiy muammolar...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            TopicEntity("topic_jahon_11_30", "ch_1", "jahon_11", 30, "29-mavzu.. XX asr oxiri- XXI asr boshlarida ilmiy-texnik taraqqiyot. Ilm-fan, adabiyot, san'at", "29-mavzu.. XX asr oxiri- XXI asr boshlarida ilmiy-texnik taraqqiyot. Ilm-fan, adabiyot, san'at...", "[]", "[]", "11-sinf Jahon tarixi", 11),
            // 11-sinf O'zbekiston tarixi (20 topics) [tb_id=uz_11]
            TopicEntity("topic_uz_11_1", "ch_1", "uz_11", 1, "1-mavzu.. O'zbekiston Respublikasi mustaqilligining qo'lga kiritilishi", "1-mavzu.. O'zbekiston Respublikasi mustaqilligining qo'lga kiritilishi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_2", "ch_1", "uz_11", 2, "2-mavzu.. O'zbekiston Respublikasi milliy davlatchiligining shakllanishi va rivojlanishi I BOB. O'ZBEKISTONDA FUQAROLIK JAMIYATINING SHAKLLANISHI VA RIVOJLANISHI", "2-mavzu.. O'zbekiston Respublikasi milliy davlatchiligining shakllanishi va rivojlanishi I BOB. O'ZBEKISTONDA FUQAROLIK JAMIYATINING SHAKLLANISHI VA RIVOJLANISHI...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_3", "ch_1", "uz_11", 3, "3-mavzu.. Fuqarolik jamiyati g'oyasining rivojlanish tarixi", "3-mavzu.. Fuqarolik jamiyati g'oyasining rivojlanish tarixi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_4", "ch_1", "uz_11", 4, "4-mavzu.. O'zbekistonda fuqarolik jamiyati rivojlanishining asosiy bosqichlari va istiqbollari III BOB. O'ZBEKISTONDA IQTISODIY ISLOHOTLAR VA IJTIMOIY SIYOSAT", "4-mavzu.. O'zbekistonda fuqarolik jamiyati rivojlanishining asosiy bosqichlari va istiqbollari III BOB. O'ZBEKISTONDA IQTISODIY ISLOHOTLAR VA IJTIMOIY SIYOSAT...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_5", "ch_1", "uz_11", 5, "5-mavzu.. O'zbek modelining ishlab chiqilishi. Iqtisodiy islohotlarning boshlanishi", "5-mavzu.. O'zbek modelining ishlab chiqilishi. Iqtisodiy islohotlarning boshlanishi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_6", "ch_1", "uz_11", 6, "6-mavzu.. Mamlakat ishlab chiqarish salohiyatining oshirilishi va jahon iqtisodiy tizimiga integratsiyalashuvi", "6-mavzu.. Mamlakat ishlab chiqarish salohiyatining oshirilishi va jahon iqtisodiy tizimiga integratsiyalashuvi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_7", "ch_1", "uz_11", 7, "7-mavzu.. Ijtimoiy siyosat va uning amalga oshirilish bosqichlari IV BOB. O'ZBEKISTONDA ETNIK VA KONFESSIYALARARO MUNOSABATLAR", "7-mavzu.. Ijtimoiy siyosat va uning amalga oshirilish bosqichlari IV BOB. O'ZBEKISTONDA ETNIK VA KONFESSIYALARARO MUNOSABATLAR...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_8", "ch_1", "uz_11", 8, "8-mavzu.. O'zbekistonda millatlararo munosabatlar va bag'rikenglik", "8-mavzu.. O'zbekistonda millatlararo munosabatlar va bag'rikenglik...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_9", "ch_1", "uz_11", 9, "9-mavzu.. O'zbekistonda konfessiyalararo munosabatlar V BOB. TA'LIM TIZIMI ISLOHOTLARI VA KADRLAR TAYYORLASH", "9-mavzu.. O'zbekistonda konfessiyalararo munosabatlar V BOB. TA'LIM TIZIMI ISLOHOTLARI VA KADRLAR TAYYORLASH...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_10", "ch_1", "uz_11", 10, "10-mavzu.. Umumiy va o'rta maxsus ta'lim tizimi", "10-mavzu.. Umumiy va o'rta maxsus ta'lim tizimi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_11", "ch_1", "uz_11", 11, "11-mavzu.. Oliy ta'lim va undan keyingi ta'limning takomillashtirilishi VI BOB. MUSTAQILLIK YILLARIDA ILM-FAN VA SPORTNING RIVOJLANISHI", "11-mavzu.. Oliy ta'lim va undan keyingi ta'limning takomillashtirilishi VI BOB. MUSTAQILLIK YILLARIDA ILM-FAN VA SPORTNING RIVOJLANISHI...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_12", "ch_1", "uz_11", 12, "12-mavzu.. O'zbekistonda ilm-fan", "12-mavzu.. O'zbekistonda ilm-fan...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_13", "ch_1", "uz_11", 13, "13-mavzu.. O'zbekiston Respublikasida jismoniy tarbiya va sportning rivojlanishi", "13-mavzu.. O'zbekiston Respublikasida jismoniy tarbiya va sportning rivojlanishi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_14", "ch_1", "uz_11", 14, "14-mavzu.. O'zbekistonda yoshlar siyosati VII BOB. MUSTAQILLIK YILLARIDA O'ZBEKISTONDA MA'NAVIY VA MADANIY TARAQQIYOT", "14-mavzu.. O'zbekistonda yoshlar siyosati VII BOB. MUSTAQILLIK YILLARIDA O'ZBEKISTONDA MA'NAVIY VA MADANIY TARAQQIYOT...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_15", "ch_1", "uz_11", 15, "15-mavzu.. O'zbekistonda ma'naviy va tarixiy merosning tiklanishi", "15-mavzu.. O'zbekistonda ma'naviy va tarixiy merosning tiklanishi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_16", "ch_1", "uz_11", 16, "16-mavzu.. O'zbekistonda madaniyat va san'at VIII BOB. O'ZBEKISTON RESPUBLIKASINING TASHQI SIYOSATI VA IKKI TOMONLAMA MUNOSABATLARI", "16-mavzu.. O'zbekistonda madaniyat va san'at VIII BOB. O'ZBEKISTON RESPUBLIKASINING TASHQI SIYOSATI VA IKKI TOMONLAMA MUNOSABATLARI...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_17", "ch_1", "uz_11", 17, "17-mavzu.. O'zbekiston Respublikasi tashqi siyosiy faoliyatining shakllanishi va uning ustuvor yo'nalishlari", "17-mavzu.. O'zbekiston Respublikasi tashqi siyosiy faoliyatining shakllanishi va uning ustuvor yo'nalishlari...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_18", "ch_1", "uz_11", 18, "1-§.-mavzu. O'zbekistonning Markaziy Osiyo mamlakatlari bilan o'zaro hamkorligi", "1-§.-mavzu. O'zbekistonning Markaziy Osiyo mamlakatlari bilan o'zaro hamkorligi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_19", "ch_1", "uz_11", 19, "21-mavzu.. O'zbekistonning Birlashgan Millatlar Tashkiloti doirasidagi faoliyati va tinchlikparvar tashqi siyosati", "21-mavzu.. O'zbekistonning Birlashgan Millatlar Tashkiloti doirasidagi faoliyati va tinchlikparvar tashqi siyosati...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
            TopicEntity("topic_uz_11_20", "ch_1", "uz_11", 20, "22-mavzu.. O'zbekistonning mintaqaviy tashkilotlar doirasida ko'ptomonlama hamkorligi", "22-mavzu.. O'zbekistonning mintaqaviy tashkilotlar doirasida ko'ptomonlama hamkorligi...", "[]", "[]", "11-sinf O'zbekiston tarixi", 11),
        )
        db.topicDao().insertAll(topics)
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
    val topicId: String = "general",
    val textbookId: String = "jahon_6",
    val gradeId: Int = 6,
    val subjectId: String = "jahon",
    val type: String = QuestionType.SINGLE_CHOICE.name,
    val difficulty: String = Difficulty.HARD.name,
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
