# HTTP inspector

The HTTP tab shows an adaptive request list and detail view. Panels at least
840 dp wide show both panes with a draggable, accessible divider. Narrower panels
open details in the full tool area; Back restores the list position. The width is
the panel's available width, not the device's screen width.

Search matches method, recorded URL, status code and transport-error category.
Quick filters distinguish HTTP errors (codes 400 and above), transport failures,
in-progress calls and slow completed/failed calls (at least 1000 ms). The slow
threshold is shown on the filter. In-progress calls have no measured duration.
Clear HTTP clears network history only; Clear all is in the panel options menu,
alongside the memory-overlay toggle. Recording remains an independent switch.

Details have Overview, Request and Response tabs. Copy actions export the recorded
URL, redacted cURL command, headers or original captured body. Body text can be
selected too. Omitted bodies keep their distinct capture states; a missing capture
is not presented as an empty server response. Transport, protocol and byte-size
details not supplied by the recorder are not inferred by the interface.

## JSON

Captured JSON objects and arrays open as a tree. Click or keyboard-activate a
nonempty container to expand/collapse that node. Child expansion is preserved
when its parent is collapsed, so reopening the parent restores the branch.
Collapse all folds the root. Text shows the formatted captured JSON; Copy keeps
the original captured text, regardless of presentation mode. Nonstructured or
malformed bodies remain readable as text. Empty objects/arrays are leaves.

Tree rows use JSON Pointer paths to distinguish array indices and property names
containing slash or tilde. Traversal is iterative and capped at 2000 visible rows,
with an explicit limit message; the complete captured body remains available in
Text mode. Rows are rendered lazily. Long tree values are ellipsized; use Text or
Copy for their full recorded contents. This limit is independent of capture limits.

`MothlightPanelState` retains the HTTP query, filter, request selection, detail tab,
list position and pane split for the current recorder. Each request's Request and
Response bodies have independent JSON view state and scroll position. Expansion
state contains paths rather than payload copies. Entries are pruned on the next
HTTP-tab observation after requests are cleared/evicted. Switching tools removes
the HTTP composition and collector. Keep the host-owned panel state above a
visibility gate to retain it across panel closure; recreating it resets UI state.

The panel inherits MaterialTheme and has English/Russian controls. Desktop UI
tests cover wide/narrow navigation, filters, local/global clear, pane resizing,
JSON branches, state restoration and a fake clipboard that verifies masking
without accessing the system clipboard. Platform compilation does not establish
physical-device rendering or platform clipboard behavior.
