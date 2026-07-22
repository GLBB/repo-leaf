package io.github.glbb.repoleaf.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechPreferencesTest {
    @Test fun `normalizes unsupported speed and timer to product choices`() {
        val normalized = SpeechPresets.normalize(
            SpeechPreferences(speed = .9f, sleepTimerMinutes = 22),
        )

        assertEquals(.88f, normalized.speed)
        assertEquals(0, normalized.sleepTimerMinutes)
    }

    @Test fun `sleep timer deadline is deterministic and zero disables it`() {
        assertEquals(1_900_000L, SpeechPresets.timerDeadline(1_000_000L, 15))
        assertNull(SpeechPresets.timerDeadline(1_000_000L, 0))
    }
}
