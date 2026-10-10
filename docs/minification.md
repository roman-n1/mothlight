# Minification and obfuscation

Mothlight ships Android consumer configuration in every AAR. A consuming app
normally needs no hand-written Mothlight keep rule. The shared source is
[`consumer-rules.pro`](../consumer-rules.pro); every KMP Android module explicitly
enables `optimization.consumerKeepRules.publish`, while the native Android View
adapter uses `consumerProguardFiles`. This follows the
[Android KMP publishing contract](https://developer.android.com/kotlin/multiplatform/plugin).

The current Android code uses typed calls, without reflective Mothlight class
lookup or JNI. The shared file is intentionally documented but has no keep
directives. Keeping all `io.github.mothlight.**` classes or disabling obfuscation
would hide issues and prevent useful shrinking. Compose, coroutines, Room,
SQLite, Ktor, OkHttp and Retrofit provide their own consumer configuration;
application models and custom reflective plugins remain the host's responsibility.

## No reflective Mothlight implementation

Mothlight production sources must not inspect members or obtain class names at
runtime, and must not depend directly on JNA or `kotlin-reflect`. The source guard
rejects those patterns. Error observations use stable `TransportError` and
`SqlError` categories while original exceptions still reach the caller.

The JVM memory module has no JNA binding or special keep rules. Windows supplies
heap and non-heap readings; process working set is unavailable by default. A host
can provide a typed `MemorySource` implemented with its native bridge without
changing the panel. Linux `/proc` and iOS Mach readers remain available.

This rule covers code owned by Mothlight. It does not claim that every internal
implementation of the JVM, Compose, coroutines, Retrofit or Room avoids reflection.
The Retrofit/Room samples exercise the host APIs; Mothlight adapters observe the
host-owned clients and databases and do not construct them reflectively.

## Verification and limits

The Android sample release enables R8 shrinking, optimization and obfuscation.
Its only local Mothlight keep rule preserves the deliberately held demo-object
field; that field is a sample leak root, not a library requirement.

CI builds the minified release APK, checks that Mothlight classes were actually
renamed, inspects all ten AARs for packaged consumer rules, and checks that the JVM JAR
contains no obsolete JNA binding/rules. `python3 scripts/check_shrinker.py --artifacts` performs
those checks after the builds; without the flag it checks source wiring only.
R8 configuration and mapping are uploaded alongside test results.

These checks prove packaging and successful shrinking; they do not establish
runtime correctness on every device or for arbitrary host plugins. Android
release-device smoke remains unperformed.
iOS uses the native compiler/linker and is not governed by ProGuard/R8 rules.
