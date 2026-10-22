package com.tarixustoz.app.data.repository

import com.tarixustoz.app.data.local.*
import com.tarixustoz.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val db: AppDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

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
            list.map { Topic(it.id, it.chapterId, it.textbookId, it.number, it.title) }
        }

    suspend fun getQuestionsForTopic(topicId: String): List<Question> =
        db.questionDao().getByTopic(topicId).map { it.toDomain(json) }

    suspend fun getQuestionById(id: String): Question? =
        db.questionDao().getById(id)?.toDomain(json)

    suspend fun getRandomQuestions(gradeId: Int, subjectId: String, count: Int): List<Question> =
        db.questionDao().getRandom(gradeId, subjectId, count).map { it.toDomain(json) }

    suspend fun seedIfNeeded() {
        if (db.questionDao().count() > 0) return
        seedContent()
    }

    private suspend fun seedContent() {
        // Seed textbooks from provided PDFs
        val textbooks = listOf(
            TextbookEntity("uz_6", "Tarix. Qadimgi dunyo", 6, "jahon", "A.S. Sagdullayev, V.A. Kostetskiy", 2017, "5-nashr", 12, 40, 80),
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

        // Sample chapters & topics for grade 7 O‘zbekiston tarixi (from available intro)
        val chapters = listOf(
            ChapterEntity("uz7_ch1", "uz_7", 1, "Ilk o‘rta asrlar (IV–VIII asrlar)"),
            ChapterEntity("uz7_ch2", "uz_7", 2, "Arab xalifaligi davri"),
            ChapterEntity("uz7_ch3", "uz_7", 3, "Somoniylar davlati"),
            ChapterEntity("uz7_ch4", "uz_7", 4, "Qoraxoniylar va Xorazmshohlar"),
            ChapterEntity("uz7_ch5", "uz_7", 5, "Amir Temur va Temuriylar davri")
        )
        db.chapterDao().insertAll(chapters)

        val topics = listOf(
            TopicEntity("uz7_t1", "uz7_ch1", "uz_7", 1, "Xioniylar, Kidariylar va Eftallar"),
            TopicEntity("uz7_t2", "uz7_ch1", "uz_7", 2, "Turk xoqonligi"),
            TopicEntity("uz7_t3", "uz7_ch2", "uz_7", 1, "Arablarning Movarounnahrga kirib kelishi"),
            TopicEntity("uz7_t4", "uz7_ch3", "uz_7", 1, "Somoniylar davlatining tashkil topishi"),
            TopicEntity("uz7_t5", "uz7_ch5", "uz_7", 1, "Amir Temur saltanatining yuksalishi")
        )
        db.topicDao().insertAll(topics)

        // Real sample questions grounded in textbook content (from provided pages)
        val questions = listOf(
            QuestionEntity(
                id = "q1",
                topicId = "uz7_t1",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "IV–XV asrlarda dastlab qaysi davlatlar faoliyat ko‘rsatgan?",
                optionsJson = """[{"id":"a","text":"Xorazm, Xioniylar, Kidariylar, Eftallar","isCorrect":true},{"id":"b","text":"Rim imperiyasi","isCorrect":false},{"id":"c","text":"Xitoy imperiyasi","isCorrect":false},{"id":"d","text":"Vizantiya","isCorrect":false}]""",
                explanation = "Darslik kirish qismida ko‘rsatilganidek, IV–XV asrlarda dastlab Xorazm, Xioniylar, Kidariylar, Eftallar, keyinchalik Turk xoqonligi va boshqa davlatlar faoliyat ko‘rsatgan.",
                historicalDate = "IV–XV asrlar",
                personality = null,
                sourceReference = "O‘zbekiston tarixi 7-sinf, Kirish",
                pageHint = "3"
            ),
            QuestionEntity(
                id = "q2",
                topicId = "uz7_t1",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "“O‘zbek” atamasi dastlab nimani anglatgan?",
                optionsJson = """[{"id":"a","text":"Qabila begi, hukmdor","isCorrect":true},{"id":"b","text":"Oddiy dehqon","isCorrect":false},{"id":"c","text":"Savdogar","isCorrect":false},{"id":"d","text":"Harbiy qul","isCorrect":false}]""",
                explanation = "Darslikda “O‘zbek” – qabila begi, hukmdor deb izohlangan.",
                historicalDate = null,
                personality = null,
                sourceReference = "O‘zbekiston tarixi 7-sinf, Kirish",
                pageHint = "3"
            ),
            QuestionEntity(
                id = "q3",
                topicId = "uz7_t3",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "VIII asrning boshlarida mamlakatimizda qaysi davlat o‘rnatildi?",
                optionsJson = """[{"id":"a","text":"Arab xalifaligi","isCorrect":true},{"id":"b","text":"Mongol imperiyasi","isCorrect":false},{"id":"c","text":"Rus knyazligi","isCorrect":false},{"id":"d","text":"Xitoy sulolasi","isCorrect":false}]""",
                explanation = "VIII asrning boshlariga kelib mamlakatimizda Arab xalifaligi o‘rnatildi.",
                historicalDate = "VIII asr boshi",
                personality = null,
                sourceReference = "O‘zbekiston tarixi 7-sinf, Kirish",
                pageHint = "3"
            ),
            QuestionEntity(
                id = "q4",
                topicId = "uz7_t4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Jahon ilm-fan rivojiga hissa qo‘shgan olimlardan biri kim?",
                optionsJson = """[{"id":"a","text":"Muhammad ibn Muso al-Xorazmiy","isCorrect":true},{"id":"b","text":"Ibn Batuta","isCorrect":false},{"id":"c","text":"Marko Polo","isCorrect":false},{"id":"d","text":"Gerodot","isCorrect":false}]""",
                explanation = "Yurtimizdan jahon ilm-fan rivojiga hissa qo‘shgan Muhammad ibn Muso al-Xorazmiy, Ahmad al-Farg‘oniy, Abu Rayhon Beruniy, Ibn Sino, Ulug‘bek va Alisher Navoiy kabi buyuk mutafakkirlar yetishib chiqdi.",
                historicalDate = null,
                personality = "Muhammad ibn Muso al-Xorazmiy",
                sourceReference = "O‘zbekiston tarixi 7-sinf, Kirish",
                pageHint = "3"
            ),
            QuestionEntity(
                id = "q5",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.TRUE_FALSE.name,
                difficulty = Difficulty.EASY.name,
                text = "Amir Temur IV–XV asrlarda hukmronlik qilgan yirik davlatlardan biri edi.",
                optionsJson = """[{"id":"a","text":"To‘g‘ri","isCorrect":true},{"id":"b","text":"Noto‘g‘ri","isCorrect":false}]""",
                explanation = "Darslikda Amir Temur hukmronlik qilgan yirik davlatlar qatorida keltirilgan.",
                historicalDate = "XIV–XV asr",
                personality = "Amir Temur",
                sourceReference = "O‘zbekiston tarixi 7-sinf, Kirish",
                pageHint = "3"
            ),
            QuestionEntity(
                id = "q6",
                topicId = "uz7_t1",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.HARD.name,
                text = "Ilk o‘rta asrlarda “qishloq hokimi” deb atalgan katta yer egasi nima deb atalgan?",
                optionsJson = """[{"id":"a","text":"Dehqon","isCorrect":true},{"id":"b","text":"Kadivar","isCorrect":false},{"id":"c","text":"Sulton","isCorrect":false},{"id":"d","text":"Emir","isCorrect":false}]""",
                explanation = "Ilk o‘rta asrlarda “qishloq hokimi” deb atalgan katta yer egasi – “dehqon”lar va ularning ekinzorlarida ishlovchi yersiz “kadivar”lar shakllandi.",
                historicalDate = "Ilk o‘rta asrlar",
                personality = null,
                sourceReference = "O‘zbekiston tarixi 7-sinf, Kirish",
                pageHint = "3"
            ),
            // Additional questions for variety
            QuestionEntity(
                id = "q7",
                topicId = "uz7_t2",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Turk xoqonligi qaysi asrlarda mavjud bo‘lgan?",
                optionsJson = """[{"id":"a","text":"VI–VIII asrlar","isCorrect":true},{"id":"b","text":"X–XII asrlar","isCorrect":false},{"id":"c","text":"XIV–XV asrlar","isCorrect":false},{"id":"d","text":"II–IV asrlar","isCorrect":false}]""",
                explanation = "Turk xoqonligi VI asrda tashkil topgan va VIII asrgacha mavjud bo‘lgan.",
                historicalDate = "VI–VIII asr",
                personality = null,
                sourceReference = "O‘zbekiston tarixi 7-sinf",
                pageHint = null
            ),
            QuestionEntity(
                id = "q8",
                topicId = "uz7_t4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EXPERT.name,
                text = "Abu Rayhon Beruniy qaysi sohada mashhur?",
                optionsJson = """[{"id":"a","text":"Astronomiya, matematika, geografiya","isCorrect":true},{"id":"b","text":"Faqat she’riyat","isCorrect":false},{"id":"c","text":"Harbiy san’at","isCorrect":false},{"id":"d","text":"Tibbiyot (faqat)","isCorrect":false}]""",
                explanation = "Abu Rayhon Beruniy buyuk ensiklopedist olim bo‘lib, astronomiya, matematika, geografiya va boshqa sohalarda asarlar yozgan.",
                historicalDate = "X–XI asr",
                personality = "Abu Rayhon Beruniy",
                sourceReference = "O‘zbekiston tarixi 7-sinf",
                pageHint = null
            )
        )
        db.questionDao().insertAll(questions)

        // Seed basic achievements
        val achievements = listOf(
            AchievementEntity("first_steps", false, null, 0f),
            AchievementEntity("daily_learner", false, null, 0f),
            AchievementEntity("streak_3", false, null, 0f),
            AchievementEntity("streak_7", false, null, 0f),
            AchievementEntity("date_master", false, null, 0f),
            AchievementEntity("personality_expert", false, null, 0f),
            AchievementEntity("mistake_corrector", false, null, 0f),
            AchievementEntity("perfect_score", false, null, 0f),
            AchievementEntity("topic_specialist", false, null, 0f),
            AchievementEntity("exam_champion", false, null, 0f)
        )
        db.achievementDao().insertAll(achievements)
    }

    private fun TextbookEntity.toDomain() = Textbook(
        id = id, title = title, gradeId = gradeId, subjectId = subjectId,
        authors = authors, year = year, edition = edition,
        totalChapters = totalChapters, totalTopics = totalTopics, totalQuestions = totalQuestions
    )

    private fun QuestionEntity.toDomain(json: Json): Question {
        val options = try {
            json.decodeFromString<List<AnswerOptionDto>>(optionsJson).map {
                AnswerOption(it.id, it.text, it.isCorrect)
            }
        } catch (e: Exception) {
            emptyList()
        }
        return Question(
            id = id, topicId = topicId, textbookId = textbookId, gradeId = gradeId,
            subjectId = subjectId,
            type = QuestionType.valueOf(type),
            difficulty = Difficulty.valueOf(difficulty),
            text = text, options = options, explanation = explanation,
            historicalDate = historicalDate, personality = personality,
            sourceReference = sourceReference, pageHint = pageHint
        )
    }
}

@kotlinx.serialization.Serializable
private data class AnswerOptionDto(val id: String, val text: String, val isCorrect: Boolean)
