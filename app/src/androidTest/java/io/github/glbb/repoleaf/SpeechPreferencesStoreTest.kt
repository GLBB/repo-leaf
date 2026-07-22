package io.github.glbb.repoleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.glbb.repoleaf.speech.SpeechPreferences
import io.github.glbb.repoleaf.speech.SpeechPreferencesStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechPreferencesStoreTest {
    @Test fun settingsPersistAcrossStoreInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = SpeechPreferences(
            voiceId = "org.example.tts:calm-male",
            speed = .88f,
            sleepMode = true,
            sleepTimerMinutes = 45,
        )

        SpeechPreferencesStore(context).put(expected)

        assertEquals(expected, SpeechPreferencesStore(context).get())
    }
}
