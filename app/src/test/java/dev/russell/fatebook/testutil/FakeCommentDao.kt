package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.CommentDao
import dev.russell.fatebook.data.local.CommentEntity
import dev.russell.fatebook.data.local.CommentSearchRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCommentDao : CommentDao {

    private val _comments = MutableStateFlow<List<CommentEntity>>(emptyList())

    val storedComments: List<CommentEntity>
        get() = _comments.value

    var deleteAllCallCount = 0
        private set

    override suspend fun getByQuestionId(questionId: String): List<CommentEntity> =
        _comments.value.filter { it.questionId == questionId }.sortedBy { it.createdAtEpochMs }

    override fun observeSearchIndex(): Flow<List<CommentSearchRow>> =
        _comments.map { list -> list.map { CommentSearchRow(it.questionId, it.comment) } }

    override suspend fun upsertAll(comments: List<CommentEntity>) {
        val current = _comments.value.toMutableList()
        for (c in comments) {
            current.removeAll { it.id == c.id }
            current.add(c)
        }
        _comments.value = current
    }

    override suspend fun insert(comment: CommentEntity) {
        upsertAll(listOf(comment))
    }

    override suspend fun deleteByQuestionId(questionId: String) {
        _comments.value = _comments.value.filter { it.questionId != questionId }
    }

    override suspend fun deleteAll() {
        deleteAllCallCount++
        _comments.value = emptyList()
    }
}
