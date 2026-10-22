package com.tarixustoz.app.domain.usecase

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.tarixustoz.app.BuildConfig
import com.tarixustoz.app.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerativeQuizService @Inject constructor() {
    
    // Fallback/Demo API KEY. In production, this should be securely fetched.
    private val apiKey = BuildConfig.GEMINI_API_KEY
    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash-latest",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.7f // Birchuncha kreativlik va qiyin chalg'ituvchi javoblar uchun
            responseMimeType = "application/json"
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateQuestions(
        topicContent: String,
        count: Int,
        difficulty: Difficulty,
        category: QuestionCategory
    ): List<Question> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY") {
            // Qachonki API key bo'lmasa, hardcoded qiyin savollar qaytaramiz (Demo uchun)
            return@withContext getFallbackQuestions()
        }

        val prompt = """
            Sen O'zbekiston va Jahon tarixi bo'yicha ekspert va universitet kirish imtihonlari (DTM) savol tuzuvchisisan.
            Quyidagi matn asosida $count ta test tuz.
            
            Talablar:
            1. Darajasi: ${difficulty.name}. Savollar oson yoki zerikarli bo'lmasin.
            2. Ayniqsa O'rta (MEDIUM) va Qiyin (HARD) darajalar uchun variantlar bir-biriga juda o'xshash va mantiqan chalg'ituvchi bo'lsin. Masalan, sanalar 1-2 yilga farq qilsin, shaxslarning ismlari o'xshash bo'lsin yoki qilingan ishlar o'xshash bo'lsin.
            3. Kategoriya: ${category.titleUz}. Agar kategoriya "Yillar va Sanalar" bo'lsa, asosan sanalarga urg'u ber. Agar "Tarixiy Shaxslar" bo'lsa, shaxslar faoliyatiga urg'u ber.
            4. Savollar o'zbek tilida (lotin yozuvida) bo'lsin.
            
            Matn:
            "$topicContent"
            
            Faqat va faqat JSON formatda qaytar. Hech qanday boshqa matn qo'shma. JSON strukturasi quyidagicha bo'lsin:
            [
              {
                "text": "Savol matni",
                "explanation": "To'g'ri javob uchun batafsil tushuntirish va manba",
                "options": [
                  { "id": "a", "text": "A varianti", "isCorrect": true },
                  { "id": "b", "text": "B varianti", "isCorrect": false },
                  { "id": "c", "text": "C varianti", "isCorrect": false },
                  { "id": "d", "text": "D varianti", "isCorrect": false }
                ]
              }
            ]
        """.trimIndent()

        try {
            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text?.trim() ?: "[]"
            val cleanJson = if (jsonText.startsWith("```json")) {
                jsonText.substring(7, jsonText.length - 3).trim()
            } else jsonText
            
            val dto = json.decodeFromString<List<QuestionDto>>(cleanJson)
            dto.mapIndexed { index, q ->
                Question(
                    id = "gen_${System.currentTimeMillis()}_$index",
                    topicId = "gen_topic",
                    textbookId = "gen_tb",
                    gradeId = 0,
                    subjectId = "gen",
                    type = QuestionType.SINGLE_CHOICE,
                    difficulty = difficulty,
                    category = category,
                    text = q.text,
                    options = q.options.map { AnswerOption(it.id, it.text, it.isCorrect) }.shuffled(),
                    explanation = q.explanation
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackQuestions()
        }
    }

    private fun getFallbackQuestions(): List<Question> {
        // AI ishlamay qolganda qaytariladigan juda qiyin / chalg'ituvchi savollar namunasi
        return listOf(
            Question(
                id = "f1", topicId = "t1", textbookId = "tb", gradeId = 10, subjectId = "uzb",
                type = QuestionType.SINGLE_CHOICE, difficulty = Difficulty.HARD, category = QuestionCategory.YEAR,
                text = "Amir Temur qachon To'xtamishxonga qarshi Qunduzcha jangida g'alaba qozongan?",
                options = listOf(
                    AnswerOption("a", "1391-yil 18-iyun", true),
                    AnswerOption("b", "1391-yil 18-iyul", false),
                    AnswerOption("c", "1395-yil 15-aprel", false),
                    AnswerOption("d", "1389-yil 18-iyun", false)
                ),
                explanation = "Qunduzcha (Qunduzcha daryosi bo'yidagi) jang 1391-yil 18-iyunda bo'lib o'tgan. Terek daryosi bo'yidagi jang esa 1395-yilda bo'lgan. Oylar va yillar bilan chalg'itish keng tarqalgan."
            ),
            Question(
                id = "f2", topicId = "t1", textbookId = "tb", gradeId = 10, subjectId = "uzb",
                type = QuestionType.SINGLE_CHOICE, difficulty = Difficulty.HARD, category = QuestionCategory.PERSON,
                text = "Xorazmshohlar davlatida davlat boshqaruvida turk-qang'li zodagonlarining ta'siri kuchayib ketishiga kim sabab bo'lgan?",
                options = listOf(
                    AnswerOption("a", "Turkon Xotun", true),
                    AnswerOption("b", "Jaloliddin Manguberdi", false),
                    AnswerOption("c", "O'zloqshoh", false),
                    AnswerOption("d", "Qutbiddin Muhammad", false)
                ),
                explanation = "Turkon Xotun – Muhammad Xorazmshohning onasi, qipchoq-qang'li urug'idan bo'lib, o'z urug'doshlarini yuqori lavozimlarga tayinlagan."
            ),
            Question(
                id = "f3", topicId = "t1", textbookId = "tb", gradeId = 10, subjectId = "uzb",
                type = QuestionType.SINGLE_CHOICE, difficulty = Difficulty.EXPERT, category = QuestionCategory.LOGICAL,
                text = "Quyidagi voqealarni xronologik ketma-ketlikda to'g'ri joylashtiring:\n1) Buxoro amirligi Rossiya imperiyasi protektoratiga aylanishi\n2) Xiva xonligi protektoratga aylanishi\n3) Qo'qon xonligi tugatilishi",
                options = listOf(
                    AnswerOption("a", "1, 2, 3", true),
                    AnswerOption("b", "2, 1, 3", false),
                    AnswerOption("c", "3, 1, 2", false),
                    AnswerOption("d", "1, 3, 2", false)
                ),
                explanation = "Buxoro - 1868 yil, Xiva - 1873 yil, Qo'qon xonligi - 1876 yilda tugatilgan."
            )
        )
    }

    @Serializable
    private data class QuestionDto(
        val text: String,
        val explanation: String,
        val options: List<OptionDto>
    )

    @Serializable
    private data class OptionDto(
        val id: String,
        val text: String,
        val isCorrect: Boolean
    )
}
