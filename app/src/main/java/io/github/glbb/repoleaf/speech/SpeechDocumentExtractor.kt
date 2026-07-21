package io.github.glbb.repoleaf.speech

import java.io.File
import java.security.MessageDigest

enum class SpeechSegmentType { Heading, Paragraph, ListItem, Quote, TableSummary }

data class SpeechSegment(
    val id: String,
    val blockId: String,
    val type: SpeechSegmentType,
    val text: String,
)

data class SpeechDocument(
    val documentId: String,
    val contentHash: String,
    val title: String,
    val segments: List<SpeechSegment>,
)

/**
 * Creates stable, readable speech blocks from Markdown without exposing the source to any network
 * service. The block numbering intentionally mirrors the semantic tags annotated by MarkdownRenderer.
 */
object SpeechDocumentExtractor {
    private const val RULE_VERSION = "speech-v1"

    fun fromFile(documentId: String, file: File): SpeechDocument =
        fromMarkdown(documentId, file.nameWithoutExtension, file.readText())

    fun fromMarkdown(documentId: String, fallbackTitle: String, markdown: String): SpeechDocument {
        val source = markdown.removeFrontMatter().replace("\r\n", "\n")
        val segments = mutableListOf<SpeechSegment>()
        val paragraph = mutableListOf<String>()
        var inCodeFence = false
        var blockNumber = 0
        var tableRows = 0
        var tableColumns = 0

        fun append(type: SpeechSegmentType, value: String) {
            val normalized = ChineseSpeechNormalizer.normalize(value)
            if (normalized.isBlank()) return
            ChineseSpeechNormalizer.splitForSpeech(normalized).forEachIndexed { partIndex, part ->
                val blockId = "speech-${blockNumber.toString().padStart(4, '0')}"
                segments += SpeechSegment(
                    id = hash("$documentId:$blockId:$partIndex:$part"),
                    blockId = blockId,
                    type = type,
                    text = part,
                )
            }
            blockNumber++
        }

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                append(SpeechSegmentType.Paragraph, paragraph.joinToString(" "))
                paragraph.clear()
            }
        }

        fun flushTable() {
            if (tableRows > 0) append(SpeechSegmentType.TableSummary, "表格，共 ${tableRows} 行 ${tableColumns} 列")
            tableRows = 0
            tableColumns = 0
        }

        source.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) {
                flushParagraph()
                flushTable()
                inCodeFence = !inCodeFence
                return@forEach
            }
            if (inCodeFence) return@forEach
            if (line.isBlank()) {
                flushParagraph()
                flushTable()
                return@forEach
            }
            if (line.isTableLine()) {
                flushParagraph()
                if (!line.isTableSeparator()) {
                    tableRows++
                    tableColumns = maxOf(tableColumns, line.tableColumnCount())
                }
                return@forEach
            }
            flushTable()
            when {
                line.startsWith("#") -> {
                    flushParagraph()
                    append(SpeechSegmentType.Heading, line.replaceFirst(Regex("^#{1,6}\\s+"), ""))
                }
                line.startsWith(">") -> {
                    flushParagraph()
                    append(SpeechSegmentType.Quote, line.replaceFirst(Regex("^>+\\s*"), ""))
                }
                line.matches(Regex("^([-*+]|\\d+[.)])\\s+.+")) -> {
                    flushParagraph()
                    append(SpeechSegmentType.ListItem, line.replaceFirst(Regex("^([-*+]|\\d+[.)])\\s+"), ""))
                }
                line.startsWith("![") -> Unit // Images are intentionally skipped by the local-first default.
                line.startsWith("---") || line.startsWith("***") -> flushParagraph()
                else -> paragraph += line
            }
        }
        flushParagraph()
        flushTable()

        val title = segments.firstOrNull { it.type == SpeechSegmentType.Heading }?.text ?: fallbackTitle
        return SpeechDocument(
            documentId = documentId,
            contentHash = hash("$RULE_VERSION:$source"),
            title = title,
            segments = segments,
        )
    }

    private fun String.isTableLine(): Boolean = startsWith("|") && count { it == '|' } >= 2
    private fun String.isTableSeparator(): Boolean = replace("|", "").replace(":", "").replace("-", "").trim().isEmpty()
    private fun String.tableColumnCount(): Int = trim().trim('|').split('|').size

    private fun String.removeFrontMatter(): String {
        if (!startsWith("---\n") && !startsWith("---\r\n")) return this
        val match = Regex("\\A---\\R.*?\\R---\\R", RegexOption.DOT_MATCHES_ALL).find(this) ?: return this
        return removeRange(match.range)
    }

    internal fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

object ChineseSpeechNormalizer {
    private val pronunciation = mapOf(
        "EBITDA" to "E B I T D A",
        "AIDC" to "A I D C",
        "GitHub" to "GitHub",
        "API" to "A P I",
        "AI" to "A I",
        "5G" to "五 G",
    )

    fun normalize(raw: String): String {
        var value = raw
            .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        value = value.replace(Regex("(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})")) {
            "${it.groupValues[1]}年${it.groupValues[2].toInt()}月${it.groupValues[3].toInt()}日"
        }
        value = value.replace(Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)")) {
            "${it.groupValues[1].toInt()}点${it.groupValues[2].toInt()}分"
        }
        value = value.replace(Regex("(?<![\\d.])(\\d+(?:\\.\\d+)?)%")) {
            "百分之${numberToChinese(it.groupValues[1])}"
        }
        value = value.replace(Regex("(?<![A-Za-z])(\\d+(?:\\.\\d+)?)(亿元|万元|元|倍|个|家)(?![A-Za-z])")) {
            "${numberToChinese(it.groupValues[1])}${it.groupValues[2]}"
        }
        pronunciation.forEach { (term, speech) -> value = value.replace(term, speech) }
        return value.replace(Regex("[|*_~]+"), " ").replace(Regex("[。！？]{2,}"), "。")
            .replace(Regex("\\s+"), " ").trim()
    }

    fun splitForSpeech(text: String, limit: Int = 220): List<String> {
        if (text.length <= limit) return listOf(text)
        val result = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.length > limit) {
            val window = remaining.take(limit + 1)
            val split = window.indexOfLast { it in "。！？；;，," }.takeIf { it >= limit / 2 } ?: limit
            result += remaining.take(split + if (split < remaining.length && remaining[split] in "。！？；;，,") 1 else 0).trim()
            remaining = remaining.drop(split + if (split < remaining.length && remaining[split] in "。！？；;，,") 1 else 0).trim()
        }
        if (remaining.isNotBlank()) result += remaining
        return result
    }

    private fun numberToChinese(number: String): String {
        val parts = number.split('.', limit = 2)
        val integer = parts[0].toLongOrNull()?.let(::integerToChinese) ?: parts[0]
        return if (parts.size == 1) integer else integer + "点" + parts[1].map { chineseDigits[it - '0'] }.joinToString("")
    }

    private fun integerToChinese(value: Long): String {
        if (value == 0L) return "零"
        if (value < 10) return chineseDigits[value.toInt()].toString()
        if (value >= 10_000) return value.toString().map { chineseDigits[it - '0'] }.joinToString("")
        val units = arrayOf("", "十", "百", "千")
        val digits = value.toString().map { it - '0' }
        val output = StringBuilder()
        digits.forEachIndexed { index, digit ->
            val unit = units[digits.lastIndex - index]
            if (digit == 0) {
                if (output.isNotEmpty() && index < digits.lastIndex && digits[index + 1] != 0 && !output.endsWith("零")) output.append("零")
            } else {
                if (!(digit == 1 && unit == "十" && output.isEmpty())) output.append(chineseDigits[digit])
                output.append(unit)
            }
        }
        return output.toString()
    }

    private val chineseDigits = charArrayOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')
}
