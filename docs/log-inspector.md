# Log inspector

The Logs tab searches messages and tags case-insensitively and combines that
search with one exact severity and one exact tag filter. All levels and all tags
are the defaults. Available tags come from the current bounded buffer; selecting
a tag preserves the filter even if its last entry is later evicted.

Rows show relative time, a text severity label, tag and a two-line message preview.
Open a row to read its complete message, inspect its original timestamp and copy
the record. Opening a row pauses automatic scrolling so incoming logs do not
interrupt reading. Only one message is expanded at a time. The wide panel puts
search, tag selection and follow controls on one row; narrow panels stack them.

## Following and reading

Following starts enabled and shows the newest matching entry first. Pause scrolling
stops following; it does not change Mothlight's recording switch. Scrolling away
from the top also pauses following. Follow new logs returns to the newest matching
entry. The same button shows the number of new matching entries still retained in
the buffer while reading. Its location stays fixed when new entries arrive.

The counter describes the current buffer, not a lifetime count: conflated updates
or more arrivals than the buffer capacity can remove earlier unseen records.
Pause does not freeze or copy the log buffer. Stable item keys preserve the reading
anchor while it remains retained. Eviction can remove that anchor or an expanded
message; clear removes displayed records too. No paused history survives outside
the recorder's retention policy.

`MothlightPanelState` retains query, severity/tag filters, expanded record ID,
follow mode and list position for the current log feed. It keeps bounded sets of
record IDs for the new-entry counter, not extra message snapshots. Leaving the
tool removes its composition and collector. Reopening a paused view counts matching
retained records that arrived while it was hidden. Keep the panel state above a
visibility gate to retain it across panel closure.

## Time and copying

The host owns `LogEntry.timestampMillis`; it can use an epoch or another origin.
The inspector displays `t+MM:SS.mmm` relative to the first buffered entry when
viewing begins. That origin stays fixed through eviction and resets when an empty
buffer is observed or the UI clears logs. Out-of-order timestamps can produce
negative relative times. An expanded row exposes the original millisecond value.

Copy entry exports its original timestamp, severity, tag and complete message.
Copy selection exports matching retained records in display order, newest first,
with a blank line between entries. Larger exports are assembled only when Copy
is clicked. Clear logs affects this feed only; Clear all remains in panel options.
Copy is disabled when no records match.

Log messages and tags are host-supplied text. The host must scrub them before
recording, as required by `LogEntry`; this UI does not add general-purpose redaction.
The copy actions use recorded text and preserve its line breaks.

## Verification

Common tests cover combined filters, export content, bounded unseen counts and
relative clocks including overflow boundaries. Desktop UI tests cover narrow
controls, combined filters, full-message copying with an in-memory clipboard,
state restoration, a stable reading anchor during new arrivals, automatic pause
on scrolling, resume, eviction and clear. Reproducible light/dark screenshots are
produced by `InspectorReviewScreenshotsTest`. Device rendering and platform
clipboard behavior remain separate checks.
