package io.github.mothlight.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.github.mothlight.SqlTraceRecorder
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException

/** Passive wrapper. Bind calls go straight to the original statement; values are never copied. */
class TracingSQLiteDriver(
    private val delegate: SQLiteDriver,
    private val recorder: SqlTraceRecorder,
    private val enabled: () -> Boolean,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : SQLiteDriver by delegate {
    override fun open(fileName: String): SQLiteConnection = TracingConnection(delegate.open(fileName), recorder, enabled, nowMillis)
}

private class TracingConnection(
    private val delegate: SQLiteConnection,
    private val recorder: SqlTraceRecorder,
    private val enabled: () -> Boolean,
    private val nowMillis: () -> Long,
) : SQLiteConnection by delegate {
    override fun prepare(sql: String): SQLiteStatement {
        val timing = if (sql.startsWith(SqlTraceRecorder.PANEL_MARKER)) TraceTiming.Inactive else startTiming(enabled, nowMillis)
        val statement = delegate.prepare(sql)
        return when (timing) {
            TraceTiming.Inactive -> statement
            is TraceTiming.Started -> TracingStatement(statement, sql, recorder, nowMillis, timing)
        }
    }
}

private class TracingStatement(
    private val delegate: SQLiteStatement,
    private val sql: String,
    private val recorder: SqlTraceRecorder,
    private val nowMillis: () -> Long,
    private var timing: TraceTiming,
) : SQLiteStatement by delegate {
    private var stepped = false
    private var outcome: io.github.mothlight.SqlOutcome = io.github.mothlight.SqlOutcome.Succeeded
    override fun step(): Boolean {
        stepped = true
        return try { delegate.step() } catch (error: Throwable) {
            // Stable across shrinking and platforms; the original exception still reaches the caller.
            outcome = io.github.mothlight.SqlOutcome.Failed("SqlError")
            throw error
        }
    }
    override fun reset() {
        try { delegate.reset() } catch (failure: Throwable) {
            stepped = false
            outcome = io.github.mothlight.SqlOutcome.Succeeded
            throw failure
        }
        finish()
        timing = startTiming({ true }, nowMillis)
    }
    override fun close() {
        try { delegate.close() } catch (failure: Throwable) {
            stepped = false
            outcome = io.github.mothlight.SqlOutcome.Succeeded
            throw failure
        }
        finish()
    }
    private fun finish() {
        if (!stepped) return
        stepped = false
        val finishedOutcome = outcome
        outcome = io.github.mothlight.SqlOutcome.Succeeded
        val started = timing
        if (started !is TraceTiming.Started) return
        try { recorder.record(sql, started.millis, nowMillis() - started.millis, finishedOutcome) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Observation must not fail a completed database operation. */ }
    }
}

private sealed interface TraceTiming {
    data object Inactive : TraceTiming
    data class Started(val millis: Long) : TraceTiming
}

private fun startTiming(enabled: () -> Boolean, nowMillis: () -> Long): TraceTiming = try {
    if (enabled()) TraceTiming.Started(nowMillis()) else TraceTiming.Inactive
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    TraceTiming.Inactive
}
