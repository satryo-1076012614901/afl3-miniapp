package ac.sfj.afl3.domain

/**
 * Informasi koneksi yang dibaca dari satu endpoint database (RW atau RO).
 */
data class DatabaseProbe(
    val databaseName: String,
    val schemaName: String?,
    val serverVersion: String,
    /** `true` bila transaksi berjalan read-only (koneksi dari pool RO). */
    val readOnlyTransaction: Boolean,
    /** `true` bila server adalah replika (hot standby). */
    val inRecovery: Boolean,
)
