# Mothlight Android consumer rules (automatically bundled in every Android AAR).
# Mothlight production code must remain reflection-free. Platform integrations
# use typed calls and explicit host injection, never reflective member lookup.
# No Mothlight keep directive is currently necessary: public APIs alone do not
# require stable names when the app and its libraries are optimized together.
# Compose, coroutines, Room, SQLite, Ktor, OkHttp and Retrofit ship their own rules.
# Do not add package-wide -keep, -dontwarn or -dontobfuscate workarounds.
# scripts/check_shrinker.py guards source wiring, reflection and packaged rules.
