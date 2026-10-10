package io.github.mothlight.sqlite

import androidx.sqlite.SQLiteDriver
import io.github.mothlight.SqlTraceRecorder
import kotlin.time.Clock

class TracingSQLiteDriver(delegate: SQLiteDriver, recorder: SqlTraceRecorder, enabled: () -> Boolean, nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }) : SQLiteDriver by delegate
