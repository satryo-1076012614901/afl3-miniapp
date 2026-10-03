package ac.sfj.afl3.repository

import ac.sfj.afl3.domain.DatabaseProbe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

/**
 * Akses data untuk pengecekan koneksi database.
 */
@Repository
class DatabaseProbeRepository(private val jdbcTemplate: JdbcTemplate) {

    fun probe(): DatabaseProbe =
        jdbcTemplate.queryForObject(PROBE_SQL, PROBE_ROW_MAPPER)

    private companion object {
        val PROBE_SQL = """
            SELECT current_database()                AS database_name,
                   current_schema()                  AS schema_name,
                   current_setting('server_version') AS server_version
        """.trimIndent()

        val PROBE_ROW_MAPPER = RowMapper { rs, _ ->
            DatabaseProbe(
                databaseName = rs.getString("database_name"),
                schemaName = rs.getString("schema_name"),
                serverVersion = rs.getString("server_version"),
            )
        }
    }
}
