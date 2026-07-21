package io.github.glbb.repoleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.glbb.repoleaf.speech.SpeechProgressStore
import io.github.glbb.repoleaf.speech.StoredSpeechProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechProgressStoreTest {
    @Test fun speechProgressPersistsWithoutStoringDocumentText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SpeechProgressStore(context)
        val documentId = "test:voice-progress.md"
        val expected = StoredSpeechProgress(
            documentId = documentId,
            sourcePath = "/private/offline/voice-progress.md",
            title = "离线朗读测试",
            contentHash = "content-hash",
            voiceId = "offline-zh",
            segmentIndex = 3,
            positionMs = 12_345,
            speed = 1.25f,
        )

        store.put(expected)

        assertEquals(expected, store.get(documentId))
        store.clear(documentId)
    }
}
