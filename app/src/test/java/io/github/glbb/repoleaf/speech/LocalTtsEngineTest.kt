package io.github.glbb.repoleaf.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalTtsEngineTest {
    @Test fun `voice keys keep identical voice names distinct across engines`() {
        val xiaomi = LocalTtsEngine.voiceKey("com.xiaomi.mibrain.speech", "zh-CN")
        val another = LocalTtsEngine.voiceKey("org.example.tts", "zh-CN")

        assertNotEquals(xiaomi, another)
        assertEquals("zh-CN", LocalTtsEngine.voiceName(xiaomi))
        assertEquals("zh-CN", LocalTtsEngine.voiceName(another))
    }

    @Test fun `sherpa generic Chinese voice has a user-facing label`() {
        assertEquals(
            "Kokoro 实验性当前音色",
            LocalTtsEngine.displayVoiceName("com.k2fsa.sherpa.onnx.tts.engine", "zh"),
        )
        assertEquals(true, LocalTtsEngine.isExperimentalEngine("com.k2fsa.sherpa.onnx.tts.engine"))
    }
}
