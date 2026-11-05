package br.com.metanoia.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["accountId", "operationId"],
    indices = [
        Index(value = ["accountId", "status", "nextAttemptAt"]),
        Index(value = ["accountId", "recordId", "createdAt"])
    ]
)
data class SyncOutboxOperation(
    @ColumnInfo(defaultValue = "''") val accountId: String,
    val operationId: String,
    val recordId: String,
    val dimension: String,
    val recordType: String,
    val operation: String,
    val payloadCiphertext: String,
    val encryptionVersion: Int = 1,
    val baseRevision: Int = 0,
    val clientUpdatedAt: Long,
    val status: String = "pending",
    val attemptCount: Int = 0,
    val nextAttemptAt: Long = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val sourceDeviceId: String = "",
    @ColumnInfo(defaultValue = "'record'") val purpose: String = "record"
)
