package br.com.metanoia.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps an account DEK with a non-exportable, per-account Android Keystore key.
 * Only the wrapped DEK is persisted in app-private preferences.
 */
class AccountKeyStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("metonoia_account_keys_v1", Context.MODE_PRIVATE)

    fun save(userId: String, dataKey: ByteArray) {
        require(userId.isNotBlank() && dataKey.size == 32)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(userId))
        cipher.updateAAD(aad(userId))
        val encrypted = cipher.doFinal(dataKey)
        val packed = cipher.iv + encrypted
        check(prefs.edit().putString(prefKey(userId), "v1." + Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Não foi possível persistir a chave protegida."
        }
    }

    fun load(userId: String): ByteArray? {
        require(userId.isNotBlank())
        val stored = prefs.getString(prefKey(userId), null) ?: return null
        require(stored.startsWith("v1.")) { "Versão local da chave não suportada." }
        val packed = Base64.decode(stored.removePrefix("v1."), Base64.NO_WRAP)
        require(packed.size == 12 + 32 + 16) { "Chave local protegida inválida." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(userId), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        cipher.updateAAD(aad(userId))
        return cipher.doFinal(packed.copyOfRange(12, packed.size)).also {
            require(it.size == 32) { "Chave de conta inválida." }
        }
    }

    fun remove(userId: String, deleteKeystoreKey: Boolean = false) {
        prefs.edit().remove(prefKey(userId)).commit()
        if (deleteKeystoreKey) {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(alias(userId))
        }
    }

    private fun getOrCreateKey(userId: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias(userId), null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias(userId), KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun alias(userId: String) = "metonoia.account.dek." + digest(userId).take(32)
    private fun prefKey(userId: String) = "dek." + digest(userId)
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun aad(userId: String) = "metanoia-local-dek|1|$userId".toByteArray(Charsets.UTF_8)

    companion object { private const val TRANSFORMATION = "AES/GCM/NoPadding" }
}
