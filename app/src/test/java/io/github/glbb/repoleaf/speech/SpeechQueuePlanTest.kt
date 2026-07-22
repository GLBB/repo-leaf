package io.github.glbb.repoleaf.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechQueuePlanTest {
    @Test fun `cloud narration prebuffers up to three segments`() {
        assertEquals(3, SpeechQueuePlan.initialSegmentCount(isCloudProvider = true, remainingSegments = 8))
        assertEquals(2, SpeechQueuePlan.initialSegmentCount(isCloudProvider = true, remainingSegments = 2))
    }

    @Test fun `local narration retains immediate first segment start`() {
        assertEquals(1, SpeechQueuePlan.initialSegmentCount(isCloudProvider = false, remainingSegments = 8))
        assertEquals(0, SpeechQueuePlan.initialSegmentCount(isCloudProvider = false, remainingSegments = 0))
    }
}
