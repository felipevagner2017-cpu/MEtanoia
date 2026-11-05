package br.com.metanoia.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCryptoInstrumentedTest {
    @Test
    fun ciphertextDoesNotExposePlaintextAndRoundTrips() {
        val crypto = SyncCrypto("metonoia.test.sync.aes256")
        val plaintext = """{"title":"private journal entry","dimension":"MATRIMONIAL"}""".toByteArray()
        val encrypted = crypto.encrypt(plaintext)
        assertFalse(encrypted.contains("private journal entry"))
        assertTrue(encrypted.startsWith("v1."))
        assertArrayEquals(plaintext, crypto.decrypt(encrypted))
    }

    @Test(expected = Exception::class)
    fun ciphertextCannotBeOpenedWithAnotherInstallationKey() {
        val writer = SyncCrypto("metonoia.test.sync.writer")
        val reader = SyncCrypto("metonoia.test.sync.reader")
        reader.decrypt(writer.encrypt("sensitive".toByteArray()))
    }
}
