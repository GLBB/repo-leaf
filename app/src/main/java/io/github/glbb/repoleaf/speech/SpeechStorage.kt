package io.github.glbb.repoleaf.speech

import android.content.Context
import org.json.JSONObject
import java.io.File

data class StoredSpeechProgress(
    val documentId: String,
    val sourcePath: String,
    val title: String,
    val contentHash: String,
    val voiceId: String,
    val segmentIndex: Int,
    val positionMs: Long,
    val speed: Float,
)

class SpeechProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("speech_progress", Context.MODE_PRIVATE)

    fun get(documentId: String): StoredSpeechProgress? = prefs.getString(documentId, null)?.let { raw ->
        runCatching {
            JSONObject(raw).let {
                StoredSpeechProgress(
                    documentId = documentId,
                    sourcePath = it.getString("sourcePath"),
                    title = it.getString("title"),
                    contentHash = it.getString("contentHash"),
                    voiceId = it.optString("voiceId"),
                    segmentIndex = it.optInt("segmentIndex"),
                    positionMs = it.optLong("positionMs"),
                    speed = it.optDouble("speed", 1.0).toFloat(),
                )
            }
        }.getOrNull()
    }

    fun put(state: StoredSpeechProgress) {
        prefs.edit().putString(state.documentId, JSONObject().apply {
            put("sourcePath", state.sourcePath)
            put("title", state.title)
            put("contentHash", state.contentHash)
            put("voiceId", state.voiceId)
            put("segmentIndex", state.segmentIndex)
            put("positionMs", state.positionMs)
            put("speed", state.speed)
        }.toString()).apply()
    }

    fun clear(documentId: String) = prefs.edit().remove(documentId).apply()
}

class SpeechCache private constructor(private val root: File) {
    constructor(context: Context) : this(File(context.cacheDir, "speech"))

    internal constructor(rootDirectory: File, testOnly: Boolean) : this(rootDirectory)

    fun fileFor(document: SpeechDocument, voiceId: String, segmentIndex: Int): File {
        val folder = File(File(File(root, shortHash(document.documentId)), document.contentHash), shortHash(voiceId))
        return File(folder, "segment-${segmentIndex.toString().padStart(4, '0')}.wav")
    }

    fun usable(file: File): Boolean = file.isFile && file.length() > 44L

    fun prepareTarget(file: File): File {
        file.parentFile?.mkdirs()
        return File(file.parentFile, "${file.name}.tmp")
    }

    fun commit(temp: File, target: File): Boolean {
        if (!usable(temp)) {
            temp.delete()
            return false
        }
        if (target.exists()) target.delete()
        return temp.renameTo(target)
    }

    fun trimTo(maxBytes: Long = DEFAULT_MAX_BYTES, protected: Set<File> = emptySet()) {
        if (!root.exists()) return
        val files = root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.toList()
        var size = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (size <= maxBytes || protected.any { it.canonicalFile == file.canonicalFile }) return@forEach
            val length = file.length()
            if (file.delete()) size -= length
        }
        root.walkBottomUp().filter { it.isDirectory && it.list().isNullOrEmpty() }.forEach(File::delete)
    }

    private fun shortHash(value: String): String = SpeechDocumentExtractor.hash(value).take(24)

    companion object { const val DEFAULT_MAX_BYTES = 500L * 1024L * 1024L }
}
