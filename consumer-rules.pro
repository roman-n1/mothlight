# Mothlight Android consumer rules (automatically bundled in every Android AAR).
# Mothlight production code must remain reflection-free. Platform integrations
# use typed calls and explicit host injection, never reflective member lookup.
# Compose, coroutines, Room, SQLite, Ktor, OkHttp and Retrofit ship their own rules.
# Do not add package-wide -keep, -dontwarn or -dontobfuscate workarounds.
# scripts/check_shrinker.py guards source wiring, reflection and packaged rules.

# HPROF serialized schema: these two fields identify watched objects across heap captures.
# Keep their names and storage, even though there are no reflective calls at runtime.
-keep class io.github.mothlight.retention.HeapWatchReference {
    java.lang.String sessionId;
    long watchId;
}
