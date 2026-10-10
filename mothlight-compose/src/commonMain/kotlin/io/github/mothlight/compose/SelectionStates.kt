package io.github.mothlight.compose

import io.github.mothlight.NetworkTransaction
import io.github.mothlight.retention.RetentionWatcher

sealed interface ToolSelection {
    data object Default : ToolSelection
    data class Named(val id: String) : ToolSelection
}
internal sealed interface NetworkSelection {
    data object Unselected : NetworkSelection
    data class Request(val id: String) : NetworkSelection
}
internal sealed interface RequestDetails {
    data object Prompt : RequestDetails
    data class Ready(val transaction: NetworkTransaction) : RequestDetails
}
internal sealed interface TableSelection {
    data object Prompt : TableSelection
    data class Chosen(val name: String) : TableSelection
}
internal sealed interface AnalysisSelection {
    data object First : AnalysisSelection
    data class Watched(val id: Long) : AnalysisSelection
}
sealed interface RetentionOverlay {
    data object Hidden : RetentionOverlay
    data class Visible(val watcher: RetentionWatcher, val action: RetentionAction = RetentionAction.ReadOnly) : RetentionOverlay
}
sealed interface RetentionAction {
    data object ReadOnly : RetentionAction
    data class OpenTool(val execute: () -> Unit) : RetentionAction
}
