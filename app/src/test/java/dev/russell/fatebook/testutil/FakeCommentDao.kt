package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.CommentDao
import dev.russell.fatebook.data.local.CommentEntity

class FakeCommentDao : CommentDao {

    private val _comments = mutableListOf<CommentEntity>()

    val storedComments: List<CommentEntity>
        get() = _comments.toList()

    var deleteAllCallCount = 0
        private set

    override suspend fun getByQuestionId(questionId: String): List<CommentEntity> =
        _comments.filter { it.questionId == questionId }.sortedBy { it.createdAtEpochMs }

    override suspend fun upsertAll(comments: List<CommentEntity>) {
        for (c in comments) {
            _comments.removeAll { it.id == c.id }
            _comments.add(c)
        }
    }

    override suspend fun insert(comment: CommentEntity) {
        _comments.removeAll { it.id == comment.id }
        _comments.add(comment)
    }

    override suspend fun deleteByQuestionId(questionId: String) {
        _comments.removeAll { it.questionId == questionId }
    }

    override suspend fun deleteAll() {
        deleteAllCallCount++
        _comments.clear()
    }
}
