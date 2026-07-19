package io.github.glbb.repoleaf.data

import io.github.glbb.repoleaf.domain.RepositoryConfig
import io.github.glbb.repoleaf.domain.SyncResult
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

class SnapshotSync(
    private val store: RepositoryStore,
    private val client: GitHubClient,
    private val credentials: CredentialStore,
) {
    fun sync(config: RepositoryConfig): Pair<RepositoryConfig, SyncResult> {
        val token = credentials.get(repositoryTokenAlias(config.id)) ?: credentials.get(ACCOUNT_TOKEN)
        val commit = client.resolveCommit(config, token)
        if (commit == config.activeCommit && store.snapshotDirectory(config) != null) {
            return config to SyncResult(commit, store.documents(config), unchanged = true)
        }

        val repositoryRoot = store.repositoryDirectory(config.id).apply { mkdirs() }
        val staging = File(repositoryRoot, "staging/${UUID.randomUUID()}").apply { mkdirs() }
        val archive = File(staging, "snapshot.zip")
        val extracted = File(staging, "extracted").apply { mkdirs() }
        try {
            client.downloadArchive(config, commit, token, archive)
            extractSafely(archive, extracted)
            val archiveRoot = extracted.listFiles()?.singleOrNull { it.isDirectory } ?: extracted
            val selectedRoot = config.rootPath.trim('/').takeIf { it.isNotEmpty() }
                ?.let { File(archiveRoot, it).canonicalFile } ?: archiveRoot.canonicalFile
            require(selectedRoot.toPath().startsWith(archiveRoot.canonicalFile.toPath()) && selectedRoot.isDirectory) {
                "仓库资料目录不存在"
            }
            val destination = File(repositoryRoot, "snapshots/$commit")
            destination.parentFile?.mkdirs()
            if (destination.exists()) destination.deleteRecursively()
            check(selectedRoot.copyRecursively(destination, overwrite = true)) { "无法激活仓库快照" }
            val updated = config.copy(activeCommit = commit, lastSyncedAt = System.currentTimeMillis())
            store.save(updated)
            val documents = store.documents(updated)
            return updated to SyncResult(commit, documents, unchanged = false)
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        const val ACCOUNT_TOKEN = "github.account"
        fun repositoryTokenAlias(id: String) = "github.repository.$id"

        fun extractSafely(archive: File, destination: File) {
            val root = destination.canonicalFile
            var entries = 0
            var totalBytes = 0L
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 50_000) { "压缩包文件数量超限" }
                    val output = File(root, entry.name).canonicalFile
                    require(output.toPath().startsWith(root.toPath())) { "压缩包包含不安全路径" }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { stream ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                totalBytes += read
                                require(totalBytes <= 500L * 1024 * 1024) { "解压后内容超过 500 MB" }
                                stream.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
    }
}
