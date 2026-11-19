package com.tarixustoz.app.data.remote

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("categories/")
    suspend fun getCategories(
        @Query("grade") grade: Int? = null
    ): List<ApiCategoryDto>

    @GET("books/")
    suspend fun getPdfBooks(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null
    ): List<ApiPdfBookDto>

    @GET("books/")
    suspend fun getPdfBooksDelta(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null,
        @Header("If-None-Match") ifNoneMatch: String? = null,
        @Header("Cache-Control") cacheControl: String? = null
    ): Response<List<ApiPdfBookDto>>

    @GET("topics/")
    suspend fun getTopics(
        @Query("category_id") categoryId: Long? = null
    ): List<ApiTopicDto>

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

    @GET("entities/")
    suspend fun getEntities(): List<ApiEntityDto>

    @GET("questions/directions/")
    suspend fun getDirections(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null
    ): DirectionsResponseDto

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

    @GET("search/")
    suspend fun ragSearch(
        @Query("q") query: String,
        @Query("grade") grade: Int = 0,
        @Query("subject") subject: String = ""
    ): RAGSearchResponse

    @POST("quizzes/generate/")
    suspend fun generateQuiz(
        @Body request: QuizGenerateRequest
    ): QuizSessionDto

    @POST("quizzes/{sessionId}/answers/")
    suspend fun submitAnswer(
        @Path("sessionId") sessionId: String,
        @Body request: SubmitAnswerRequest
    ): SubmitAnswerResponse

    @POST("quizzes/{sessionId}/finish/")
    suspend fun finishQuiz(
        @Path("sessionId") sessionId: String
    ): Map<String, Any>

    @GET("audit/")
    suspend fun getAudit(): ApiAuditDto
}
