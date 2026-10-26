package com.tarixustoz.app.data.remote

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiCategoryDto(
    @SerializedName("id") @SerialName("id") val id: Long,
    @SerializedName("name") @SerialName("name") val name: String,
    @SerializedName("description") @SerialName("description") val description: String? = null,
    @SerializedName("order") @SerialName("order") val order: Int = 0,
    @SerializedName("grade_id") @SerialName("grade_id") val gradeId: Int = 6,
    @SerializedName("topics") @SerialName("topics") val topics: List<ApiTopicDto> = emptyList(),
    @SerializedName("pdf_books") @SerialName("pdf_books") val pdfBooks: List<ApiPdfBookDto> = emptyList()
)

@Serializable
data class ApiTopicDto(
    @SerializedName("id") @SerialName("id") val id: Long,
    @SerializedName("category") @SerialName("category") val categoryId: Long = 0,
    @SerializedName("document") @SerialName("document") val documentId: Long? = null,
    @SerializedName("grade") @SerialName("grade") val grade: Int? = null,
    @SerializedName("subject") @SerialName("subject") val subject: String? = null,
    @SerializedName("textbook_id") @SerialName("textbook_id") val textbookId: String? = null,
    @SerializedName("title") @SerialName("title") val title: String,
    @SerializedName("content") @SerialName("content") val content: String? = null,
    @SerializedName("order") @SerialName("order") val order: Int = 0,
    @SerializedName("chapter_title") @SerialName("chapter_title") val chapterTitle: String? = null,
    @SerializedName("questions") @SerialName("questions") val questions: List<ApiQuestionDto> = emptyList()
)

@Serializable
data class ApiPdfBookDto(
    @SerializedName("id") @SerialName("id") val id: Long,
    @SerializedName("category") @SerialName("category") val categoryId: Long? = null,
    @SerializedName("title") @SerialName("title") val title: String,
    @SerializedName("grade_id") @SerialName("grade_id") val gradeId: Int = 6,
    @SerializedName("grade") @SerialName("grade") val grade: Int? = null,
    @SerializedName("subject_id") @SerialName("subject_id") val subjectId: String? = "uzbekiston",
    @SerializedName("subject") @SerialName("subject") val subject: String? = null,
    @SerializedName("authors") @SerialName("authors") val authors: String? = null,
    @SerializedName("year") @SerialName("year") val year: Int = 2024,
    @SerializedName("edition") @SerialName("edition") val edition: String? = "1-nashr",
    @SerializedName("pdf_file") @SerialName("pdf_file") val pdfFile: String? = null,
    @SerializedName("cover_image") @SerialName("cover_image") val coverImage: String? = null,
    @SerializedName("pdf_url") @SerialName("pdf_url") val pdfUrl: String? = null,
    @SerializedName("cover_url") @SerialName("cover_url") val coverUrl: String? = null,
    @SerializedName("total_chapters") @SerialName("total_chapters") val totalChapters: Int = 10,
    @SerializedName("total_topics") @SerialName("total_topics") val totalTopics: Int = 35,
    @SerializedName("total_questions") @SerialName("total_questions") val totalQuestions: Int = 70
) {
    val effectiveGrade: Int
        get() = grade ?: gradeId.takeIf { it > 0 } ?: 6

    val effectiveSubject: String
        get() = subjectId ?: subject ?: "uzbekiston"
}

@Serializable
data class ApiQuestionDto(
    @SerializedName("id") @SerialName("id") val id: Long,
    @SerializedName("topic") @SerialName("topic") val topicId: Long? = null,
    @SerializedName("grade_id") @SerialName("grade_id") val gradeId: Int = 7,
    @SerializedName("subject_id") @SerialName("subject_id") val subjectId: String? = "uzbekiston",
    @SerializedName("text") @SerialName("text") val text: String,
    @SerializedName("option_a") @SerialName("option_a") val optionA: String,
    @SerializedName("option_b") @SerialName("option_b") val optionB: String,
    @SerializedName("option_c") @SerialName("option_c") val optionC: String,
    @SerializedName("option_d") @SerialName("option_d") val optionD: String,
    @SerializedName("correct_option") @SerialName("correct_option") val correctOption: String = "A",
    @SerializedName("explanation") @SerialName("explanation") val explanation: String? = null,
    @SerializedName("short_explanation") @SerialName("short_explanation") val shortExplanation: String? = null,
    @SerializedName("source_citation") @SerialName("source_citation") val sourceCitation: String? = null,
    @SerializedName("difficulty") @SerialName("difficulty") val difficulty: String = "EASY",
    @SerializedName("category_type") @SerialName("category_type") val categoryType: String = "ALL"
)

@Serializable
data class ApiEntityDto(
    @SerializedName("id") @SerialName("id") val id: Long,
    @SerializedName("canonical_name") @SerialName("canonical_name") val canonicalName: String,
    @SerializedName("entity_type") @SerialName("entity_type") val entityType: String,
    @SerializedName("description") @SerialName("description") val description: String? = null
)

@Serializable
data class DirectionCountDto(
    @SerializedName("code") @SerialName("code") val code: String,
    @SerializedName("title") @SerialName("title") val title: String = "",
    @SerializedName("count") @SerialName("count") val count: Int = 0
)

@Serializable
data class DirectionsResponseDto(
    @SerializedName("grade") @SerialName("grade") val grade: Int = 0,
    @SerializedName("subject") @SerialName("subject") val subject: String = "",
    @SerializedName("directions") @SerialName("directions") val directions: List<DirectionCountDto> = emptyList()
)

@Serializable
data class TicketQuestionsDto(
    @SerializedName("category") @SerialName("category") val category: String = "ALL",
    @SerializedName("ticket") @SerialName("ticket") val ticket: Int = 1,
    @SerializedName("size") @SerialName("size") val size: Int = 30,
    @SerializedName("total") @SerialName("total") val total: Int = 0,
    @SerializedName("tickets") @SerialName("tickets") val tickets: Int = 0,
    @SerializedName("questions") @SerialName("questions") val questions: List<ApiQuestionDto> = emptyList()
)

@Serializable
data class QuizGenerateRequest(
    @SerializedName("count") @SerialName("count") val count: Int = 10,
    @SerializedName("category_code") @SerialName("category_code") val categoryCode: String = "PRACTICE",
    @SerializedName("grade") @SerialName("grade") val grade: Int = 0,
    @SerializedName("subject") @SerialName("subject") val subject: String = "",
    @SerializedName("difficulty") @SerialName("difficulty") val difficulty: String = "MEDIUM",
    @SerializedName("topic_ids") @SerialName("topic_ids") val topicIds: List<Long> = emptyList()
)

@Serializable
data class QuizSessionDto(
    @SerializedName("session_id") @SerialName("session_id") val sessionId: String,
    @SerializedName("total_questions") @SerialName("total_questions") val totalQuestions: Int,
    @SerializedName("questions") @SerialName("questions") val questions: List<ApiQuestionDto>
)

@Serializable
data class SubmitAnswerRequest(
    @SerializedName("question_id") @SerialName("question_id") val questionId: Long,
    @SerializedName("selected_option") @SerialName("selected_option") val selectedOption: String,
    @SerializedName("time_taken_seconds") @SerialName("time_taken_seconds") val timeTakenSeconds: Int = 0
)

@Serializable
data class SubmitAnswerResponse(
    @SerializedName("is_correct") @SerialName("is_correct") val isCorrect: Boolean,
    @SerializedName("correct_option") @SerialName("correct_option") val correctOption: String,
    @SerializedName("explanation") @SerialName("explanation") val explanation: String? = null,
    @SerializedName("short_explanation") @SerialName("short_explanation") val shortExplanation: String? = null,
    @SerializedName("source_citation") @SerialName("source_citation") val sourceCitation: String? = null
)

@Serializable
data class RAGSearchResponse(
    @SerializedName("answer") @SerialName("answer") val answer: String,
    @SerializedName("evidence") @SerialName("evidence") val evidence: List<String> = emptyList(),
    @SerializedName("source_citations") @SerialName("source_citations") val sourceCitations: List<String> = emptyList()
)

@Serializable
data class ExamCategoryDto(
    @SerializedName("id") @SerialName("id") val id: Long,
    @SerializedName("code") @SerialName("code") val code: String,
    @SerializedName("title") @SerialName("title") val title: String,
    @SerializedName("description") @SerialName("description") val description: String? = null,
    @SerializedName("question_count_default") @SerialName("question_count_default") val questionCountDefault: Int = 30,
    @SerializedName("time_limit_minutes") @SerialName("time_limit_minutes") val timeLimitMinutes: Int = 60,
    @SerializedName("is_official") @SerialName("is_official") val isOfficial: Boolean = true
)

@Serializable
data class ApiAuditDto(
    @SerializedName("status") @SerialName("status") val status: String,
    @SerializedName("total_pdf_books") @SerialName("total_pdf_books") val totalPdfBooks: Int = 0,
    @SerializedName("total_categories") @SerialName("total_categories") val totalCategories: Int = 0,
    @SerializedName("total_topics") @SerialName("total_topics") val totalTopics: Int = 0,
    @SerializedName("total_questions") @SerialName("total_questions") val totalQuestions: Int = 0
)
