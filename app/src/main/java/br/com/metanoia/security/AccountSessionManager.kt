package br.com.metanoia.security

import android.content.Context
import br.com.metanoia.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AuthenticatedAccount(val userId: String, val deviceId: String)

/**
 * Android account session bootstrap. The account DEK is opened from the server envelope
 * using the user's passphrase, then wrapped locally by AccountKeyStore. It is never sent
 * back to the server or logged.
 *
 * Call from Dispatchers.IO; methods perform blocking HTTPS requests.
 */
class AccountSessionManager(
    context: Context,
    private val identities: InstallationIdentityStore = InstallationIdentityStore(context),
    private val accountKeys: AccountKeyStore = AccountKeyStore(context),
    private val accountCrypto: AccountCrypto = AccountCrypto(),
    private val baseUrl: String = API_BASE_URL
) {
    fun login(email: String, password: String, passphrase: CharArray): AuthenticatedAccount {
        try {
            require(email.isNotBlank() && password.isNotBlank())
            val deviceId = identities.getOrCreateDeviceId()
            val response = request(
                "/api/auth/login", "POST",
                JSONObject().put("email", email.trim()).put("password", password)
                    .put("platform", "android").put("deviceLabel", "Android MEtanoia")
                    .put("deviceId", deviceId).toString()
            )
            val session = StoredAccountSession(
                response.getString("userId"), response.getString("deviceId"),
                response.getString("token"), response.getLong("expiresAt")
            )
            require(session.deviceId == deviceId) { "O servidor retornou outra identidade de dispositivo." }
            unlockAccountKey(session, passphrase)
            identities.saveSession(session)
            return AuthenticatedAccount(session.userId, session.deviceId)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    fun restore(passphrase: CharArray): AuthenticatedAccount? {
        try {
            val session = identities.loadSession() ?: return null
            if (session.expiresAt <= System.currentTimeMillis()) {
                identities.clearSession()
                return null
            }
            unlockAccountKey(session, passphrase)
            return AuthenticatedAccount(session.userId, session.deviceId)
        } finally {
            passphrase.fill('\u0000')
        }
    }

    fun currentSession(): StoredAccountSession? = identities.loadSession()

    fun logout() {
        identities.clearSession()
        // The account DEK remains locally wrapped for offline access; logout does not erase user data.
    }

    private fun unlockAccountKey(session: StoredAccountSession, passphrase: CharArray) {
        val headers = mapOf("Authorization" to "Bearer ${session.token}")
        val envelopeResponse = request("/api/crypto/key-envelope", "GET", null, headers)
        var envelope = envelopeResponse.optJSONObject("envelope")
        if (envelope == null) {
            val created = JSONObject(accountCrypto.createEnvelope(session.userId, passphrase))
            request(
                "/api/crypto/key-envelope", "POST",
                JSONObject().put("envelope", created).toString(), headers
            )
            envelope = created
        }
        val key = accountCrypto.openEnvelope(session.userId, passphrase, AccountCrypto.KeyEnvelope(
            envelope.getInt("version"), envelope.getString("kdf"), envelope.getInt("iterations"),
            envelope.getString("salt"), envelope.getString("nonce"), envelope.getString("ciphertext")
        ))
        try {
            val cached = accountKeys.load(session.userId)
            if (cached != null) {
                try {
                    check(MessageDigest.isEqual(cached, key)) {
                        "A chave local não corresponde ao envelope desta conta. Os dados locais foram preservados."
                    }
                } finally { cached.fill(0) }
            }
            accountKeys.save(session.userId, key)
        } finally { key.fill(0) }
    }

    private fun request(path: String, method: String, body: String?, headers: Map<String, String> = emptyMap()): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(responseText) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw IOException(json.optString("error").ifBlank { "A API respondeu HTTP $status." })
            return json
        } finally { connection.disconnect() }
    }

    companion object { val API_BASE_URL: String = BuildConfig.METANOIA_API_BASE_URL }
}
