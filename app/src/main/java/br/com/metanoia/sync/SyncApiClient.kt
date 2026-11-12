package br.com.metanoia.sync

import br.com.metanoia.data.SyncOutboxDao
import br.com.metanoia.data.SyncOutboxOperation
import br.com.metanoia.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PulledSyncRecord(
    val recordId: String, val dimension: String, val recordType: String,
    val payloadCiphertext: String, val encryptionVersion: Int, val revision: Long,
    val clientUpdatedAt: Long, val serverUpdatedAt: Long, val deletedAt: Long?,
    val sourceDeviceId: String, val purpose: String
)

data class SyncCycleResult(
    val uploaded: Int, val conflicts: Int, val rejected: Int,
    val downloaded: List<PulledSyncRecord>, val hasMore: Boolean, val nextCursor: String?
)

class SyncApiClient(
    private val baseUrl: String = API_BASE_URL,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) {
    fun push(token: String, operations: List<SyncOutboxOperation>, deviceId: String): JSONArray {
        require(operations.isNotEmpty() && operations.size <= 50)
        require(deviceId.isNotBlank())
        require(operations.all { it.accountId.isNotBlank() }) { "Operação sem conta autenticada não pode ser enviada." }
        val payload = JSONObject().put("operations", JSONArray().apply {
            operations.forEach { operation ->
                put(JSONObject()
                    .put("operationId", operation.operationId)
                    .put("recordId", operation.recordId)
                    .put("dimension", operation.dimension)
                    .put("recordType", operation.recordType)
                    .put("operation", operation.operation)
                    .put("payloadCiphertext", operation.payloadCiphertext)
                    .put("encryptionVersion", operation.encryptionVersion)
                    .put("baseRevision", operation.baseRevision)
                    .put("clientUpdatedAt", operation.clientUpdatedAt)
                    .put("sourceDeviceId", operation.sourceDeviceId.ifBlank { deviceId })
                    .put("purpose", operation.purpose)
                    .put("deviceId", deviceId))
            }
        })
        return request("/api/sync/push", token, "POST", payload.toString())
            .optJSONArray("results") ?: throw IOException("Resposta de sincronização inválida.")
    }

    fun pull(token: String, since: Long, afterId: String = ""): JSONObject {
        val query = "since=$since&afterId=" + URLEncoder.encode(afterId, Charsets.UTF_8.name())
        return request("/api/sync/pull?$query", token, "GET", null)
    }

    private fun request(path: String, token: String, method: String, body: String?): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw IOException(response.optString("error").ifBlank { "Sincronização respondeu HTTP $status." })
            return response
        } finally { connection.disconnect() }
    }

    companion object { val API_BASE_URL: String = BuildConfig.METANOIA_API_BASE_URL }
}

/** Outbox drain scoped to the authenticated account returned by the server. */
class SyncCoordinator(
    private val outbox: SyncOutboxDao,
    private val api: SyncApiClient = SyncApiClient(),
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun synchronize(
        accountId: String, token: String, deviceId: String,
        since: Long = 0L, afterId: String = ""
    ): SyncCycleResult {
        require(accountId.isNotBlank() && token.isNotBlank() && deviceId.isNotBlank())
        var uploaded = 0
        var conflicts = 0
        var rejected = 0
        val batch = outbox.nextBatch(accountId, now(), 50)
        if (batch.isNotEmpty()) {
            val results = try {
                api.push(token, batch, deviceId)
            } catch (error: Exception) {
                batch.forEach { op ->
                    val exponent = op.attemptCount.coerceAtMost(10)
                    val delay = minOf(6 * 60 * 60 * 1000L, 5_000L * (1L shl exponent))
                    outbox.scheduleRetry(accountId, op.operationId, now() + delay, error.message ?: "Falha de rede")
                }
                throw error
            }
            val knownIds = batch.map { it.operationId }.toSet()
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val id = item.optString("operationId")
                if (id !in knownIds) continue
                when (item.optString("status")) {
                    "applied" -> { outbox.markApplied(accountId, id); uploaded++ }
                    "conflict" -> { outbox.markConflict(accountId, id, "Revisão remota divergente: " + item.optInt("currentRevision")); conflicts++ }
                    else -> { outbox.markConflict(accountId, id, item.optString("reason", "Operação rejeitada")); rejected++ }
                }
            }
        }
        val pull = api.pull(token, since, afterId)
        val recordsJson = pull.optJSONArray("records") ?: JSONArray()
        val records = buildList {
            for (index in 0 until recordsJson.length()) {
                val item = recordsJson.optJSONObject(index) ?: continue
                add(PulledSyncRecord(
                    recordId = item.optString("record_id"),
                    dimension = item.optString("dimension"),
                    recordType = item.optString("record_type"),
                    payloadCiphertext = item.optString("payload_ciphertext"),
                    encryptionVersion = item.optInt("encryption_version", 1),
                    revision = item.optLong("revision"),
                    clientUpdatedAt = item.optLong("client_updated_at"),
                    serverUpdatedAt = item.optLong("server_updated_at"),
                    deletedAt = if (item.isNull("deleted_at")) null else item.optLong("deleted_at"),
                    sourceDeviceId = item.optString("source_device_id"),
                    purpose = item.optString("purpose", "record")
                ))
            }
        }
        val cursorObject = pull.optJSONObject("nextCursor")
        val lastRecord = records.lastOrNull()
        val cursor = if (cursorObject != null) {
            cursorObject.optLong("since").toString() + ":" + cursorObject.optString("afterId")
        } else {
            lastRecord?.let { it.serverUpdatedAt.toString() + ":" + it.recordId }
        }
        return SyncCycleResult(uploaded, conflicts, rejected, records, pull.optBoolean("hasMore"), cursor)
    }
}
