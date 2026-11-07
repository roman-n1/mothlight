# Minification and obfuscation

Every Android AAR packages the shared [consumer rules](../consumer-rules.pro).
KMP Android modules publish them through `optimization.consumerKeepRules.publish`;
the native Android View adapter uses `consumerProguardFiles`. No Mothlight-specific
keep rule is currently required. Compose, coroutines, Room, SQLite, Ktor, OkHttp
and Retrofit supply their own rules. Host models and custom reflective tools
remain the host's responsibility.

## No reflective Mothlight implementation

Production sources must not inspect members or obtain class names at runtime,
or depend directly on JNA or `kotlin-reflect`. The source guard rejects these
patterns. HTTP and SQL diagnostics use stable error categories while preserving
the delegate's original exceptions. This rule covers Mothlight's own code;
third-party implementations may use reflection internally.

The memory module uses direct platform calls and typed host injection. It has
no JNA binding or special keep rules. See [memory usage](memory.md) for metrics
and platform limitations.

## Verification

The Android release sample enables R8 shrinking, optimization and obfuscation.
It has no synthetic object root or local Mothlight keep rule. CI verifies packaged
consumer rules in all current AARs, checks that Mothlight classes were actually
renamed, and checks that the JVM memory JAR contains no obsolete JNA binding/rules.

Run `python3 scripts/check_shrinker.py` for source wiring and reflection checks.
After building the AARs, JVM memory JAR and release APK, use `--artifacts` to check
the packages and R8 mapping. Release mapping and test reports are uploaded by CI.
Successful shrinking does not establish runtime correctness on every device;
release-device smoke remains a separate check. iOS uses its native compiler and
linker rather than ProGuard/R8.
