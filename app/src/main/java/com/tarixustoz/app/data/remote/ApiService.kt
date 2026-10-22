package com.tarixustoz.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {

    @GET("categories/")
    suspend fun getCategories(
        @Query("grade") grade: Int? = null
    ): List<ApiCategoryDto>

    @GET("pdf-books/")
    suspend fun getPdfBooks(
        @Query("grade") grade: Int? = null,
        @Query("subject") subject: String? = null
    ): List<ApiPdfBookDto>

    @GET("topics/")
    suspend fun getTopics(
        @Query("category_id") categoryId: Long? = null
    ): List<ApiTopicDto>

    @GET("questions/")
    suspend fun getQuestions(
        @Query("grade") grade: Int? = null,
        @Query("topic_id") topicId: Long? = null,
        @Query("category") category: String? = null,
        @Query("difficulty") difficulty: String? = null,
        @Query("limit") limit: Int? = null
    ): List<ApiQuestionDto>

    @GET("audit/")
    suspend fun getAudit(): ApiAuditDto
}
