package dev.russell.fatebook.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResolutionTest {

    @Test
    fun `fromApi parses YES`() {
        assertThat(Resolution.fromApi("YES")).isEqualTo(Resolution.YES)
    }

    @Test
    fun `fromApi parses NO`() {
        assertThat(Resolution.fromApi("NO")).isEqualTo(Resolution.NO)
    }

    @Test
    fun `fromApi parses AMBIGUOUS`() {
        assertThat(Resolution.fromApi("AMBIGUOUS")).isEqualTo(Resolution.AMBIGUOUS)
    }

    @Test
    fun `fromApi is case insensitive`() {
        assertThat(Resolution.fromApi("yes")).isEqualTo(Resolution.YES)
        assertThat(Resolution.fromApi("No")).isEqualTo(Resolution.NO)
        assertThat(Resolution.fromApi("ambiguous")).isEqualTo(Resolution.AMBIGUOUS)
    }

    @Test
    fun `fromApi returns null for unknown value`() {
        assertThat(Resolution.fromApi("MAYBE")).isNull()
        assertThat(Resolution.fromApi("")).isNull()
    }
}
