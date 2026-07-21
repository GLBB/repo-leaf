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
        assertTrue(html.contains("table-shell table-compact"))
        assertTrue(html.contains("table-hint"))
        assertTrue(html.contains("table-scroll"))
        assertTrue(html.contains("table-resize-handle"))
        assertTrue(html.contains("repoleaf-table-widths:"))
        assertTrue(html.contains("speechBlock"))
        assertTrue(html.contains("repoleafSpeech"))
        assertTrue(html.contains("speech-current"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("type=\"checkbox\""))
        assertTrue(html.contains("<del>old</del>"))
        assertFalse(html.contains("Hidden metadata"))
    }

    @Test fun `sanitizes unsafe markdown URLs before enabling table gestures`() {
        val file = temporaryFolder.newFile("unsafe-link.md").apply {
            writeText("[unsafe](javascript:alert('x'))")
        }

        val html = MarkdownRenderer.render(file, dark = false, fontScale = 1f).html

        assertFalse(html.contains("javascript:alert"))
    }

    @Test fun `does not duplicate a repository maintained table of contents`() {
        val file = temporaryFolder.newFile("readme.md").apply {
            writeText(
                """
                # Knowledge

                ## 目录
                - [Agent](#agent)

                ## Agent
                正文
                """.trimIndent(),
            )
        }

        val html = MarkdownRenderer.render(file, dark = false, fontScale = 1f).html

        assertFalse(html.contains("<nav class=\"toc\""))
        assertTrue(html.contains("<h2 id=\"目录\">目录</h2>"))
    }

    @Test fun `wide tables use horizontal reading treatment`() {
        val file = temporaryFolder.newFile("wide.md").apply {
            writeText(
                """
                | 指标 | 2024 | 2025 | 2026 | 说明 |
                |---|---|---|---|---|
                | 收入 | 1 | 2 | 3 | 长文本说明 |
                """.trimIndent(),
            )
        }

        val html = MarkdownRenderer.render(file, dark = false, fontScale = 1f).html

        assertTrue(html.contains("table-shell table-wide"))
        assertTrue(html.contains("data-columns=\"5\""))
        assertFalse(html.contains("--table-width"))
        assertTrue(html.contains("table.style.width = widths.reduce"))
        assertFalse(html.contains("position:sticky"))
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
