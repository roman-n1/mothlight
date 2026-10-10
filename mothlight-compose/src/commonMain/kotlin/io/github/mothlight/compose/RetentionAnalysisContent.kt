package io.github.mothlight.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.mothlight.compose.resources.*
import io.github.mothlight.retention.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun RetentionAnalysisContent(watcher: RetentionWatcher, analyzer: RetentionAnalyzer) {
    if (analyzer === UnsupportedRetentionAnalyzer) {
        Text(stringResource(Res.string.analysis_unavailable), style = MaterialTheme.typography.bodySmall)
        return
    }
    val controller = remember(watcher, analyzer) { RetentionAnalysisController(watcher, analyzer) }
    val state by controller.states.collectAsState(controller.snapshot)
    val snapshot by watcher.snapshots.collectAsState(watcher.snapshot)
    val scope = rememberCoroutineScope()
    var job by remember(controller) { mutableStateOf<Job>(Job().apply { complete() }) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.analysis_capture_hint), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { job = scope.launch { controller.analyze() } }, enabled = canAnalyze(snapshot, state)) {
                Text(stringResource(Res.string.analysis_start))
            }
            if (state is RetentionAnalysisState.Running) {
                TextButton(onClick = { job.cancel() }) { Text(stringResource(Res.string.analysis_cancel)) }
            }
        }
        when (val current = state) {
            RetentionAnalysisState.Idle -> Unit
            is RetentionAnalysisState.Running -> Text(stringResource(Res.string.analysis_running))
            is RetentionAnalysisState.Finished -> when (val result = current.result) {
                RetentionAnalysisResult.Unsupported -> Text(stringResource(Res.string.analysis_unavailable))
                is RetentionAnalysisResult.Failed -> Text(stringResource(failureLabel(result.failure)))
                is RetentionAnalysisResult.Report -> RetentionReportContent(current.request, result)
            }
        }
    }
}

@Composable
private fun RetentionReportContent(request: RetentionAnalysisRequest, report: RetentionAnalysisResult.Report) {
    var selected by remember(report) { mutableStateOf<AnalysisSelection>(AnalysisSelection.First) }
    val rows = remember(request, report) { analysisRows(request, report) }
    val finding = remember(report, selected) { when (val selection = selected) {
        AnalysisSelection.First -> report.findings.take(1)
        is AnalysisSelection.Watched -> report.findings.filter { it.watchId == selection.id }
    } }
    Text(stringResource(Res.string.analysis_snapshot_hint), style = MaterialTheme.typography.bodySmall)
    rows.forEach { row ->
        TextButton(onClick = { selected = AnalysisSelection.Watched(row.id) }) {
            Text(stringResource(Res.string.analysis_finding, row.label, stringResource(findingLabel(row.status))))
        }
    }
    finding.filterIsInstance<RetentionFinding.StrongPath>().forEach { result ->
        val path = result.path
        Text(stringResource(Res.string.analysis_root, path.gcRootType))
        Text(stringResource(Res.string.analysis_signature, path.signature), style = MaterialTheme.typography.bodySmall)
        path.references.forEach { reference ->
            Text("${reference.ownerClass}.${reference.referenceName} →", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Text(path.targetClass, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

internal data class AnalysisRow(val id: Long, val label: String, val status: FindingStatus)
internal fun analysisRows(request: RetentionAnalysisRequest, report: RetentionAnalysisResult.Report): List<AnalysisRow> {
    val entries = request.entries.associateBy { it.id }
    return report.findings.filter { it.watchId in entries }.map { finding ->
        val entry = entries.getValue(finding.watchId)
        AnalysisRow(entry.id, entry.label, finding.status)
    }
}
internal fun canAnalyze(snapshot: RetentionSnapshot, state: RetentionAnalysisState) =
    snapshot.mode == WatcherMode.RUNNING && snapshot.retainedCount > 0 && state !is RetentionAnalysisState.Running
private fun findingLabel(status: FindingStatus) = when (status) {
    FindingStatus.STRONG_PATH -> Res.string.analysis_strong_path
    FindingStatus.NOT_IN_DUMP -> Res.string.analysis_not_in_dump
    FindingStatus.NO_STRONG_PATH -> Res.string.analysis_no_path
}
private fun failureLabel(failure: AnalysisFailure) = when (failure) {
    AnalysisFailure.CAPTURE -> Res.string.analysis_capture_failed
    AnalysisFailure.DUMP_TOO_LARGE -> Res.string.analysis_too_large
    AnalysisFailure.ANALYSIS -> Res.string.analysis_failed
    AnalysisFailure.CLEANUP -> Res.string.analysis_cleanup_failed
}
