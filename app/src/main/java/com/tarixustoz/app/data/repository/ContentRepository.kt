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

    fun getAllTopics(): Flow<List<Topic>> =
        db.topicDao().getAll().map { list ->
            list.map { Topic(it.id, it.chapterId, it.textbookId, it.number, it.title) }
        }

    fun getTopicsByTextbook(textbookId: String): Flow<List<Topic>> =
        db.topicDao().getByTextbook(textbookId).map { list ->
            list.map { Topic(it.id, it.chapterId, it.textbookId, it.number, it.title) }
        }

    suspend fun getQuestionsForTopics(topicIds: List<String>): List<Question> {
        seedIfNeeded()
        val list = if (topicIds.isEmpty()) getAllQuestions() else db.questionDao().getByTopics(topicIds).map { it.toDomain(json) }
        return if (list.isNotEmpty()) list else getAllQuestions()
    }

    suspend fun getQuestionsForTopic(topicId: String): List<Question> =
        getQuestionsForTopics(listOf(topicId))

    suspend fun getQuestionById(id: String): Question? =
        db.questionDao().getById(id)?.toDomain(json)

    suspend fun getRandomQuestions(gradeId: Int, subjectId: String, count: Int): List<Question> =
        db.questionDao().getRandom(gradeId, subjectId, count).map { it.toDomain(json) }

    suspend fun getAllQuestions(): List<Question> =
        db.questionDao().getAllQuestions().map { it.toDomain(json) }

    suspend fun getDailyQuestion(): Question {
        seedIfNeeded()
        return db.questionDao().getById("q_daily_nile")?.toDomain(json)
            ?: getAllQuestions().firstOrNull()
            ?: Question(
                id = "q_daily_nile",
                topicId = "uz6_t1",
                textbookId = "uz_6",
                gradeId = 6,
                subjectId = "jahon",
                type = QuestionType.SINGLE_CHOICE,
                difficulty = Difficulty.MEDIUM,
                text = "Qadimgi Misrda Nil daryosining toshishi nimaga sabab bo‘lgan?",
                options = listOf(
                    AnswerOption("a", "Yerlarning unumdor bo‘lishiga", true),
                    AnswerOption("b", "Qurg‘oqchilik kuchayishiga", false),
                    AnswerOption("c", "Savdo yo‘llarining yopilishiga", false),
                    AnswerOption("d", "Shahar devorlarining buzilishiga", false)
                ),
                explanation = "Nil daryosining har yili toshishi qora loyqa keltirib, vodiydagi yerlarni juda unumdor qilgan va dehqonchilik rivojlanishiga asosiy zamin yaratgan.",
                historicalDate = "Mil. avv. IV mingyillik",
                personality = null,
                sourceReference = "6-sinf Qadimgi dunyo tarixi",
                pageHint = "18"
            )
    }

    suspend fun getQuestionsByCategory(category: String): List<Question> {
        seedIfNeeded()
        return when (category.lowercase()) {
            "shahar_nomlari", "shaharlar" -> {
                val list = db.questionDao().getByCities().map { it.toDomain(json) }
                if (list.isNotEmpty()) list else getAllQuestions()
            }
            "tarixiy_shaxslar", "shaxslar" -> {
                val list = db.questionDao().getByPersonality().map { it.toDomain(json) }
                if (list.isNotEmpty()) list else getAllQuestions()
            }
            "yillar_boyicha", "yillar" -> {
                val list = db.questionDao().getByHistoricalDate().map { it.toDomain(json) }
                if (list.isNotEmpty()) list else getAllQuestions()
            }
            "tarixiy_voqealar", "voqealar" -> {
                val list = db.questionDao().getByEvents().map { it.toDomain(json) }
                if (list.isNotEmpty()) list else getAllQuestions()
            }
            "tarixiy_obidalar", "obidalar" -> {
                val list = db.questionDao().getByMonuments().map { it.toDomain(json) }
                if (list.isNotEmpty()) list else getAllQuestions()
            }
            "urushlar_va_janglar", "urushlar", "janglar" -> {
                val list = db.questionDao().getByWars().map { it.toDomain(json) }
                if (list.isNotEmpty()) list else getAllQuestions()
            }
            "streak_campaign", "campaign_6" -> {
                val list = db.questionDao().getByGrade6().map { it.toDomain(json) }
                if (list.isNotEmpty()) list else getAllQuestions()
            }
            "dtm_exam", "dtm" -> {
                val all = getAllQuestions()
                if (all.size >= 15) all.shuffled().take(30) else all
            }
            else -> getAllQuestions()
        }
    }

    suspend fun seedIfNeeded() {
        if (db.questionDao().getById("q_daily_nile") == null) {
            seedContent()
        }
    }

    private suspend fun seedContent() {
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

        val chapters = listOf(
            ChapterEntity("uz6_ch1", "uz_6", 1, "Qadimgi Sharq va Misr"),
            ChapterEntity("uz6_ch2", "uz_6", 2, "Qadimgi Baqtriya va So‘g‘diyona"),
            ChapterEntity("uz7_ch1", "uz_7", 1, "Ilk o‘rta asrlar (IV–VIII asrlar)"),
            ChapterEntity("uz7_ch2", "uz_7", 2, "Arab xalifaligi davri"),
            ChapterEntity("uz7_ch3", "uz_7", 3, "Somoniylar davlati"),
            ChapterEntity("uz7_ch4", "uz_7", 4, "Qoraxoniylar va Xorazmshohlar"),
            ChapterEntity("uz7_ch5", "uz_7", 5, "Amir Temur va Temuriylar davri"),
            ChapterEntity("jahon7_ch1", "jahon_7", 1, "O‘rta asrlarda Yevropa"),
            ChapterEntity("uz8_ch1", "uz_8", 1, "O‘zbek xonliklari davri"),
            ChapterEntity("uz9_ch1", "uz_9", 1, "Turkistonda milliy uyg‘onish va jadidchilik"),
            ChapterEntity("uz10_ch1", "uz_10", 1, "Sovet mustamlakachiligi va qatag‘on yillari"),
            ChapterEntity("uz11_ch1", "uz_11", 1, "Mustaqil O‘zbekiston taraqqiyoti")
        )
        db.chapterDao().insertAll(chapters)

        val topics = listOf(
            TopicEntity("uz6_t1", "uz6_ch1", "uz_6", 1, "Qadimgi Misr madaniyati va Nil daryosi"),
            TopicEntity("uz6_t2", "uz6_ch2", "uz_6", 2, "Qadimgi Baqtriya va So‘g‘diyona davlati"),
            TopicEntity("uz6_t3", "uz6_ch2", "uz_6", 3, "Yunon-Baqtriya podsholigi va Kushonlar"),
            TopicEntity("uz7_t1", "uz7_ch1", "uz_7", 1, "Xioniylar, Kidariylar va Eftallar"),
            TopicEntity("uz7_t2", "uz7_ch1", "uz_7", 2, "Turk xoqonligi va Buyuk Ipak yo‘li"),
            TopicEntity("uz7_t3", "uz7_ch2", "uz_7", 3, "Arablarning Movarounnahrga kirib kelishi"),
            TopicEntity("uz7_t4", "uz7_ch3", "uz_7", 4, "Somoniylar davlatining tashkil topishi va madaniyat"),
            TopicEntity("uz7_t5", "uz7_ch5", "uz_7", 5, "Amir Temur saltanatining yuksalishi"),
            TopicEntity("jahon7_t1", "jahon7_ch1", "jahon_7", 1, "Vizantiya imperiyasi va Salib yurishlari"),
            TopicEntity("uz8_t1", "uz8_ch1", "uz_8", 1, "Buxoro amirligi, Xiva va Qo‘qon xonliklari"),
            TopicEntity("uz9_t1", "uz9_ch1", "uz_9", 1, "Jadidchilik harakati va Mahmudxo‘ja Behbudiy"),
            TopicEntity("uz10_t1", "uz10_ch1", "uz_10", 1, "Turkiston Muxtoriyati fojiasi"),
            TopicEntity("uz11_t1", "uz11_ch1", "uz_11", 1, "O‘zbekiston Respublikasining Mustaqilligi e’lon qilinishi")
        )
        db.topicDao().insertAll(topics)

        val questions = listOf(
            // Kun savoli (Figma UI Featured)
            QuestionEntity(
                id = "q_daily_nile",
                topicId = "uz6_t1",
                textbookId = "uz_6",
                gradeId = 6,
                subjectId = "jahon",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Qadimgi Misrda Nil daryosining toshishi nimaga sabab bo‘lgan?",
                optionsJson = """[{"id":"a","text":"Yerlarning unumdor bo‘lishiga","isCorrect":true},{"id":"b","text":"Qurg‘oqchilik boshlanishiga","isCorrect":false},{"id":"c","text":"Savdo yo‘llarining yopilishiga","isCorrect":false},{"id":"d","text":"Shahar devorlarining qulashiga","isCorrect":false}]""",
                explanation = "Nil daryosining toshishi qora loyqa keltirib chiqargan. Bu loyqa yerlarni tabiiy o‘g‘itlab, Nil vodiysida yuqori hosil yetishtirish imkonini bergan.",
                historicalDate = "Mil. avv. IV mingyillik",
                personality = null,
                sourceReference = "6-sinf Tarix, Qadimgi dunyo",
                pageHint = "18"
            ),
            // Shahar nomlari
            QuestionEntity(
                id = "q_city_1",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "Amir Temur saltanatining bosh poytaxti qaysi shahar bo‘lgan?",
                optionsJson = """[{"id":"a","text":"Samarqand","isCorrect":true},{"id":"b","text":"Buxoro","isCorrect":false},{"id":"c","text":"Shahrisabz","isCorrect":false},{"id":"d","text":"Hirot","isCorrect":false}]""",
                explanation = "Amir Temur 1370-yilda hokimiyatni qo‘lga olgach, Samarqandni o‘zining qudratli saltanati poytaxtiga aylantirdi.",
                historicalDate = "1370-yil",
                personality = "Amir Temur",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "78"
            ),
            QuestionEntity(
                id = "q_city_2",
                topicId = "uz7_t4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Somoniylar davlatining asosiy poytaxti qaysi shahar bo‘lgan?",
                optionsJson = """[{"id":"a","text":"Buxoro","isCorrect":true},{"id":"b","text":"Samarqand","isCorrect":false},{"id":"c","text":"Termiz","isCorrect":false},{"id":"d","text":"Toshkent","isCorrect":false}]""",
                explanation = "Ismoil Somoniy davrida Buxoro nafaqat siyosiy poytaxt, balki Sharqning eng yirik ilm-fan markaziga aylandi.",
                historicalDate = "875–999-yillar",
                personality = "Ismoil Somoniy",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "45"
            ),
            QuestionEntity(
                id = "q_city_3",
                topicId = "uz7_t1",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Qadimgi Baqtriya davlatining poytaxti qaysi shahar bo‘lgan?",
                optionsJson = """[{"id":"a","text":"Baqtra (Balx)","isCorrect":true},{"id":"b","text":"Marv","isCorrect":false},{"id":"c","text":"Niso","isCorrect":false},{"id":"d","text":"Chach","isCorrect":false}]""",
                explanation = "Baqtra (hozirgi Afg‘oniston hududidagi Balx shahri) minglab yillar davomida qudratli Baqtriyaning markazi bo‘lgan.",
                historicalDate = "Mil. avv. I mingyillik",
                personality = null,
                sourceReference = "6-sinf Qadimgi dunyo tarixi",
                pageHint = "62"
            ),
            // Tarixiy shaxslar
            QuestionEntity(
                id = "q_person_1",
                topicId = "uz7_t4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "Algebra fanining asoschisi hisoblangan vatandosh allomamiz kim?",
                optionsJson = """[{"id":"a","text":"Al-Xorazmiy","isCorrect":true},{"id":"b","text":"Abu Ali ibn Sino","isCorrect":false},{"id":"c","text":"Abu Rayhon Beruniy","isCorrect":false},{"id":"d","text":"Mirzo Ulug‘bek","isCorrect":false}]""",
                explanation = "Muhammad ibn Muso al-Xorazmiy o‘zining “Al-jabr val-muqobala” asari orqali algebra faniga asos solgan.",
                historicalDate = "783–850-yillar",
                personality = "Al-Xorazmiy",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "54"
            ),
            QuestionEntity(
                id = "q_person_2",
                topicId = "uz7_t4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "“Tib qonunlari” shoh asarining muallifi kim?",
                optionsJson = """[{"id":"a","text":"Abu Ali ibn Sino","isCorrect":true},{"id":"b","text":"Al-Xorazmiy","isCorrect":false},{"id":"c","text":"Farobiy","isCorrect":false},{"id":"d","text":"Ahmad Farg‘oniy","isCorrect":false}]""",
                explanation = "Ibn Sino (Avitsenna) tomonidan yozilgan “Tib qonunlari” asari bir necha asrlar davomida Yevropa va Sharq universitetlarida asosiy darslik bo‘lgan.",
                historicalDate = "980–1037-yillar",
                personality = "Ibn Sino",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "58"
            ),
            QuestionEntity(
                id = "q_person_3",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Mo‘g‘ul bosqinchilariga qarshi mardonavor kurashgan afsonaviy sarkarda kim?",
                optionsJson = """[{"id":"a","text":"Jaloliddin Manguberdi","isCorrect":true},{"id":"b","text":"Temur Malik","isCorrect":false},{"id":"c","text":"Najmiddin Kubro","isCorrect":false},{"id":"d","text":"Mahmud Torobiy","isCorrect":false}]""",
                explanation = "Jaloliddin Manguberdi Chingizxon boshliq mo‘g‘ul qo‘shinlariga qarshi 11 yil davomida tengsiz jasorat bilan jang olib borgan.",
                historicalDate = "1220–1231-yillar",
                personality = "Jaloliddin Manguberdi",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "68"
            ),
            // Yillar bo'yicha
            QuestionEntity(
                id = "q_year_1",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "Sohibqiron Amir Temur qaysi yilda tavallud topgan?",
                optionsJson = """[{"id":"a","text":"1336-yil 9-aprel","isCorrect":true},{"id":"b","text":"1370-yil 10-aprel","isCorrect":false},{"id":"c","text":"1405-yil 18-fevral","isCorrect":false},{"id":"d","text":"1342-yil 1-may","isCorrect":false}]""",
                explanation = "Amir Temur 1336-yil 9-aprelda Kesh (hozirgi Shahrisabz) yaqinidagi Xo‘ja Ilg‘or qishlog‘ida tug‘ilgan.",
                historicalDate = "1336-yil 9-aprel",
                personality = "Amir Temur",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "75"
            ),
            QuestionEntity(
                id = "q_year_2",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Jaloliddin Manguberdi Parvon jangida mo‘g‘ullarni qaysi yilda mag‘lubiyatga uchratgan?",
                optionsJson = """[{"id":"a","text":"1221-yil","isCorrect":true},{"id":"b","text":"1219-yil","isCorrect":false},{"id":"c","text":"1223-yil","isCorrect":false},{"id":"d","text":"1225-yil","isCorrect":false}]""",
                explanation = "1221-yilgi Parvon jangida Jaloliddin Manguberdi Chingizxon qo‘shinini ochiq maydonda ilk bor tor-mor etgan.",
                historicalDate = "1221-yil",
                personality = "Jaloliddin Manguberdi",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "70"
            ),
            // Mavzu bo'yicha
            QuestionEntity(
                id = "q_topic_1",
                topicId = "uz7_t1",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Buyuk Ipak yo‘li bo‘ylab savdoda asosiy vositachi xalq qaysi bo‘lgan?",
                optionsJson = """[{"id":"a","text":"Sug‘diylar","isCorrect":true},{"id":"b","text":"Xitoyliklar","isCorrect":false},{"id":"c","text":"Forslar","isCorrect":false},{"id":"d","text":"Arablar","isCorrect":false}]""",
                explanation = "Sug‘diylar Buyuk Ipak yo‘lining deyarli barcha shaharlarida savdo koloniyalari barpo etib, Sharq va G‘arb o‘rtasidagi savdoni boshqargan.",
                historicalDate = "IV–VIII asrlar",
                personality = null,
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "25"
            ),
            QuestionEntity(
                id = "q_topic_2",
                topicId = "uz7_t1",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "Zardushtiylik dinining muqaddas kitobi nima deb ataladi?",
                optionsJson = """[{"id":"a","text":"Avesto","isCorrect":true},{"id":"b","text":"Veda","isCorrect":false},{"id":"c","text":"Tavrot","isCorrect":false},{"id":"d","text":"Tripitaka","isCorrect":false}]""",
                explanation = "Zardushtiylikning muqaddas yodgorligi “Avesto” bo‘lib, unda qadimgi ajdodlarimizning diniy, falsafiy va axloqiy qarashlari jamlangan.",
                historicalDate = "Mil. avv. I mingyillik",
                personality = "Zardusht",
                sourceReference = "6-sinf Qadimgi dunyo tarixi",
                pageHint = "40"
            ),
            // Tarixiy voqealar
            QuestionEntity(
                id = "q_event_1",
                topicId = "uz11_t1",
                textbookId = "uz_11",
                gradeId = 11,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "O‘zbekiston Respublikasining davlat mustaqilligi qachon e’lon qilingan?",
                optionsJson = """[{"id":"a","text":"1991-yil 31-avgust","isCorrect":true},{"id":"b","text":"1990-yil 20-iyun","isCorrect":false},{"id":"c","text":"1992-yil 8-dekabr","isCorrect":false},{"id":"d","text":"1991-yil 1-sentyabr","isCorrect":false}]""",
                explanation = "1991-yil 31-avgustda Oliy Kengashning navbatdan tashqari sessiyasida O‘zbekiston Respublikasining davlat mustaqilligi to‘g‘risidagi Oliy Kengash Bayonoti qabul qilindi.",
                historicalDate = "1991-yil 31-avgust",
                personality = null,
                sourceReference = "11-sinf O‘zbekiston tarixi",
                pageHint = "8"
            ),
            QuestionEntity(
                id = "q_event_2",
                topicId = "uz10_t1",
                textbookId = "uz_10",
                gradeId = 10,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Turkiston Muxtoriyati qaysi shaharda va qachon tashkil etilgan?",
                optionsJson = """[{"id":"a","text":"Qo‘qon shahrida, 1917-yil noyabr","isCorrect":true},{"id":"b","text":"Toshkentda, 1918-yil yanvar","isCorrect":false},{"id":"c","text":"Samarqandda, 1917-yil oktyabr","isCorrect":false},{"id":"d","text":"Buxoroda, 1920-yil sentyabr","isCorrect":false}]""",
                explanation = "1917-yil 26-28-noyabrda Qo‘qon shahrida o‘tkazilgan IV Umumturkiston favqulodda musulmonlar syezdida Turkiston Muxtoriyati e’lon qilindi.",
                historicalDate = "1917-yil 27-noyabr",
                personality = "Mustafo Cho‘qay",
                sourceReference = "10-sinf O‘zbekiston tarixi",
                pageHint = "14"
            ),
            // Tarixiy obidalar
            QuestionEntity(
                id = "q_monument_1",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "Samarqanddagi Registon maydonida bunyod etilgan eng qadimgi madrasa qaysi?",
                optionsJson = """[{"id":"a","text":"Ulug‘bek madrasasi","isCorrect":true},{"id":"b","text":"Sherdor madrasasi","isCorrect":false},{"id":"c","text":"Tilla-Qori madrasasi","isCorrect":false},{"id":"d","text":"Ko‘kaldosh madrasasi","isCorrect":false}]""",
                explanation = "Registon maydonidagi birinchi va eng ulug‘vor bino Mirzo Ulug‘bek tomonidan 1417–1420-yillarda qurilgan Ulug‘bek madrasasidir.",
                historicalDate = "1417–1420-yillar",
                personality = "Mirzo Ulug‘bek",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "92"
            ),
            QuestionEntity(
                id = "q_monument_2",
                topicId = "uz7_t4",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Somoniylar maqbarasi qaysi shahar me’moriy yodgorligi hisoblanadi?",
                optionsJson = """[{"id":"a","text":"Buxoro","isCorrect":true},{"id":"b","text":"Samarqand","isCorrect":false},{"id":"c","text":"Xiva","isCorrect":false},{"id":"d","text":"Termiz","isCorrect":false}]""",
                explanation = "IX–X asrlarda pishiq g‘ishtdan terilgan noyob Ismoil Somoniy maqbarasi qadimiy Buxoro shahrida joylashgan.",
                historicalDate = "IX–X asrlar",
                personality = "Ismoil Somoniy",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "48"
            ),
            // Urushlar va janglar
            QuestionEntity(
                id = "q_war_1",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Anqara jangi (1402) qaysi ikki yirik davlat hukmdorlari o‘rtasida bo‘lib o‘tgan?",
                optionsJson = """[{"id":"a","text":"Amir Temur va Boyazid Yildirim","isCorrect":true},{"id":"b","text":"Amir Temur va To‘xtamishxon","isCorrect":false},{"id":"c","text":"Jaloliddin Manguberdi va Chingizxon","isCorrect":false},{"id":"d","text":"Ulug‘bek va Abdullatif","isCorrect":false}]""",
                explanation = "1402-yil 28-iyulda Anqara yaqinida Amir Temur va Usmonli imperiyasi sultoni Boyazid Yildirim qo‘shinlari o‘rtasida tarixiy jang bo‘lib o‘tgan.",
                historicalDate = "1402-yil 28-iyul",
                personality = "Amir Temur",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "85"
            ),
            QuestionEntity(
                id = "q_war_2",
                topicId = "uz7_t5",
                textbookId = "uz_7",
                gradeId = 7,
                subjectId = "uzbekiston",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "1221-yildagi Parvon jangida mo‘g‘ul sarkardalarini kim tor-mor etgan?",
                optionsJson = """[{"id":"a","text":"Jaloliddin Manguberdi","isCorrect":true},{"id":"b","text":"Temur Malik","isCorrect":false},{"id":"c","text":"Mahmud Torobiy","isCorrect":false},{"id":"d","text":"Muhammad Xorazmshoh","isCorrect":false}]""",
                explanation = "Parvon jangida Jaloliddin Manguberdi Shigi Xutuxu boshchiligidagi mo‘g‘ul qo‘shinini butunlay mag‘lubiyatga uchratdi.",
                historicalDate = "1221-yil",
                personality = "Jaloliddin Manguberdi",
                sourceReference = "7-sinf O‘zbekiston tarixi",
                pageHint = "71"
            ),
            // 6-sinf Tarix Qadimgi dunyo Campaign questions
            QuestionEntity(
                id = "q_campaign_6_1",
                topicId = "uz6_t1",
                textbookId = "uz_6",
                gradeId = 6,
                subjectId = "jahon",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.EASY.name,
                text = "Qadimgi Misrda ehromlar (piramidalar) nima maqsadda barpo etilgan?",
                optionsJson = """[{"id":"a","text":"Fir’avnlarning daxmasi (qabri) sifatida","isCorrect":true},{"id":"b","text":"Astronomik rasadxona sifatida","isCorrect":false},{"id":"c","text":"Harbiy istehkom sifatida","isCorrect":false},{"id":"d","text":"G‘alla ombori sifatida","isCorrect":false}]""",
                explanation = "Qadimgi Misr ehromlari fir’avnlarning abadiy oromgohi bo‘lgan ulkan me’moriy inshootlardir.",
                historicalDate = "Mil. avv. III mingyillik",
                personality = "Xufu (Xeops)",
                sourceReference = "6-sinf Qadimgi dunyo tarixi",
                pageHint = "22"
            ),
            QuestionEntity(
                id = "q_campaign_6_2",
                topicId = "uz6_t2",
                textbookId = "uz_6",
                gradeId = 6,
                subjectId = "jahon",
                type = QuestionType.SINGLE_CHOICE.name,
                difficulty = Difficulty.MEDIUM.name,
                text = "Zardushtiylik ta’limotiga ko‘ra ezgulik va yorug‘lik xudosi kim?",
                optionsJson = """[{"id":"a","text":"Axuramazda","isCorrect":true},{"id":"b","text":"Axriman","isCorrect":false},{"id":"c","text":"Mitra","isCorrect":false},{"id":"d","text":"Anaxita","isCorrect":false}]""",
                explanation = "Avestoda Axuramazda olamdagi ezgulik, haqiqat va yorug‘lik homiysi sifatida ulug‘langan.",
                historicalDate = "Mil. avv. I mingyillik",
                personality = "Zardusht",
                sourceReference = "6-sinf Qadimgi dunyo tarixi",
                pageHint = "42"
            )
        )
        db.questionDao().insertAll(questions)

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
