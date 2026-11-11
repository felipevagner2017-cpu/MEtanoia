package br.com.metanoia.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SqlCipherDatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class Fixture(
        val databaseName: String = "sqlcipher-test-${UUID.randomUUID()}.db",
        val keyId: String = "sqlcipher-test-${UUID.randomUUID()}",
        val planId: String = "fasting-${UUID.randomUUID()}",
        val now: Long = System.currentTimeMillis()
    )

    @Test
    fun plaintextRoomDatabaseIsEncryptedAndAllPersonalTablesSurviveReopen() = runBlocking {
        val f = Fixture()
        val plain = Room.databaseBuilder(context, MetanoiaDatabase::class.java, f.databaseName).build()
        val agendaId = plain.agendaDao().insert(
            AgendaItem(title = "Compromisso preservado", scheduledAt = f.now, completed = true)
        )
        val journalId = plain.journalDao().insert(
            JournalEntry(title = "Diário preservado", content = "Relato privado de teste", dimension = LifeDimension.MATRIMONIAL, createdAt = f.now, updatedAt = f.now)
        )
        val reviewId = plain.reviewDao().insert(
            ReviewEntry(period = ReviewPeriod.WEEKLY, periodStart = f.now - 7 * 86400000L, periodEnd = f.now, reflection = "Revisão preservada", createdAt = f.now)
        )
        plain.confessionDao().insert(ConfessionEntry(dayKey = "2026-11-11", content = "Confissão de teste preservada", createdAt = f.now, updatedAt = f.now))
        plain.dailyAnalysisDao().save(
            DailyAnalysisEntry(
                dayKey = "2026-11-11", categories = "private", dimensions = "MATRIMONIAL",
                emotionalState = "registrado", summary = "Resumo preservado", attention = "atenção",
                learning = "aprendizado", direction = "direção", nextStep = "próximo passo",
                scriptureReference = "Efésios 4:1–3", scriptureContext = "Contexto preservado", updatedAt = f.now
            )
        )
        plain.fastingDao().insertPlan(
            FastingPlan(
                id = f.planId, dimension = LifeDimension.SPIRITUAL, type = "NON_FOOD",
                identifiedNeed = "Atenção à rotina", recommendationReason = "Registro de teste",
                purpose = "Separar um período de oração", scriptureReferences = "Marcos 1:35",
                scriptureContext = "Jesus buscava lugar solitário para orar",
                preparation = "Organizar o horário", dailyGuidance = "Orar e refletir",
                durationDays = 1, startAt = f.now, endAt = f.now + 86400000L, createdAt = f.now
            )
        )
        plain.fastingDao().insertDailyEntry(
            FastingDailyEntry(id = "entry-${f.planId}", planId = f.planId, dayIndex = 0, reflection = "Reflexão preservada", experience = "Experiência preservada", recordedAt = f.now)
        )
        plain.close()

        val key = SqlCipherDatabaseMigration.ensureEncrypted(context, f.databaseName, f.keyId)
        val databaseFile = context.getDatabasePath(f.databaseName)
        assertFalse("O arquivo ativo não pode continuar com o cabeçalho SQLite em texto", hasPlainSqliteHeader(databaseFile))
        assertFalse("A cópia de recuperação deve ser removida após a validação", File(databaseFile.absolutePath + ".plaintext-recovery").exists())

        val encrypted = openEncrypted(f, key)
        assertEquals("Compromisso preservado", encrypted.agendaDao().findById(agendaId)?.title)
        assertEquals("Relato privado de teste", encrypted.journalDao().observeAll().first().single { it.id == journalId }.content)
        assertEquals("Revisão preservada", encrypted.reviewDao().observeHistory().first().single { it.id == reviewId }.reflection)
        assertEquals("Confissão de teste preservada", encrypted.confessionDao().findForDay("2026-11-11")?.content)
        assertEquals("Resumo preservado", encrypted.dailyAnalysisDao().findForDay("2026-11-11")?.summary)
        assertEquals("NON_FOOD", encrypted.fastingDao().findPlan(f.planId)?.type)
        assertEquals("Reflexão preservada", encrypted.fastingDao().findDailyEntry(f.planId, 0)?.reflection)

        val entry = encrypted.fastingDao().findDailyEntry(f.planId, 0)!!
        encrypted.fastingDao().updateDailyEntry(entry.copy(reflection = "Reflexão atualizada"))
        assertEquals("Reflexão atualizada", encrypted.fastingDao().findDailyEntry(f.planId, 0)?.reflection)
        encrypted.fastingDao().deletePlanAndEntries(f.planId)
        assertNull(encrypted.fastingDao().findPlan(f.planId))
        assertNull(encrypted.fastingDao().findDailyEntry(f.planId, 0))
        assertEquals("Compromisso preservado", encrypted.agendaDao().findById(agendaId)?.title)
        assertEquals("Relato privado de teste", encrypted.journalDao().observeAll().first().single { it.id == journalId }.content)
        encrypted.close()

        val reopenedKey = SqlCipherDatabaseMigration.ensureEncrypted(context, f.databaseName, f.keyId)
        val reopened = openEncrypted(f, reopenedKey)
        assertEquals("Compromisso preservado", reopened.agendaDao().findById(agendaId)?.title)
        assertEquals("Relato privado de teste", reopened.journalDao().observeAll().first().single { it.id == journalId }.content)
        assertEquals("Revisão preservada", reopened.reviewDao().observeHistory().first().single { it.id == reviewId }.reflection)
        reopened.close()
        context.deleteDatabase(f.databaseName)
        Unit
    }

    @Test
    fun interruptionAfterPlaintextRenameRestoresSourceAndNextLaunchCompletesMigration() = runBlocking {
        val f = Fixture()
        val plain = Room.databaseBuilder(context, MetanoiaDatabase::class.java, f.databaseName).build()
        val id = plain.agendaDao().insert(AgendaItem(title = "Recuperação", scheduledAt = f.now))
        plain.close()
        val file = context.getDatabasePath(f.databaseName)

        assertThrows(IOException::class.java) {
            SqlCipherDatabaseMigration.ensureEncrypted(context, f.databaseName, f.keyId) { stage ->
                if (stage == SqlCipherDatabaseMigration.Stage.AFTER_PLAINTEXT_BACKUP_RENAMED) {
                    throw IOException("simulated process interruption")
                }
            }
        }
        assertTrue("A base original precisa ser restaurada após a falha", hasPlainSqliteHeader(file))
        val key = SqlCipherDatabaseMigration.ensureEncrypted(context, f.databaseName, f.keyId)
        assertFalse("A segunda abertura deve concluir a criptografia", hasPlainSqliteHeader(file))
        val encrypted = openEncrypted(f, key)
        assertEquals("Recuperação", encrypted.agendaDao().findById(id)?.title)
        encrypted.close()
        context.deleteDatabase(f.databaseName)
        Unit
    }

    @Test
    fun interruptionAfterEncryptedPromotionIsRecoveredWithoutLosingEitherCopy() = runBlocking {
        val f = Fixture()
        val plain = Room.databaseBuilder(context, MetanoiaDatabase::class.java, f.databaseName).build()
        val id = plain.agendaDao().insert(AgendaItem(title = "Promoção recuperável", scheduledAt = f.now))
        plain.close()
        val file = context.getDatabasePath(f.databaseName)

        assertThrows(IOException::class.java) {
            SqlCipherDatabaseMigration.ensureEncrypted(context, f.databaseName, f.keyId) { stage ->
                if (stage == SqlCipherDatabaseMigration.Stage.AFTER_ENCRYPTED_DATABASE_PROMOTED) {
                    throw IOException("simulated process interruption")
                }
            }
        }
        assertFalse("A cópia promovida deve estar cifrada", hasPlainSqliteHeader(file))
        assertTrue("A cópia de recuperação deve existir até a próxima validação", File(file.absolutePath + ".plaintext-recovery").exists())

        val key = SqlCipherDatabaseMigration.ensureEncrypted(context, f.databaseName, f.keyId)
        assertFalse(File(file.absolutePath + ".plaintext-recovery").exists())
        val encrypted = openEncrypted(f, key)
        assertEquals("Promoção recuperável", encrypted.agendaDao().findById(id)?.title)
        encrypted.close()
        context.deleteDatabase(f.databaseName)
        Unit
    }

    private fun openEncrypted(f: Fixture, key: ByteArray): MetanoiaDatabase =
        Room.databaseBuilder(context, MetanoiaDatabase::class.java, f.databaseName)
            .openHelperFactory(SupportOpenHelperFactory(key.copyOf()))
            .build()

    private fun hasPlainSqliteHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        return file.inputStream().use { input ->
            val header = ByteArray(16)
            input.read(header) == 16 && header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
        }
    }
}
