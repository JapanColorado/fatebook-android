package dev.russell.fatebook.data.remote

import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto
import dev.russell.fatebook.data.remote.dto.QuestionDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
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

    /** Fetch a single question by ID (for deep links and enriching detail view). */
    @GET("getQuestion")
    suspend fun getQuestion(
        @Query("questionId") questionId: String,
    ): QuestionDto

    /** Edit question fields (title, resolveBy, notes). */
    @FormUrlEncoded
    @HTTP(method = "PATCH", path = "editQuestion", hasBody = true)
    suspend fun editQuestion(
        @Field("questionId") questionId: String,
        @Field("title") title: String? = null,
        @Field("resolveBy") resolveBy: String? = null,
        @Field("notes") notes: String? = null,
        @Field("apiKey") apiKey: String,
    )

    /** Delete a question. DELETE uses query params (like GET); apiKey added by interceptor. */
    @HTTP(method = "DELETE", path = "deleteQuestion", hasBody = false)
    suspend fun deleteQuestion(
        @Query("questionId") questionId: String,
    )

    /** Add a comment to a question. */
    @FormUrlEncoded
    @POST("addComment")
    suspend fun addComment(
        @Field("questionId") questionId: String,
        @Field("comment") comment: String,
        @Field("apiKey") apiKey: String,
    )

    /** Toggle public visibility of a question. */
    @FormUrlEncoded
    @HTTP(method = "PATCH", path = "setSharedPublicly", hasBody = true)
    suspend fun setSharedPublicly(
        @Field("questionId") questionId: String,
        @Field("sharedPublicly") sharedPublicly: Boolean,
        @Field("unlisted") unlisted: Boolean,
        @Field("apiKey") apiKey: String,
    )
}
