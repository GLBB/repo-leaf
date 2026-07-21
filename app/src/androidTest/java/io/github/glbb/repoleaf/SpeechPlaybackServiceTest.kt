package io.github.glbb.repoleaf

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.glbb.repoleaf.speech.LocalTtsEngine
import io.github.glbb.repoleaf.speech.SpeechController
import io.github.glbb.repoleaf.speech.SpeechPlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpeechPlaybackServiceTest {
    @Test fun playbackServiceSynthesizesMarkdownIntoPrivateCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val speechEngine = LocalTtsEngine(context)
        val offlineVoice = runCatching { runBlocking { speechEngine.availableVoices().firstOrNull() } }.getOrNull()
        val preflight = File(context.cacheDir, "speech-service-preflight-${System.nanoTime()}.wav")
        val canSynthesize = offlineVoice != null && runBlocking {
            speechEngine.synthesize("验收。", offlineVoice.id, preflight).isSuccess
        }
        speechEngine.close()
        preflight.delete()
        assumeTrue("device has no usable offline Chinese voice", canSynthesize)
        val documentId = "instrumentation:spoken-${System.nanoTime()}.md"
        val source = File(context.filesDir, "spoken-instrumentation.md").apply {
            writeText("# 验收\n\n这是本地中文语音合成验收。")
        }
        val intent = Intent(context, SpeechPlaybackService::class.java)
            .setAction(SpeechController.ACTION_PLAY_DOCUMENT)
            .putExtra(SpeechController.EXTRA_DOCUMENT_ID, documentId)
            .putExtra(SpeechController.EXTRA_SOURCE_PATH, source.canonicalPath)
            .putExtra(SpeechController.EXTRA_TITLE, "验收")
        ContextCompat.startForegroundService(context, intent)

        val cacheRoot = File(context.cacheDir, "speech")
        val deadline = System.currentTimeMillis() + 15_000L
        var producedAudio = false
        while (System.currentTimeMillis() < deadline && !producedAudio) {
            producedAudio = cacheRoot.exists() && cacheRoot.walkTopDown().any { it.isFile && it.extension == "wav" && it.length() > 44L }
            if (!producedAudio) Thread.sleep(100)
        }
        context.startService(Intent(context, SpeechPlaybackService::class.java).setAction(SpeechController.ACTION_STOP))
        source.delete()

        assertTrue("speech service did not synthesize a private audio cache file", producedAudio)
    }
}
