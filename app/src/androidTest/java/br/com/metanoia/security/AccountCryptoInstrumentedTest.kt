package br.com.metanoia.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountCryptoInstrumentedTest {
    private val crypto = AccountCrypto()
    @Test fun accountEnvelopeAndRecordsRoundTripWithBoundIdentity() {
        val user = "account-android-test"
        val phrase = "test-only-encryption-phrase".toCharArray()
        val envelopeJson = crypto.createEnvelope(user, phrase)
        val fields = Regex("\"(version|kdf|iterations|salt|nonce|ciphertext)\"\\s*:\\s*(?:\"([^\"]*)\"|(\\d+))")
            .findAll(envelopeJson).associate { it.groupValues[1] to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
        val envelope = AccountCrypto.KeyEnvelope(
            fields.getValue("version").toInt(), fields.getValue("kdf"), fields.getValue("iterations").toInt(),
            fields.getValue("salt"), fields.getValue("nonce"), fields.getValue("ciphertext")
        )
        val key = crypto.openEnvelope(user, phrase, envelope)
        val plaintext = "registro Android e PWA".toByteArray()
        val encrypted = crypto.encryptRecord(user, "record-1", "DEVOTION", "journal", key, plaintext)
        assertArrayEquals(plaintext, crypto.decryptRecord(user, "record-1", "DEVOTION", "journal", key, encrypted))
        assertThrows(Exception::class.java) { crypto.decryptRecord("another-account", "record-1", "DEVOTION", "journal", key, encrypted) }
        assertThrows(Exception::class.java) { crypto.openEnvelope("another-account", phrase, envelope) }
    }
    @Test fun eachAccountGetsIndependentDataKey() {
        val phrase = "test-only-encryption-phrase".toCharArray()
        val a = crypto.createEnvelope("user-a", phrase)
        val b = crypto.createEnvelope("user-b", phrase)
        assertNotEquals(a, b)
    }
}
