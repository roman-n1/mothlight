package io.github.mothlight

/** Optional metadata capability. Default values and SQL expressions are deliberately omitted. */
sealed interface DatabaseSchema {
    data object Unsupported : DatabaseSchema
    data class Available(val columns: List<DatabaseColumn>, val truncated: Boolean = false) : DatabaseSchema
}

/** notNull is the declared SQLite constraint, not an inference about effective nullability. */
data class DatabaseColumn(
    val name: String,
    val declaredType: String,
    val notNull: Boolean,
    val primaryKeyPosition: Int,
    val kind: DatabaseColumnKind = DatabaseColumnKind.Regular,
)

enum class DatabaseColumnKind { Regular, Hidden, GeneratedVirtual, GeneratedStored }
