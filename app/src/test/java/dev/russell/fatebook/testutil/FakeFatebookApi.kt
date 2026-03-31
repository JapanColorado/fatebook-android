package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.remote.FatebookApi
import dev.russell.fatebook.data.remote.dto.QuestionDto
import dev.russell.fatebook.data.remote.dto.QuestionsResponseDto

class FakeFatebookApi : FatebookApi {

    var getQuestionsResponse: (cursor: Int?) -> QuestionsResponseDto = {
        TestData.questionsResponse()
    }
    var createQuestionResult: String = "https://fatebook.io/q/new"
    var resolveQuestionError: Exception? = null
    var addForecastError: Exception? = null
    var validateApiKeyError: Exception? = null
    var getQuestionResponse: QuestionDto = TestData.questionDto()
    var getQuestionError: Exception? = null
    var editQuestionError: Exception? = null
    var deleteQuestionError: Exception? = null
    var addCommentError: Exception? = null
    var setSharedPubliclyError: Exception? = null

    val getQuestionsCalls = mutableListOf<Int?>()
    val createQuestionCalls = mutableListOf<Triple<String, String, Double>>()
    val resolveQuestionCalls = mutableListOf<Triple<String, String, String>>()
    val addForecastCalls = mutableListOf<Triple<String, Double, String>>()
    val getQuestionCalls = mutableListOf<String>()
    val editQuestionCalls = mutableListOf<EditQuestionCall>()
    val deleteQuestionCalls = mutableListOf<String>()
    val addCommentCalls = mutableListOf<Pair<String, String>>()
    val setSharedPubliclyCalls = mutableListOf<SetSharedPubliclyCall>()

    data class EditQuestionCall(
        val questionId: String,
        val title: String?,
        val resolveBy: String?,
        val notes: String?,
    )

    data class SetSharedPubliclyCall(
        val questionId: String,
        val sharedPublicly: Boolean,
        val unlisted: Boolean,
    )

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

    override suspend fun getQuestion(questionId: String): QuestionDto {
        getQuestionError?.let { throw it }
        getQuestionCalls.add(questionId)
        return getQuestionResponse
    }

    override suspend fun editQuestion(
        questionId: String,
        title: String?,
        resolveBy: String?,
        notes: String?,
        apiKey: String,
    ) {
        editQuestionError?.let { throw it }
        editQuestionCalls.add(EditQuestionCall(questionId, title, resolveBy, notes))
    }

    override suspend fun deleteQuestion(questionId: String) {
        deleteQuestionError?.let { throw it }
        deleteQuestionCalls.add(questionId)
    }

    override suspend fun addComment(
        questionId: String,
        comment: String,
        apiKey: String,
    ) {
        addCommentError?.let { throw it }
        addCommentCalls.add(questionId to comment)
    }

    override suspend fun setSharedPublicly(
        questionId: String,
        sharedPublicly: Boolean,
        unlisted: Boolean,
        apiKey: String,
    ) {
        setSharedPubliclyError?.let { throw it }
        setSharedPubliclyCalls.add(SetSharedPubliclyCall(questionId, sharedPublicly, unlisted))
    }
}
