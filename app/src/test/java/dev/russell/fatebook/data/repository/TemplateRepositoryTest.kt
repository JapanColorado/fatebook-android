package dev.russell.fatebook.data.repository

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import dev.russell.fatebook.testutil.FakeTemplateDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TemplateRepositoryTest {

    private val dao = FakeTemplateDao()
    private val repository = TemplateRepository(dao, Moshi.Builder().build())

    @Test
    fun `save round-trips tags through JSON and clamps inputs`() = runTest {
        val id = repository.save(
            title = "  Gym 3x this week?  ",
            defaultProbability = 1.4,
            tags = listOf("health", "weekly"),
            resolveOffsetDays = -2,
        )

        val template = repository.observeTemplates().first().single()
        assertThat(template.id).isEqualTo(id)
        assertThat(template.title).isEqualTo("Gym 3x this week?")
        assertThat(template.defaultProbability).isEqualTo(1.0)
        assertThat(template.tags).containsExactly("health", "weekly").inOrder()
        assertThat(template.resolveOffsetDays).isEqualTo(0)
    }

    @Test
    fun `delete removes the template`() = runTest {
        val id = repository.save("A", 0.5, emptyList(), 7)

        repository.delete(id)

        assertThat(repository.observeTemplates().first()).isEmpty()
    }
}
