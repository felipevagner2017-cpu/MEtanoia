package br.com.metanoia.security

import android.util.Base64
import java.security.SecureRandom
import java.nio.CharBuffer
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

/** Client-only account crypto matching web/crypto.js byte-for-byte. */
class AccountCrypto(
    private val random: SecureRandom = SecureRandom(),
    private val iterations: Int = DEFAULT_ITERATIONS
) {
    init { require(iterations >= MIN_ITERATIONS) }
    fun createEnvelope(userId: String, passphrase: CharArray): String {
        require(userId.isNotBlank() && passphrase.size >= 12)
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val dataKey = ByteArray(32).also(random::nextBytes)
        val wrappingKey = derive(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(128, nonce))
            cipher.updateAAD("metanoia-account-key|1|$userId".toByteArray(Charsets.UTF_8))
            val ciphertext = cipher.doFinal(dataKey)
            """{"format":"metanoia-key-envelope","version":1,"kdf":"PBKDF2-SHA256","iterations":$iterations,"salt":"${b64(salt)}","nonce":"${b64(nonce)}","ciphertext":"${b64(ciphertext)}"}"""
        } finally { dataKey.fill(0) }
    }
    fun openEnvelope(userId: String, passphrase: CharArray, envelope: KeyEnvelope): ByteArray {
        require(envelope.version == 1 && envelope.kdf == "PBKDF2-SHA256")
        require(envelope.iterations in MIN_ITERATIONS..MAX_ITERATIONS)
        val salt = decode(envelope.salt, 16)
        val nonce = decode(envelope.nonce, 12)
        val ciphertext = Base64.decode(envelope.ciphertext, Base64.NO_WRAP)
        require(ciphertext.size == 48)
        val key = derive(passphrase, salt, envelope.iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD("metanoia-account-key|1|$userId".toByteArray(Charsets.UTF_8))
        return cipher.doFinal(ciphertext)
    }
    fun encryptRecord(userId: String, recordId: String, dimension: String, recordType: String, dataKey: ByteArray, plaintext: ByteArray): String {
        require(dataKey.size == 32 && plaintext.isNotEmpty())
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(recordAad(userId, recordId, dimension, recordType))
        return "v1." + b64(nonce + cipher.doFinal(plaintext))
    }
    fun decryptRecord(userId: String, recordId: String, dimension: String, recordType: String, dataKey: ByteArray, value: String): ByteArray {
        require(dataKey.size == 32 && value.startsWith("v1."))
        val all = Base64.decode(value.removePrefix("v1."), Base64.NO_WRAP)
        require(all.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        cipher.updateAAD(recordAad(userId, recordId, dimension, recordType))
        return cipher.doFinal(all.copyOfRange(12, all.size))
    }
    private fun derive(passphrase: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
        // Explicit UTF-8 PBKDF2 avoids provider-specific PBEKeySpec character conversion
        // and exactly matches Web Crypto's TextEncoder + PBKDF2 contract.
        val encoded: ByteBuffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(passphrase))
        val passwordBytes = ByteArray(encoded.remaining()).also(encoded::get)
        encoded.array()?.fill(0)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            val block = salt + byteArrayOf(0, 0, 0, 1)
            var u = mac.doFinal(block)
            val output = u.copyOf()
            for (round in 2..rounds) {
                u = mac.doFinal(u)
                for (index in output.indices) output[index] = (output[index].toInt() xor u[index].toInt()).toByte()
            }
            u.fill(0)
            val result = SecretKeySpec(output, "AES")
            output.fill(0)
            return result
        } finally { passwordBytes.fill(0) }
    }
    private fun recordAad(userId: String, id: String, dimension: String, type: String) =
        "metanoia-record|1|$userId|$id|$dimension|$type".toByteArray(Charsets.UTF_8)
    private fun decode(value: String, size: Int) = Base64.decode(value, Base64.NO_WRAP).also { require(it.size == size) }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    data class KeyEnvelope(val version: Int, val kdf: String, val iterations: Int, val salt: String, val nonce: String, val ciphertext: String)
    companion object { const val DEFAULT_ITERATIONS = 600_000; const val MIN_ITERATIONS = 600_000; const val MAX_ITERATIONS = 2_000_000 }
}
