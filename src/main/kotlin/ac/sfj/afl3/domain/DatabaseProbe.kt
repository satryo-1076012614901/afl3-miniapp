package ac.sfj.afl3.domain

/**
 * Informasi koneksi datasource aplikasi.
 */
data class DatabaseProbe(
    val databaseName: String,
    val schemaName: String?,
    val serverVersion: String,
)
