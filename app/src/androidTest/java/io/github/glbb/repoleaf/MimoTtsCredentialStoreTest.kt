package io.github.glbb.repoleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.glbb.repoleaf.speech.MimoTtsCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MimoTtsCredentialStoreTest {
    @Test fun apiKeyIsEncryptedAndCanBeRemoved() {
        val store = MimoTtsCredentialStore(InstrumentationRegistry.getInstrumentation().targetContext)
        store.removeApiKey()
        store.putApiKey("mimo-test-secret")

        assertEquals("mimo-test-secret", store.getApiKey())

        store.removeApiKey()
        assertNull(store.getApiKey())
    }
}
