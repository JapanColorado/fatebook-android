package dev.russell.fatebook.domain.model

import androidx.compose.runtime.Immutable

/** A saved pattern for a recurring prediction; applied onto the Create form. */
@Immutable
data class QuestionTemplate(
    val id: Long,
    val title: String,
    val defaultProbability: Double,
    val tags: List<String>,
    val resolveOffsetDays: Int,
)
