package io.github.glbb.repoleaf.speech

import android.content.Context
import android.util.Base64
import io.github.glbb.repoleaf.data.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Xiaomi MiMo V2.5 TTS client. The API key is kept in Android Keystore-backed storage;
 * this provider only receives one already-extracted reading segment at a time.
 */
class MimoTtsProvider(
    private val apiKey: String,
    private val sleepMode: Boolean,
    private val styleId: String,
) : SpeechProvider {
    override suspend fun availableVoices(): List<SpeechVoice> = MimoTts.voices.map { voice ->
        SpeechVoice(
            id = MimoTts.voiceId(voice.id),
            name = voice.label,
            enginePackage = MimoTts.ENGINE_ID,
            engineName = "MiMo 云端自然朗读",
            locale = java.util.Locale.CHINA,
            offline = false,
        )
    }

    override suspend fun synthesize(text: String, voiceId: String?, output: File): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val voice = MimoTts.voiceName(voiceId)
            val connection = (URL(MimoTts.API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(MimoTts.requestBody(text, voice, sleepMode, styleId))
                }
                val responseCode = connection.responseCode
                val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (responseCode !in 200..299) {
                    val providerMessage = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                    throw IllegalStateException("MiMo 朗读请求失败（$responseCode）${providerMessage?.let { "：$it" }.orEmpty()}")
                }
                val encodedAudio = JSONObject(response)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getJSONObject("audio")
                    .getString("data")
                val bytes = Base64.decode(encodedAudio, Base64.DEFAULT)
                check(bytes.isNotEmpty()) { "MiMo 未返回可播放的语音" }
                output.outputStream().use { it.write(bytes) }
            } finally {
                connection.disconnect()
            }
        }
    }

    override fun close() = Unit

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 40_000
    }
}

data class MimoVoice(val id: String, val label: String)
data class MimoStyle(val id: String, val label: String)

object MimoTts {
    const val ENGINE_ID = "mimo-v2.5-tts"
    const val API_URL = "https://api.xiaomimimo.com/v1/chat/completions"
    const val CREDENTIAL_ALIAS = "mimo.tts.api-key.v1"
    const val DEFAULT_VOICE = "苏打"
    const val DEFAULT_STYLE = "natural"

    val voices = listOf(
        MimoVoice("mimo_default", "官方默认 · 自动"),
        MimoVoice("冰糖", "冰糖 · 女声"),
        MimoVoice("茉莉", "茉莉 · 女声"),
        MimoVoice("苏打", "苏打 · 男声"),
        MimoVoice("白桦", "白桦 · 男声"),
        MimoVoice("Mia", "Mia · 英文女声"),
        MimoVoice("Chloe", "Chloe · 英文甜美女声"),
        MimoVoice("Milo", "Milo · 英文男声"),
        MimoVoice("Dean", "Dean · 英文男声"),
    )

    val styles = listOf(
        MimoStyle("natural", "自然"),
        MimoStyle("sweet", "甜美轻柔"),
        MimoStyle("warm", "温暖舒缓"),
    )

    fun voiceId(voice: String): String = "$ENGINE_ID:$voice"

    fun voiceName(voiceId: String?): String = voiceId
        ?.takeIf { it.startsWith("$ENGINE_ID:") }
        ?.removePrefix("$ENGINE_ID:")
        ?.takeIf { candidate -> voices.any { it.id == candidate } }
        ?: DEFAULT_VOICE

    fun styleName(styleId: String?): String = styleId?.takeIf { candidate ->
        styles.any { it.id == candidate }
    } ?: DEFAULT_STYLE

    fun requestBody(text: String, voice: String, sleepMode: Boolean, styleId: String = DEFAULT_STYLE): String {
        val style = narrationInstruction(styleId, sleepMode)
        return """{"model":"$ENGINE_ID","messages":[{"role":"user","content":"${jsonEscape(style)}"},{"role":"assistant","content":"${jsonEscape(text)}"}],"audio":{"format":"wav","voice":"${jsonEscape(voice)}"}}"""
    }

    private fun narrationInstruction(styleId: String, sleepMode: Boolean): String {
        if (sleepMode) {
            return "请用温和、低沉、舒缓的普通话朗读，保持自然停顿和柔和收尾。严格朗读原文，不要添加原文没有的词、英文或解释。"
        }
        return when (styleName(styleId)) {
            "sweet" -> "请用甜美、轻柔、温暖、亲切的普通话女声朗读，语气自然不夸张，避免生硬播报感，保持清晰咬字和舒缓停顿。严格朗读原文，不要添加原文没有的词、英文或解释。"
            "warm" -> "请用温暖、舒缓、有陪伴感的普通话朗读，语速平稳，保持自然停顿和清晰咬字。严格朗读原文，不要添加原文没有的词、英文或解释。"
            else -> "请用自然、沉稳、清晰的普通话朗读，段落之间自然停顿。严格朗读原文，不要添加原文没有的词、英文或解释。"
        }
    }

    private fun jsonEscape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else if (char.code < 0x20) -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
    }
}

class MimoTtsCredentialStore(context: Context) {
    private val credentials = CredentialStore(context)

    fun getApiKey(): String? = credentials.get(MimoTts.CREDENTIAL_ALIAS)?.trim()?.takeIf(String::isNotEmpty)

    fun putApiKey(apiKey: String) = credentials.put(MimoTts.CREDENTIAL_ALIAS, apiKey.trim())

    fun removeApiKey() = credentials.remove(MimoTts.CREDENTIAL_ALIAS)
}
