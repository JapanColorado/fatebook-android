package dev.russell.fatebook.data.remote

import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface FatebookApi {

    companion object {
        const val BASE_URL = "https://fatebook.io/api/v0/"
    }

    /** Returns paginated list of questions. */
    @GET("getQuestions")
    suspend fun getQuestions(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: Int? = null,
        @Query("resolved") resolved: Boolean? = null,
        @Query("unresolved") unresolved: Boolean? = null,
        @Query("readyToResolve") readyToResolve: Boolean? = null,
        @Query("resolvingSoon") resolvingSoon: Boolean? = null,
        @Query("searchString") searchString: String? = null,
        @Query("sortEarliestFirst") sortEarliestFirst: Boolean? = null,
    ): QuestionsResponseDto

    /**
     * Creates a question. NOTE: This is a GET request (Fatebook quirk).
     * Returns a plain-text URL string, not JSON.
     */
    @GET("createQuestion")
    suspend fun createQuestion(
        @Query("title") title: String,
        @Query("resolveBy") resolveBy: String,
        @Query("forecast") forecast: Double,
    ): String

    /** Resolves a question with YES / NO / AMBIGUOUS. */
    @FormUrlEncoded
    @POST("resolveQuestion")
    suspend fun resolveQuestion(
        @Field("questionId") questionId: String,
        @Field("resolution") resolution: String,
        @Field("apiKey") apiKey: String,
        @Field("questionType") questionType: String = "BINARY",
    )

    /** Adds or updates a forecast on a question. */
    @FormUrlEncoded
    @POST("addForecast")
    suspend fun addForecast(
        @Field("questionId") questionId: String,
        @Field("forecast") forecast: Double,
        @Field("apiKey") apiKey: String,
    )

    /** Validation call — fetches 1 question to confirm the API key works. */
    @GET("getQuestions")
    suspend fun validateApiKey(
        @Query("limit") limit: Int = 1,
    ): QuestionsResponseDto
}
