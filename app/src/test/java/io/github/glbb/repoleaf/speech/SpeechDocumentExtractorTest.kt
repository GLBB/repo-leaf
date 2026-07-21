package io.github.glbb.repoleaf.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDocumentExtractorTest {
    @Test fun `extractor skips front matter code and urls while retaining readable markdown`() {
        val document = SpeechDocumentExtractor.fromMarkdown(
            documentId = "repo:guide.md",
            fallbackTitle = "guide",
            markdown = """
                ---
                private: metadata
                ---
                # 中国移动研究

                收入增长 [查看来源](https://example.com/private?token=secret)，为 11.1%。

                - EBITDA 改善
                > 这是引用

                ```kotlin
                val token = "must not speak"
                ```

                | 指标 | 数值 |
                |---|---|
                | 收入 | 908亿元 |
            """.trimIndent(),
        )

        assertEquals("中国移动研究", document.title)
        assertTrue(document.segments.any { it.text.contains("百分之十一点一") })
        assertTrue(document.segments.any { it.text.contains("E B I T D A") })
        assertTrue(document.segments.any { it.text == "表格，共 2 行 2 列" })
        assertFalse(document.segments.any { it.text.contains("https://") || it.text.contains("must not speak") || it.text.contains("metadata") })
        assertEquals("speech-0000", document.segments.first().blockId)
    }

    @Test fun `normalizer handles dates currency and long speech blocks`() {
        val normalized = ChineseSpeechNormalizer.normalize("2026-07-22 09:30，908亿元，AIDC")

        assertTrue(normalized.contains("2026年7月22日"))
        assertTrue(normalized.contains("9点30分"))
        assertTrue(normalized.contains("九百零八亿元"))
        assertTrue(normalized.contains("A I D C"))
        assertTrue(ChineseSpeechNormalizer.splitForSpeech("甲。".repeat(200), limit = 80).all { it.length <= 81 })
    }
}
