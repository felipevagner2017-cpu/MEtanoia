package br.com.metanoia.data

import android.content.Context
import net.zetetic.database.sqlcipher.SQLiteDatabase
import br.com.metanoia.security.AccountKeyStore
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Converts the existing Room database in-place without deleting the source until
 * the encrypted copy has passed SQLCipher integrity and row-count verification.
 *
 * The temporary plaintext recovery file exists only during the atomic swap. If
 * the process stops during that window, the next launch validates the encrypted
 * candidate before removing the recovery copy; otherwise it restores the source.
 */
internal object SqlCipherDatabaseMigration {
    const val DATABASE_NAME = "metanoia.db"
    private const val LOCAL_KEY_ID = "local-room-database-v1"
    private const val BACKUP_SUFFIX = ".plaintext-recovery"
    private const val TEMP_SUFFIX = ".encrypted.tmp"
    private const val FAILED_SUFFIX = ".invalid-encrypted"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    enum class Stage {
        AFTER_EXPORT_VALIDATED,
        AFTER_PLAINTEXT_BACKUP_RENAMED,
        AFTER_ENCRYPTED_DATABASE_PROMOTED
    }

    fun ensureEncrypted(
        context: Context,
        databaseName: String = DATABASE_NAME,
        keyId: String = LOCAL_KEY_ID,
        faultInjector: ((Stage) -> Unit)? = null
    ): ByteArray {
        System.loadLibrary("sqlcipher")
        val app = context.applicationContext
        val database = app.getDatabasePath(databaseName)
        database.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Não foi possível preparar o diretório do banco.") }
        val backup = File(database.absolutePath + BACKUP_SUFFIX)
        val temporary = File(database.absolutePath + TEMP_SUFFIX)
        val failed = File(database.absolutePath + FAILED_SUFFIX)
        val keyStore = AccountKeyStore(app)

        var key = keyStore.load(keyId)
        recoverInterruptedSwap(database, backup, temporary, failed, key)

        if (!database.exists()) {
            key = key ?: createAndPersistKey(keyStore, keyId)
            return key
        }

        if (isPlainSqlite(database)) {
            key = key ?: createAndPersistKey(keyStore, keyId)
            migratePlaintext(database, backup, temporary, failed, key, faultInjector)
            return key
        }

        key ?: throw IllegalStateException(
            "O banco MEtanoia parece criptografado, mas a chave protegida não está disponível. O arquivo original foi preservado."
        )
        verifyEncrypted(database, key)
        return key
    }

    private fun createAndPersistKey(keyStore: AccountKeyStore, keyId: String): ByteArray {
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        keyStore.save(keyId, key)
        return key
    }

    private fun recoverInterruptedSwap(
        database: File,
        backup: File,
        temporary: File,
        failed: File,
        key: ByteArray?
    ) {
        if (backup.exists()) {
            val encryptedCurrentIsValid = database.exists() && key != null &&
                runCatching { verifyEncrypted(database, key); true }.getOrDefault(false)
            if (encryptedCurrentIsValid) {
                check(backup.delete() || !backup.exists()) {
                    "Não foi possível remover a cópia temporária não criptografada após validar o banco criptografado."
                }
                temporary.delete()
                return
            }

            if (!database.exists()) {
                val encryptedTemporaryIsValid = temporary.exists() && key != null &&
                    runCatching { verifyEncrypted(temporary, key); true }.getOrDefault(false)
                if (encryptedTemporaryIsValid) {
                    check(temporary.renameTo(database)) { "Não foi possível recuperar a cópia criptografada temporária." }
                    verifyEncrypted(database, key!!)
                    check(backup.delete() || !backup.exists()) { "Não foi possível remover a cópia de recuperação." }
                    return
                }
                check(backup.renameTo(database)) { "Não foi possível restaurar a base original após interrupção." }
                temporary.delete()
                return
            }

            if (isPlainSqlite(database)) {
                throw IllegalStateException(
                    "Foram encontradas duas cópias SQLite não criptografadas. Nenhuma foi alterada; recuperação automática interrompida por segurança."
                )
            }

            if (key != null && isPlainSqlite(backup)) {
                if (failed.exists()) failed.delete()
                check(database.renameTo(failed)) { "Não foi possível preservar o arquivo criptografado incompleto." }
                check(backup.renameTo(database)) { "Não foi possível restaurar a base original." }
                temporary.delete()
                return
            }
            throw IllegalStateException("Estado de recuperação do banco não reconhecido. Os arquivos foram preservados.")
        }

        if (temporary.exists()) {
            // Without a recovery source this can only be an incomplete pre-swap export.
            check(temporary.delete() || !temporary.exists()) { "Não foi possível remover a exportação temporária incompleta." }
        }
        if (failed.exists() && !database.exists()) {
            check(failed.renameTo(database)) { "Não foi possível restaurar o arquivo preservado." }
        }
    }

    private fun migratePlaintext(
        sourceFile: File,
        backupFile: File,
        temporaryFile: File,
        failedFile: File,
        key: ByteArray,
        faultInjector: ((Stage) -> Unit)?
    ) {
        check(!backupFile.exists()) { "Já existe uma cópia de recuperação; migração interrompida." }
        if (temporaryFile.exists()) check(temporaryFile.delete()) { "Não foi possível limpar a exportação temporária anterior." }

        val source = SQLiteDatabase.openDatabase(
            sourceFile.absolutePath,
            byteArrayOf(),
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null
        )
        val expectedCounts: Map<String, Long>
        val userVersion: Int
        try {
            source.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray<String>()).use { }
            source.rawQuery("PRAGMA journal_mode=DELETE", emptyArray<String>()).use { }
            expectedCounts = tableCounts(source)
            userVersion = pragmaInt(source, "PRAGMA user_version")
            source.execSQL(
                "ATTACH DATABASE ? AS encrypted KEY ?",
                arrayOf<Any>(temporaryFile.absolutePath, key)
            )
            try {
                source.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { cursor ->
                    check(cursor.moveToFirst()) { "SQLCipher não exportou o banco." }
                }
                source.execSQL("PRAGMA encrypted.user_version = $userVersion")
            } finally {
                runCatching { source.execSQL("DETACH DATABASE encrypted") }
            }
        } finally {
            source.close()
        }

        verifyEncrypted(temporaryFile, key, expectedCounts)
        faultInjector?.invoke(Stage.AFTER_EXPORT_VALIDATED)

        check(sourceFile.renameTo(backupFile)) { "Não foi possível criar a cópia de recuperação da base original." }
        try {
            faultInjector?.invoke(Stage.AFTER_PLAINTEXT_BACKUP_RENAMED)
            check(temporaryFile.renameTo(sourceFile)) { "Não foi possível promover a base criptografada." }
            faultInjector?.invoke(Stage.AFTER_ENCRYPTED_DATABASE_PROMOTED)
            verifyEncrypted(sourceFile, key, expectedCounts)
            check(backupFile.delete() || !backupFile.exists()) {
                "A base foi criptografada, mas a cópia temporária não pôde ser removida. A abertura foi bloqueada por segurança."
            }
            File(sourceFile.absolutePath + "-wal").delete()
            File(sourceFile.absolutePath + "-shm").delete()
        } catch (error: Throwable) {
            val promotedIsValid = sourceFile.exists() &&
                runCatching { verifyEncrypted(sourceFile, key, expectedCounts); true }.getOrDefault(false)
            if (!promotedIsValid && backupFile.exists()) {
                if (sourceFile.exists()) {
                    if (failedFile.exists()) failedFile.delete()
                    sourceFile.renameTo(failedFile)
                }
                if (!sourceFile.exists()) backupFile.renameTo(sourceFile)
            }
            throw error
        }
    }

    private fun verifyEncrypted(file: File, key: ByteArray, expectedCounts: Map<String, Long>? = null) {
        check(file.isFile && file.length() > 0) { "A cópia criptografada está vazia." }
        check(!isPlainSqlite(file)) { "A cópia de destino ainda está em texto SQLite legível." }
        val encrypted = SQLiteDatabase.openDatabase(
            file.absolutePath,
            key,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null
        )
        try {
            encrypted.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "A verificação de integridade SQLCipher falhou."
                }
            }
            val actualCounts = tableCounts(encrypted)
            if (expectedCounts != null) {
                check(actualCounts == expectedCounts) {
                    "A contagem de registros divergiu durante a migração: origem=$expectedCounts destino=$actualCounts"
                }
            }
        } finally {
            encrypted.close()
        }
    }

    private fun tableCounts(database: SQLiteDatabase): Map<String, Long> {
        val names = mutableListOf<String>()
        database.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            emptyArray<String>()
        ).use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        return names.associateWith { name ->
            val escaped = name.replace("\"", "\"\"")
            database.rawQuery("SELECT COUNT(*) FROM \"$escaped\"", emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst()) { "Não foi possível contar os registros de $name." }
                cursor.getLong(0)
            }
        }
    }

    private fun pragmaInt(database: SQLiteDatabase, query: String): Int =
        database.rawQuery(query, emptyArray<String>()).use { cursor ->
            check(cursor.moveToFirst()) { "Não foi possível consultar $query." }
            cursor.getInt(0)
        }

    private fun isPlainSqlite(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        FileInputStream(file).use { input ->
            if (input.read(header) != header.size) return false
        }
        return header.contentEquals(SQLITE_HEADER)
    }
}
