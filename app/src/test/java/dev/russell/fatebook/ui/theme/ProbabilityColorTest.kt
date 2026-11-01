package dev.russell.fatebook.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProbabilityColorTest {

    @Test
    fun `ends and midpoint land on the scale's anchors`() {
        assertThat(probabilityColor(1.0, LightLedgerColors)).isEqualTo(LightLedgerColors.yes)
        assertThat(probabilityColor(0.0, LightLedgerColors)).isEqualTo(LightLedgerColors.no)
        assertThat(probabilityColor(0.5, LightLedgerColors)).isEqualTo(LightLedgerColors.neutral)
    }

    @Test
    fun `out of range input clamps`() {
        assertThat(probabilityColor(1.7, DarkLedgerColors)).isEqualTo(DarkLedgerColors.yes)
        assertThat(probabilityColor(-0.2, DarkLedgerColors)).isEqualTo(DarkLedgerColors.no)
    }
}
