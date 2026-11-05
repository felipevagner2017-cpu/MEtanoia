package br.com.metanoia.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncNonceCounterTest {
    @Test
    fun countersAreScopedByAccountDeviceAndPurposeAndPrimaryKeyIsUnique() = runBlocking {
        val db = openFreshDatabase()
        try {
            val dao = db.syncNonceCounterDao()
            assertEquals(NonceCounterReservation(0, 3), dao.reserveBlock("account-A", "device-A", "record", 3))
            assertEquals(NonceCounterReservation(0, 1), dao.reserveBlock("account-A", "device-B", "record"))
            assertEquals(NonceCounterReservation(0, 1), dao.reserveBlock("account-A", "device-A", "envelope"))
            assertEquals(NonceCounterReservation(0, 1), dao.reserveBlock("account-B", "device-A", "record"))
            assertEquals(3L, dao.currentNextCounter("account-A", "device-A", "record"))

            var rejected = false
            try {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO sync_nonce_counters(accountId,deviceId,purpose,nextCounter) VALUES('account-A','device-A','record',0)"
                )
            } catch (_: SQLiteConstraintException) {
                rejected = true
            }
            assertTrue("Composite primary key must reject a duplicate tuple", rejected)
        } finally {
            db.close()
            deleteDatabase()
        }
    }

    @Test
    fun concurrentReservationsAreDisjointAndMonotonic() = runBlocking {
        val db = openFreshDatabase()
        try {
            val dao = db.syncNonceCounterDao()
            val reservations = (0 until 48).map {
                async(Dispatchers.IO) { dao.reserveBlock("acct", "device", "payload", 7) }
            }.awaitAll()
            val ordered = reservations.sortedBy { it.startInclusive }
            assertEquals(48, ordered.size)
            ordered.forEachIndexed { index, reservation ->
                assertEquals(index * 7L, reservation.startInclusive)
                assertEquals(index * 7L + 7L, reservation.endExclusive)
            }
            assertEquals(48L * 7L, dao.currentNextCounter("acct", "device", "payload"))
            assertEquals(48, reservations.map { it.startInclusive }.toSet().size)
        } finally {
            db.close()
            deleteDatabase()
        }
    }

    @Test
    fun committedReservationsAreNeverReusedAfterCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = DB_NAME
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name).build()
        val consumed = db.syncNonceCounterDao().reserveBlock("acct", "device", "record", 9)
        assertEquals(NonceCounterReservation(0, 9), consumed)
        db.close()

        db = Room.databaseBuilder(context, MetanoiaDatabase::class.java, name).build()
        try {
            val next = db.syncNonceCounterDao().reserveBlock("acct", "device", "record", 4)
            assertEquals(NonceCounterReservation(9, 13), next)
            assertEquals(13L, db.syncNonceCounterDao().currentNextCounter("acct", "device", "record"))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun failedAtomicReservationRollsBackAndCommittedButAbandonedBlockIsSkipped() = runBlocking {
        val db = openFreshDatabase()
        try {
            val sql = db.openHelper.writableDatabase
            sql.execSQL(
                """CREATE TRIGGER force_nonce_update_failure BEFORE UPDATE ON sync_nonce_counters
                   BEGIN SELECT RAISE(ABORT, 'simulated reservation interruption'); END"""
            )
            var failed = false
            try {
                db.syncNonceCounterDao().reserveBlock("acct", "device", "record", 5)
            } catch (_: Exception) {
                failed = true
            }
            assertTrue("Injected SQLite failure should abort the transaction", failed)
            assertNull(db.syncNonceCounterDao().currentNextCounter("acct", "device", "record"))

            sql.execSQL("DROP TRIGGER force_nonce_update_failure")
            val committed = db.syncNonceCounterDao().reserveBlock("acct", "device", "record", 5)
            assertEquals(NonceCounterReservation(0, 5), committed)

            // Simulate the caller crashing after the reservation committed but before using its block.
            val afterAbandonedBlock = db.syncNonceCounterDao().reserveBlock("acct", "device", "record", 2)
            assertEquals(NonceCounterReservation(5, 7), afterAbandonedBlock)
            assertNotEquals(committed.startInclusive, afterAbandonedBlock.startInclusive)
        } finally {
            db.close()
            deleteDatabase()
        }
    }

    private fun openFreshDatabase(): MetanoiaDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)
        return Room.databaseBuilder(context, MetanoiaDatabase::class.java, DB_NAME).build()
    }

    private fun deleteDatabase() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB_NAME)
    }

    companion object {
        private const val DB_NAME = "nonce_counter_instrumented_test.db"
    }
}
