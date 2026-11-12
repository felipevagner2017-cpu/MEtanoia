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

    fun encryptRecordV2(
        accountId: String, sourceDeviceId: String, purpose: String, recordId: String,
        dimension: String, recordType: String, dataKey: ByteArray, counter: Long, plaintext: ByteArray
    ): String {
        require(accountId.isNotBlank() && sourceDeviceId.isNotBlank() && purpose.isNotBlank())
        require(recordId.isNotBlank() && dimension.isNotBlank() && recordType.isNotBlank())
        require(dataKey.size == 32 && counter >= 0L && plaintext.isNotEmpty())
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()
        val material = deriveSyncMaterial(dataKey, accountId, sourceDeviceId, purpose)
        val aesKey = material.copyOfRange(0, 32)
        val nonce = ByteArray(12).also {
            material.copyInto(it, 0, 32, 36)
            counterBytes.copyInto(it, 4)
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(syncRecordAad(accountId, sourceDeviceId, purpose, recordId, dimension, recordType, counterBytes))
            return "v2." + Base64.encodeToString(counterBytes + cipher.doFinal(plaintext), Base64.NO_WRAP)
        } finally {
            material.fill(0); aesKey.fill(0); nonce.fill(0); counterBytes.fill(0)
        }
    }

    fun decryptRecordV2(
        accountId: String, sourceDeviceId: String, purpose: String, recordId: String,
        dimension: String, recordType: String, dataKey: ByteArray, value: String
    ): ByteArray {
        require(accountId.isNotBlank() && sourceDeviceId.isNotBlank() && purpose.isNotBlank())
        require(recordId.isNotBlank() && dimension.isNotBlank() && recordType.isNotBlank())
        require(dataKey.size == 32 && value.startsWith("v2."))
        val packed = Base64.decode(value.removePrefix("v2."), Base64.NO_WRAP)
        require(packed.size > 24) { "Payload v2 inválido." }
        val counterBytes = packed.copyOfRange(0, 8)
        require(ByteBuffer.wrap(counterBytes).long >= 0L) { "Contador v2 fora do intervalo suportado." }
        val material = deriveSyncMaterial(dataKey, accountId, sourceDeviceId, purpose)
        val aesKey = material.copyOfRange(0, 32)
        val nonce = ByteArray(12).also {
            material.copyInto(it, 0, 32, 36)
            counterBytes.copyInto(it, 4)
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(syncRecordAad(accountId, sourceDeviceId, purpose, recordId, dimension, recordType, counterBytes))
            return cipher.doFinal(packed.copyOfRange(8, packed.size))
        } finally {
            material.fill(0); aesKey.fill(0); nonce.fill(0); counterBytes.fill(0)
        }
    }

    private fun deriveSyncMaterial(dataKey: ByteArray, accountId: String, sourceDeviceId: String, purpose: String): ByteArray {
        require(dataKey.size == 32)
        val info = encodeLengthPrefixed(
            "metonoia-sync-record-v2".toByteArray(Charsets.UTF_8),
            accountId.toByteArray(Charsets.UTF_8),
            sourceDeviceId.toByteArray(Charsets.UTF_8),
            purpose.toByteArray(Charsets.UTF_8)
        )
        return hkdfSha256(dataKey, "metonoia-sync-v2-hkdf-salt".toByteArray(Charsets.UTF_8), info, 36)
            .also { info.fill(0) }
    }

    private fun syncRecordAad(
        accountId: String, sourceDeviceId: String, purpose: String, recordId: String,
        dimension: String, recordType: String, counterBytes: ByteArray
    ): ByteArray = encodeLengthPrefixed(
        "metonoia-sync-record-v2".toByteArray(Charsets.UTF_8),
        accountId.toByteArray(Charsets.UTF_8),
        sourceDeviceId.toByteArray(Charsets.UTF_8),
        purpose.toByteArray(Charsets.UTF_8),
        recordId.toByteArray(Charsets.UTF_8),
        dimension.toByteArray(Charsets.UTF_8),
        recordType.toByteArray(Charsets.UTF_8),
        counterBytes
    )

    private fun encodeLengthPrefixed(vararg fields: ByteArray): ByteArray =
        ByteBuffer.allocate(fields.sumOf { 4 + it.size }).apply {
            fields.forEach { field -> putInt(field.size); put(field) }
        }.array()

    /** RFC 5869 HKDF-SHA-256. */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32))
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = extract.doFinal(ikm)
        var previous = ByteArray(0)
        try {
            val output = ByteArray(length)
            var offset = 0
            var blockIndex = 1
            while (offset < length) {
                val expand = Mac.getInstance("HmacSHA256")
                expand.init(SecretKeySpec(prk, "HmacSHA256"))
                expand.update(previous)
                expand.update(info)
                expand.update(blockIndex.toByte())
                val block = expand.doFinal()
                previous.fill(0)
                previous = block
                val count = minOf(block.size, length - offset)
                block.copyInto(output, offset, 0, count)
                offset += count
                blockIndex++
            }
            return output
        } finally {
            prk.fill(0)
            previous.fill(0)
        }
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
            // Reuse the two HMAC output buffers for every PBKDF2 round. Calling
            // Mac.doFinal(input) allocates a fresh byte array each time; at 600,000+
            // rounds that creates millions of short-lived objects on Android and can
            // exhaust the native Scudo allocator on constrained devices/emulators.
            val first = mac.doFinal(block)
            val second = ByteArray(mac.macLength)
            var current = first
            var next = second
            val output = first.copyOf()
            try {
                for (round in 2..rounds) {
                    mac.update(current)
                    mac.doFinal(next, 0)
                    for (index in output.indices) {
                        output[index] = (output[index].toInt() xor next[index].toInt()).toByte()
                    }
                    val previous = current
                    current = next
                    next = previous
                }
                // SecretKeySpec copies the supplied key bytes; clear our working copy.
                return SecretKeySpec(output, "AES")
            } finally {
                first.fill(0)
                second.fill(0)
                output.fill(0)
            }
        } finally { passwordBytes.fill(0) }
    }
    private fun recordAad(userId: String, id: String, dimension: String, type: String) =
        "metanoia-record|1|$userId|$id|$dimension|$type".toByteArray(Charsets.UTF_8)
    private fun decode(value: String, size: Int) = Base64.decode(value, Base64.NO_WRAP).also { require(it.size == size) }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    data class KeyEnvelope(val version: Int, val kdf: String, val iterations: Int, val salt: String, val nonce: String, val ciphertext: String)
    companion object { const val DEFAULT_ITERATIONS = 600_000; const val MIN_ITERATIONS = 600_000; const val MAX_ITERATIONS = 2_000_000 }
}
