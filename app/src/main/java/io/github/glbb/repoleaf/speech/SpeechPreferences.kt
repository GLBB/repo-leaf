package io.github.glbb.repoleaf.speech

import android.content.Context

data class SpeechPreferences(
    val voiceId: String = "",
    val mimoEnabled: Boolean = false,
    val mimoVoice: String = MimoTts.DEFAULT_VOICE,
    val mimoStyle: String = MimoTts.DEFAULT_STYLE,
    val speed: Float = 1f,
    val sleepMode: Boolean = false,
    val sleepTimerMinutes: Int = 0,
)

object SpeechPresets {
    val supportedSpeeds = listOf(.75f, .88f, 1f, 1.25f, 1.5f, 2f)
    val supportedSleepTimers = listOf(0, 15, 30, 45, 60)
    const val SLEEP_SPEED = .88f
    const val SLEEP_PITCH = .96f

    fun normalize(preferences: SpeechPreferences): SpeechPreferences = preferences.copy(
        mimoVoice = MimoTts.voiceName(MimoTts.voiceId(preferences.mimoVoice)),
        mimoStyle = MimoTts.styleName(preferences.mimoStyle),
        speed = supportedSpeeds.minBy { kotlin.math.abs(it - preferences.speed) },
        sleepTimerMinutes = preferences.sleepTimerMinutes.takeIf(supportedSleepTimers::contains) ?: 0,
    )

    fun timerDeadline(nowMillis: Long, minutes: Int): Long? =
        minutes.takeIf { it > 0 }?.let { nowMillis + it * 60_000L }
}

class SpeechPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("speech_preferences", Context.MODE_PRIVATE)

    fun get(): SpeechPreferences = SpeechPresets.normalize(
        SpeechPreferences(
            voiceId = prefs.getString("voiceId", "").orEmpty(),
            mimoEnabled = prefs.getBoolean("mimoEnabled", false),
            mimoVoice = prefs.getString("mimoVoice", MimoTts.DEFAULT_VOICE).orEmpty(),
            mimoStyle = prefs.getString("mimoStyle", MimoTts.DEFAULT_STYLE).orEmpty(),
            speed = prefs.getFloat("speed", 1f),
            sleepMode = prefs.getBoolean("sleepMode", false),
            sleepTimerMinutes = prefs.getInt("sleepTimerMinutes", 0),
        ),
    )

    fun put(value: SpeechPreferences) {
        val normalized = SpeechPresets.normalize(value)
        prefs.edit()
            .putString("voiceId", normalized.voiceId)
            .putBoolean("mimoEnabled", normalized.mimoEnabled)
            .putString("mimoVoice", normalized.mimoVoice)
            .putString("mimoStyle", normalized.mimoStyle)
            .putFloat("speed", normalized.speed)
            .putBoolean("sleepMode", normalized.sleepMode)
            .putInt("sleepTimerMinutes", normalized.sleepTimerMinutes)
            .apply()
    }
}

enum class SpeechVoicePackStatus { Installed, NotInstalled }

data class SpeechVoicePack(
    val id: String,
    val name: String,
    val description: String,
    val approximateSize: String,
    val sourceUrl: String,
    val status: SpeechVoicePackStatus,
)

object SpeechVoicePackCatalog {
    const val KOKORO_SAMPLES_URL =
        "https://k2-fsa.github.io/sherpa/onnx/tts/all/Chinese-English/kokoro-multi-lang-v1_1.html"

    fun available(context: Context): List<SpeechVoicePack> {
        val hasSherpaEngine = LocalTtsCatalog.installedEngines(context).any { engine ->
            engine.packageName.contains("sherpa", ignoreCase = true) ||
                engine.name.contains("sherpa", ignoreCase = true) ||
                engine.name.contains("kokoro", ignoreCase = true)
        }
        return listOf(
            SpeechVoicePack(
                id = "kokoro-v1.1-zh",
                name = "Kokoro 多音色（实验性）",
                description = "55 个中文女声、45 个中文男声 · 24kHz；适合试听，不作为中文技术正文默认朗读",
                approximateSize = "约 400 MB",
                sourceUrl = KOKORO_SAMPLES_URL,
                status = if (hasSherpaEngine) SpeechVoicePackStatus.Installed else SpeechVoicePackStatus.NotInstalled,
            ),
        )
    }
}
