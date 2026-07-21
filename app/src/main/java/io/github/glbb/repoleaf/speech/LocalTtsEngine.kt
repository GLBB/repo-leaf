package io.github.glbb.repoleaf.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class SpeechVoice(
    val id: String,
    val name: String,
    val locale: Locale,
    val offline: Boolean,
)

interface SpeechProvider {
    suspend fun availableVoices(): List<SpeechVoice>
    suspend fun synthesize(text: String, voiceId: String?, output: File): Result<Unit>
    fun close()
}

/** Android's installed TTS engine, restricted to voices that explicitly do not require network. */
class LocalTtsEngine(context: Context) : SpeechProvider {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false
    private val initLock = Any()

    private suspend fun engine(): TextToSpeech = withTimeout(INIT_TIMEOUT_MS) { suspendCancellableCoroutine { continuation ->
        synchronized(initLock) {
            tts?.let { if (initialized) { continuation.resume(it); return@synchronized } }
            lateinit var created: TextToSpeech
            created = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    initialized = true
                    continuation.resume(created)
                } else continuation.resumeWith(Result.failure(IllegalStateException("系统文字转语音不可用")))
            }
            tts = created
        }
        continuation.invokeOnCancellation {
            synchronized(initLock) {
                tts?.shutdown()
                tts = null
                initialized = false
            }
        }
    } }

    override suspend fun availableVoices(): List<SpeechVoice> {
        val engine = engine()
        return engine.voices.orEmpty().asSequence()
            .filter { it.locale.language == Locale.CHINESE.language }
            .filterNot(Voice::isNetworkConnectionRequired)
            .map { SpeechVoice(it.name, it.name, it.locale, offline = true) }
            .sortedWith(compareByDescending<SpeechVoice> { it.locale.country == "CN" }.thenBy { it.name })
            .toList()
    }

    override suspend fun synthesize(text: String, voiceId: String?, output: File): Result<Unit> = runCatching {
        val engine = engine()
        val voice = engine.voices.orEmpty().firstOrNull { it.name == voiceId && !it.isNetworkConnectionRequired }
            ?: availableVoices().firstOrNull()?.let { selected -> engine.voices.orEmpty().firstOrNull { it.name == selected.id } }
            ?: throw IllegalStateException("未发现可用的中文离线音色，请在系统设置中安装语音数据")
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) throw IllegalStateException("无法启用所选离线音色")
        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { continuation ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) = Unit
                override fun onDone(id: String) { if (id == utteranceId && continuation.isActive) continuation.resume(Unit) }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String) { if (id == utteranceId && continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("语音合成失败"))) }
                override fun onError(id: String, errorCode: Int) { if (id == utteranceId && continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("语音合成失败：$errorCode"))) }
            })
            val result = engine.synthesizeToFile(text, null, output, utteranceId)
            if (result != TextToSpeech.SUCCESS && continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("语音合成任务未被接受")))
            continuation.invokeOnCancellation { engine.stop() }
        }
    }

    override fun close() {
        synchronized(initLock) {
            tts?.shutdown()
            tts = null
            initialized = false
        }
    }

    private companion object { const val INIT_TIMEOUT_MS = 8_000L }
}
