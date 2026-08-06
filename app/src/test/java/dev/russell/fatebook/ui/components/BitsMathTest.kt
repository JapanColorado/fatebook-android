package dev.russell.fatebook.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BitsMathTest {

    // --- applyBits ---

    @Test
    fun `one bit of evidence doubles the odds`() {
        // 50% is 1:1 odds; +1 bit makes it 2:1 = 2/3.
        assertThat(BitsMath.applyBits(0.5f, 1)).isWithin(0.001f).of(2f / 3f)
        assertThat(BitsMath.applyBits(0.5f, -1)).isWithin(0.001f).of(1f / 3f)
    }

    @Test
    fun `two bits quadruple the odds`() {
        // 1:1 odds + 2 bits = 4:1 = 80%.
        assertThat(BitsMath.applyBits(0.5f, 2)).isWithin(0.001f).of(0.8f)
        assertThat(BitsMath.applyBits(0.5f, -2)).isWithin(0.001f).of(0.2f)
    }

    @Test
    fun `bits apply from any starting probability`() {
        // 75% is 3:1 odds; +1 bit makes it 6:1 = 6/7.
        assertThat(BitsMath.applyBits(0.75f, 1)).isWithin(0.001f).of(6f / 7f)
    }

    @Test
    fun `zero bits is the identity`() {
        assertThat(BitsMath.applyBits(0.37f, 0)).isWithin(0.001f).of(0.37f)
    }

    @Test
    fun `adding then subtracting a bit returns to the start`() {
        val there = BitsMath.applyBits(0.42f, 1)
        assertThat(BitsMath.applyBits(there, -1)).isWithin(0.001f).of(0.42f)
    }

    @Test
    fun `results are clamped to the slider bounds`() {
        assertThat(BitsMath.applyBits(0.9f, 4)).isWithin(0.0001f).of(BitsMath.MAX_PROBABILITY)
        assertThat(BitsMath.applyBits(0.1f, -4)).isWithin(0.0001f).of(BitsMath.MIN_PROBABILITY)
    }

    @Test
    fun `degenerate inputs are coerced instead of producing NaN`() {
        assertThat(BitsMath.applyBits(1f, 1)).isWithin(0.0001f).of(BitsMath.MAX_PROBABILITY)
        assertThat(BitsMath.applyBits(0f, -1)).isWithin(0.0001f).of(BitsMath.MIN_PROBABILITY)
        assertThat(BitsMath.applyBits(0f, 1).isNaN()).isFalse()
        assertThat(BitsMath.applyBits(1f, -1).isNaN()).isFalse()
    }
}
