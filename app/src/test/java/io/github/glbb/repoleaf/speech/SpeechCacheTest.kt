package io.github.glbb.repoleaf.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpeechCacheTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `commits only a complete private audio file`() {
        val cache = SpeechCache(temporaryFolder.newFolder("speech-cache"), testOnly = true)
        val document = SpeechDocumentExtractor.fromMarkdown("repo:doc.md", "doc", "# 标题\n\n正文")
        val target = cache.fileFor(document, "offline-zh", 0)
        val temporary = cache.prepareTarget(target)

        temporary.writeBytes(ByteArray(45) { 7 })

        assertTrue(cache.commit(temporary, target))
        assertTrue(cache.usable(target))
        assertFalse(temporary.exists())
    }

    @Test fun `rejects truncated output and evicts old non protected files`() {
        val root = temporaryFolder.newFolder("speech-cache")
        val cache = SpeechCache(root, testOnly = true)
        val document = SpeechDocumentExtractor.fromMarkdown("repo:doc.md", "doc", "# 标题\n\n正文")
        val old = cache.fileFor(document, "offline-zh", 0).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(45))
            setLastModified(1L)
        }
        val active = File(old.parentFile, "segment-0001.wav").apply {
            writeBytes(ByteArray(45))
            setLastModified(2L)
        }
        val incomplete = cache.prepareTarget(File(old.parentFile, "broken.wav"))
        incomplete.writeBytes(ByteArray(44))

        assertFalse(cache.commit(incomplete, File(old.parentFile, "broken.wav")))
        cache.trimTo(maxBytes = 45L, protected = setOf(active))

        assertFalse(old.exists())
        assertTrue(active.exists())
    }
}
