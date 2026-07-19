package io.github.glbb.repoleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.glbb.repoleaf.data.ReadingStateStore
import io.github.glbb.repoleaf.domain.ReadingState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingStateStoreTest {
    @Test fun favoriteRecentAndScrollProgressPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ReadingStateStore(context)
        val expected = ReadingState(favorite = true, lastOpenedAt = 123456L, scrollY = 987)

        store.put("repository:docs/readme.md", expected)

        assertEquals(expected, store.get("repository:docs/readme.md"))
    }
}
