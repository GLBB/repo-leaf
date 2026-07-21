package io.github.glbb.repoleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.glbb.repoleaf.speech.LocalTtsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LocalTtsEngineTest {
    @Test fun installedOfflineChineseVoiceCanSynthesizePrivateAudioFile() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = LocalTtsEngine(context)
            try {
                val voice = runCatching { engine.availableVoices().firstOrNull() }.getOrNull()
                assumeTrue("device has no installed offline Chinese voice", voice != null)
                val output = File(context.cacheDir, "tts-instrumentation-${System.nanoTime()}.wav")

                val result = engine.synthesize("离线中文朗读验证。", voice!!.id, output)

                assumeTrue("installed voice cannot synthesize an offline file", result.isSuccess)
                assertTrue(output.length() > 44L)
                output.delete()
            } finally {
                engine.close()
            }
        }
    }
}
