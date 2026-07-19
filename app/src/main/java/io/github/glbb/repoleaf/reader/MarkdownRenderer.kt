package io.github.glbb.repoleaf.reader

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File

data class RenderedMarkdown(val html: String, val title: String)

object MarkdownRenderer {
    private val extensions = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create(),
        TaskListItemsExtension.create(),
    )
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).build()

    fun render(file: File, dark: Boolean, fontScale: Float): RenderedMarkdown {
        val source = file.readText().removeFrontMatter()
        val rawHtml = renderer.render(parser.parse(source))
        val (body, toc) = addHeadingAnchors(rawHtml)
        val title = Regex("(?m)^#\\s+(.+)$").find(source)?.groupValues?.get(1)?.trim()
            ?: file.nameWithoutExtension
        val background = if (dark) "#101418" else "#ffffff"
        val foreground = if (dark) "#e6edf3" else "#24292f"
        val muted = if (dark) "#8b949e" else "#57606a"
        val border = if (dark) "#30363d" else "#d0d7de"
        val code = if (dark) "#161b22" else "#f6f8fa"
        val scale = fontScale.coerceIn(0.8f, 1.5f)
        val document = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3">
            <style>
              :root { color-scheme: ${if (dark) "dark" else "light"}; }
              body { margin:0 auto; padding:24px 20px 72px; max-width:760px; background:$background;
                color:$foreground; font: ${scale}em/1.72 system-ui,-apple-system,sans-serif; overflow-wrap:anywhere; }
              h1,h2,h3,h4 { line-height:1.3; margin-top:1.6em; scroll-margin-top:18px; }
              h1,h2 { border-bottom:1px solid $border; padding-bottom:.3em; }
              a { color:#388bfd; } img { max-width:100%; height:auto; border-radius:8px; }
              pre { padding:16px; overflow:auto; background:$code; border-radius:10px; line-height:1.45; }
              code { background:$code; padding:.15em .35em; border-radius:5px; }
              pre code { padding:0; background:transparent; }
              blockquote { margin-left:0; padding-left:1em; color:$muted; border-left:4px solid $border; }
              table { display:block; width:max-content; max-width:100%; overflow:auto; border-collapse:collapse; }
              th,td { border:1px solid $border; padding:7px 12px; }
              .toc { max-height:34vh; overflow-y:auto; padding:14px 16px; border:1px solid $border; border-radius:10px; background:$code; }
              .toc-title { display:block; margin-bottom:8px; font-weight:600; }
              .toc ul { margin:0; padding-left:22px; }
              .toc li { margin:4px 0; }
              input[type=checkbox] { transform:scale(1.15); margin-right:.45em; }
            </style></head><body>$toc$body</body></html>
        """.trimIndent()
        return RenderedMarkdown(document, title)
    }

    fun resolveLocalLink(current: File, root: File, link: String): File? {
        val clean = link.substringBefore('#').substringBefore('?')
        if (clean.isBlank()) return current
        val decoded = runCatching { java.net.URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
        val target = File(current.parentFile, decoded).canonicalFile
        return target.takeIf { it.toPath().startsWith(root.canonicalFile.toPath()) && it.isFile }
    }

    private fun String.removeFrontMatter(): String {
        if (!startsWith("---\n") && !startsWith("---\r\n")) return this
        val match = Regex("\\A---\\R.*?\\R---\\R", RegexOption.DOT_MATCHES_ALL).find(this) ?: return this
        return removeRange(match.range)
    }

    private fun addHeadingAnchors(html: String): Pair<String, String> {
        val headings = mutableListOf<Triple<Int, String, String>>()
        val used = mutableMapOf<String, Int>()
        val body = Regex("<h([1-6])>(.*?)</h\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .replace(html) { match ->
                val level = match.groupValues[1].toInt()
                val label = match.groupValues[2].replace(Regex("<[^>]+>"), "").decodeEntities()
                val base = slug(label).ifBlank { "section" }
                val count = used.getOrDefault(base, 0)
                used[base] = count + 1
                val id = if (count == 0) base else "$base-$count"
                headings += Triple(level, label, id)
                "<h$level id=\"$id\">${match.groupValues[2]}</h$level>"
            }
        if (headings.size < 2) return body to ""
        val items = headings.joinToString("") { (level, label, id) ->
            "<li style=\"margin-left:${(level - 1) * 12}px\"><a href=\"#$id\">${escape(label)}</a></li>"
        }
        // Android WebView does not reliably lay out an expanded <details> element.
        // Keep the outline visible and independently scrollable instead.
        return body to "<nav class=\"toc\" aria-label=\"目录\"><span class=\"toc-title\">目录</span><ul>$items</ul></nav>"
    }

    private fun slug(value: String) = value.lowercase().trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun String.decodeEntities() = replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
}
