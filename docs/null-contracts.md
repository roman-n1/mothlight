# Null-free contracts

Mothlight's handwritten production Kotlin code does not declare nullable types,
accept or return `null`, or use `Optional`/`Option`/`Maybe` containers. This follows
the [Elegant Objects principle](https://www.yegor256.com/2014/05/13/why-null-is-bad.html):
give an object a defined meaning and behavior instead of making callers ask whether
an object exists. Generic presence wrappers are not an alternative.

The rule applies to public APIs, internal properties, parameters, return types and
generic bounds in every library source set, including Android View integration.
It does not apply to third-party implementations, generated code, tests or host
samples (for example Android callbacks and deliberately released demo roots).
Java and platform APIs can still return missing references: adapters immediately
normalize these into a domain state, skip an absent record, or report a specific
contract failure. Safe calls and Elvis expressions at those boundaries remain
available; propagating nullable types into library contracts does not.

## Meaningful absence

| Data or behavior | Contract |
| --- | --- |
| HTTP body, realtime payload, invariant sample | `CapturedBody.Text`, `NotCaptured`, `TooLarge`, `NotJson` |
| HTTP request state | `Status.InProgress`, `Completed(code, durationMillis)`, `Failed(error, durationMillis)` |
| Database cell | `DatabaseCell.Text`, `SqlNull`, `Blob` |
| Memory measurement | `MemoryReading.Bytes`, `Unavailable` |
| Screen metadata | `ScreenContext.Named`, `Unspecified` |
| SQL execution outcome | `SqlOutcome.Succeeded`, `Failed(category)` |
| Heap finding | `RetentionFinding.StrongPath(watchId, path)`, `NotInDump(watchId)`, `NoStrongPath(watchId)` |
| Persistence without a host store | `TransientMemoryOverlayStore` (settings still own their in-memory value) |
| No heap analyzer installed | `UnsupportedRetentionAnalyzer` |
| Overlay configuration | `RetentionOverlay.Hidden`, `Visible(watcher, action)` |
| Read-only badge | `RetentionAction.ReadOnly`; use `OpenTool` for an interactive badge |

These states preserve domain distinctions. A measured zero is not unavailable;
an empty SQL text cell is not SQL NULL; text containing `[BLOB]` is not a BLOB.
JSON containing a `null` field is still valid captured JSON. A pending HTTP request
has no fabricated response code or elapsed duration. A strong-path finding always
contains its path, while other findings do not expose that property at all.

No NPE is used to validate an executor's result. A malformed SQLite count result
raises `IllegalStateException` with a fixed, non-sensitive contract message; the
panel's existing error state handles it. Ordinary platform exceptions and coroutine
cancellation retain their existing semantics. This policy is not a promise that
arbitrary third-party code or a Java caller violating a Kotlin contract cannot fail.

## Migrating from the earlier alpha API

This is a source-breaking change. There are no nullable compatibility overloads.

- Replace missing body arguments with `CapturedBody.NotCaptured`. String overloads
  remain for recording real text; observable bodies are now `CapturedBody` values.
- Read HTTP code and duration from `Status.Completed`; read failure duration from
  `Status.Failed`. The old nullable fields on `NetworkTransaction` are removed.
- Custom `DatabaseSource` and `SqlReadExecutor` implementations return
  `List<List<DatabaseCell>>`. `readResult` already converts SQLite values.
- Wrap valid memory measurements with `MemoryReading.Bytes(value)`, or normalize
  platform sentinel values with `MemoryReading.bytes(rawValue)`. Use `Unavailable`
  for missing measurements. `MemoryReading.limit` also rejects zero/unbounded limits.
- Pass `ScreenContext.Named("Details")` for screen metadata. Omit the argument to
  use `Unspecified`. SQL events now expose `outcome` instead of nullable `errorType`.
- Match on `RetentionFinding.StrongPath` before accessing `path`. An absent
  analyzer is represented by the default `UnsupportedRetentionAnalyzer` object.
- Pass `retention = RetentionOverlay.Visible(watcher, RetentionAction.OpenTool { ... })`
  to the Compose overlay or Android View factory. The default is `Hidden`.
- `MothlightPanelState.selectedTool` exposes `ToolSelection.Default` or `Named(id)`.
  `selectTool(id)` remains the host entry point.
- `ProbeFeed<T : Any>` rejects nullable payload types at compilation.

## Enforcement

Run the same commands as the dedicated `null-contracts` CI job:

```shell
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check_null_contracts.py
```

The scanner discovers every `mothlight-*/src/main` and `src/*Main` tree, including
new modules and shared platform source sets. It rejects nullable Kotlin syntax,
the `null` literal, `!!`, `checkNotNull`, `requireNotNull`, nullable annotations,
and the `Optional` family, `Option`, `Maybe`, `Some` and `None`. Imports and type
aliases cannot hide those symbols. Type parameters need explicit non-null upper
bounds. Generic sealed containers are prohibited to prevent introducing a renamed
generic presence wrapper; use concrete domain states instead.

Comments and literal strings are ignored, but executable Kotlin `${...}` templates
are scanned, including templates nested inside strings. Unparseable literals fail
closed. Tests exercise callback and nested generic nullability, escaped identifiers,
aliases, generic bounds, templates, source discovery and Java boundary annotations.

There is no suppression, baseline or file allow-list. The `null-contracts` check
is required by [main's active ruleset](https://github.com/roman-n1/mothlight/rules/24845776);
new violations block merging. Keep this check and its tests enabled. New hand-written
presence containers disguised through unrelated names or arbitrary control flow must
also be rejected during review: the scanner enforces source constructs, not a formal
proof of all program behavior.
