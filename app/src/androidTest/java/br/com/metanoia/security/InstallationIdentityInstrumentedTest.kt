package br.com.metanoia.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InstallationIdentityInstrumentedTest {
    @Test
    fun installationIdentitySurvivesStoreRecreationAndLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstStore = InstallationIdentityStore(context)
        val firstId = firstStore.getOrCreateDeviceId()
        assertEquals(firstId, InstallationIdentityStore(context).getOrCreateDeviceId())
        assertNotNull(UUID.fromString(firstId))
        firstStore.saveSession(StoredAccountSession("account-test", firstId, "test-token", Long.MAX_VALUE))
        firstStore.clearSession()
        assertEquals(firstId, firstStore.getOrCreateDeviceId())
        assertEquals(null, firstStore.loadSession())
    }

    @Test
    fun encryptedSessionSurvivesStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = InstallationIdentityStore(context)
        val id = store.getOrCreateDeviceId()
        val expected = StoredAccountSession("account-session-test", id, "opaque-session-token", Long.MAX_VALUE)
        store.saveSession(expected)
        assertEquals(expected, InstallationIdentityStore(context).loadSession())
        store.clearSession()
    }

    @Test
    fun accountEnvelopeKeyIsRecoveredOnlyForTheAuthenticatedAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val crypto = AccountCrypto()
        val userId = "account-dek-recovery-test"
        val phrase = "passphrase de teste suficientemente longa".toCharArray()
        val envelope = crypto.createEnvelope(userId, phrase)
        val json = org.json.JSONObject(envelope)
        val recovered = crypto.openEnvelope(userId, phrase, AccountCrypto.KeyEnvelope(
            json.getInt("version"), json.getString("kdf"), json.getInt("iterations"),
            json.getString("salt"), json.getString("nonce"), json.getString("ciphertext")
        ))
        val store = AccountKeyStore(context)
        try {
            store.save(userId, recovered)
            val loaded = store.load(userId)
            assertNotNull(loaded)
            assertArrayEquals(recovered, loaded)
            assertEquals(null, store.load(userId + "-other-account"))
            var wrongAccountRejected = false
            try {
                crypto.openEnvelope(userId + "-other-account", phrase, AccountCrypto.KeyEnvelope(
                    json.getInt("version"), json.getString("kdf"), json.getInt("iterations"),
                    json.getString("salt"), json.getString("nonce"), json.getString("ciphertext")
                ))
            } catch (_: Exception) {
                wrongAccountRejected = true
            }
            assertEquals(true, wrongAccountRejected)
            loaded!!.fill(0)
        } finally {
            recovered.fill(0)
            phrase.fill('\u0000')
            store.remove(userId, deleteKeystoreKey = true)
        }
    }
}
