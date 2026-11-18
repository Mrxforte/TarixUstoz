package com.tarixustoz.app.data.repository

import com.tarixustoz.app.BuildConfig
import com.tarixustoz.app.data.remote.MediaUrls
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /** Yo'nalishlar bo'yicha savollar soni — tarmoqni kutmasdan ko'rsatish uchun keshlanadi. */
    private val directionPrefs by lazy {
        context.getSharedPreferences("direction_counts", Context.MODE_PRIVATE)
    }
    private val directionMemory = mutableMapOf<Int, Map<String, Int>>()

    /** Har bir mavzu bo'yicha savollar soni keshi. */
    private val topicCountsPrefs by lazy {
        context.getSharedPreferences("topic_counts", Context.MODE_PRIVATE)
    }
    private val topicCountsMemory = mutableMapOf<String, Int>()

    /** Bilet savollari keshi — qayta ochilganda serverga yuklama tushirmaydi. */
    private val ticketCachePrefs by lazy {
        context.getSharedPreferences("ticket_cache_prefs", Context.MODE_PRIVATE)
    }

    /** Kunlik savol va sinxronizatsiya vaqtlari keshi. */
    private val syncMetaPrefs by lazy {
        context.getSharedPreferences("sync_meta_prefs", Context.MODE_PRIVATE)
    }

    private val syncMutex = Mutex()
    @Volatile
    private var lastSyncedAtMs: Long = 0L

    private val sixHoursMs = 6L * 60L * 60L * 1000L

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
            val countsByBook = list.groupingBy { it.textbookId }.eachCount()
            list.map { it.toDomain(json, resolveTopicCount(it.id, countsByBook[it.textbookId] ?: 35)) }
        }

    fun getTopicsByTextbook(textbookId: String): Flow<List<Topic>> =
        db.topicDao().getByTextbook(textbookId).map { list ->
            val count = list.size.coerceAtLeast(22)
            list.map { it.toDomain(json, resolveTopicCount(it.id, count)) }
        }

    data class BookKeySpec(val grade: Int, val subject: String?)

    fun resolveBookKey(rawId: String): BookKeySpec {
        val clean = rawId.trim().lowercase().removePrefix("book_")
        val grade = clean.filter { it.isDigit() }.toIntOrNull() ?: 0
        val subject = when {
            clean.contains("jahon") -> "jahon"
            clean.contains("uz") || clean.contains("qadimgi") || grade == 6 -> "uzbekiston"
            else -> null
        }
        return BookKeySpec(grade, subject)
    }

    fun isBookKey(rawId: String): Boolean {
        val lower = rawId.trim().lowercase()
        if (lower.startsWith("topic_")) return false
        val directionCodes = setOf("event", "person", "year", "war", "city", "fact", "logical", "all")
        if (lower in directionCodes) return false
        return lower.startsWith("book_") ||
            lower.startsWith("uz_") ||
            lower.startsWith("jahon_") ||
            lower == "6_all" ||
            lower.matches(Regex("^(6|7|8|9|10|11)_(uz|jahon|all)$")) ||
            lower.all { it.isDigit() }
    }

    suspend fun getTextbookById(id: String): Textbook? {
        val cleanId = id.removePrefix("book_")
        db.textbookDao().getById(cleanId)?.let { return it.toDomain() }
        db.textbookDao().getById(id)?.let { return it.toDomain() }
        val spec = resolveBookKey(id)
        if (spec.grade in 6..11) {
            val mappedId = if (spec.subject == "jahon" && spec.grade > 6) "jahon_${spec.grade}" else "uz_${spec.grade}"
            return db.textbookDao().getById(mappedId)?.toDomain()
        }
        return null
    }

    suspend fun getBookQuestionCount(categoryKey: String): Int {
        val spec = resolveBookKey(categoryKey)
        if (spec.grade !in 6..11) {
            return getTextbookById(categoryKey)?.totalQuestions?.takeIf { it > 0 } ?: 100_000
        }
        if (spec.subject == null && spec.grade > 6) {
            val uz = db.textbookDao().getById("uz_${spec.grade}")?.totalQuestions?.takeIf { it > 0 } ?: 100_000
            val jahon = db.textbookDao().getById("jahon_${spec.grade}")?.totalQuestions?.takeIf { it > 0 } ?: 100_000
            return uz + jahon
        }
        val mappedId = if (spec.subject == "jahon" && spec.grade > 6) "jahon_${spec.grade}" else "uz_${spec.grade}"
        return db.textbookDao().getById(mappedId)?.totalQuestions?.takeIf { it > 0 } ?: 100_000
    }

    suspend fun getTopicById(topicId: String): Topic? = withContext(Dispatchers.IO) {
        val withPrefix = if (topicId.startsWith("topic_")) topicId else "topic_$topicId"
        val cleanId = topicId.removePrefix("topic_")
        val entity = db.topicDao().getById(withPrefix) ?: db.topicDao().getById(cleanId) ?: return@withContext null
        val fallbackBookTopics = DEFAULT_TOPIC_COUNTS[entity.textbookId] ?: 35
        entity.toDomain(json, resolveTopicCount(entity.id, fallbackBookTopics))
    }

    fun resolveTopicCount(topicId: String, topicsInBook: Int = 35): Int {
        topicCountsMemory[topicId]?.takeIf { it > 0 }?.let { return it }
        val stored = topicCountsPrefs.getInt(topicId, 0)
        if (stored > 0) {
            topicCountsMemory[topicId] = stored
            return stored
        }
        return 100_000 / topicsInBook.coerceAtLeast(22)
    }

    suspend fun getQuestionsForTopic(topicId: String): List<Question> = withContext(Dispatchers.IO) {
        val withPrefix = if (topicId.startsWith("topic_")) topicId else "topic_$topicId"
        val cleanId = topicId.removePrefix("topic_")
        val direct = db.questionDao().getByTopic(withPrefix).map { it.toDomain(json) }
        val clean = if (direct.isEmpty()) db.questionDao().getByTopic(cleanId).map { it.toDomain(json) } else direct

        val list = clean.toMutableList()
        val topicEntity = db.topicDao().getById(withPrefix) ?: db.topicDao().getById(cleanId)

        if (list.size < 30 && topicEntity != null) {
            val byBook = db.questionDao().getByTextbook(topicEntity.textbookId).map { it.toDomain(json) }
            for (q in byBook) {
                if (list.size >= 30) break
                if (list.none { it.id == q.id }) {
                    list.add(q)
                }
            }
        }
        if (list.size < 30) {
            val grade = topicEntity?.gradeId ?: 6
            val fallback = db.questionDao().getByGradeAndSubject(grade, "ALL", 30).map { it.toDomain(json) }
            for (q in fallback) {
                if (list.size >= 30) break
                if (list.none { it.id == q.id }) {
                    list.add(q)
                }
            }
        }
        return@withContext list
    }

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

    /** One ticket straight from the backend (30 questions of one direction, book, or topic), disk-cached. */
    suspend fun fetchTicketFromApi(
        categoryKey: String,
        ticketIndex: Int,
        count: Int = 30,
        grade: Int = 0
    ): List<Question> = withContext(Dispatchers.IO) {
        val cacheKey = "${BuildConfig.TARIX_API_BASE_URL}:ticket_${categoryKey}_${ticketIndex}_${count}_${grade}"
        val cachedJson = ticketCachePrefs.getString(cacheKey, null)
        if (!cachedJson.isNullOrBlank()) {
            try {
                val cachedList = json.decodeFromString<List<Question>>(cachedJson)
                if (cachedList.isNotEmpty()) {
                    return@withContext cachedList
                }
            } catch (_: Exception) {
                ticketCachePrefs.edit().remove(cacheKey).apply()
            }
        }

        try {
            val fetched = when {
                categoryKey.startsWith("topic_") -> {
                    val numericId = categoryKey.removePrefix("topic_").toLongOrNull()
                    val directTopic = if (numericId != null) {
                        apiService.getQuestions(
                            topicId = numericId,
                            limit = count,
                            offset = (ticketIndex - 1) * count
                        ).map { it.toDomain() }
                    } else {
                        emptyList()
                    }
                    if (directTopic.isNotEmpty()) {
                        directTopic
                    } else {
                        val topicObj = getTopicById(categoryKey)
                        val tGrade = topicObj?.let { resolveBookKey(it.textbookId).grade } ?: resolveBookKey(categoryKey).grade
                        val tSubj = topicObj?.let { resolveBookKey(it.textbookId).subject } ?: resolveBookKey(categoryKey).subject
                        val subjectParam = when (tSubj) {
                            "jahon" -> "jahon"
                            "uzbekiston" -> if (tGrade == 6) "qadimgi" else "uzbekiston"
                            else -> null
                        }
                        apiService.getTicket(
                            category = "ALL",
                            grade = tGrade.takeIf { it > 0 },
                            subject = subjectParam,
                            ticket = ticketIndex,
                            size = count
                        ).questions.map { it.toDomain() }
                    }
                }
                isBookKey(categoryKey) -> {
                    val spec = resolveBookKey(categoryKey)
                    val gradeFilter = spec.grade.takeIf { it > 0 } ?: grade.takeIf { it > 0 }
                    val subjectParam = when (spec.subject) {
                        "jahon" -> "jahon"
                        "uzbekiston" -> if (gradeFilter == 6) "qadimgi" else "uzbekiston"
                        else -> null
                    }
                    apiService.getTicket(
                        category = "ALL",
                        grade = gradeFilter,
                        subject = subjectParam,
                        ticket = ticketIndex,
                        size = count
                    ).questions.map { it.toDomain() }
                }
                else -> {
                    apiService.getTicket(
                        category = categoryKey,
                        grade = grade.takeIf { it > 0 },
                        subject = null,
                        ticket = ticketIndex,
                        size = count
                    ).questions.map { it.toDomain() }
                }
            }
            if (fetched.isNotEmpty()) {
                try {
                    ticketCachePrefs.edit().putString(cacheKey, json.encodeToString(fetched)).apply()
                    insertQuestionsToDb(fetched)
                } catch (_: Exception) { }
            }
            fetched
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTicketQuestions(
        categoryKey: String,
        ticketIndex: Int,
        count: Int = 30,
        grade: Int = 0
    ): List<Question> = withContext(Dispatchers.IO) {
        val remote = fetchTicketFromApi(categoryKey, ticketIndex, count, grade)
        if (remote.isNotEmpty()) return@withContext remote

        val offset = (ticketIndex - 1) * count
        val local = when {
            categoryKey.startsWith("topic_") || getTopicById(categoryKey) != null || getTopicById("topic_$categoryKey") != null -> {
                val topicId = categoryKey.removePrefix("topic_")
                val baseQuestions = getQuestionsForTopic(topicId)
                baseQuestions.drop(offset % baseQuestions.size.coerceAtLeast(1)).take(count)
            }
            isBookKey(categoryKey) -> {
                val spec = resolveBookKey(categoryKey)
                val gradeId = spec.grade.takeIf { it > 0 } ?: grade
                val subjectId = spec.subject ?: "ALL"
                val localQuestions = db.questionDao().getByGradeAndSubjectWithOffset(
                    gradeId = gradeId,
                    subjectId = subjectId,
                    limit = count,
                    offset = offset
                ).map { it.toDomain(json) }

                if (localQuestions.isNotEmpty()) localQuestions
                else {
                    db.questionDao().getByGradeAndSubjectWithOffset(
                        gradeId = gradeId,
                        subjectId = "ALL",
                        limit = count,
                        offset = offset
                    ).map { it.toDomain(json) }
                }
            }
            else -> {
                db.questionDao().getFilteredWithTopicsOffset(
                    gradeId = grade,
                    category = categoryKey,
                    difficulty = "ALL",
                    topicIds = emptyList(),
                    selectAllTopics = true,
                    limit = count,
                    offset = offset
                ).map { it.toDomain(json) }
            }
        }
        if (local.isNotEmpty()) return@withContext local

        val specGrade = if (isBookKey(categoryKey)) resolveBookKey(categoryKey).grade else grade
        getFallbackQuestions(count, categoryKey, specGrade)
    }

    suspend fun getWrongQuestionsForReview(limit: Int): List<Question> =
        db.questionDao().getWrongQuestions(limit).map { it.toDomain(json) }

    suspend fun getRandomQuestionForNotification(): Question? =
        db.questionDao().getRandom(7, "uzbekiston", 10).randomOrNull()?.toDomain(json)
            ?: getFallbackQuestions(1, "ALL", 0).firstOrNull()

    suspend fun getDailyQuestion(
        excludeIds: Set<String> = emptySet(),
        grade: Int = 0,
        nonce: Int = 0
    ): Question? = withContext(Dispatchers.IO) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val dailyCacheKey = "daily_q_${today}_g$grade"
        if (nonce == 0 && excludeIds.isEmpty()) {
            val cachedJson = syncMetaPrefs.getString(dailyCacheKey, null)
            if (!cachedJson.isNullOrBlank()) {
                try {
                    val cached = json.decodeFromString<Question>(cachedJson)
                    if (cached.options.any { it.text.isNotBlank() } && !excludeIds.contains(cached.id)) {
                        return@withContext cached
                    }
                } catch (_: Exception) { }
            }
        }

        val excludeParam = excludeIds
            .map { it.removePrefix("q_") }
            .filter { it.isNotBlank() && it.all(Char::isDigit) }
            .take(30)
            .joinToString(",")
            .takeIf { it.isNotBlank() }
        val userIdParam = if (nonce == 0) "daily_$today" else "daily_${today}_$nonce"
        val nonceParam = if (nonce == 0) "0" else "${nonce}_${System.currentTimeMillis()}"
        val apiDaily = try {
            apiService.getDailyQuestion(
                grade = grade.takeIf { it > 0 },
                userIdentifier = userIdParam,
                nonce = nonceParam,
                exclude = excludeParam
            ).toDomain()
        } catch (_: Exception) {
            null
        }
        if (apiDaily != null &&
            apiDaily.options.any { it.text.isNotBlank() } &&
            !excludeIds.contains(apiDaily.id)
        ) {
            if (nonce == 0 && excludeIds.isEmpty()) {
                try {
                    syncMetaPrefs.edit().putString(dailyCacheKey, json.encodeToString(apiDaily)).apply()
                } catch (_: Exception) { }
            }
            insertQuestionsToDb(listOf(apiDaily))
            return@withContext apiDaily
        }

        val directionRotation = listOf("EVENT", "PERSON", "YEAR", "WAR", "CITY", "FACT", "LOGICAL")
        val difficultyRotation = listOf("MEDIUM", "EASY", "HARD")
        val chosenDirection = directionRotation[(nonce + today.hashCode().ushr(1)) % directionRotation.size]
        val chosenDifficulty = difficultyRotation[nonce % difficultyRotation.size]

        val apiQuizPick = fetchQuizFromApi(
            count = 15,
            categoryCode = chosenDirection,
            grade = grade.coerceAtLeast(0),
            difficulty = chosenDifficulty
        ).filter { it.options.any { opt -> opt.text.isNotBlank() } && !excludeIds.contains(it.id) }
            .shuffled()
            .firstOrNull()
        if (apiQuizPick != null) {
            return@withContext apiQuizPick
        }

        val ticketNumber = (nonce % 12) + 1
        val apiTicketPick = try {
            apiService.getTicket(
                category = chosenDirection,
                grade = grade.takeIf { it > 0 },
                subject = null,
                ticket = ticketNumber,
                size = 30
            ).questions.map { it.toDomain() }.also { fetched ->
                if (fetched.isNotEmpty()) insertQuestionsToDb(fetched)
            }.filter { it.options.any { opt -> opt.text.isNotBlank() } && !excludeIds.contains(it.id) }
                .shuffled()
                .firstOrNull()
        } catch (_: Exception) {
            null
        }
        if (apiTicketPick != null) {
            return@withContext apiTicketPick
        }

        val localRandomPool = if (grade in 6..11) {
            db.questionDao().getFilteredWithTopics(
                gradeId = grade,
                category = "ALL",
                difficulty = "ALL",
                topicIds = emptyList(),
                selectAllTopics = true,
                limit = 60
            ).map { it.toDomain(json) }.ifEmpty {
                db.questionDao().getRandomAny(60).map { it.toDomain(json) }
            }
        } else {
            db.questionDao().getRandomAny(60).map { it.toDomain(json) }
        }
        val unseenDb = localRandomPool.filter {
            it.options.any { opt -> opt.text.isNotBlank() } && !excludeIds.contains(it.id)
        }
        val dbPick = (if (unseenDb.isNotEmpty()) unseenDb else localRandomPool).shuffled().firstOrNull()
        if (dbPick != null && !excludeIds.contains(dbPick.id)) {
            return@withContext dbPick
        }

        val fallbackPool = getFallbackQuestions(30, "ALL", if (grade in 6..11) grade else 0)
            .ifEmpty { getFallbackQuestions(30, "ALL", 0) }
        val unseenFallback = fallbackPool.filterNot { excludeIds.contains(it.id) }
        (if (unseenFallback.isNotEmpty()) unseenFallback else fallbackPool).shuffled().firstOrNull()
            ?: dbPick
    }

    fun getAllTopicsFlow(): Flow<List<Topic>> =
        db.topicDao().getAll().map { list ->
            val countsByBook = list.groupingBy { it.textbookId }.eachCount()
            list.map { it.toDomain(json, resolveTopicCount(it.id, countsByBook[it.textbookId] ?: 35)) }
        }

    suspend fun getTopicCountsMap(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            val topics = db.topicDao().getAllOnce()
            val countsByBook = topics.groupingBy { it.textbookId }.eachCount()
            topics.associate { t ->
                t.id to resolveTopicCount(t.id, countsByBook[t.textbookId] ?: 35)
            }
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

    fun defaultDirectionCounts(grade: Int = 0): Map<String, Int> {
        val base = mapOf(
            "EVENT" to 331_844,
            "PERSON" to 165_903,
            "YEAR" to 221_211,
            "WAR" to 545_000,
            "CITY" to 110_543,
            "FACT" to 485_000,
            "LOGICAL" to 270_499
        )
        if (grade !in 6..11) return base
        val booksInGrade = if (grade == 6) 1 else 2
        return base.mapValues { (_, total) -> (total * booksInGrade) / 11 }
    }

    suspend fun fetchDirectionCounts(
        grade: Int = 0,
        forceRefresh: Boolean = false
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            directionMemory[grade]?.let { return@withContext it }
            val lastTs = directionPrefs.getLong("v2_grade_${grade}_ts", 0L)
            if (System.currentTimeMillis() - lastTs < sixHoursMs) {
                cachedDirectionCounts(grade)?.let { cached ->
                    directionMemory[grade] = cached
                    return@withContext cached
                }
            }
        }
        try {
            val fresh = apiService.getDirections(grade = grade.takeIf { it > 0 })
                .directions
                .associate { it.code to it.count }
            if (fresh.isNotEmpty() && fresh.values.any { it > 0 }) {
                directionMemory[grade] = fresh
                directionPrefs.edit()
                    .putString("v2_grade_$grade", json.encodeToString(fresh))
                    .putLong("v2_grade_${grade}_ts", System.currentTimeMillis())
                    .apply()
                return@withContext fresh
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedDirectionCounts(grade) ?: defaultDirectionCounts(grade)
    }

    /** Oxirgi saqlangan sonlar (tarmoqsiz, darhol). */
    fun cachedDirectionCounts(grade: Int = 0): Map<String, Int>? {
        directionMemory[grade]?.let { return it }
        val raw = directionPrefs.getString("v2_grade_$grade", null) ?: return null
        return try {
            json.decodeFromString<Map<String, Int>>(raw).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun insertQuestionsToDb(questions: List<Question>) = withContext(Dispatchers.IO) {
        try {
            val entities = questions.map { q ->
                QuestionEntity(
                    id = q.id,
                    topicId = q.topicId,
                    textbookId = q.textbookId,
                    gradeId = q.gradeId,
                    subjectId = q.subjectId,
                    type = q.type.name,
                    difficulty = q.difficulty.name,
                    category = q.category.name,
                    text = q.text,
                    optionsJson = Json.encodeToString(q.options),
                    explanation = q.explanation,
                    historicalDate = q.historicalDate,
                    personality = q.personality,
                    sourceReference = q.sourceReference,
                    pageHint = q.pageHint
                )
            }
            db.questionDao().insertAll(entities)
        } catch (e: Exception) {
            e.printStackTrace()
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
            val effectiveSubject = when {
                numericTopicIds.isNotEmpty() -> ""
                subject.isNotBlank() -> subject
                else -> ""
            }

            val req = QuizGenerateRequest(
                count = count,
                categoryCode = categoryCode,
                grade = effectiveGrade,
                subject = effectiveSubject,
                difficulty = difficulty,
                topicIds = numericTopicIds
            )
            val response = apiService.generateQuiz(req)
            val domainQuestions = response.questions.map { it.toDomain() }
            if (domainQuestions.isNotEmpty()) {
                insertQuestionsToDb(domainQuestions)
            }
            domainQuestions
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun com.tarixustoz.app.data.remote.ApiQuestionDto.toDomain(): Question {
        val optionsList = listOf(
            AnswerOption("a", optionA, correctOption.equals("A", ignoreCase = true)),
            AnswerOption("b", optionB, correctOption.equals("B", ignoreCase = true)),
            AnswerOption("c", optionC, correctOption.equals("C", ignoreCase = true)),
            AnswerOption("d", optionD, correctOption.equals("D", ignoreCase = true))
        )
        val cat = try { QuestionCategory.valueOf(categoryType) } catch (_: Exception) { QuestionCategory.ALL }
        val diff = try { Difficulty.valueOf(difficulty) } catch (_: Exception) { Difficulty.MEDIUM }
        return Question(
            id = "q_$id",
            topicId = if (topicId != null) "topic_$topicId" else "general",
            textbookId = if (subjectId == "jahon") "jahon_$gradeId" else "uz_$gradeId",
            gradeId = gradeId,
            subjectId = subjectId ?: "uzbekiston",
            type = QuestionType.SINGLE_CHOICE,
            difficulty = diff,
            category = cat,
            text = text,
            options = optionsList,
            explanation = explanation ?: shortExplanation ?: "Darslik va manba asosida tayyorlangan.",
            sourceReference = sourceCitation ?: "Django Backend API"
        )
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
            ),

            // Tarixiy faktlar va urushlar (oflayn zahira)
            Question(
                id = "fb_fact_1",
                topicId = "topic_uz_7_36",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                category = QuestionCategory.FACT,
                text = "Mirzo Ulug‘bek Samarqanddagi mashhur rasadxonani nechanchi yilda bunyod ettirgan?",
                options = listOf(
                    AnswerOption("a", "1428–1429-yillarda", true),
                    AnswerOption("b", "1409–1411-yillarda", false),
                    AnswerOption("c", "1444–1445-yillarda", false),
                    AnswerOption("d", "1435–1437-yillarda", false)
                ),
                explanation = "Mirzo Ulug‘bek rasadxonasi 1428–1429-yillarda Ko‘hak tepaligi etagida qurilgan.",
                historicalDate = "1428–1429",
                sourceReference = "7-sinf O‘zbekiston tarixi"
            ),
            Question(
                id = "fb_fact_2",
                topicId = "topic_uz_7_26",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                category = QuestionCategory.FACT,
                text = "“Devonu lug‘otit-turk” asari qaysi buyuk alloma tomonidan yaratilgan?",
                options = listOf(
                    AnswerOption("a", "Mahmud Koshg‘ariy", true),
                    AnswerOption("b", "Yusuf Xos Hojib", false),
                    AnswerOption("c", "Ahmad Yugnakiy", false),
                    AnswerOption("d", "Abu Rayhon Beruniy", false)
                ),
                explanation = "Mahmud Koshg‘ariy 1072–1074-yillarda turkiy tillarning qomusiy lug‘ati — “Devonu lug‘otit-turk”ni yozgan.",
                personality = "Mahmud Koshg‘ariy",
                sourceReference = "7-sinf O‘zbekiston tarixi"
            ),
            Question(
                id = "fb_war_1",
                topicId = "topic_uz_7_20",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                category = QuestionCategory.WAR,
                text = "Jaloliddin Manguberdi va Chingizxon qo‘shinlari o‘rtasidagi mashhur Parvon jangi qaysi yilda bo‘lib o‘tgan?",
                options = listOf(
                    AnswerOption("a", "1221-yil", true),
                    AnswerOption("b", "1219-yil", false),
                    AnswerOption("c", "1223-yil", false),
                    AnswerOption("d", "1225-yil", false)
                ),
                explanation = "1221-yilda Parvon yaqinidagi jangda Jaloliddin Manguberdi mo‘g‘ul qo‘shinini mag‘lubiyatga uchratgan.",
                historicalDate = "1221-yil",
                sourceReference = "7-sinf O‘zbekiston tarixi"
            )
        )

        var filtered = rawPool
        if (gradeId > 0) {
            val gradeFiltered = filtered.filter { it.gradeId == gradeId }
            if (gradeFiltered.isNotEmpty()) {
                filtered = gradeFiltered
            }
        }

        when (categoryCode) {
            "YEAR" -> filtered.filter { it.category == QuestionCategory.YEAR || it.historicalDate != null }.ifEmpty { filtered }
            "PERSON" -> filtered.filter { it.category == QuestionCategory.PERSON || it.personality != null }.ifEmpty { filtered }
            "CITY" -> filtered.filter { it.category == QuestionCategory.CITY }.ifEmpty { filtered }
            "EVENT" -> filtered.filter { it.category == QuestionCategory.EVENT }.ifEmpty { filtered }
            "FACT" -> filtered.filter { it.category == QuestionCategory.FACT }.ifEmpty { filtered }
            "WAR" -> filtered.filter { it.category == QuestionCategory.WAR }.ifEmpty { filtered }
            else -> filtered
        }.also { filtered = it }

        return filtered.shuffled().take(count.coerceAtLeast(1))
    }

    suspend fun syncWithDjangoBackend(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val now = System.currentTimeMillis()
            val persistedSyncAt = maxOf(lastSyncedAtMs, syncMetaPrefs.getLong("last_synced_at_ms", 0L))
            if (!force && now - persistedSyncAt < sixHoursMs &&
                db.textbookDao().count() >= 11 &&
                db.topicDao().getAllOnce().isNotEmpty()
            ) {
                lastSyncedAtMs = persistedSyncAt
                return@withLock true
            }
            try {
                // Fetch PDF Books from Django REST API
                val apiBooks = try { apiService.getPdfBooks() } catch (e: Exception) { emptyList() }
                if (apiBooks.isNotEmpty()) {
                    val entities = apiBooks.map { b ->
                        val rawPdfUrl = b.pdfUrl ?: b.pdfFile
                        val formattedPdfUrl = MediaUrls.resolve(rawPdfUrl)
                        val formattedCoverUrl = MediaUrls.resolve(b.coverUrl ?: b.coverImage)
                        val fallbackPdfUrl = MediaUrls.textbook(b.effectiveGrade, b.effectiveSubject)
                        val bookId = if (b.effectiveSubject == "jahon") "jahon_${b.effectiveGrade}" else "uz_${b.effectiveGrade}"
                        val totalQs = if (b.totalQuestions > 0) b.totalQuestions else 100_000
                        val totalTops = if (b.totalTopics > 0) b.totalTopics else (DEFAULT_TOPIC_COUNTS[bookId] ?: 35)

                        TextbookEntity(
                            id = bookId,
                            title = b.title,
                            gradeId = b.effectiveGrade,
                            subjectId = b.effectiveSubject,
                            authors = b.authors ?: "Tarixchi olimlar",
                            year = b.year,
                            edition = b.edition ?: "1-nashr",
                            totalChapters = b.totalChapters,
                            totalTopics = totalTops,
                            totalQuestions = totalQs,
                            coverAsset = formattedCoverUrl,
                            pdfAsset = formattedPdfUrl?.takeIf { it.isNotBlank() } ?: fallbackPdfUrl
                        )
                    }
                    db.textbookDao().insertAll(entities)
                } else if (db.textbookDao().count() < 11) {
                    seedContent()
                }

                // Fetch Topics from Django REST API
                val apiTopics = try { apiService.getTopics() } catch (e: Exception) { emptyList() }
                if (apiTopics.isNotEmpty()) {
                    val countsEditor = topicCountsPrefs.edit()
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

                        val topicId = "topic_${t.id}"
                        if (t.questionsCount > 0) {
                            topicCountsMemory[topicId] = t.questionsCount
                            countsEditor.putInt(topicId, t.questionsCount)
                        }

                        TopicEntity(
                            id = topicId,
                            chapterId = "ch_${t.categoryId}",
                            textbookId = inferredTextbookId,
                            number = t.order,
                            title = t.title,
                            content = t.content ?: "",
                            summary = t.chapterTitle ?: "",
                            gradeId = inferredGrade
                        )
                    }
                    countsEditor.apply()
                    db.topicDao().deleteAll()
                    entities.chunked(50).forEach { chunk ->
                        db.topicDao().insertAll(chunk)
                    }
                }

                lastSyncedAtMs = System.currentTimeMillis()
                syncMetaPrefs.edit().putLong("last_synced_at_ms", lastSyncedAtMs).apply()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
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
            val key = bookDownloadKey(grade, bookSubjectId)
            if (bankPrefs.getBoolean(key, false) &&
                db.questionDao().countByGradeAndSubject(grade, bookSubjectId) > 0
            ) {
                return@withContext 0
            }
            val subjectParam = when {
                grade == 6 -> "qadimgi"
                subjectId == "jahon" -> "jahon"
                else -> "uzbekiston"
            }

            val effectivePageSize = pageSize.coerceIn(1, 5000)
            var offset = 0
            var inserted = 0
            while (true) {
                val page = try {
                    apiService.getQuestions(
                        grade = grade,
                        subject = subjectParam,
                        limit = effectivePageSize,
                        offset = offset
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return@withContext inserted
                }
                if (page.isEmpty()) break
                page.map { it.toEntity() }
                    .chunked(500)
                    .forEach { chunk -> db.questionDao().insertAll(chunk) }
                inserted += page.size
                offset += page.size
                if (page.size < effectivePageSize) break
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

    private fun bookDownloadKey(grade: Int, subjectId: String): String {
        val subject = if (grade == 6) "uzbekiston" else subjectId
        return "${BuildConfig.TARIX_API_BASE_URL}:book_${grade}_$subject"
    }

    fun isBookDownloaded(grade: Int, subjectId: String): Boolean =
        bankPrefs.getBoolean(bookDownloadKey(grade, subjectId), false)

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
            textbookId = if (subjectId == "jahon") "jahon_$gradeId" else "uz_$gradeId",
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

    data class OfflineSyncResult(
        val backendSynced: Boolean,
        val cachedQuestionsTotal: Int,
        val syncedTicketsCount: Int
    )

    /**
     * Background WorkManager offline-cache warmup:
     * caches textbooks, topics, direction counts, daily question, historical facts,
     * and the active tickets for each direction + user's grade books into Room & disk cache.
     */
    suspend fun syncOfflineCache(
        grade: Int,
        forceRefresh: Boolean = false,
        lastOpenedByCategory: Map<String, Int> = emptyMap()
    ): OfflineSyncResult = withContext(Dispatchers.IO) {
        seedIfNeeded()
        val backendOk = syncWithDjangoBackend(force = forceRefresh)
        fetchDirectionCounts(0, forceRefresh = forceRefresh)
        if (grade in 6..11) {
            fetchDirectionCounts(grade, forceRefresh = forceRefresh)
        }
        runCatching { getDailyQuestion(grade = grade) }
        runCatching { getDailyQuestion(grade = 0) }

        var syncedTickets = 0
        val priorityCategories = buildList {
            add("FACT")
            add("EVENT")
            add("PERSON")
            add("YEAR")
            add("WAR")
            add("CITY")
            add("LOGICAL")
            if (grade in 6..11) {
                add("book_uz_$grade")
                if (grade > 6) add("book_jahon_$grade")
            }
        }

        for (cat in priorityCategories) {
            val targetIdx = (lastOpenedByCategory[cat] ?: 1).coerceAtLeast(1)
            val indicesToWarm = if (targetIdx > 1) listOf(1, targetIdx) else listOf(1)
            for (idx in indicesToWarm) {
                val qs = runCatching {
                    fetchTicketFromApi(
                        categoryKey = cat,
                        ticketIndex = idx,
                        count = 30,
                        grade = if (cat.startsWith("book_")) grade else 0
                    )
                }.getOrDefault(emptyList())
                if (qs.isNotEmpty()) syncedTickets++
            }
        }

        val totalCached = db.questionDao().count()
        OfflineSyncResult(
            backendSynced = backendOk,
            cachedQuestionsTotal = totalCached,
            syncedTicketsCount = syncedTickets
        )
    }

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val textbookCount = db.textbookDao().count()
        val existingTopics = db.topicDao().getAllOnce()
        val hasStaleSmallBook = db.textbookDao().getById("uz_6")?.totalQuestions?.let { it < 1000 } == true

        if (textbookCount < 11 || existingTopics.isEmpty() || hasStaleSmallBook) {
            val assetJson = loadAssetJson("content.json")
            if (!assetJson.isNullOrBlank()) {
                importFromJsonString(assetJson)
            }
            if (db.textbookDao().count() < 11 || hasStaleSmallBook) {
                seedTextbooksOnly()
            }
            if (db.topicDao().getAllOnce().isEmpty()) {
                seedContent()
            }
        }
        if (db.questionDao().count() == 0) {
            insertQuestionsToDb(getFallbackQuestions(20, "ALL", 0))
        }
    }

    private suspend fun seedTextbooksOnly() {
        val textbooks = listOf(
            TextbookEntity("uz_6", "Tarix. Qadimgi dunyo", 6, "uzbekiston", "A.S. Sagdullayev, V.A. Kostetskiy", 2017, "5-nashr", 5, 44, 100_000, "file:///android_asset/images/uz_6_cover.png", MediaUrls.textbook(6, "uzbekiston")),
            TextbookEntity("uz_7", "O‘zbekiston tarixi (IV–XVI asr)", 7, "uzbekiston", "A. Muhammadjonov", 2017, "3-nashr", 10, 42, 100_000, pdfAsset = MediaUrls.textbook(7, "uzbekiston")),
            TextbookEntity("jahon_7", "Jahon tarixi (V–XV asr)", 7, "jahon", "T.O‘. Salimov, F.E. Sultonov", 2017, "3-nashr", 12, 44, 100_000, pdfAsset = MediaUrls.textbook(7, "jahon")),
            TextbookEntity("uz_8", "O‘zbekiston tarixi (XVI–XIX asr I yarmi)", 8, "uzbekiston", "U. Jo‘rayev va b.", 2019, "4-nashr", 12, 40, 100_000, pdfAsset = MediaUrls.textbook(8, "uzbekiston")),
            TextbookEntity("jahon_8", "Jahon tarixi (XVI–XIX asr)", 8, "jahon", "R. Farmonov, U. Jo‘rayev, Sh. Ergashev", 2019, "4-nashr", 10, 32, 100_000, pdfAsset = MediaUrls.textbook(8, "jahon")),
            TextbookEntity("uz_9", "O‘zbekiston tarixi (XIX asr II yarmi – XX asr boshi)", 9, "uzbekiston", "S. Tillaboyev, A. Zamonov", 2019, "3-nashr", 10, 37, 100_000, pdfAsset = MediaUrls.textbook(9, "uzbekiston")),
            TextbookEntity("jahon_9", "Jahon tarixi (XX asr boshi)", 9, "jahon", "M. Lafasov va b.", 2019, "4-nashr", 12, 41, 100_000, pdfAsset = MediaUrls.textbook(9, "jahon")),
            TextbookEntity("uz_10", "O‘zbekiston tarixi (1917–1991)", 10, "uzbekiston", "Q. Rajabov, A. Zamonov", 2017, "1-nashr", 10, 28, 100_000, pdfAsset = MediaUrls.textbook(10, "uzbekiston")),
            TextbookEntity("jahon_10", "Jahon tarixi (1918–1991)", 10, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2017, "1-nashr", 10, 22, 100_000, pdfAsset = MediaUrls.textbook(10, "jahon")),
            TextbookEntity("uz_11", "O‘zbekiston tarixi (Mustaqillik davri)", 11, "uzbekiston", "N. Jo‘rayev, A. Zamonov", 2018, "1-nashr", 8, 22, 100_000, pdfAsset = MediaUrls.textbook(11, "uzbekiston")),
            TextbookEntity("jahon_11", "Jahon tarixi (1991–2017)", 11, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2018, "1-nashr", 8, 29, 100_000, pdfAsset = MediaUrls.textbook(11, "jahon"))
        )
        db.textbookDao().insertAll(textbooks)
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
        seedTextbooksOnly()

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

    private fun TopicEntity.toDomain(json: Json, questionsCount: Int = 0): Topic {
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
            imageCaption = imageCaption,
            questionsCount = questionsCount
        )
    }

    companion object {
        private val DEFAULT_TOPIC_COUNTS = mapOf(
            "uz_6" to 44,
            "uz_7" to 42,
            "jahon_7" to 44,
            "uz_8" to 40,
            "jahon_8" to 32,
            "uz_9" to 37,
            "jahon_9" to 41,
            "uz_10" to 28,
            "jahon_10" to 22,
            "uz_11" to 22,
            "jahon_11" to 29
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
