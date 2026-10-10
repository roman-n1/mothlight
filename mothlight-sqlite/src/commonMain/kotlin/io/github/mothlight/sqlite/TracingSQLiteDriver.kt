package io.github.mothlight.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import io.github.mothlight.SqlTraceRecorder
import kotlin.time.Clock

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
        val statement = delegate.prepare(sql)
        if (!enabled() || sql.startsWith(SqlTraceRecorder.PANEL_MARKER)) return statement
        return TracingStatement(statement, sql, recorder, nowMillis)
    }
}

private class TracingStatement(
    private val delegate: SQLiteStatement,
    private val sql: String,
    private val recorder: SqlTraceRecorder,
    private val nowMillis: () -> Long,
) : SQLiteStatement by delegate {
    private var startedAt = nowMillis()
    private var stepped = false
    private var errorType: String? = null
    override fun step(): Boolean {
        stepped = true
        return try { delegate.step() } catch (error: Throwable) {
            errorType = error::class.simpleName
            throw error
        }
    }
    override fun reset() {
        try { delegate.reset() } finally {
            finish()
            startedAt = nowMillis()
        }
    }
    override fun close() {
        try { delegate.close() } finally { finish() }
    }
    private fun finish() {
        if (!stepped) return
        recorder.record(sql, startedAt, nowMillis() - startedAt, errorType)
        stepped = false
        errorType = null
    }
}
