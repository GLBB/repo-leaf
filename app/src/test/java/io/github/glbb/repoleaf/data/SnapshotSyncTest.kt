package io.github.glbb.repoleaf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SnapshotSyncTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `extracts valid github archive`() {
        val archive = zip("repo-sha/README.md" to "# Hello", "repo-sha/docs/a.md" to "A")
        val output = temporaryFolder.newFolder("output")

        SnapshotSync.extractSafely(archive, output)

        assertEquals("# Hello", output.resolve("repo-sha/README.md").readText())
        assertEquals("A", output.resolve("repo-sha/docs/a.md").readText())
    }

    @Test fun `rejects zip slip`() {
        val archive = zip("../escaped.md" to "bad")
        val output = temporaryFolder.newFolder("safe")

        val result = runCatching { SnapshotSync.extractSafely(archive, output) }

        assertFalse(result.isSuccess)
        assertFalse(temporaryFolder.root.resolve("escaped.md").exists())
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val archive = temporaryFolder.newFile("archive-${System.nanoTime()}.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return archive
    }
}
