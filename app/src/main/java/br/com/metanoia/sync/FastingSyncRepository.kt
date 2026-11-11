package br.com.metanoia.sync

import androidx.room.withTransaction
import br.com.metanoia.data.FastingDailyEntry
import br.com.metanoia.data.FastingPlan
import br.com.metanoia.data.LifeDimension
import br.com.metanoia.data.MetanoiaDatabase
import br.com.metanoia.data.SyncOutboxOperation
import br.com.metanoia.data.SyncCursorEntity
import br.com.metanoia.data.SyncRecordMetadata
import br.com.metanoia.data.SyncRemoteRecord
import br.com.metanoia.security.AccountCrypto
import br.com.metanoia.security.AccountKeyStore
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Encrypts fasting plans and daily reflections with the account DEK (not a
 * device-only key), then queues only authenticated AES-GCM ciphertext.
 * Nonce reservations are durable and scoped by account/device/purpose.
 */
class FastingSyncRepository(
    private val database: MetanoiaDatabase,
    private val accountKeys: AccountKeyStore,
    private val accountCrypto: AccountCrypto = AccountCrypto(),
    private val now: () -> Long = System::currentTimeMillis
) {
    /**
     * Sends queued account-encrypted records and applies authenticated fasting
     * records returned by the same pull cycle. The caller persists nextCursor
     * and invokes again when hasMore is true.
     */
    suspend fun synchronize(
        accountId: String,
        token: String,
        deviceId: String,
        since: Long? = null,
        afterId: String? = null
    ): SyncCycleResult {
        require(accountId.isNotBlank() && token.isNotBlank() && deviceId.isNotBlank())
        val storedCursor = database.syncCursorDao().get(accountId, deviceId)
        val explicitCursor = since != null || afterId != null
        var cursorSince = if (explicitCursor) since ?: 0L else storedCursor?.serverUpdatedAt ?: 0L
        var cursorAfter = if (explicitCursor) afterId.orEmpty() else storedCursor?.afterRecordId.orEmpty()
        var uploaded = 0
        var conflicts = 0
        var rejected = 0
        var hasMore = false
        var nextCursor: String? = null
        val downloaded = mutableListOf<PulledSyncRecord>()
        val coordinator = SyncCoordinator(database.syncOutboxDao())
        var pages = 0
        do {
            val cycle = coordinator.synchronize(
                accountId = accountId, token = token, deviceId = deviceId,
                since = cursorSince, afterId = cursorAfter
            )
            uploaded += cycle.uploaded
            conflicts += cycle.conflicts
            rejected += cycle.rejected
            downloaded += cycle.downloaded
            cycle.downloaded
                .filter { it.encryptionVersion == 2 && it.recordType in setOf("fasting_plan", "fasting_daily_entry") }
                .sortedBy { if (it.recordType == "fasting_plan") 0 else 1 }
                .forEach { record ->
                    if (!applyRemote(record, accountId)) conflicts++
                }
            hasMore = cycle.hasMore
            nextCursor = cycle.nextCursor
            if (nextCursor != null) {
                val separator = nextCursor.indexOf(':')
                require(separator > 0) { "Cursor de sincronização inválido." }
                cursorSince = nextCursor.substring(0, separator).toLongOrNull()
                    ?: throw IllegalStateException("Timestamp do cursor inválido.")
                cursorAfter = nextCursor.substring(separator + 1)
                database.syncCursorDao().save(
                    SyncCursorEntity(accountId, deviceId, cursorSince, cursorAfter, now())
                )
            }
            pages++
        } while (hasMore && pages < 20)
        return SyncCycleResult(uploaded, conflicts, rejected, downloaded, hasMore, nextCursor)
    }

    suspend fun savePlanAndQueue(plan: FastingPlan, accountId: String, deviceId: String): SyncOutboxOperation {
        val dimension = wireDimension(plan.dimension)
        val recordId = stableRecordId("fasting-plan", plan.id)
        val existing = database.syncRecordMetadataDao().find("fasting_plan", plan.id)
        val operation = encryptedOperation(
            accountId, deviceId, "fasting-plan", recordId, dimension, "fasting_plan",
            existing?.revision ?: 0L, planJson(plan).toString()
        )
        database.withTransaction {
            database.fastingDao().upsertPlan(plan)
            database.syncOutboxDao().enqueue(operation)
            database.syncRecordMetadataDao().upsert(
                SyncRecordMetadata(
                    entityType = "fasting_plan", localId = plan.id, recordId = recordId,
                    dimension = dimension, recordType = "fasting_plan",
                    revision = existing?.revision ?: 0L, syncState = "pending",
                    sourceDeviceId = deviceId, encryptionVersion = 2, updatedAt = now()
                )
            )
        }
        return operation
    }

    suspend fun saveReflectionAndQueue(
        entry: FastingDailyEntry,
        accountId: String,
        deviceId: String
    ): SyncOutboxOperation {
        val plan = requireNotNull(database.fastingDao().findPlan(entry.planId)) {
            "A reflexão precisa pertencer a um plano de jejum salvo."
        }
        val dimension = wireDimension(plan.dimension)
        val recordId = stableRecordId("fasting-entry", entry.id)
        val existing = database.syncRecordMetadataDao().find("fasting_daily_entry", entry.id)
        val operation = encryptedOperation(
            accountId, deviceId, "fasting-entry", recordId, dimension, "fasting_daily_entry",
            existing?.revision ?: 0L, entryJson(entry).toString()
        )
        database.withTransaction {
            database.fastingDao().upsertDailyEntry(entry)
            database.syncOutboxDao().enqueue(operation)
            database.syncRecordMetadataDao().upsert(
                SyncRecordMetadata(
                    entityType = "fasting_daily_entry", localId = entry.id, recordId = recordId,
                    dimension = dimension, recordType = "fasting_daily_entry",
                    revision = existing?.revision ?: 0L, syncState = "pending",
                    sourceDeviceId = deviceId, encryptionVersion = 2, updatedAt = now()
                )
            )
        }
        return operation
    }

    /**
     * Stores the remote ciphertext before applying it. Local pending edits are
     * never overwritten: the incoming record is retained and marked conflict.
     */
    suspend fun applyRemote(record: PulledSyncRecord, accountId: String): Boolean {
        require(accountId.isNotBlank())
        require(record.encryptionVersion == 2) { "Versão de criptografia de jejum não suportada." }
        require(record.sourceDeviceId.isNotBlank() && record.purpose in setOf("fasting-plan", "fasting-entry")) {
            "Contexto criptográfico remoto incompleto."
        }
        require(
            (record.recordType == "fasting_plan" && record.purpose == "fasting-plan") ||
                (record.recordType == "fasting_daily_entry" && record.purpose == "fasting-entry")
        ) { "Tipo e finalidade criptográfica do registro não correspondem." }
        return database.withTransaction {
            val metadata = database.syncRecordMetadataDao().findByRecordId(record.recordId)
            database.syncRemoteRecordDao().replace(
                SyncRemoteRecord(
                    accountId = accountId, recordId = record.recordId, dimension = record.dimension,
                    recordType = record.recordType, payloadCiphertext = record.payloadCiphertext,
                    encryptionVersion = record.encryptionVersion, revision = record.revision,
                    clientUpdatedAt = record.clientUpdatedAt, serverUpdatedAt = record.serverUpdatedAt,
                    deletedAt = record.deletedAt, sourceDeviceId = record.sourceDeviceId, status = "received"
                )
            )
            if (metadata?.syncState == "pending") {
                database.syncRecordMetadataDao().markConflict(record.recordId, now())
                database.syncRemoteRecordDao().markConflict(accountId, record.recordId, "Há alterações locais pendentes.")
                return@withTransaction false
            }
            if (metadata != null && metadata.revision >= record.revision) {
                database.syncRemoteRecordDao().markApplied(accountId, record.recordId, record.revision)
                return@withTransaction true
            }

            val key = requireNotNull(accountKeys.load(accountId)) {
                "A chave da conta não está desbloqueada neste aparelho."
            }
            val plaintext = try {
                accountCrypto.decryptRecordV2(
                    accountId, record.sourceDeviceId, record.purpose, record.recordId,
                    record.dimension, record.recordType, key, record.payloadCiphertext
                )
            } finally {
                key.fill(0)
            }
            val json = try {
                JSONObject(String(plaintext, Charsets.UTF_8))
            } finally {
                plaintext.fill(0)
            }
            when (record.recordType) {
                "fasting_plan" -> {
                    require(record.purpose == "fasting-plan")
                    val plan = parsePlan(json)
                    require(stableRecordId("fasting-plan", plan.id) == record.recordId)
                    database.fastingDao().upsertPlan(plan)
                    database.syncRecordMetadataDao().upsert(
                        SyncRecordMetadata("fasting_plan", plan.id, record.recordId, record.dimension,
                            record.recordType, record.revision, "synced", record.sourceDeviceId, 2,
                            record.serverUpdatedAt, now())
                    )
                }
                "fasting_daily_entry" -> {
                    require(record.purpose == "fasting-entry")
                    val entry = parseEntry(json)
                    require(stableRecordId("fasting-entry", entry.id) == record.recordId)
                    require(database.fastingDao().findPlan(entry.planId) != null) {
                        "O plano relacionado ainda não foi sincronizado."
                    }
                    database.fastingDao().upsertDailyEntry(entry)
                    database.syncRecordMetadataDao().upsert(
                        SyncRecordMetadata("fasting_daily_entry", entry.id, record.recordId, record.dimension,
                            record.recordType, record.revision, "synced", record.sourceDeviceId, 2,
                            record.serverUpdatedAt, now())
                    )
                }
                else -> throw IllegalArgumentException("Tipo de registro remoto de jejum desconhecido.")
            }
            database.syncRemoteRecordDao().markApplied(accountId, record.recordId, record.revision)
            true
        }
    }

    private suspend fun encryptedOperation(
        accountId: String, deviceId: String, purpose: String, recordId: String,
        dimension: String, recordType: String, baseRevision: Long, json: String
    ): SyncOutboxOperation {
        require(accountId.isNotBlank() && deviceId.isNotBlank())
        require(baseRevision in 0L..Int.MAX_VALUE.toLong()) {
            "A revisão excede o intervalo suportado pela fila local."
        }
        val key = requireNotNull(accountKeys.load(accountId)) {
            "Entre na conta e desbloqueie a chave criptográfica antes de sincronizar."
        }
        try {
            val reservation = database.syncNonceCounterDao().reserveBlock(accountId, deviceId, purpose)
            val ciphertext = accountCrypto.encryptRecordV2(
                accountId, deviceId, purpose, recordId, dimension, recordType,
                key, reservation.startInclusive, json.toByteArray(Charsets.UTF_8)
            )
            return SyncOutboxOperation(
                accountId = accountId, operationId = UUID.randomUUID().toString(),
                recordId = recordId, dimension = dimension, recordType = recordType,
                operation = "upsert", payloadCiphertext = ciphertext, encryptionVersion = 2,
                baseRevision = baseRevision.toInt(), clientUpdatedAt = now(),
                sourceDeviceId = deviceId, purpose = purpose, createdAt = now()
            )
        } finally {
            key.fill(0)
        }
    }

    private fun planJson(value: FastingPlan) = JSONObject()
        .put("id", value.id).put("dimension", value.dimension.name).put("type", value.type)
        .put("identifiedNeed", value.identifiedNeed).put("recommendationReason", value.recommendationReason)
        .put("purpose", value.purpose).put("scriptureReferences", value.scriptureReferences)
        .put("scriptureContext", value.scriptureContext).put("preparation", value.preparation)
        .put("dailyGuidance", value.dailyGuidance).put("durationDays", value.durationDays)
        .put("startAt", value.startAt).put("endAt", value.endAt).put("foodAbstinence", value.foodAbstinence)
        .put("status", value.status).put("createdAt", value.createdAt)
        .put("completedAt", value.completedAt ?: JSONObject.NULL)

    private fun entryJson(value: FastingDailyEntry) = JSONObject()
        .put("id", value.id).put("planId", value.planId).put("dayIndex", value.dayIndex)
        .put("reflection", value.reflection).put("experience", value.experience).put("recordedAt", value.recordedAt)

    private fun parsePlan(value: JSONObject) = FastingPlan(
        id = value.getString("id"), dimension = LifeDimension.valueOf(value.getString("dimension")),
        type = value.getString("type"), identifiedNeed = value.getString("identifiedNeed"),
        recommendationReason = value.getString("recommendationReason"), purpose = value.getString("purpose"),
        scriptureReferences = value.getString("scriptureReferences"), scriptureContext = value.getString("scriptureContext"),
        preparation = value.getString("preparation"), dailyGuidance = value.getString("dailyGuidance"),
        durationDays = value.getInt("durationDays"), startAt = value.getLong("startAt"),
        endAt = value.getLong("endAt"), foodAbstinence = value.getBoolean("foodAbstinence"),
        status = value.getString("status"), createdAt = value.getLong("createdAt"),
        completedAt = if (value.isNull("completedAt")) null else value.getLong("completedAt")
    )

    private fun parseEntry(value: JSONObject) = FastingDailyEntry(
        id = value.getString("id"), planId = value.getString("planId"), dayIndex = value.getInt("dayIndex"),
        reflection = value.getString("reflection"), experience = value.getString("experience"),
        recordedAt = value.getLong("recordedAt")
    )

    private fun wireDimension(value: LifeDimension) = when (value) {
        LifeDimension.SPIRITUAL -> "DEVOTION"
        LifeDimension.MATRIMONIAL -> "MATRIMONIAL"
        LifeDimension.PROFESSIONAL -> "PROFESSIONAL"
        LifeDimension.INDIVIDUAL -> "DAILY"
        LifeDimension.ROUTINE -> "DAILY"
    }

    private fun stableRecordId(kind: String, localId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(localId.toByteArray(Charsets.UTF_8))
            .take(24).joinToString("") { "%02x".format(it) }
        return "$kind-$digest"
    }
}
