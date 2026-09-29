package ac.sfj.afl3.service

import ac.sfj.afl3.dto.DatabaseEndpointStatus
import ac.sfj.afl3.dto.DatabaseStatus
import ac.sfj.afl3.dto.HealthStatus
import ac.sfj.afl3.dto.SystemStatusResponse
import ac.sfj.afl3.repository.DatabaseProbeRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.NestedExceptionUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Pengecekan kondisi sistem: aplikasi berjalan dan kedua endpoint database dapat diakses.
 */
@Service
class SystemStatusService(
    private val databaseProbeRepository: DatabaseProbeRepository,
    transactionManager: PlatformTransactionManager,
    @Value("\${spring.application.name}") private val applicationName: String,
    @Value("\${app.environment}") private val environment: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Transaksi read-write -> pool RW. */
    private val readWriteTransaction = TransactionTemplate(transactionManager)

    /** Transaksi read-only -> pool RO (lihat config.DataSourceConfig). */
    private val readOnlyTransaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    fun getStatus(): SystemStatusResponse {
        val readWrite = probe("read-write", readWriteTransaction)
        val readOnly = probe("read-only", readOnlyTransaction)
        val overall =
            if (readWrite.status == HealthStatus.UP && readOnly.status == HealthStatus.UP) HealthStatus.UP
            else HealthStatus.DOWN

        return SystemStatusResponse(
            status = overall,
            application = applicationName,
            environment = environment,
            timestamp = Instant.now(),
            database = DatabaseStatus(readWrite = readWrite, readOnly = readOnly),
        )
    }

    private fun probe(target: String, transaction: TransactionTemplate): DatabaseEndpointStatus {
        val start = System.nanoTime()
        return try {
            val probe = transaction.execute { databaseProbeRepository.probe() }
                ?: error("Probe $target tidak mengembalikan hasil")
            DatabaseEndpointStatus(
                status = HealthStatus.UP,
                latencyMs = elapsedMs(start),
                database = probe.databaseName,
                schema = probe.schemaName,
                serverVersion = probe.serverVersion,
                readOnlyTransaction = probe.readOnlyTransaction,
                inRecovery = probe.inRecovery,
            )
        } catch (ex: Exception) {
            log.warn("Pengecekan database {} gagal", target, ex)
            DatabaseEndpointStatus(
                status = HealthStatus.DOWN,
                latencyMs = elapsedMs(start),
                error = NestedExceptionUtils.getMostSpecificCause(ex).javaClass.simpleName,
            )
        }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000
}
