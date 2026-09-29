package ac.sfj.afl3.repository

import ac.sfj.afl3.domain.DatabaseProbe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

/**
 * Akses data untuk pengecekan koneksi database.
 *
 * Endpoint yang dipakai (RW atau RO) ditentukan oleh transaksi pemanggil di layer service,
 * bukan oleh repository.
 */
@Repository
class DatabaseProbeRepository(private val jdbcTemplate: JdbcTemplate) {

    fun probe(): DatabaseProbe =
        jdbcTemplate.queryForObject(PROBE_SQL, PROBE_ROW_MAPPER)
            ?: error("Query probe tidak mengembalikan baris")

    private companion object {
        val PROBE_SQL = """
            SELECT current_database()                                AS database_name,
                   current_schema()                                  AS schema_name,
                   current_setting('server_version')                 AS server_version,
                   current_setting('transaction_read_only')::boolean AS read_only_transaction,
                   pg_is_in_recovery()                               AS in_recovery
        """.trimIndent()

        val PROBE_ROW_MAPPER = RowMapper { rs, _ ->
            DatabaseProbe(
                databaseName = rs.getString("database_name"),
                schemaName = rs.getString("schema_name"),
                serverVersion = rs.getString("server_version"),
                readOnlyTransaction = rs.getBoolean("read_only_transaction"),
                inRecovery = rs.getBoolean("in_recovery"),
            )
        }
    }
}
