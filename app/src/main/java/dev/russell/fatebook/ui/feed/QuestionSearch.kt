package dev.russell.fatebook.ui.feed

import dev.russell.fatebook.domain.model.Question

/** Pure feed-search predicate: title, notes, tags, option texts and comments. */
object QuestionSearch {
    /**
     * @param commentText lower-cased concatenated comments for this question, or null if none
     * @param queryLower the user's query, already lower-cased and non-blank
     */
    fun matches(question: Question, commentText: String?, queryLower: String): Boolean =
        question.searchText.contains(queryLower) ||
            (commentText != null && commentText.contains(queryLower))
}
