package ac.sfj.afl3.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import javax.sql.DataSource

/**
 * Routing koneksi database read-write (RW) dan read-only (RO).
 *
 * - Transaksi `@Transactional(readOnly = true)` -> koneksi diambil dari pool RO (`app.datasource.ro`).
 * - Transaksi biasa, akses tanpa transaksi, dan Flyway -> pool RW (`app.datasource.rw`).
 *
 * Koneksi fisik baru diambil saat statement pertama dieksekusi ([LazyConnectionDataSourceProxy]),
 * sehingga flag read-only transaksi sudah diketahui ketika pool dipilih.
 */
@Configuration(proxyBeanMethods = false)
class DataSourceConfig {

    /** Pool read-write. Dipakai juga oleh Flyway untuk migrasi schema. */
    @Bean(defaultCandidate = false)
    @Qualifier(READ_WRITE)
    @FlywayDataSource
    @ConfigurationProperties("app.datasource.rw")
    fun readWriteDataSource(): HikariDataSource =
        DataSourceBuilder.create().type(HikariDataSource::class.java).build()

    /** Pool read-only. Koneksi di-set read-only melalui `app.datasource.ro.read-only: true`. */
    @Bean(defaultCandidate = false)
    @Qualifier(READ_ONLY)
    @ConfigurationProperties("app.datasource.ro")
    fun readOnlyDataSource(): HikariDataSource =
        DataSourceBuilder.create().type(HikariDataSource::class.java).build()

    /** DataSource utama yang dipakai JPA dan JdbcTemplate; memilih pool RW atau RO sesuai sifat transaksi. */
    @Bean
    @Primary
    fun dataSource(
        @Qualifier(READ_WRITE) readWrite: DataSource,
        @Qualifier(READ_ONLY) readOnly: DataSource,
    ): DataSource = LazyConnectionDataSourceProxy(readWrite).apply {
        setReadOnlyDataSource(readOnly)
    }

    companion object {
        const val READ_WRITE = "readWrite"
        const val READ_ONLY = "readOnly"
    }
}
