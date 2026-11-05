package br.com.metanoia.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class StoredAccountSession(
    val userId: String,
    val deviceId: String,
    val token: String,
    val expiresAt: Long
)

/**
 * Keeps a stable installation identity and encrypts session credentials with a
 * non-exportable Android Keystore key. Logout removes the session, never the device ID.
 */
class InstallationIdentityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @Synchronized
    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null && UUID_PATTERN.matches(existing)) return existing
        val created = UUID.randomUUID().toString()
        check(prefs.edit().putString(KEY_DEVICE_ID, created).commit()) {
            "Não foi possível persistir a identidade desta instalação."
        }
        return created
    }

    @Synchronized
    fun saveSession(session: StoredAccountSession) {
        require(session.userId.isNotBlank() && session.token.isNotBlank())
        require(session.deviceId == getOrCreateDeviceId())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(JSONObject()
            .put("userId", session.userId)
            .put("deviceId", session.deviceId)
            .put("token", session.token)
            .put("expiresAt", session.expiresAt)
            .toString().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        check(prefs.edit().putString(KEY_SESSION, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Não foi possível proteger a sessão."
        }
    }

    fun loadSession(): StoredAccountSession? {
        val encoded = prefs.getString(KEY_SESSION, null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > IV_BYTES + TAG_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, packed.copyOfRange(0, IV_BYTES)))
            cipher.updateAAD(AAD)
            val json = JSONObject(String(cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size)), Charsets.UTF_8))
            val session = StoredAccountSession(
                json.getString("userId"), json.getString("deviceId"),
                json.getString("token"), json.getLong("expiresAt")
            )
            require(session.deviceId == getOrCreateDeviceId() && session.userId.isNotBlank() && session.token.isNotBlank())
            session
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    @Synchronized
    fun clearSession() {
        check(prefs.edit().remove(KEY_SESSION).commit()) { "Não foi possível encerrar a sessão local." }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES = "metonoia_installation_identity_v1"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SESSION = "encrypted_session"
        private const val KEY_ALIAS = "metonoia.session.storage.aes256.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private val AAD = "metonoia-installation-session|1".toByteArray(Charsets.UTF_8)
        private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
    }
}
