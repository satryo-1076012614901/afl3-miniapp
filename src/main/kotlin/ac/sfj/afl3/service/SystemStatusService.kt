package ac.sfj.afl3.service

import ac.sfj.afl3.dto.DatabaseStatus
import ac.sfj.afl3.dto.HealthStatus
import ac.sfj.afl3.dto.SystemStatusResponse
import ac.sfj.afl3.repository.DatabaseProbeRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.NestedExceptionUtils
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Pengecekan kondisi sistem: aplikasi berjalan dan datasource database dapat diakses.
 */
@Service
class SystemStatusService(
    private val databaseProbeRepository: DatabaseProbeRepository,
    @Value("\${spring.application.name}") private val applicationName: String,
    @Value("\${app.environment}") private val environment: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getStatus(): SystemStatusResponse {
        val database = probe()

        return SystemStatusResponse(
            status = database.status,
            application = applicationName,
            environment = environment,
            timestamp = Instant.now(),
            database = database,
        )
    }

    private fun probe(): DatabaseStatus {
        val start = System.nanoTime()
        return try {
            val probe = databaseProbeRepository.probe()
            DatabaseStatus(
                status = HealthStatus.UP,
                latencyMs = elapsedMs(start),
                database = probe.databaseName,
                schema = probe.schemaName,
                serverVersion = probe.serverVersion,
            )
        } catch (ex: Exception) {
            log.warn("Pengecekan database gagal", ex)
            DatabaseStatus(
                status = HealthStatus.DOWN,
                latencyMs = elapsedMs(start),
                error = NestedExceptionUtils.getMostSpecificCause(ex).javaClass.simpleName,
            )
        }
    }

    private fun elapsedMs(start: Long): Long = (System.nanoTime() - start) / 1_000_000
}
