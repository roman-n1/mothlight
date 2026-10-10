# Retrofit and OkHttp include their consumer rules. No broad keep rules.

# WHY: this synthetic leak root is deliberately write-only until Release.
# R8 must preserve it so the release demo demonstrates actual retention.
-keepclassmembers class io.github.mothlight.sample.android.MainActivity {
    java.lang.Object heldObject;
}
