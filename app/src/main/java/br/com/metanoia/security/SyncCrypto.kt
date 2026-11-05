package br.com.metanoia.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sync payloads with an Android Keystore AES-256-GCM key.
 * Ciphertext format: v1.<base64(iv || ciphertext+tag)>.
 * The key is non-exportable and scoped to this Android installation.
 */
class SyncCrypto(private val alias: String = DEFAULT_ALIAS) {
    fun encrypt(plaintext: ByteArray): String {
        require(plaintext.isNotEmpty()) { "Payload vazio não pode ser sincronizado." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return "v1." + Base64.encodeToString(ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array(), Base64.NO_WRAP)
    }

    fun decrypt(value: String): ByteArray {
        require(value.startsWith("v1.")) { "Versão de criptografia não suportada." }
        val combined = Base64.decode(value.removePrefix("v1."), Base64.NO_WRAP)
        require(combined.size > IV_LENGTH + TAG_LENGTH_BYTES) { "Payload criptografado inválido." }
        val buffer = ByteBuffer.wrap(combined)
        val iv = ByteArray(IV_LENGTH).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val DEFAULT_ALIAS = "metonoia.sync.aes256.v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8
    }
}
