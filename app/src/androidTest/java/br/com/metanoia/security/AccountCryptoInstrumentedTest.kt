package br.com.metanoia.security

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.SecureRandom

class AccountCryptoInstrumentedTest {
    @Test fun androidAndWebCryptoShareFixedInteroperabilityVectors() {
        val user = "interop-user-01"
        val phrase = "interop passphrase 123".toCharArray()
        val fixedRandom = FixedSecureRandom(listOf(
            bytes(0, 16), bytes(16, 12), bytes(32, 32), bytes(80, 12)
        ))
        val android = AccountCrypto(fixedRandom)
        val expectedEnvelope = """{"format":"metanoia-key-envelope","version":1,"kdf":"PBKDF2-SHA256","iterations":600000,"salt":"AAECAwQFBgcICQoLDA0ODw==","nonce":"EBESExQVFhcYGRob","ciphertext":"TEkPBZsPEhWxEfDk4jt+tZq4dFKFTmjZ3tR4rQC4+L/+p0cDJNMM9ealgfi2H14P"}"""
        val actualEnvelope = android.createEnvelope(user, phrase)
        assertEquals("Android envelope must match the Web Crypto test vector", expectedEnvelope, actualEnvelope)
        val key = android.openEnvelope(user, phrase, parseEnvelope(actualEnvelope))
        val plaintext = "Conteúdo criado no Android e aberto na PWA".toByteArray(Charsets.UTF_8)
        val record = android.encryptRecord(user, "record-interop-1", "DEVOTION", "journal", key, plaintext)
        assertEquals("v1.UFFSU1RVVldYWVpb8s/GZlTszWlbL2loBGjnwPyCIB85mB1yzZW8etUqQVuEtDK3Rk/YUnXFLTjkJae5iv7LIpXRIuM/kzc=", record)

        // The following envelope and record were generated independently with Web Crypto's
        // specified PBKDF2/AES-GCM format. Android must be able to open them unchanged.
        val webEnvelope = AccountCrypto.KeyEnvelope(
            1, "PBKDF2-SHA256", 600000,
            "EBESExQVFhcYGRobHB0eHw==", "KCkqKywtLi8wMTIz",
            "6imIo6IAg9BsXvVld9AnOtPQB/in8/PQmOF1kRrXK5ULX+5c9zMWqQkm+X+YSLdV"
        )
        val webKey = android.openEnvelope("interop-user-pwa", "pwa-generated phrase 456".toCharArray(), webEnvelope)
        assertArrayEquals(bytes(64, 32), webKey)
        val webPlain = android.decryptRecord(
            "interop-user-pwa", "record-pwa-1", "MATRIMONIAL", "journal", webKey,
            "v1.YGFiY2RlZmdoaWpr70dncWJXKsJmoRpEbOc4ZC9CjzFHadAua1qq2s6wc5QAgYI+OqW7+8eQxB+X3YwgYxmg6EHoBNKru5I="
        )
        assertArrayEquals("Conteúdo criado na PWA e aberto no Android".toByteArray(Charsets.UTF_8), webPlain)
    }

    private fun parseEnvelope(value: String): AccountCrypto.KeyEnvelope {
        val json = JSONObject(value)
        return AccountCrypto.KeyEnvelope(
            json.getInt("version"), json.getString("kdf"), json.getInt("iterations"),
            json.getString("salt"), json.getString("nonce"), json.getString("ciphertext")
        )
    }
    private fun bytes(start: Int, count: Int) = ByteArray(count) { (start + it).toByte() }
    private class FixedSecureRandom(private val sequence: List<ByteArray>) : SecureRandom() {
        private var index = 0
        override fun nextBytes(bytes: ByteArray) {
            val next = sequence[index++]
            require(next.size == bytes.size)
            next.copyInto(bytes)
        }
    }
}
