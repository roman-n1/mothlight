package io.github.mothlight.compose

import io.github.mothlight.NetworkTransaction

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
