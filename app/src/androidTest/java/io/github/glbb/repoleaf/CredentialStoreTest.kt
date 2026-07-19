package io.github.glbb.repoleaf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.glbb.repoleaf.data.CredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialStoreTest {
    @Test fun secretRoundTripsWithoutPlaintextPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val secret = "test-secret-that-must-not-be-plaintext"
        val alias = "test.alias"
        val store = CredentialStore(context)

        store.put(alias, secret)

        assertEquals(secret, store.get(alias))
        val persistedValue = context.getSharedPreferences("credentials", android.content.Context.MODE_PRIVATE)
            .getString(alias, "").orEmpty()
        assertFalse(persistedValue.contains(secret))
        store.remove(alias)
    }
}
