package io.github.glbb.repoleaf.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MimoTtsTest {
    @Test fun `request keeps narration text in assistant role and style outside spoken text`() {
        val request = MimoTts.requestBody("收入增长 11.1%。", "苏打", sleepMode = false)

        assertTrue(request.contains("\"model\":\"${MimoTts.ENGINE_ID}\""))
        assertTrue(request.contains("\"role\":\"user\""))
        assertTrue(request.contains("不要添加"))
        assertTrue(request.contains("\"role\":\"assistant\",\"content\":\"收入增长 11.1%。\""))
        assertTrue(request.contains("\"voice\":\"苏打\""))
        assertTrue(request.contains("\"format\":\"wav\""))
    }

    @Test fun `unknown saved voice falls back to calm male voice`() {
        assertEquals(MimoTts.DEFAULT_VOICE, MimoTts.voiceName("mimo-v2.5-tts:unknown"))
        assertEquals("茉莉", MimoTts.voiceName(MimoTts.voiceId("茉莉")))
    }

    @Test fun `preset list contains every current named MiMo voice plus official default`() {
        assertEquals(
            setOf("mimo_default", "冰糖", "茉莉", "苏打", "白桦", "Mia", "Chloe", "Milo", "Dean"),
            MimoTts.voices.map { it.id }.toSet(),
        )
    }

    @Test fun `sweet style asks for a gentle and non-announcer narration`() {
        val request = MimoTts.requestBody("测试", "冰糖", sleepMode = false, styleId = "sweet")

        assertTrue(request.contains("甜美、轻柔、温暖"))
        assertTrue(request.contains("避免生硬播报感"))
    }
}
