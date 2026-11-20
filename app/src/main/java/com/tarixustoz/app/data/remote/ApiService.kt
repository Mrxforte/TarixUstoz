package com.tarixustoz.app.data.remote

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("books/")
    suspend fun getPdfBooksDelta(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): Response<List<ApiPdfBookDto>>

    @GET("topics/")
    suspend fun getTopicsDelta(
        @Query("category_id") categoryId: Long? = null,
        @Query("since_id") sinceId: Long? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): Response<List<ApiTopicDto>>

    @GET("questions/")
    suspend fun getQuestions(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Query("topic_id") topicId: Long? = null,
        @Query("category") category: String? = null,
        @Query("difficulty") difficulty: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null
    ): List<ApiQuestionDto>

    @GET("questions/")
    suspend fun getQuestionsDelta(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Query("since_id") sinceId: Long? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): Response<List<ApiQuestionDto>>

    @GET("questions/directions/")
    suspend fun getDirectionsDelta(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): Response<DirectionsResponseDto>

    @GET("questions/ticket/")
    suspend fun getTicket(
        @Query("category") category: String,
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Query("ticket") ticket: Int = 1,
        @Query("size") size: Int = 30
    ): TicketQuestionsDto

    @GET("questions/ticket/")
    suspend fun getTicketDelta(
        @Query("category") category: String,
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Query("ticket") ticket: Int = 1,
        @Query("size") size: Int = 30,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): Response<TicketQuestionsDto>

    @GET("questions/daily/")
    suspend fun getDailyQuestion(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Query("user_identifier") userIdentifier: String = "guest_user",
        @Query("nonce") nonce: String? = null,
        @Query("exclude") exclude: String? = null
    ): ApiQuestionDto

    @GET("exams/")
    suspend fun getExamCategories(): List<ExamCategoryDto>

    @POST("quizzes/generate/")
    suspend fun generateQuiz(
        @Body request: QuizGenerateRequest
    ): QuizSessionDto
}
