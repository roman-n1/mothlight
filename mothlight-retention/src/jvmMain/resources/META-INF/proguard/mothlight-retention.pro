# Stable HPROF schema; no reflective access to live objects.
-keep class io.github.mothlight.retention.HeapWatchReference {
    java.lang.String sessionId;
    long watchId;
}
