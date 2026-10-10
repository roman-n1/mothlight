package io.github.mothlight.shark

import android.content.Context
import android.os.Debug

/** Explicit capture can pause the application. Uses app-private cache storage, never external storage. */
fun androidRetentionAnalyzer(context: Context): SharkRetentionAnalyzer = SharkRetentionAnalyzer(
    context.applicationContext.cacheDir,
    HeapDumpCapture { destination -> Debug.dumpHprofData(destination.absolutePath) },
)
