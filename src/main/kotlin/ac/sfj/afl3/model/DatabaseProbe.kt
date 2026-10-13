package ac.sfj.afl3.model

/**
 * Informasi koneksi datasource aplikasi.
 */
data class DatabaseProbe(
    val databaseName: String,
    val schemaName: String?,
    val serverVersion: String,
)
