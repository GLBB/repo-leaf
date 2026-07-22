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
    private val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build()

    fun render(file: File, dark: Boolean, fontScale: Float): RenderedMarkdown {
        val source = file.readText().removeFrontMatter()
        val rawHtml = renderer.render(parser.parse(source))
        val (anchoredBody, generatedToc) = addHeadingAnchors(rawHtml)
        // Many repository READMEs already maintain a hand-written "目录" section. Showing a
        // second generated outline above it is redundant and can look like a clipped empty box
        // in Android WebView.
        val toc = if (source.hasExplicitTableOfContents()) "" else generatedToc
        val body = makeTablesMobileFriendly(anchoredBody)
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
                color:$foreground; font: ${scale}em/1.72 system-ui,-apple-system,sans-serif; overflow-wrap:break-word; }
              h1,h2,h3,h4 { line-height:1.3; margin-top:1.6em; scroll-margin-top:18px; }
              h1,h2 { border-bottom:1px solid $border; padding-bottom:.3em; }
              a { color:#388bfd; } img { max-width:100%; height:auto; border-radius:8px; }
              pre { padding:16px; overflow:auto; background:$code; border-radius:10px; line-height:1.45; }
              code { background:$code; padding:.15em .35em; border-radius:5px; }
              pre code { padding:0; background:transparent; }
              blockquote { margin-left:0; padding-left:1em; color:$muted; border-left:4px solid $border; }
              .table-shell { position:relative; margin:1.15em 0; border:1px solid $border; border-radius:12px; overflow:hidden; background:$background; }
              .table-shell.is-resizing { user-select:none; }
              .table-shell::after { content:""; position:absolute; top:38px; right:0; bottom:0; width:22px; pointer-events:none;
                background:linear-gradient(90deg,transparent,$background); }
              .table-hint { display:flex; align-items:center; gap:6px; padding:7px 12px; color:$muted; background:$code;
                border-bottom:1px solid $border; font-size:.82em; line-height:1.35; }
              .table-scroll { overflow-x:auto; overflow-y:hidden; -webkit-overflow-scrolling:touch; touch-action:pan-x pan-y; }
              .table-scroll:focus { outline:2px solid #388bfd; outline-offset:-2px; }
              .table-shell table { display:table; width:100%; max-width:none; margin:0; border-collapse:separate; border-spacing:0; }
              .table-shell th,.table-shell td { min-width:0; padding:8px 10px; border-right:1px solid $border; border-bottom:1px solid $border;
                vertical-align:top; text-align:left; overflow-wrap:break-word; }
              .table-shell th:last-child,.table-shell td:last-child { border-right:0; }
              .table-shell tbody tr:last-child td { border-bottom:0; }
              .table-shell thead th { position:relative; color:$foreground; background:$code; font-weight:650; }
              .table-resize-handle { position:absolute; z-index:4; top:0; right:-12px; width:24px; height:100%; cursor:col-resize; touch-action:none; }
              .table-resize-handle::after { content:""; position:absolute; top:20%; right:11px; width:2px; height:60%; border-radius:2px; background:$border; }
              .table-resize-handle:active::after,.table-shell.is-resizing .table-resize-handle::after { background:#388bfd; }
              .table-wide table { width:max-content; min-width:0; table-layout:fixed; }
              .table-wide th,.table-wide td { width:auto; min-width:0; max-width:none; }
              .table-compact table { table-layout:fixed; }
              .table-compact th,.table-compact td { padding:8px; }
              .table-compact .table-hint,.table-compact::after { display:none; }
              @media (min-width:600px) { .table-hint { display:none; } .table-shell::after { display:none; } }
              .toc { margin:0 0 28px; padding:14px 16px; border:1px solid $border; border-radius:10px; background:$code; }
              .toc-title { display:block; margin-bottom:8px; font-weight:600; }
              .toc ul { margin:0; padding:0; list-style:none; }
              .toc li { margin:6px 0; line-height:1.45; }
              input[type=checkbox] { transform:scale(1.15); margin-right:.45em; }
              [data-speech-block].speech-current { background:rgba(56,139,253,.16); border-radius:8px; box-shadow:0 0 0 4px rgba(56,139,253,.08); }
            </style></head><body>$toc$body
            <script>
              (() => {
                const minColumnWidth = 72;
                const storagePrefix = "repoleaf-table-widths:";

                function savedWidths(key) {
                  try { return JSON.parse(localStorage.getItem(key) || "null"); } catch (_) { return null; }
                }

                function saveWidths(key, widths) {
                  try { localStorage.setItem(key, JSON.stringify(widths)); } catch (_) { /* Storage is optional. */ }
                }

                function setupTable(shell, tableIndex) {
                  const table = shell.querySelector("table");
                  const headers = Array.from(table.querySelectorAll("thead th"));
                  if (headers.length < 2) return;

                  const key = storagePrefix + location.pathname + ":" + tableIndex;
                  const initial = headers.map((header) => Math.round(header.getBoundingClientRect().width));
                  const stored = savedWidths(key);
                  let widths = Array.isArray(stored) && stored.length === headers.length ? stored : initial;
                  const colgroup = document.createElement("colgroup");
                  const columns = widths.map((width) => {
                    const col = document.createElement("col");
                    col.style.width = width + "px";
                    colgroup.appendChild(col);
                    return col;
                  });
                  table.prepend(colgroup);

                  const applyWidths = () => {
                    columns.forEach((col, index) => { col.style.width = widths[index] + "px"; });
                    table.style.width = widths.reduce((total, width) => total + width, 0) + "px";
                  };
                  applyWidths();

                  headers.slice(0, -1).forEach((header, index) => {
                    const handle = document.createElement("span");
                    handle.className = "table-resize-handle";
                    handle.setAttribute("role", "separator");
                    handle.setAttribute("aria-label", "调整第 " + (index + 1) + " 列宽度");
                    handle.addEventListener("pointerdown", (event) => {
                      event.preventDefault();
                      const startX = event.clientX;
                      const startWidths = widths.slice();
                      handle.setPointerCapture(event.pointerId);
                      shell.classList.add("is-resizing");

                      const move = (moveEvent) => {
                        const delta = Math.round(moveEvent.clientX - startX);
                        const minimumDelta = minColumnWidth - startWidths[index];
                        const boundedDelta = Math.max(minimumDelta, delta);
                        widths[index] = startWidths[index] + boundedDelta;
                        applyWidths();
                      };
                      const finish = () => {
                        shell.classList.remove("is-resizing");
                        saveWidths(key, widths);
                        document.removeEventListener("pointermove", move);
                        document.removeEventListener("pointerup", finish);
                        document.removeEventListener("pointercancel", finish);
                      };
                      document.addEventListener("pointermove", move);
                      document.addEventListener("pointerup", finish);
                      document.addEventListener("pointercancel", finish);
                    });
                    header.appendChild(handle);
                  });
                }

                function annotateSpeechBlocks() {
                  Array.from(document.querySelectorAll("h1,h2,h3,h4,h5,h6,p,li,.table-shell"))
                    .filter((element) => !element.closest(".toc"))
                    .forEach((element, index) => {
                    element.dataset.speechBlock = "speech-" + String(index).padStart(4, "0");
                  });
                }

                window.repoleafSpeech = {
                  highlight(blockId, follow) {
                    document.querySelectorAll(".speech-current").forEach((element) => element.classList.remove("speech-current"));
                    const target = document.querySelector('[data-speech-block="' + blockId + '"]');
                    if (!target) return false;
                    target.classList.add("speech-current");
                    if (follow) target.scrollIntoView({ behavior:"smooth", block:"center" });
                    return true;
                  }
                };

                document.addEventListener("DOMContentLoaded", () => {
                  annotateSpeechBlocks();
                  document.querySelectorAll(".table-shell").forEach(setupTable);
                });
              })();
            </script></body></html>
        """.trimIndent()
        return RenderedMarkdown(document, title)
    }

    fun resolveLocalLink(current: File, root: File, link: String): File? {
        val clean = link.substringBefore('#').substringBefore('?')
        if (clean.isBlank()) return current
        val decoded = runCatching { java.net.URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
        // WebView resolves a relative Markdown link against loadDataWithBaseURL and supplies an
        // absolute /data/... file path here. Joining that path to current.parentFile produces an
        // invalid nested path, so preserve absolute paths and resolve only genuine relative ones.
        val requested = File(decoded)
        val target = (if (requested.isAbsolute) requested else File(current.parentFile, decoded)).canonicalFile
        return target.takeIf { it.toPath().startsWith(root.canonicalFile.toPath()) && it.isFile }
    }

    private fun String.removeFrontMatter(): String {
        if (!startsWith("---\n") && !startsWith("---\r\n")) return this
        val match = Regex("\\A---\\R.*?\\R---\\R", RegexOption.DOT_MATCHES_ALL).find(this) ?: return this
        return removeRange(match.range)
    }

    private fun String.hasExplicitTableOfContents(): Boolean =
        Regex(
            "(?im)^#{1,6}\\s*(目录|目錄|table\\s+of\\s+contents|contents)\\s*$",
        ).containsMatchIn(this)

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

    private fun makeTablesMobileFriendly(html: String): String =
        Regex("<table>(.*?)</table>", RegexOption.DOT_MATCHES_ALL).replace(html) { match ->
            val table = match.value
            val columns = Regex("<th(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).findAll(table).count().coerceAtLeast(1)
            val kind = if (columns <= 3) "table-compact" else "table-wide"
            """
                <section class="table-shell $kind" data-columns="$columns">
                  <div class="table-hint" aria-hidden="true">↔ 左右滑动；拖动表头分割线调整列宽</div>
                  <div class="table-scroll" tabindex="0" role="region" aria-label="可横向滚动的表格">$table</div>
                </section>
            """.trimIndent()
        }

    private fun slug(value: String) = value.lowercase().trim()
        .replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-')

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun String.decodeEntities() = replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
}
