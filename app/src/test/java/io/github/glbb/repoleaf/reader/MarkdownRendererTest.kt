package io.github.glbb.repoleaf.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarkdownRendererTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `renders GFM, toc and removes front matter`() {
        val file = temporaryFolder.newFile("guide.md").apply {
            writeText("""
                ---
                title: Hidden metadata
                ---
                # Guide
                ## Start here
                | A | B |
                |---|---|
                | 1 | 2 |
                - [x] Done
                ~~old~~
            """.trimIndent())
        }

        val html = MarkdownRenderer.render(file, dark = false, fontScale = 1f).html

        assertTrue(html.contains("<nav class=\"toc\""))
        assertTrue(html.contains("aria-label=\"目录\""))
        assertFalse(html.contains("<details"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("type=\"checkbox\""))
        assertTrue(html.contains("<del>old</del>"))
        assertFalse(html.contains("Hidden metadata"))
    }

    @Test fun `local links cannot escape repository root`() {
        val root = temporaryFolder.newFolder("repo")
        val docs = root.resolve("docs").apply { mkdirs() }
        val current = docs.resolve("a.md").apply { writeText("a") }
        val target = root.resolve("b.md").apply { writeText("b") }
        val outside = temporaryFolder.newFile("secret.md")

        assertTrue(MarkdownRenderer.resolveLocalLink(current, root, "../b.md") == target.canonicalFile)
        assertTrue(MarkdownRenderer.resolveLocalLink(current, root, "../../secret.md") == null)
        assertTrue(outside.exists())
    }
}
