package ac.sfj.afl3.dto

import java.time.Instant

enum class HealthStatus { UP, DOWN }

/** Response `GET /system/status`. */
data class SystemStatusResponse(
    val status: HealthStatus,
    val application: String,
    val environment: String,
    val timestamp: Instant,
    val database: DatabaseStatus,
)

data class DatabaseStatus(
    val status: HealthStatus,
    val latencyMs: Long,
    val database: String? = null,
    val schema: String? = null,
    val serverVersion: String? = null,
    /** Nama kelas penyebab kegagalan (tanpa pesan detail agar informasi internal tidak terekspos). */
    val error: String? = null,
)
