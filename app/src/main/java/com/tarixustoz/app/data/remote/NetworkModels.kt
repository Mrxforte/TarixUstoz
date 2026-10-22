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
    @SerializedName("subject_id") @SerialName("subject_id") val subjectId: String = "uzbekiston",
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
)

@Serializable
data class ApiQuestionDto(
    @SerializedName("id") @SerialName("id") val id: Long,
    @SerializedName("topic") @SerialName("topic") val topicId: Long? = null,
    @SerializedName("grade_id") @SerialName("grade_id") val gradeId: Int = 7,
    @SerializedName("subject_id") @SerialName("subject_id") val subjectId: String = "uzbekiston",
    @SerializedName("text") @SerialName("text") val text: String,
    @SerializedName("option_a") @SerialName("option_a") val optionA: String,
    @SerializedName("option_b") @SerialName("option_b") val optionB: String,
    @SerializedName("option_c") @SerialName("option_c") val optionC: String,
    @SerializedName("option_d") @SerialName("option_d") val optionD: String,
    @SerializedName("correct_option") @SerialName("correct_option") val correctOption: String = "A",
    @SerializedName("explanation") @SerialName("explanation") val explanation: String? = null,
    @SerializedName("difficulty") @SerialName("difficulty") val difficulty: String = "EASY",
    @SerializedName("category_type") @SerialName("category_type") val categoryType: String = "ALL"
)

@Serializable
data class ApiAuditDto(
    @SerializedName("status") @SerialName("status") val status: String,
    @SerializedName("total_pdf_books") @SerialName("total_pdf_books") val totalPdfBooks: Int = 0,
    @SerializedName("total_categories") @SerialName("total_categories") val totalCategories: Int = 0,
    @SerializedName("total_topics") @SerialName("total_topics") val totalTopics: Int = 0,
    @SerializedName("total_questions") @SerialName("total_questions") val totalQuestions: Int = 0
)
