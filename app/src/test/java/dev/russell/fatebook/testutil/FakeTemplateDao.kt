package dev.russell.fatebook.testutil

import dev.russell.fatebook.data.local.TemplateDao
import dev.russell.fatebook.data.local.TemplateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTemplateDao : TemplateDao {

    private val _templates = MutableStateFlow<List<TemplateEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<TemplateEntity>
        get() = _templates.value

    override fun observeAll(): Flow<List<TemplateEntity>> =
        _templates.map { list -> list.sortedByDescending { it.createdAtEpochMs } }

    override suspend fun insert(template: TemplateEntity): Long {
        val id = if (template.id == 0L) nextId++ else template.id
        _templates.value = _templates.value + template.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        _templates.value = _templates.value.filter { it.id != id }
    }
}
