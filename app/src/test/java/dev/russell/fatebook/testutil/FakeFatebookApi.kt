package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.remote.FatebookApi
import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto

class FakeFatebookApi : FatebookApi {

    var getQuestionsResponse: (cursor: Int?) -> QuestionsResponseDto = {
        TestData.questionsResponse()
    }
    var createQuestionResult: String = "https://fatebook.io/q/new"
    var resolveQuestionError: Exception? = null
    var addForecastError: Exception? = null
    var validateApiKeyError: Exception? = null

    val getQuestionsCalls = mutableListOf<Int?>()
    val createQuestionCalls = mutableListOf<Triple<String, String, Double>>()
    val resolveQuestionCalls = mutableListOf<Triple<String, String, String>>()
    val addForecastCalls = mutableListOf<Triple<String, Double, String>>()

    override suspend fun getQuestions(
        limit: Int,
        cursor: Int?,
        resolved: Boolean?,
        unresolved: Boolean?,
        readyToResolve: Boolean?,
        resolvingSoon: Boolean?,
        searchString: String?,
        sortEarliestFirst: Boolean?,
    ): QuestionsResponseDto {
        getQuestionsCalls.add(cursor)
        return getQuestionsResponse(cursor)
    }

    override suspend fun createQuestion(
        title: String,
        resolveBy: String,
        forecast: Double,
    ): String {
        createQuestionCalls.add(Triple(title, resolveBy, forecast))
        return createQuestionResult
    }

    override suspend fun resolveQuestion(
        questionId: String,
        resolution: String,
        apiKey: String,
        questionType: String,
    ) {
        resolveQuestionError?.let { throw it }
        resolveQuestionCalls.add(Triple(questionId, resolution, apiKey))
    }

    override suspend fun addForecast(
        questionId: String,
        forecast: Double,
        apiKey: String,
    ) {
        addForecastError?.let { throw it }
        addForecastCalls.add(Triple(questionId, forecast, apiKey))
    }

    override suspend fun validateApiKey(limit: Int): QuestionsResponseDto {
        validateApiKeyError?.let { throw it }
        return TestData.questionsResponse()
    }
}
