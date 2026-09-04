package dev.russell.fatebook.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dev.russell.fatebook.data.local.TemplateDao
import dev.russell.fatebook.data.local.TemplateEntity
import dev.russell.fatebook.domain.model.QuestionTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Local-only quick-forecast templates. Nothing here touches the network. */
@Singleton
class TemplateRepository @Inject constructor(
    private val dao: TemplateDao,
    moshi: Moshi,
) {
    private val tagsAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java),
    )

    fun observeTemplates(): Flow<List<QuestionTemplate>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun save(
        title: String,
        defaultProbability: Double,
        tags: List<String>,
        resolveOffsetDays: Int,
    ): Long = dao.insert(
        TemplateEntity(
            title = title.trim(),
            defaultProbability = defaultProbability.coerceIn(0.0, 1.0),
            tagsJson = tagsAdapter.toJson(tags),
            resolveOffsetDays = resolveOffsetDays.coerceAtLeast(0),
            createdAtEpochMs = System.currentTimeMillis(),
        ),
    )

    suspend fun delete(id: Long) = dao.deleteById(id)

    private fun TemplateEntity.toDomain() = QuestionTemplate(
        id = id,
        title = title,
        defaultProbability = defaultProbability,
        tags = runCatching { tagsAdapter.fromJson(tagsJson) }.getOrNull() ?: emptyList(),
        resolveOffsetDays = resolveOffsetDays,
    )
}
