package ac.sfj.afl3.domain

/**
 * Informasi koneksi yang dibaca dari satu endpoint database (RW atau RO).
 */
data class DatabaseProbe(
    val databaseName: String,
    val schemaName: String?,
    val serverVersion: String,
    /** `true` bila statement berjalan di dalam transaksi read-only (tidak membuktikan asal pool). */
    val readOnlyTransaction: Boolean,
    /** `true` bila server adalah replika (hot standby). */
    val inRecovery: Boolean,
)
