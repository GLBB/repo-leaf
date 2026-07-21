package io.github.glbb.repoleaf

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Diagnostic coverage for vendor engines that support a language but do not publish Voice entries. */
@RunWith(AndroidJUnit4::class)
class DefaultChineseTtsProbeTest {
    @Test fun defaultChineseLanguageCanSynthesizePrivateAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val initialized = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        val engine = TextToSpeech(context) { status -> initStatus = status; initialized.countDown() }
        try {
            assertTrue("TTS initialization timed out", initialized.await(8, TimeUnit.SECONDS))
            assumeTrue("system TTS initialization failed", initStatus == TextToSpeech.SUCCESS)
            val language = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
            assumeTrue("system default engine has no zh_CN support", language >= TextToSpeech.LANG_AVAILABLE)
            val output = File(context.cacheDir, "default-tts-probe-${System.nanoTime()}.wav")
            val done = CountDownLatch(1)
            var error: Int? = null
            val utteranceId = UUID.randomUUID().toString()
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(id: String) { if (id == utteranceId) done.countDown() }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String) { if (id == utteranceId) { error = -1; done.countDown() } }
                override fun onError(id: String, errorCode: Int) { if (id == utteranceId) { error = errorCode; done.countDown() } }
            })

            assertEquals(TextToSpeech.SUCCESS, engine.synthesizeToFile("本地中文合成验证。", null, output, utteranceId))
            assertTrue("default Chinese synthesis timed out", done.await(12, TimeUnit.SECONDS))
            assumeTrue("system default engine cannot synthesize Chinese offline audio: $error", error == null)
            assertTrue("default Chinese synthesis produced an empty file", output.length() > 44L)
            output.delete()
        } finally {
            engine.shutdown()
        }
    }

    @Test fun xiaomiEngineCanInitializeWhenSelectedExplicitly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageName = "com.xiaomi.mibrain.speech"
        assumeTrue("Xiaomi system speech engine is not installed", runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess)
        val initialized = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        val engine = TextToSpeech(context, { status -> initStatus = status; initialized.countDown() }, packageName)
        try {
            assertTrue("explicit Xiaomi engine initialization timed out", initialized.await(8, TimeUnit.SECONDS))
            assertEquals("explicit Xiaomi engine initialization failed", TextToSpeech.SUCCESS, initStatus)
        } finally {
            engine.shutdown()
        }
    }
}
