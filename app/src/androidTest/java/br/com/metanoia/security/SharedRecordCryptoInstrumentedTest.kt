package br.com.metanoia.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRecordCryptoInstrumentedTest {
    @Test
    fun deterministicV2VectorMatchesWebCryptoAndAuthenticatesAllMetadata() {
        val dek = ByteArray(32) { it.toByte() }
        val plaintext = "Conteúdo compartilhado Android/PWA — vetor v2".toByteArray(Charsets.UTF_8)
        val encrypted = SharedRecordCrypto.encrypt(
            accountId = "interop-user-v2",
            sourceDeviceId = "device-android-01",
            purpose = "record",
            recordId = "record-v2-01",
            dimension = "DEVOTION",
            recordType = "journal",
            accountDek = dek,
            counter = 42L,
            plaintext = plaintext
        )
        assertEquals(
            "v2.AAAAAAAAACqcwwxL0VXPNJ4sfTkb+YRGhVourP2v6px5/LWgS4rMDmXu+zpsLHeQe/jyU9fnjsmwA3uglaviKRnXZW7SrhQe",
            encrypted
        )
        assertTrue(encrypted.startsWith("v2."))
        assertArrayEquals(
            plaintext,
            SharedRecordCrypto.decrypt(
                "interop-user-v2", "device-android-01", "record",
                "record-v2-01", "DEVOTION", "journal", dek, encrypted
            )
        )
    }

    @Test
    fun accountDevicePurposeAndRecordMetadataAreAuthenticated() {
        val dek = ByteArray(32) { (it * 3).toByte() }
        val encrypted = SharedRecordCrypto.encrypt(
            "account-A", "device-A", "record", "record-A", "DEVOTION",
            "journal", dek, 7L, "conteúdo protegido".toByteArray()
        )
        val invalidContexts = listOf(
            arrayOf("account-B", "device-A", "record", "record-A", "DEVOTION", "journal"),
            arrayOf("account-A", "device-B", "record", "record-A", "DEVOTION", "journal"),
            arrayOf("account-A", "device-A", "ai-history", "record-A", "DEVOTION", "journal"),
            arrayOf("account-A", "device-A", "record", "record-B", "DEVOTION", "journal"),
            arrayOf("account-A", "device-A", "record", "record-A", "MATRIMONIAL", "journal"),
            arrayOf("account-A", "device-A", "record", "record-A", "DEVOTION", "review")
        )
        invalidContexts.forEach { fields ->
            var rejected = false
            try {
                SharedRecordCrypto.decrypt(
                    fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], dek, encrypted
                )
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue("Alteração de metadados deve invalidar AES-GCM", rejected)
        }
    }
}
