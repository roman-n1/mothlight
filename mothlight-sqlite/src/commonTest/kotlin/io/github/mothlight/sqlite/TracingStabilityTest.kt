package io.github.mothlight.sqlite

import androidx.sqlite.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.mothlight.SqlTraceRecorder
import kotlin.test.*

class TracingStabilityTest {
    @Test fun gateAndInitialClockFailuresDoNotPreventQueries() {
        val recorder = SqlTraceRecorder()
        for (driver in listOf(
            TracingSQLiteDriver(BundledSQLiteDriver(), recorder, { error("gate failed") }),
            TracingSQLiteDriver(BundledSQLiteDriver(), recorder, { true }, { error("clock failed") }),
        )) {
            driver.open(":memory:").use { connection ->
                connection.prepare("SELECT 42").use { statement ->
                    assertTrue(statement.step())
                    assertEquals(42L, statement.getLong(0))
                }
            }
        }
        assertTrue(recorder.snapshot.value.events.isEmpty())
    }

    @Test fun recorderFailureDoesNotBreakResetReuseOrCloseAndRecordingRecovers() {
        var fail = true
        val recorder = SqlTraceRecorder(enabled = { if (fail) error("recorder failed") else true })
        val driver = TracingSQLiteDriver(BundledSQLiteDriver(), recorder, { true }) { 10 }
        driver.open(":memory:").use { connection ->
            val statement = connection.prepare("SELECT 42")
            assertTrue(statement.step())
            statement.reset()
            assertTrue(recorder.snapshot.value.events.isEmpty())
            fail = false
            assertTrue(statement.step())
            assertEquals(42L, statement.getLong(0))
            statement.close()
        }
        assertEquals(1, recorder.snapshot.value.events.size)
    }

    @Test fun finishingClockFailureDoesNotBreakClose() {
        var reads = 0
        val recorder = SqlTraceRecorder()
        val driver = TracingSQLiteDriver(BundledSQLiteDriver(), recorder, { true }) {
            if (++reads == 1) 0 else error("clock offline")
        }
        driver.open(":memory:").use { connection ->
            connection.prepare("SELECT 42").use { assertTrue(it.step()) }
        }
        assertTrue(recorder.snapshot.value.events.isEmpty())
    }

    @Test fun delegateCloseAndResetFailuresAreNeverReplacedByRecorderFailures() {
        for (failClose in listOf(false, true)) {
            val original = IllegalStateException("original database failure")
            val bundled = BundledSQLiteDriver()
            val delegate = object : SQLiteDriver by bundled {
                override fun open(fileName: String): SQLiteConnection {
                    val connection = bundled.open(fileName)
                    return object : SQLiteConnection by connection {
                        override fun prepare(sql: String): SQLiteStatement {
                            val statement = connection.prepare(sql)
                            return object : SQLiteStatement by statement {
                                override fun reset() { statement.reset(); if (!failClose) throw original }
                                override fun close() { statement.close(); if (failClose) throw original }
                            }
                        }
                    }
                }
            }
            val recorder = SqlTraceRecorder(enabled = { error("diagnostics failed") })
            TracingSQLiteDriver(delegate, recorder, { true }).open(":memory:").use { connection ->
                val statement = connection.prepare("SELECT 42")
                assertTrue(statement.step())
                val caught = assertFailsWith<IllegalStateException> {
                    if (failClose) statement.close() else statement.reset()
                }
                assertSame(original, caught)
                if (!failClose) statement.close()
            }
        }
    }
}
