package io.github.glbb.repoleaf.speech

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class SpeechEngine(
    val packageName: String,
    val name: String,
)

data class SpeechVoice(
    /** Stable key covering both the engine package and its voice name. */
    val id: String,
    val name: String,
    val enginePackage: String,
    val engineName: String,
    val locale: Locale,
    val offline: Boolean,
)

interface SpeechProvider {
    suspend fun availableVoices(): List<SpeechVoice>
    suspend fun synthesize(text: String, voiceId: String?, output: File): Result<Unit>
    fun close()
}

/** Lists every installed Android TTS service, then only exposes their Chinese voices marked offline. */
object LocalTtsCatalog {
    fun installedEngines(context: Context): List<SpeechEngine> {
        val packageManager = context.packageManager
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        return packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.serviceInfo?.packageName ?: return@mapNotNull null
                SpeechEngine(packageName, info.loadLabel(packageManager).toString().ifBlank { packageName })
            }
            .distinctBy(SpeechEngine::packageName)
            .sortedWith(compareBy<SpeechEngine> { it.name.lowercase() }.thenBy(SpeechEngine::packageName))
    }

    suspend fun offlineChineseVoices(context: Context): List<SpeechVoice> = buildList {
        installedEngines(context).forEach { engineInfo ->
            val engine = LocalTtsEngine(context, engineInfo)
            try {
                addAll(runCatching { engine.availableVoices() }.getOrDefault(emptyList()))
            } finally {
                engine.close()
            }
        }
    }.sortedWith(
        compareBy<SpeechVoice> { LocalTtsEngine.isExperimentalEngine(it.enginePackage) }
            .thenBy { it.engineName.lowercase() }
            .thenByDescending { it.locale.country == "CN" }
            .thenBy { it.name },
    )
}

/** A selected Android TTS engine, restricted to voices that explicitly do not require network. */
class LocalTtsEngine(
    context: Context,
    private val selectedEngine: SpeechEngine? = null,
) : SpeechProvider {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var initialized = false
    private val initLock = Any()

    private suspend fun engine(): TextToSpeech = withTimeout(INIT_TIMEOUT_MS) { suspendCancellableCoroutine { continuation ->
        synchronized(initLock) {
            tts?.let { if (initialized) { continuation.resume(it); return@synchronized } }
            lateinit var created: TextToSpeech
            created = if (selectedEngine == null) {
                TextToSpeech(appContext) { status -> onInitialized(status, continuation, created) }
            } else {
                TextToSpeech(appContext, { status -> onInitialized(status, continuation, created) }, selectedEngine.packageName)
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

    private fun onInitialized(
        status: Int,
        continuation: kotlinx.coroutines.CancellableContinuation<TextToSpeech>,
        created: TextToSpeech,
    ) {
        if (status == TextToSpeech.SUCCESS) {
            initialized = true
            if (continuation.isActive) continuation.resume(created)
        } else if (continuation.isActive) {
            continuation.resumeWith(Result.failure(IllegalStateException("系统文字转语音不可用")))
        }
    }

    override suspend fun availableVoices(): List<SpeechVoice> {
        val engine = engine()
        val enginePackage = selectedEngine?.packageName ?: engine.defaultEngine.orEmpty()
        val engineName = selectedEngine?.name ?: enginePackage.ifBlank { "系统语音引擎" }
        return engine.voices.orEmpty().asSequence()
            .filter { it.locale.language == Locale.CHINESE.language }
            .filterNot(Voice::isNetworkConnectionRequired)
            .map { voice ->
                SpeechVoice(
                    id = voiceKey(enginePackage, voice.name),
                    name = displayVoiceName(enginePackage, voice.name),
                    enginePackage = enginePackage,
                    engineName = engineName,
                    locale = voice.locale,
                    offline = true,
                )
            }
            // Some engines report one logical voice once for each Chinese locale. The Android
            // TTS API cannot select those duplicates independently, so do not show false choices.
            .distinctBy(SpeechVoice::id)
            .sortedWith(compareByDescending<SpeechVoice> { it.locale.country == "CN" }.thenBy { it.name })
            .toList()
    }

    override suspend fun synthesize(text: String, voiceId: String?, output: File): Result<Unit> = runCatching {
        val engine = engine()
        val voiceName = voiceName(voiceId)
        val voice = engine.voices.orEmpty().firstOrNull { it.name == voiceName && !it.isNetworkConnectionRequired }
            ?: availableVoices().firstOrNull()?.let { selected -> engine.voices.orEmpty().firstOrNull { it.name == selected.name } }
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

    companion object {
        private const val INIT_TIMEOUT_MS = 8_000L
        private const val VOICE_SEPARATOR = ":"

        fun voiceKey(enginePackage: String, voiceName: String): String = "$enginePackage$VOICE_SEPARATOR$voiceName"
        fun voiceName(voiceId: String?): String? = voiceId?.substringAfter(VOICE_SEPARATOR, voiceId)

        fun displayVoiceName(enginePackage: String, voiceName: String): String = when {
            enginePackage.contains("sherpa", ignoreCase = true) && voiceName.equals("zh", ignoreCase = true) ->
                "Kokoro 实验性当前音色"
            else -> voiceName
        }

        /** The external engine is useful for evaluation but is not the stable Chinese default. */
        fun isExperimentalEngine(enginePackage: String): Boolean =
            enginePackage.contains("sherpa", ignoreCase = true)
    }
}
