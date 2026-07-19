package io.github.glbb.repoleaf.data

import android.content.Context
import io.github.glbb.repoleaf.domain.KnowledgeDocument
import io.github.glbb.repoleaf.domain.RepositoryConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RepositoryStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("repositories", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "repositories").apply { mkdirs() }

    fun repositories(): List<RepositoryConfig> {
        val json = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val values = JSONArray(json)
            List(values.length()) { index -> values.getJSONObject(index).toConfig() }
        }.getOrDefault(emptyList())
    }

    fun save(config: RepositoryConfig) {
        val values = repositories().toMutableList()
        val index = values.indexOfFirst { it.id == config.id }
        if (index >= 0) values[index] = config else values += config
        persist(values)
    }

    fun remove(id: String) {
        persist(repositories().filterNot { it.id == id })
        repositoryDirectory(id).deleteRecursively()
    }

    fun repositoryDirectory(id: String) = File(root, id)

    fun snapshotDirectory(config: RepositoryConfig): File? =
        config.activeCommit?.let { File(repositoryDirectory(config.id), "snapshots/$it") }?.takeIf(File::isDirectory)

    fun documents(config: RepositoryConfig): List<KnowledgeDocument> {
        val snapshot = snapshotDirectory(config) ?: return emptyList()
        return snapshot.walkTopDown()
            .filter { it.isFile && it.extension.equals("md", true) }
            .map { file ->
                val path = file.relativeTo(snapshot).invariantSeparatorsPath
                KnowledgeDocument(config.id, path, markdownTitle(file, path), file)
            }
            .sortedBy { it.relativePath.lowercase() }
            .toList()
    }

    private fun markdownTitle(file: File, fallbackPath: String): String {
        val title = runCatching {
            file.bufferedReader().useLines { lines ->
                lines.take(80).firstOrNull { it.trimStart().startsWith("# ") }
            }?.trim()?.removePrefix("# ")?.trim()
        }.getOrNull()
        return title?.takeIf { it.isNotBlank() }
            ?: fallbackPath.substringAfterLast('/').substringBeforeLast('.')
    }

    private fun persist(values: List<RepositoryConfig>) {
        val array = JSONArray()
        values.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun RepositoryConfig.toJson() = JSONObject()
        .put("id", id).put("owner", owner).put("name", name).put("branch", branch)
        .put("rootPath", rootPath).put("displayName", displayName)
        .put("activeCommit", activeCommit).put("lastSyncedAt", lastSyncedAt)

    private fun JSONObject.toConfig() = RepositoryConfig(
        id = getString("id"), owner = getString("owner"), name = getString("name"),
        branch = optString("branch", "main"), rootPath = optString("rootPath"),
        displayName = optString("displayName", "${getString("owner")}/${getString("name")}"),
        activeCommit = optString("activeCommit").takeIf { it.isNotBlank() && it != "null" },
        lastSyncedAt = if (has("lastSyncedAt") && !isNull("lastSyncedAt")) getLong("lastSyncedAt") else null,
    )
}
