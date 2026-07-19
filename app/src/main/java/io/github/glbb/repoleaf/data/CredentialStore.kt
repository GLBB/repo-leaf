package io.github.glbb.repoleaf.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("credentials", Context.MODE_PRIVATE)
    private val keyAlias = "repoleaf.credentials.v1"

    fun put(alias: String, secret: String?) {
        if (secret.isNullOrBlank()) {
            prefs.edit().remove(alias).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val value = Base64.encodeToString(cipher.iv + cipher.doFinal(secret.toByteArray()), Base64.NO_WRAP)
        prefs.edit().putString(alias, value).apply()
    }

    fun get(alias: String): String? = runCatching {
        val bytes = Base64.decode(prefs.getString(alias, null), Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString()
    }.getOrNull()

    fun remove(alias: String) = prefs.edit().remove(alias).apply()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }
}
